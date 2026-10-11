package backend.network;

import frontend.engine.GameEngine;
import frontend.Shape;
import frontend.Tetrominoes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import shared.network.OnlineFrame;

public final class OnlineMatchServiceTest {
    public static void main(String[] args) throws Exception {
        countdownAndGravity();
        itemMetadata();
        battleItemDelivery();
        itemModePieceFairness();
        scoreRules();
        departuresAndExpiry();
        concurrentJoinAndCapacity();
        blockedWriterWatchdog();
        idleConnectionWatchdog();
        protocolErrors();
        System.out.println("OnlineMatchServiceTest passed");
    }

    private static void countdownAndGravity() throws Exception {
        AtomicLong clock = new AtomicLong(1);
        try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
            OnlineMatchService.Connection first = service.enter(null, "first", new Socket());
            OnlineMatchService.Connection second = service.enter(first.room.code, "second", new Socket());
            check(frame(service, first).getPhase().equals("WAITING"), "처음에는 대기");
            service.request(first, "READY");
            check(frame(service, first).getPhase().equals("WAITING"), "한 사람의 준비는 대기");
            OnlineFrame countdown = OnlineFrame.parse(service.request(second, "READY"));
            check(countdown.getCountdownMillis() == 3000, "두 사람 준비 후 3초");
            check(OnlineFrame.parse(service.request(first, "READY")).getCountdownMillis() == 3000,
                    "중복 준비는 카운트다운을 재시작하지 않음");
            check(OnlineFrame.parse(service.request(first, "INPUT LEFT")).getPhase().equals("COUNTDOWN"),
                    "시작 전 지연 입력은 현재 상태를 반환");
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(2999));
            service.advance();
            check(frame(service, first).getPhase().equals("COUNTDOWN"), "3초 이전에 시작 불가");
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(1));
            service.advance();
            OnlineFrame playing = frame(service, first);
            check(playing.getPhase().equals("PLAYING"), "3초에서 시작");
            check(playing.getPlayer(0).getNextShape() == playing.getPlayer(1).getNextShape(), "같은 다음 블록");
            int occupied = 0;
            for (int cell : playing.getPlayer(0).getCells()) if (cell != 0) occupied++;
            check(occupied == 4, "스냅숏에 낙하 블록 포함");
            int y = first.engine.getCurrentY();
            for (int i = 0; i < 100; i++) service.request(first, "POLL");
            check(first.engine.getCurrentY() == y, "POLL은 중력을 진행하지 않음");
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(399));
            service.advance();
            check(first.engine.getCurrentY() == y, "400ms 이전에는 낙하하지 않음");
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(1));
            service.advance();
            check(first.engine.getCurrentY() == y - 1 && second.engine.getCurrentY() == y - 1, "두 보드에 400ms 중력");
            for (int i = 0; i < 25; i++) service.request(first, "INPUT LEFT");
            expect(service, first, "INPUT LEFT", "RATE_LIMIT");
            expect(service, first, "INPUT HACK", "BAD_REQUEST");
        }
    }

    private static void itemMetadata() throws Exception {
        AtomicLong clock = new AtomicLong(1);
        try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
            OnlineMatchService.Connection first = service.enter(null, "first", new Socket());
            check(first.engine.isItemMode(), "온라인 연결은 아이템 모드 사용");
            OnlineFrame.Player empty = frame(service, first).getPlayer(1);
            check(Arrays.equals(empty.getItemTypes(), new int[220]) && !empty.isFogActive()
                    && empty.getFogRemaining() == 0 && empty.getRotationLockRemaining() == 0,
                    "빈 자리의 아이템과 방해 상태는 초기값");
            OnlineMatchService.Connection second = service.enter(first.room.code, "second", new Socket());
            start(service, clock, first, second);
            Shape piece = (Shape) get(first.engine, "curPiece");
            piece.setItemCell(0);
            set(first.engine, "currentItemType", GameEngine.ItemType.FOG);
            Tetrominoes[] board = (Tetrominoes[]) get(first.engine, "board");
            GameEngine.ItemType[] items = (GameEngine.ItemType[]) get(first.engine, "boardItemTypes");
            board[0] = Tetrominoes.SquareShape;
            items[0] = GameEngine.ItemType.SCORE_BOOST;
            int fallingIndex = (first.engine.getCurrentY() - first.engine.getPieceY(0))
                    * GameEngine.BOARD_WIDTH + first.engine.getCurrentX() + first.engine.getPieceX(0);
            int[] expected = new int[220];
            expected[0] = GameEngine.ItemType.SCORE_BOOST.ordinal() + 1;
            expected[fallingIndex] = GameEngine.ItemType.FOG.ordinal() + 1;
            OnlineFrame view = frame(service, first);
            check(Arrays.equals(view.getPlayer(0).getItemTypes(), expected),
                    "착지한 아이템과 낙하 중인 아이템의 위치와 종류 전송");
            check(view.getPlayer(0).getCells()[0] == Tetrominoes.SquareShape.ordinal()
                    && view.getPlayer(0).getCells()[fallingIndex] == first.engine.getCurrentShape().ordinal(),
                    "아이템 메타데이터와 블록 위치 일치");
            check(Arrays.equals(frame(service, second).getPlayer(0).getItemTypes(), expected),
                    "상대 조회에도 동일한 아이템 메타데이터 전송");
        }
    }

    private static void battleItemDelivery() throws Exception {
        AtomicLong clock = new AtomicLong(1);
        try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
            OnlineMatchService.Connection first = service.enter(null, "first", new Socket());
            OnlineMatchService.Connection second = service.enter(first.room.code, "second", new Socket());
            start(service, clock, first, second);
            prepareItemRow(first.engine, GameEngine.ItemType.FOG, GameEngine.ItemType.ROTATION_LOCK);
            OnlineFrame attack = OnlineFrame.parse(service.request(first, "INPUT HARD_DROP"));
            checkEffects(attack.getPlayer(0), false, 0, 0, "발동한 본인은 방해 효과를 받지 않음");
            checkEffects(attack.getPlayer(1), true, 3, 2, "입력에서 상대에게 두 방해 효과 전달");
            check(first.engine.pollBattleItem() == null, "전달한 방해 아이템은 대기열에서 제거");
            checkEffects(frame(service, second).getPlayer(1), true, 3, 2,
                    "반복 조회는 방해 효과를 중복 적용하거나 감소시키지 않음");

            Shape piece = new Shape();
            piece.setShape(Tetrominoes.LineShape);
            set(second.engine, "curPiece", piece);
            set(second.engine, "currentItemType", null);
            set(second.engine, "curX", 4);
            set(second.engine, "curY", 10);
            service.request(second, "INPUT ROTATE_LEFT");
            service.request(second, "INPUT ROTATE_RIGHT");
            for (int i = 0; i < 4; i++)
                check(second.engine.getPieceX(i) == piece.x(i) && second.engine.getPieceY(i) == piece.y(i),
                        "상대 회전 잠금은 양쪽 회전 입력 차단");

            prepareItemRow(second.engine, GameEngine.ItemType.FOG, GameEngine.ItemType.ROTATION_LOCK);
            set(second.engine, "curY", 2);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(400));
            service.advance();
            OnlineFrame reverse = frame(service, first);
            checkEffects(reverse.getPlayer(0), true, 3, 2, "중력 착지에서도 반대 방향으로 방해 효과 전달");
            checkEffects(reverse.getPlayer(1), true, 3, 2, "발동한 본인의 기존 효과는 중복 증가하지 않음");
            check(second.engine.pollBattleItem() == null, "타이머 전달 후에도 대기열은 비어 있음");
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(400));
            service.advance();
            checkEffects(frame(service, second).getPlayer(1), true, 2, 1,
                    "다음 블록의 방해 효과 잔여 수를 스냅숏에 반영");
        }
    }

    private static void itemModePieceFairness() throws Exception {
        GameEngine.ItemType[] effects = {GameEngine.ItemType.ROW_CLEAR, GameEngine.ItemType.COLUMN_CLEAR,
                GameEngine.ItemType.COLUMN_CLEAR};
        for (int effectIndex = 0; effectIndex < effects.length; effectIndex++) {
            GameEngine.ItemType effect = effects[effectIndex];
            boolean battleColumn = effectIndex == 2;
            String effectName = battleColumn ? "대전 열 제거" : effect.name();
            AtomicLong clock = new AtomicLong(1);
            try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
                // 고정 시드로 한쪽만 제거 아이템을 사용해도 같은 순번의 블록이 유지되는지 확인
                OnlineMatchService.Room room = new OnlineMatchService.Room("123456", clock.get(), 101L);
                OnlineMatchService.Connection first = new OnlineMatchService.Connection(
                        room, 0, "first", new Socket(), clock.get());
                OnlineMatchService.Connection second = new OnlineMatchService.Connection(
                        room, 1, "second", new Socket(), clock.get());
                room.players[0] = first;
                room.players[1] = second;
                start(service, clock, first, second);
                OnlineFrame initial = frame(service, first);
                check(initial.getPlayer(0).getNextShape() == initial.getPlayer(1).getNextShape(),
                        "아이템 모드에서 시작 블록 순서 일치");
                prepareItemRow(first.engine, battleColumn ? null : effect, null);
                prepareItemRow(second.engine, null, null);
                service.request(first, "INPUT HARD_DROP");
                service.request(second, "INPUT HARD_DROP");
                if (!battleColumn)
                    check(first.engine.getLastItemType() == effect, "한쪽만 제거 아이템 발동: " + effectName);
                clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(400));
                frame(service, first);
                if (battleColumn) first.engine.applyBattleColumnClear();
                for (int i = 0; i < 12; i++) {
                    OnlineFrame after = frame(service, first);
                    check(first.engine.getCurrentShape() == second.engine.getCurrentShape()
                            && after.getPlayer(0).getNextShape() == after.getPlayer(1).getNextShape(),
                            "한쪽 제거 아이템 발동 후에도 같은 순번의 블록 유지: " + effectName + " " + i);
                    check(first.engine.getCurrentItemType() == second.engine.getCurrentItemType()
                            && first.engine.getCurrentItemCellIndex() == second.engine.getCurrentItemCellIndex(),
                            "같은 순번의 아이템 종류와 칸 유지: " + effectName + " " + i);
                    // 쌓인 보드 때문에 종료되지 않도록 비운 뒤 실제 입력으로 다음 블록 진행
                    for (GameEngine engine : new GameEngine[] {first.engine, second.engine}) {
                        Arrays.fill((Tetrominoes[]) get(engine, "board"), Tetrominoes.NoShape);
                        Arrays.fill((GameEngine.ItemType[]) get(engine, "boardItemTypes"), null);
                    }
                    service.request(first, "INPUT HARD_DROP");
                    service.request(second, "INPUT HARD_DROP");
                }
            }
        }
    }

    private static void prepareItemRow(GameEngine engine, GameEngine.ItemType firstItem,
            GameEngine.ItemType secondItem) throws Exception {
        Tetrominoes[] board = (Tetrominoes[]) get(engine, "board");
        GameEngine.ItemType[] items = (GameEngine.ItemType[]) get(engine, "boardItemTypes");
        Arrays.fill(board, Tetrominoes.NoShape);
        Arrays.fill(items, null);
        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) board[x] = Tetrominoes.LineShape;
        items[0] = firstItem;
        items[1] = secondItem;
        Shape piece = new Shape();
        piece.setShape(Tetrominoes.SquareShape);
        set(engine, "curPiece", piece);
        set(engine, "currentItemType", null);
        set(engine, "curX", 4);
        set(engine, "curY", 10);
    }

    private static void checkEffects(OnlineFrame.Player player, boolean fog, int fogRemaining,
            int rotationRemaining, String message) {
        check(player.isFogActive() == fog && player.getFogRemaining() == fogRemaining
                && player.getRotationLockRemaining() == rotationRemaining, message);
    }

    private static void scoreRules() throws Exception {
        int[][] states = {{100, 50, 0, 1, -2}, {100, 100, 0, 1, -2}, {100, 101, 0, 1, 1},
                {100, 100, 0, 0, -1}, {110, 100, 0, 0, 0}, {100, 110, 0, 0, 1}, {101, 100, 1, 0, 0}};
        for (int[] state : states) {
            AtomicLong clock = new AtomicLong(1);
            try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
                OnlineMatchService.Connection first = service.enter(null, "first", new Socket());
                OnlineMatchService.Connection second = service.enter(first.room.code, "second", new Socket());
                start(service, clock, first, second);
                set(first.engine, "score", state[0]);
                set(second.engine, "score", state[1]);
                set(first.engine, "isStarted", state[2] == 1);
                set(second.engine, "isStarted", state[3] == 1);
                clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(400));
                service.advance();
                OnlineFrame result = frame(service, first);
                check(result.isFinished() == (state[4] != -2), "top-out 후 점수 추월 또는 양쪽 종료");
                if (state[2] == 0 || result.isFinished()) {
                    OnlineFrame late = OnlineFrame.parse(service.request(first, "INPUT HARD_DROP"));
                    check(late.getPhase().equals(result.getPhase()) && late.getWinner() == result.getWinner(),
                            "top-out 또는 종료 뒤 지연 키는 결과를 유지");
                    check(late.getPlayer(0).getScore() == state[0], "지연 입력은 종료한 보드를 변경하지 않음");
                }
                OnlineFrame repeatedReady = OnlineFrame.parse(service.request(first, "READY"));
                check(repeatedReady.getPhase().equals(result.getPhase()), "진행 또는 종료 뒤 READY는 상태 유지");
                if (result.isFinished()) {
                    check(result.getWinner() == state[4] && result.getReason().equals("SCORE"), "점수 비교 및 무승부");
                    long elapsed = result.getElapsedMillis();
                    clock.addAndGet(TimeUnit.SECONDS.toNanos(10));
                    check(frame(service, second).getElapsedMillis() == elapsed, "경기 종료 시간 고정");
                }
            }
        }
    }

    private static void departuresAndExpiry() throws Exception {
        for (boolean playing : new boolean[] {false, true}) {
            AtomicLong clock = new AtomicLong(1);
            try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
                OnlineMatchService.Connection first = service.enter(null, "first", new Socket());
                OnlineMatchService.Connection second = service.enter(first.room.code, "second", new Socket());
                if (playing) start(service, clock, first, second);
                check(service.request(first, "LEAVE").equals("ONLINE/1 BYE"), "이탈 응답");
                OnlineFrame result = frame(service, second);
                check(result.isFinished() && result.getReason().equals("LEFT"), "이탈 시 방 종료");
                check(result.getWinner() == (playing ? 1 : -1), "경기 중에만 상대 승리");
                service.depart(first, "DISCONNECT");
                check(frame(service, second).getReason().equals("LEFT"), "finally는 결과를 덮어쓰지 않음");
            }
        }
        AtomicLong clock = new AtomicLong(1);
        try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
            OnlineMatchService.Connection first = service.enter(null, "first", new Socket());
            clock.addAndGet(TimeUnit.MINUTES.toNanos(10));
            service.advance();
            check(frame(service, first).getReason().equals("EXPIRED"), "대기 방 10분 만료");
        }
        clock.set(1);
        try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
            OnlineMatchService.Connection first = service.enter(null, "first", new Socket());
            OnlineMatchService.Connection second = service.enter(first.room.code, "second", new Socket());
            start(service, clock, first, second);
            service.depart(first, "DISCONNECT");
            check(frame(service, second).getWinner() == 1, "연결 종료는 상대 승리");
        }
    }

    private static void concurrentJoinAndCapacity() throws Exception {
        AtomicLong clock = new AtomicLong(1);
        try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
            OnlineMatchService.Connection first = service.enter(null, "first", new Socket());
            AtomicInteger joined = new AtomicInteger();
            AtomicInteger full = new AtomicInteger();
            CountDownLatch gate = new CountDownLatch(1);
            Runnable join = () -> {
                try {
                    gate.await();
                    service.enter(first.room.code, "second", new Socket());
                    joined.incrementAndGet();
                } catch (OnlineMatchService.RequestException e) {
                    if (e.code.equals("ROOM_FULL")) full.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            Thread a = new Thread(join);
            Thread b = new Thread(join);
            a.start(); b.start(); gate.countDown(); a.join(2000); b.join(2000);
            check(joined.get() == 1 && full.get() == 1, "동시 참가에서 두 번째 자리 하나만 배정");
            for (int i = 1; i < 16; i++) service.enter(null, "room" + i, new Socket());
            try {
                service.enter(null, "extra", new Socket());
                throw new AssertionError("16개 방 제한");
            } catch (OnlineMatchService.RequestException e) {
                check(e.code.equals("SERVER_FULL"), "방 제한 오류");
            }
            service.depart(first.room.players[1], "LEFT");
            service.depart(first, "LEFT");
            service.enter(null, "reused", new Socket());
        }
    }

    private static void blockedWriterWatchdog() throws Exception {
        AtomicLong clock = new AtomicLong(1);
        try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
            BlockingSocket socket = new BlockingSocket();
            check(service.handoff(socket, "ONLINE/1 CREATE Zmlyc3Q"), "온라인 소켓 소유권 이전");
            check(socket.started.await(2, TimeUnit.SECONDS), "writer가 실제 차단됨");
            // 차단된 출력 때문에 방 생성이나 타이머가 lock을 기다리지 않아야 합니다.
            service.enter(null, "another", new Socket());
            clock.addAndGet(TimeUnit.SECONDS.toNanos(15));
            service.advance();
            check(socket.isClosed(), "15초 writer watchdog이 소켓 회수");
            check(socket.ended.await(2, TimeUnit.SECONDS), "소켓 close가 차단된 writer를 해제");
        }
    }

    private static void idleConnectionWatchdog() throws Exception {
        AtomicLong clock = new AtomicLong(1);
        try (OnlineMatchService service = new OnlineMatchService(clock::get, false)) {
            IdleSocket socket = new IdleSocket();
            service.handoff(socket, "ONLINE/1 CREATE Zmlyc3Q");
            check(socket.reading.await(2, TimeUnit.SECONDS), "첫 응답 후 다음 요청을 기다림");
            clock.addAndGet(TimeUnit.SECONDS.toNanos(15));
            service.advance();
            check(socket.isClosed(), "15초 무응답 소켓 회수");
            check(socket.ended.await(2, TimeUnit.SECONDS), "close가 차단된 읽기를 해제");
        }
    }

    private static void protocolErrors() throws Exception {
        try (OnlineMatchService service = new OnlineMatchService()) {
            MemorySocket socket = new MemorySocket();
            service.handoff(socket, "ONLINE/1 CREATE =");
            check(socket.closed.await(2, TimeUnit.SECONDS), "잘못된 닉네임 연결 정리");
            check(socket.output.toString("UTF-8").equals("ERROR INVALID_NICKNAME\n"), "닉네임 오류 응답");
        }
    }

    private static OnlineFrame frame(OnlineMatchService service, OnlineMatchService.Connection c) throws Exception {
        return OnlineFrame.parse(service.request(c, "POLL"));
    }
    private static void start(OnlineMatchService service, AtomicLong clock,
            OnlineMatchService.Connection first, OnlineMatchService.Connection second) throws Exception {
        service.request(first, "READY"); service.request(second, "READY");
        clock.addAndGet(TimeUnit.SECONDS.toNanos(3)); service.advance();
    }
    private static void expect(OnlineMatchService service, OnlineMatchService.Connection c,
            String request, String code) throws Exception {
        try { service.request(c, request); throw new AssertionError(code); }
        catch (OnlineMatchService.RequestException e) { check(e.code.equals(code), "정확한 오류: " + code); }
    }
    private static void set(GameEngine engine, String name, Object value) throws Exception {
        Field field = GameEngine.class.getDeclaredField(name); field.setAccessible(true); field.set(engine, value);
    }
    private static Object get(GameEngine engine, String name) throws Exception {
        Field field = GameEngine.class.getDeclaredField(name); field.setAccessible(true); return field.get(engine);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class BlockingSocket extends Socket {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch ended = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        @Override public void setTcpNoDelay(boolean value) { }
        @Override public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override public void write(int value) throws IOException {
                    started.countDown();
                    try { release.await(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { ended.countDown(); }
                    throw new IOException("닫힌 소켓");
                }
            };
        }
        @Override public void close() throws IOException { super.close(); release.countDown(); }
    }
    private static final class IdleSocket extends Socket {
        final CountDownLatch reading = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch ended = new CountDownLatch(1);
        @Override public void setTcpNoDelay(boolean value) { }
        @Override public void setSoTimeout(int value) { }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public java.io.InputStream getInputStream() {
            return new java.io.InputStream() {
                @Override public int read() throws IOException {
                    reading.countDown();
                    try { release.await(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { ended.countDown(); }
                    throw new IOException("닫힌 소켓");
                }
            };
        }
        @Override public void close() throws IOException { super.close(); release.countDown(); }
    }
    private static final class MemorySocket extends Socket {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final CountDownLatch closed = new CountDownLatch(1);
        @Override public void setTcpNoDelay(boolean value) { }
        @Override public OutputStream getOutputStream() { return output; }
        @Override public void close() throws IOException { super.close(); closed.countDown(); }
    }
}
