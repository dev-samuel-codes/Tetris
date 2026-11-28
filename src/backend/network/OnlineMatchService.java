package backend.network;

import backend.score.ScoreRepository;
import frontend.engine.GameEngine;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import shared.network.OnlineFrame;

// 클라이언트는 입력만 전송하고 서버가 시간, 보드, 점수와 승패를 결정합니다.
public final class OnlineMatchService implements AutoCloseable {
    private static final long COUNTDOWN = TimeUnit.SECONDS.toNanos(3);
    private static final long GRAVITY = TimeUnit.MILLISECONDS.toNanos(400);
    private static final long WAIT_EXPIRY = TimeUnit.MINUTES.toNanos(10);
    private static final long IDLE_TIMEOUT = TimeUnit.SECONDS.toNanos(15);
    private static final int MAX_ROOMS = 16;
    private static final int MAX_MESSAGE_BYTES = 160;
    private static final int INPUTS_PER_SECOND = 25;
    private static final String PROTOCOL = "ONLINE/1";
    private static final String BYE = "ONLINE/1 BYE";
    private static final String BAD_REQUEST = "BAD_REQUEST";
    private static final String ROOM_CLOSED = "ROOM_CLOSED";
    private static final String SCORE_REASON = "SCORE";

    private final Object stateLock = new Object();
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<Socket, Activity> sockets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    private final ThreadPoolExecutor clients = new ThreadPoolExecutor(
            0, 32, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean closed;

    public OnlineMatchService() {
        this(System::nanoTime, true);
    }

    // 가짜 시계로 카운트다운과 중력을 기다리지 않고 검증하기 위한 생성자
    OnlineMatchService(LongSupplier clock, boolean automatic) {
        this.clock = clock;
        if (automatic)
            scheduler.scheduleAtFixedRate(this::advance, 50, 50, TimeUnit.MILLISECONDS);
    }

    /** true이면 ScoreServer가 소켓을 닫지 않아야 합니다. 이후 소유권은 이 서비스에 있습니다. */
    public boolean handoff(Socket socket, String firstMessage) {
        if (!firstMessage.startsWith(PROTOCOL))
            return false;
        Activity activity = new Activity(clock.getAsLong());
        if (!register(socket, activity)) {
            closeSocket(socket);
            return true;
        }
        try {
            clients.execute(() -> handle(socket, firstMessage, activity));
        } catch (RejectedExecutionException e) {
            // 전용 풀 포화 시 짧은 응답도 watchdog 대상이며 다른 방 lock을 잡지 않습니다.
            try {
                respond(socket, "ERROR SERVER_FULL", activity);
            } catch (IOException ignored) {
                // 상대가 먼저 종료한 경우도 아래에서 소켓을 회수
            } finally {
                sockets.remove(socket);
                closeSocket(socket);
            }
        }
        return true;
    }

    private boolean register(Socket socket, Activity activity) {
        synchronized (stateLock) {
            if (closed)
                return false;
            sockets.put(socket, activity);
            return true;
        }
    }

    private void handle(Socket socket, String firstMessage, Activity activity) {
        Connection connection = null;
        try {
            socket.setTcpNoDelay(true);
            connection = joinOrCreate(socket, firstMessage);
            respond(socket, request(connection, "POLL"), activity);
            serveRequests(connection, activity);
        } catch (RequestException e) {
            try {
                respond(socket, "ERROR " + e.code, activity);
            } catch (IOException ignored) {
                // 형식 오류에 대한 응답을 못 보내도 자원은 회수
            }
        } catch (IOException ignored) {
            // 읽기/쓰기/연결 오류를 동일한 이탈 규칙으로 처리
        } finally {
            if (connection != null)
                depart(connection, "DISCONNECT");
            sockets.remove(socket);
            closeSocket(socket);
        }
    }

    private Connection joinOrCreate(Socket socket, String message) throws RequestException {
        String[] parts = message.split(" ", -1);
        if (PROTOCOL.equals(parts[0])) {
            if (parts.length == 3 && "CREATE".equals(parts[1]))
                return enter(null, nickname(parts[2]), socket);
            if (parts.length == 4 && "JOIN".equals(parts[1]) && parts[2].matches("[A-Z0-9]{6}"))
                return enter(parts[2], nickname(parts[3]), socket);
        }
        throw new RequestException(BAD_REQUEST);
    }

    private void serveRequests(Connection connection, Activity activity) throws IOException {
        String response;
        do {
            String message = readMessage(connection.socket);
            try {
                response = request(connection, message);
            } catch (RequestException e) {
                response = "ERROR " + e.code;
            }
            respond(connection.socket, response, activity);
        } while (!BYE.equals(response));
    }

    private static String nickname(String token) throws RequestException {
        try {
            return ScoreRepository.decodeNicknameToken(token);
        } catch (IllegalArgumentException e) {
            throw new RequestException("INVALID_NICKNAME");
        }
    }

    Connection enter(String code, String nickname, Socket socket) throws RequestException {
        synchronized (stateLock) {
            if (closed)
                throw new RequestException("SERVER_FULL");
            long now = clock.getAsLong();
            Room room = code == null ? createRoom(now) : availableRoom(code, now);
            int seat = code == null ? 0 : 1;
            Connection connection = new Connection(room, seat, nickname, socket, now);
            room.players[seat] = connection;
            return connection;
        }
    }

    private Room createRoom(long now) throws RequestException {
        if (rooms.size() >= MAX_ROOMS)
            throw new RequestException("SERVER_FULL");
        String code;
        do {
            code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (rooms.containsKey(code));
        Room room = new Room(code, now, random.nextLong());
        rooms.put(code, room);
        return room;
    }

    private Room availableRoom(String code, long now) throws RequestException {
        Room room = rooms.get(code);
        if (room == null)
            throw new RequestException("ROOM_NOT_FOUND");
        if (room.phase == Phase.WAITING && now - room.created >= WAIT_EXPIRY)
            finish(room, -1, "EXPIRED", now);
        if (room.phase != Phase.WAITING)
            throw new RequestException(ROOM_CLOSED);
        if (room.players[1] != null)
            throw new RequestException("ROOM_FULL");
        return room;
    }

    String request(Connection connection, String message) throws RequestException {
        synchronized (stateLock) {
            if (connection.departed)
                throw new RequestException(ROOM_CLOSED);
            Room room = connection.room;
            long now = clock.getAsLong();
            advanceRoom(room, now);
            if ("LEAVE".equals(message)) {
                departLocked(connection, "LEFT", now);
                return BYE;
            }
            if ("READY".equals(message)) {
                markReady(connection, now);
            } else if (message.startsWith("INPUT ")) {
                applyInput(connection, message.substring(6), now);
            } else if (!"POLL".equals(message)) {
                throw new RequestException(BAD_REQUEST);
            }
            return snapshot(connection, now).encode();
        }
    }

    private static void markReady(Connection connection, long now) {
        Room room = connection.room;
        if (room.phase != Phase.WAITING)
            return;
        connection.ready = true;
        Connection opponent = room.players[1 - connection.seat];
        if (opponent != null && opponent.ready) {
            room.phase = Phase.COUNTDOWN;
            room.startAt = now + COUNTDOWN;
        }
    }

    private static void applyInput(Connection connection, String action, long now) throws RequestException {
        if (!validAction(action))
            throw new RequestException(BAD_REQUEST);
        Room room = connection.room;
        // 게임 종료 직후에 도착한 입력은 보드를 변경하지 않습니다.
        if (room.phase != Phase.PLAYING || !connection.engine.isStarted())
            return;
        if (now - connection.inputWindow >= TimeUnit.SECONDS.toNanos(1)) {
            connection.inputWindow = now;
            connection.inputCount = 0;
        }
        connection.inputCount++;
        if (connection.inputCount > INPUTS_PER_SECOND)
            throw new RequestException("RATE_LIMIT");
        input(connection.engine, action);
        applyBattleItems(room);
        evaluate(room, now);
    }

    private static boolean validAction(String action) {
        return "LEFT".equals(action) || "RIGHT".equals(action) || "ROTATE_LEFT".equals(action)
                || "ROTATE_RIGHT".equals(action) || "SOFT_DROP".equals(action) || "HARD_DROP".equals(action);
    }

    private static void input(GameEngine engine, String action) {
        switch (action) {
            case "LEFT": engine.moveLeft(); break;
            case "RIGHT": engine.moveRight(); break;
            case "ROTATE_LEFT": engine.rotateLeft(); break;
            case "ROTATE_RIGHT": engine.rotateRight(); break;
            case "SOFT_DROP": engine.oneLineDown(); break;
            case "HARD_DROP": engine.dropDown(); break;
            default: throw new IllegalArgumentException(action);
        }
    }

    // 모든 방의 시간 진행은 네트워크 작업 풀과 독립적인 scheduler에서 수행합니다.
    void advance() {
        List<Socket> expired = new ArrayList<>();
        long now = clock.getAsLong();
        synchronized (stateLock) {
            for (Room room : rooms.values())
                advanceRoom(room, now);
        }
        for (Map.Entry<Socket, Activity> entry : sockets.entrySet()) {
            if (now - entry.getValue().lastCompleted >= IDLE_TIMEOUT)
                expired.add(entry.getKey());
        }
        // close가 블록된 writer를 깨워도 상태 lock을 보유하지 않습니다.
        for (Socket socket : expired)
            closeSocket(socket);
    }

    private static void advanceRoom(Room room, long now) {
        if ((room.phase == Phase.WAITING || room.phase == Phase.COUNTDOWN)
                && now - room.created >= WAIT_EXPIRY) {
            finish(room, -1, "EXPIRED", now);
            return;
        }
        if (room.phase == Phase.COUNTDOWN && now >= room.startAt) {
            room.phase = Phase.PLAYING;
            room.nextTick = room.startAt + GRAVITY;
            for (Connection connection : room.players)
                connection.engine.start();
        }
        while (room.phase == Phase.PLAYING && now >= room.nextTick) {
            for (Connection connection : room.players)
                connection.engine.tick();

            applyBattleItems(room);
            evaluate(room, room.nextTick);
            room.nextTick += GRAVITY;
        }
    }

    // 각 플레이어가 발동한 방해 아이템을 상대에게 적용
    private static void applyBattleItems(Room room) {
        applyPendingItems(room.players[0].engine, room.players[1].engine);
        applyPendingItems(room.players[1].engine, room.players[0].engine);
    }

    // 대기 중인 방해 아이템을 상대 엔진에 적용
    private static void applyPendingItems(GameEngine source, GameEngine target) {
        GameEngine.ItemType item;

        while ((item = source.pollBattleItem()) != null) {
            if (item == GameEngine.ItemType.ROTATION_LOCK) {
                target.applyBattleRotationLock();
            } else if (item == GameEngine.ItemType.FOG) {
                target.applyBattleFog();
            }
        }
    }

    private static void evaluate(Room room, long now) {
        GameEngine first = room.players[0].engine;
        GameEngine second = room.players[1].engine;
        if (!first.isStarted() && second.getScore() > first.getScore()) {
            finish(room, 1, SCORE_REASON, now);
        } else if (!second.isStarted() && first.getScore() > second.getScore()) {
            finish(room, 0, SCORE_REASON, now);
        } else if (!first.isStarted() && !second.isStarted()) {
            finish(room, -1, SCORE_REASON, now);
        }
    }

    private static void finish(Room room, int winner, String reason, long now) {
        room.phase = Phase.FINISHED;
        room.winner = winner;
        room.reason = reason;
        room.finishedAt = now;
    }

    void depart(Connection connection, String reason) {
        synchronized (stateLock) {
            departLocked(connection, reason, clock.getAsLong());
        }
    }

    private void departLocked(Connection connection, String reason, long now) {
        if (connection.departed)
            return;
        connection.departed = true;
        Room room = connection.room;
        if (room.phase != Phase.FINISHED) {
            int winner = room.phase == Phase.PLAYING ? 1 - connection.seat : -1;
            finish(room, winner, reason, now);
        }
        // 결과는 남은 참가자가 조회할 수 있게 보존하고 모두 나가면 방 코드를 회수
        if ((room.players[0] == null || room.players[0].departed)
                && (room.players[1] == null || room.players[1].departed))
            rooms.remove(room.code);
    }

    private static OnlineFrame snapshot(Connection connection, long now) {
        Room room = connection.room;
        long elapsed = 0;
        if (room.startAt != 0 && room.phase != Phase.COUNTDOWN && room.phase != Phase.WAITING) {
            long end = room.phase == Phase.FINISHED ? room.finishedAt : now;
            elapsed = Math.max(0, end - room.startAt);
        }
        long countdown = room.phase == Phase.COUNTDOWN ? Math.max(0, room.startAt - now) : 0;
        return new OnlineFrame(room.code, connection.seat, room.phase.name(), room.winner, room.reason,
                TimeUnit.NANOSECONDS.toMillis(elapsed), TimeUnit.NANOSECONDS.toMillis(countdown),
                player(room.players[0]), player(room.players[1]));
    }

    // 플레이어의 보드와 아이템 상태를 네트워크 전송용으로 만듦
    private static OnlineFrame.Player player(Connection connection) {
        int[] cells = new int[GameEngine.BOARD_WIDTH * GameEngine.BOARD_HEIGHT];
        int[] itemTypes = new int[GameEngine.BOARD_WIDTH * GameEngine.BOARD_HEIGHT];
        if (connection == null)
            return new OnlineFrame.Player("", false, 0, 0, false, 0,
                    cells, itemTypes, false, 0, 0);
        GameEngine engine = connection.engine;
        // 이미 착지한 블록과 아이템 위치 저장
        for (int y = 0; y < GameEngine.BOARD_HEIGHT; y++) {
            for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
                int index = y * GameEngine.BOARD_WIDTH + x;
                cells[index] = engine.shapeAt(x, y).ordinal();
                if (engine.getItemTypeAt(x, y) != null)
                    itemTypes[index] = engine.getItemTypeAt(x, y).ordinal() + 1;
            }
        }
        // 현재 내려오는 블록도 함께 저장
        if (engine.isStarted() && engine.getCurrentShape().ordinal() != 0) {
            for (int i = 0; i < 4; i++) {
                int x = engine.getCurrentX() + engine.getPieceX(i);
                int y = engine.getCurrentY() - engine.getPieceY(i);
                int index = y * GameEngine.BOARD_WIDTH + x;
                cells[index] = engine.getCurrentShape().ordinal();
                // 현재 내려오는 블록의 아이템 칸도 저장
                if (engine.isCurrentPieceItem()
                        && i == engine.getCurrentItemCellIndex()
                        && engine.getCurrentItemType() != null) {
                    itemTypes[index] = engine.getCurrentItemType().ordinal() + 1;
                }
            }
        }
        return new OnlineFrame.Player(
                connection.nickname,
                connection.ready,
                engine.getScore(),
                engine.getNumLinesRemoved(),
                engine.isStarted(),
                engine.getNextShape().ordinal(),
                cells,
                itemTypes,
                engine.isFogActive(),
                engine.getFogRemaining(),
                engine.getRotationLockRemaining()
        );
    }

    private static String readMessage(Socket socket) throws IOException {
        InputStream input = socket.getInputStream();
        long deadline = System.nanoTime() + IDLE_TIMEOUT;
        StringBuilder line = new StringBuilder();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0)
                throw new IOException("온라인 요청 시간이 초과되었습니다.");
            socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            int value = input.read();
            if (value < 0)
                throw new IOException("접속이 종료되었습니다.");
            if (value == '\n') {
                if (line.length() > 0 && line.charAt(line.length() - 1) == '\r')
                    line.setLength(line.length() - 1);
                return line.toString();
            }
            if (value > 127 || line.length() >= MAX_MESSAGE_BYTES)
                throw new IOException("요청 형식 또는 길이 오류입니다.");
            line.append((char) value);
        }
    }

    private void respond(Socket socket, String response, Activity activity) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write((response + "\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
        activity.lastCompleted = clock.getAsLong();
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 이미 닫힌 소켓도 동일하게 처리
        }
    }

    @Override
    public void close() {
        synchronized (stateLock) {
            closed = true;
            rooms.clear();
        }
        scheduler.shutdownNow();
        for (Socket socket : sockets.keySet())
            closeSocket(socket);
        clients.shutdownNow();
    }

    static final class Connection {
        final Room room;
        final int seat;
        final String nickname;
        final Socket socket;
        final GameEngine engine;
        boolean ready;
        boolean departed;
        long inputWindow;
        int inputCount;

        Connection(Room room, int seat, String nickname, Socket socket, long now) {
            this.room = room;
            this.seat = seat;
            this.nickname = nickname;
            this.socket = socket;
            this.engine = new GameEngine(room.seed);
            // 네트워크 대전에서도 아이템 모드 사용
            this.engine.setItemMode(true);
            // 방해 아이템은 상대에게 보내는 대전 방식 사용
            this.engine.setBattleItemMode(true);
            this.inputWindow = now;
        }
    }

    private enum Phase { WAITING, COUNTDOWN, PLAYING, FINISHED }

    static final class Room {
        final String code;
        final long created;
        final long seed;
        final Connection[] players = new Connection[2];
        Phase phase = Phase.WAITING;
        String reason = "NONE";
        int winner = -1;
        long startAt;
        long nextTick;
        long finishedAt;

        Room(String code, long created, long seed) {
            this.code = code;
            this.created = created;
            this.seed = seed;
        }
    }

    static final class RequestException extends Exception {
        private static final long serialVersionUID = 1L;
        final String code;
        RequestException(String code) { this.code = code; }
    }

    private static final class Activity {
        volatile long lastCompleted;
        Activity(long now) { lastCompleted = now; }
    }
}
