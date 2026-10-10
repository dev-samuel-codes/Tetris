package backend.network;

import backend.score.ScoreRepository;
import frontend.engine.GameEngine;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
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

    private final Object stateLock = new Object();
    private final Map<String, Room> rooms = new HashMap<String, Room>();
    private final Map<Socket, Activity> sockets = new ConcurrentHashMap<Socket, Activity>();
    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    private final ThreadPoolExecutor clients = new ThreadPoolExecutor(
            0, 32, 30, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
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
        if (!firstMessage.startsWith("ONLINE/1"))
            return false;
        Activity activity = new Activity(clock.getAsLong());
        sockets.put(socket, activity);
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

    private void handle(Socket socket, String firstMessage, Activity activity) {
        Connection connection = null;
        try {
            socket.setTcpNoDelay(true);
            String[] parts = firstMessage.split(" ", -1);
            if (parts.length == 3 && "ONLINE/1".equals(parts[0]) && "CREATE".equals(parts[1])) {
                connection = enter(null, nickname(parts[2]), socket);
            } else if (parts.length == 4 && "ONLINE/1".equals(parts[0]) && "JOIN".equals(parts[1])) {
                if (!parts[2].matches("[A-Z0-9]{6}"))
                    throw new RequestException("BAD_REQUEST");
                connection = enter(parts[2], nickname(parts[3]), socket);
            } else {
                throw new RequestException("BAD_REQUEST");
            }
            respond(socket, request(connection, "POLL"), activity);
            while (true) {
                String message = readMessage(socket);
                String response;
                try {
                    response = request(connection, message);
                } catch (RequestException e) {
                    response = "ERROR " + e.code;
                }
                respond(socket, response, activity);
                if ("ONLINE/1 BYE".equals(response))
                    break;
            }
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

    private String nickname(String token) throws RequestException {
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
            Room room;
            int seat;
            if (code == null) {
                if (rooms.size() >= MAX_ROOMS)
                    throw new RequestException("SERVER_FULL");
                do {
                    code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1000000));
                } while (rooms.containsKey(code));
                room = new Room(code, now, random.nextLong());
                rooms.put(code, room);
                seat = 0;
            } else {
                room = rooms.get(code);
                if (room == null)
                    throw new RequestException("ROOM_NOT_FOUND");
                if (now - room.created >= WAIT_EXPIRY && "WAITING".equals(room.phase)) {
                    finish(room, -1, "EXPIRED", now);
                    throw new RequestException("ROOM_CLOSED");
                }
                if (!"WAITING".equals(room.phase))
                    throw new RequestException("ROOM_CLOSED");
                if (room.players[1] != null)
                    throw new RequestException("ROOM_FULL");
                seat = 1;
            }
            Connection connection = new Connection(room, seat, nickname, socket, now);
            room.players[seat] = connection;
            return connection;
        }
    }

    String request(Connection connection, String message) throws RequestException {
        synchronized (stateLock) {
            if (connection.departed)
                throw new RequestException("ROOM_CLOSED");
            Room room = connection.room;
            long now = clock.getAsLong();
            advanceRoom(room, now);
            if ("LEAVE".equals(message)) {
                departLocked(connection, "LEFT", now);
                return "ONLINE/1 BYE";
            }
            if ("READY".equals(message)) {
                if ("WAITING".equals(room.phase))
                    connection.ready = true;
                if ("WAITING".equals(room.phase) && room.players[0] != null && room.players[1] != null
                        && room.players[0].ready && room.players[1].ready) {
                    room.phase = "COUNTDOWN";
                    room.startAt = now + COUNTDOWN;
                }
            } else if (message.startsWith("INPUT ")) {
                String action = message.substring(6);
                if (!validAction(action))
                    throw new RequestException("BAD_REQUEST");
                // 화면의 마지막 키가 top-out 또는 종료보다 늦게 도착해도 정상 결과를 유지
                if (!"PLAYING".equals(room.phase) || !connection.engine.isStarted())
                    return snapshot(connection, now).encode();
                // POLL 횟수는 게임 속도에 영향을 주지 않습니다. 입력은 초당 25개로 제한합니다.
                if (now - connection.inputWindow >= TimeUnit.SECONDS.toNanos(1)) {
                    connection.inputWindow = now;
                    connection.inputCount = 0;
                }
                if (++connection.inputCount > INPUTS_PER_SECOND)
                    throw new RequestException("RATE_LIMIT");
                input(connection.engine, action);
                evaluate(room, now);
            } else if (!"POLL".equals(message)) {
                throw new RequestException("BAD_REQUEST");
            }
            return snapshot(connection, now).encode();
        }
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
        List<Socket> expired = new ArrayList<Socket>();
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

    private void advanceRoom(Room room, long now) {
        if (("WAITING".equals(room.phase) || "COUNTDOWN".equals(room.phase))
                && now - room.created >= WAIT_EXPIRY) {
            finish(room, -1, "EXPIRED", now);
            return;
        }
        if ("COUNTDOWN".equals(room.phase) && now >= room.startAt) {
            room.phase = "PLAYING";
            room.nextTick = room.startAt + GRAVITY;
            for (Connection connection : room.players)
                connection.engine.start();
        }
        while ("PLAYING".equals(room.phase) && now >= room.nextTick) {
            for (Connection connection : room.players)
                connection.engine.tick();
            evaluate(room, room.nextTick);
            room.nextTick += GRAVITY;
        }
    }

    private void evaluate(Room room, long now) {
        GameEngine first = room.players[0].engine;
        GameEngine second = room.players[1].engine;
        if (!first.isStarted() && !second.isStarted()) {
            int comparison = Integer.compare(first.getScore(), second.getScore());
            finish(room, comparison == 0 ? -1 : comparison > 0 ? 0 : 1, "SCORE", now);
        } else if (!first.isStarted() && second.getScore() > first.getScore()) {
            finish(room, 1, "SCORE", now);
        } else if (!second.isStarted() && first.getScore() > second.getScore()) {
            finish(room, 0, "SCORE", now);
        }
    }

    private static void finish(Room room, int winner, String reason, long now) {
        room.phase = "FINISHED";
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
        if (!"FINISHED".equals(room.phase)) {
            int winner = "PLAYING".equals(room.phase) ? 1 - connection.seat : -1;
            finish(room, winner, reason, now);
        }
        // 결과는 남은 참가자가 조회할 수 있게 보존하고 모두 나가면 방 코드를 회수
        if ((room.players[0] == null || room.players[0].departed)
                && (room.players[1] == null || room.players[1].departed))
            rooms.remove(room.code);
    }

    private OnlineFrame snapshot(Connection connection, long now) {
        Room room = connection.room;
        long elapsed = room.startAt == 0 || "COUNTDOWN".equals(room.phase)
                || "WAITING".equals(room.phase) ? 0
                : Math.max(0, ("FINISHED".equals(room.phase) ? room.finishedAt : now) - room.startAt);
        long countdown = "COUNTDOWN".equals(room.phase) ? Math.max(0, room.startAt - now) : 0;
        return new OnlineFrame(room.code, connection.seat, room.phase, room.winner, room.reason,
                TimeUnit.NANOSECONDS.toMillis(elapsed), TimeUnit.NANOSECONDS.toMillis(countdown),
                player(room.players[0]), player(room.players[1]));
    }

    private static OnlineFrame.Player player(Connection connection) {
        int[] cells = new int[GameEngine.BOARD_WIDTH * GameEngine.BOARD_HEIGHT];
        if (connection == null)
            return new OnlineFrame.Player("", false, 0, 0, false, 0, cells);
        GameEngine engine = connection.engine;
        for (int y = 0; y < GameEngine.BOARD_HEIGHT; y++) {
            for (int x = 0; x < GameEngine.BOARD_WIDTH; x++)
                cells[y * GameEngine.BOARD_WIDTH + x] = engine.shapeAt(x, y).ordinal();
        }
        if (engine.isStarted() && engine.getCurrentShape().ordinal() != 0) {
            for (int i = 0; i < 4; i++) {
                int x = engine.getCurrentX() + engine.getPieceX(i);
                int y = engine.getCurrentY() - engine.getPieceY(i);
                cells[y * GameEngine.BOARD_WIDTH + x] = engine.getCurrentShape().ordinal();
            }
        }
        return new OnlineFrame.Player(connection.nickname, connection.ready, engine.getScore(),
                engine.getNumLinesRemoved(), engine.isStarted(), engine.getNextShape().ordinal(), cells);
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
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        writer.write(response);
        writer.write('\n');
        writer.flush();
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

    // 나중에 방 저장소와 소켓 전송을 별도 클래스로 나누어도 괜찮을 것 같음
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
            this.inputWindow = now;
        }
    }

    static final class Room {
        final String code;
        final long created;
        final long seed;
        final Connection[] players = new Connection[2];
        String phase = "WAITING";
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
