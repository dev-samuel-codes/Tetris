package backend.network;

import frontend.engine.GameEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import shared.network.OnlineFrame;

public final class OnlineMatchServiceTest {
    public static void main(String[] args) throws Exception {
        countdownAndGravity();
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
