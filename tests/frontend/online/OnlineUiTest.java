package frontend.online;

import frontend.Resolution;
import frontend.network.OnlineClient;
import frontend.style.Style;
import shared.network.OnlineFrame;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

// 실제 TCP와 EDT를 사용하지만 창을 열지 않아 CI에서도 수명·입력·표시를 검증합니다.
public final class OnlineUiTest {
    public static void main(String[] args) throws Exception {
        verifyMailbox();
        SwingUtilities.invokeAndWait(OnlineUiTest::verifyRendering);
        verifyCompatibilityError();
        verifyCloseCancelsRead();
        verifyCancelSuppressesLateFrame();
        verifyResponsePacing();
        verifyRotationDropSequence();
        verifySlowResponseDeadline();
        System.out.println("OnlineUiTest: 1초 입력 만료·원격 지연 후 회전/낙하 보존·보드 방향·다음 블록·좌석·상태·구서버·취소·늦은 EDT 응답·50ms 간격·8초 제한 통과");
    }

    private static void verifyMailbox() throws Exception {
        OnlineSession.InputMailbox mailbox = new OnlineSession.InputMailbox();
        for (int i = 0; i < 1000; i++) mailbox.offer("LEFT");
        mailbox.offer("HARD_DROP");
        require("LEFT".equals(mailbox.poll()) && "HARD_DROP".equals(mailbox.poll()) && mailbox.poll() == null,
                "연속 이동을 합치고 이동 다음 즉시 낙하 순서를 보존해야 합니다");
        mailbox.offer("ROTATE_LEFT");
        mailbox.offer("HARD_DROP");
        for (int i = 0; i < 1000; i++) mailbox.offer("LEFT");
        require("ROTATE_LEFT".equals(mailbox.poll()) && "HARD_DROP".equals(mailbox.poll())
                && "LEFT".equals(mailbox.poll()) && mailbox.poll() == null,
                "회전→SPACE 입력이 방향키 자동 반복에 덮어쓰였습니다");
        mailbox.offer("LEFT");
        mailbox.offer("ROTATE_LEFT");
        mailbox.offer("RIGHT");
        mailbox.offer("HARD_DROP");
        mailbox.offer("ROTATE_RIGHT");
        require("ROTATE_LEFT".equals(mailbox.poll()) && "RIGHT".equals(mailbox.poll())
                && "HARD_DROP".equals(mailbox.poll()) && "ROTATE_RIGHT".equals(mailbox.poll())
                && mailbox.poll() == null, "가득 찬 큐에서 이동부터 제거하지 않았습니다");
        mailbox.offer("ROTATE_LEFT");
        mailbox.offer("ROTATE_RIGHT");
        mailbox.offer("HARD_DROP");
        mailbox.offer("ROTATE_LEFT");
        for (int i = 0; i < 1000; i++) mailbox.offer(i % 2 == 0 ? "LEFT" : "RIGHT");
        require("ROTATE_LEFT".equals(mailbox.poll()) && "ROTATE_RIGHT".equals(mailbox.poll())
                && "HARD_DROP".equals(mailbox.poll()) && "ROTATE_LEFT".equals(mailbox.poll())
                && mailbox.poll() == null, "가득 찬 중요 입력 큐에 OS 반복 입력이 침입했습니다");
        mailbox.offer("ROTATE_LEFT");
        mailbox.offer("HARD_DROP");
        Thread.sleep(350); // 외부 서버의 250ms 이상 RTT에서도 단발 입력을 보존합니다.
        require("ROTATE_LEFT".equals(mailbox.poll()) && "HARD_DROP".equals(mailbox.poll())
                && mailbox.poll() == null, "정상 원격 지연으로 회전·낙하 입력이 만료되었습니다");
        mailbox.offer("RIGHT");
        Thread.sleep(1100);
        require(mailbox.poll() == null, "오래된 키 입력이 실행됩니다");
        mailbox.offer("LEFT");
        mailbox.clear();
        require(mailbox.poll() == null, "포커스 이동 후 입력이 남습니다");
    }

    private static void verifyRendering() {
        OnlineMatchWindow.BoardCanvas canvas = new OnlineMatchWindow.BoardCanvas();
        canvas.cells[0] = 1;
        canvas.cells[219] = 7;
        canvas.setSize(202, 442);
        BufferedImage image = render(canvas);
        require(image.getRGB(11, 431) != Style.BOARD_BACKGROUND.getRGB(), "y=0은 아래쪽이어야 합니다");
        require(image.getRGB(191, 11) != Style.BOARD_BACKGROUND.getRGB(), "y=21은 위쪽이어야 합니다");
        require(image.getRGB(11, 11) == Style.BOARD_BACKGROUND.getRGB(), "빈칸 방향이 바뀌었습니다");
        int before = image.getRGB(11, 431);
        canvas.ended = true;
        require(render(canvas).getRGB(11, 431) != before, "게임오버 오버레이가 없습니다");
        OnlineMatchWindow.BoardView view = new OnlineMatchWindow.BoardView("나", Resolution.SMALL);
        OnlineFrame frame = frame("PLAYING", -1, "NONE", 0);
        view.update(frame.getPlayer(1));
        require(view.name.getText().contains("두번째"), "좌석 1 닉네임 누락");
        require(view.metrics.getText().contains("40점") && view.metrics.getText().contains("3줄"), "점수와 줄수 표시 오류");
        require(view.next.shape == 7, "다음 블록 표시 오류");
        view.update(new OnlineFrame.Player("두번째", false, 0, 0, false, 0, new int[220]), false);
        require(!view.canvas.ended, "경기 시작 전 게임오버가 표시됩니다");
        require("03:01".equals(OnlineMatchWindow.formatElapsedTime(181999)), "서버 경과 시간 표시 오류");
        require("시작까지 3초".equals(OnlineMatchWindow.statusText(frame("COUNTDOWN", -1, "NONE", 3000))), "카운트다운 표시 오류");
        require(OnlineMatchWindow.statusText(frame("FINISHED", 1, "DISCONNECT", 0)).startsWith("승리!"), "좌석 1 승패 표시 오류");
        require("무승부".equals(OnlineMatchWindow.statusText(frame("FINISHED", -1, "SCORE", 0))), "무승부 표시 오류");
        require(OnlineMatchWindow.statusText(frame("FINISHED", -1, "EXPIRED", 0)).startsWith("방 종료"), "대기 방 만료는 경기 무승부가 아닙니다");
    }

    private static BufferedImage render(javax.swing.JPanel canvas) {
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { canvas.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }

    private static void verifyCompatibilityError() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            PrintWriter writer = writer(socket);
            writer.println("TETRIS/1 READY");
            reader(socket).readLine();
            writer.println("ERROR BAD_REQUEST");
        }); OnlineClient client = new OnlineClient("127.0.0.1", server.port())) {
            try {
                client.create("테스트");
                throw new AssertionError("구서버 응답이 성공 처리되었습니다");
            } catch (IOException error) {
                require(error.getMessage().contains("서버 업데이트"), "구서버 업데이트 안내 누락");
            }
        }
    }

    private static void verifyCloseCancelsRead() throws Exception {
        CountDownLatch requested = new CountDownLatch(1);
        try (FakeServer server = new FakeServer(socket -> {
            writer(socket).println("TETRIS/1 READY");
            BufferedReader reader = reader(socket);
            reader.readLine();
            requested.countDown();
            require(reader.readLine() == null, "취소 후 소켓이 열려 있습니다");
        })) {
            OnlineClient client = new OnlineClient("127.0.0.1", server.port());
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<?> result = worker.submit(() -> {
                    try { client.create("첫번째"); throw new AssertionError("취소된 요청이 성공했습니다"); }
                    catch (IOException expected) { }
                });
                require(requested.await(2, TimeUnit.SECONDS), "생성 요청이 도착하지 않았습니다");
                long started = System.nanoTime();
                SwingUtilities.invokeAndWait(client::close);
                result.get(1, TimeUnit.SECONDS);
                require(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 500, "EDT에서 read 취소가 지연되었습니다");
            } finally { client.close(); worker.shutdownNow(); }
        }
    }

    private static void verifyCancelSuppressesLateFrame() throws Exception {
        CountDownLatch requestSeen = new CountDownLatch(1);
        CountDownLatch releaseReply = new CountDownLatch(1);
        CountDownLatch replySent = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        try (FakeServer server = new FakeServer(socket -> {
            PrintWriter writer = writer(socket);
            writer.println("TETRIS/1 READY");
            reader(socket).readLine();
            requestSeen.countDown();
            releaseReply.await(2, TimeUnit.SECONDS);
            writer.println(frame("PLAYING", -1, "NONE", 0).encode());
            replySent.countDown();
            reader(socket).readLine();
        })) {
            OnlineSession session = new OnlineSession(new OnlineClient("127.0.0.1", server.port()),
                    frame -> callbacks.incrementAndGet(), error -> callbacks.incrementAndGet());
            session.start("첫번째", null);
            require(requestSeen.await(2, TimeUnit.SECONDS), "요청 타임아웃");
            // EDT가 다른 작업으로 바쁜 사이 네트워크 응답이 도착하는 상황입니다.
            SwingUtilities.invokeAndWait(() -> {
                releaseReply.countDown();
                try {
                    require(replySent.await(1, TimeUnit.SECONDS), "응답 타임아웃");
                    Thread.sleep(100);
                } catch (InterruptedException error) { throw new RuntimeException(error); }
                session.close();
            });
            SwingUtilities.invokeAndWait(() -> { });
            require(callbacks.get() == 0, "취소한 화면에 늦은 응답이 반영됩니다");
        }
    }

    private static void verifyResponsePacing() throws Exception {
        AtomicLong renderedAt = new AtomicLong();
        AtomicLong nextRequestAt = new AtomicLong();
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        CountDownLatch finished = new CountDownLatch(1);
        try (FakeServer server = new FakeServer(socket -> {
            PrintWriter writer = writer(socket);
            BufferedReader reader = reader(socket);
            writer.println("TETRIS/1 READY");
            reader.readLine();
            Thread.sleep(120);
            writer.println(frame("PLAYING", -1, "NONE", 0).encode());
            require("POLL".equals(reader.readLine()), "다음 요청이 POLL이 아닙니다");
            nextRequestAt.set(System.nanoTime());
            writer.println(frame("FINISHED", 1, "SCORE", 0).encode());
        })) {
            OnlineSession session = new OnlineSession(new OnlineClient("127.0.0.1", server.port()), frame -> {
                if (callbacks.incrementAndGet() == 1) {
                    try { Thread.sleep(120); } catch (InterruptedException error) { throw new RuntimeException(error); }
                    renderedAt.set(System.nanoTime());
                } else { finished.countDown(); }
            }, error -> { failure.set(error); finished.countDown(); });
            try {
                session.start("첫번째", null);
                require(finished.await(3, TimeUnit.SECONDS), "프레임 간격 테스트 타임아웃");
                require(failure.get() == null, "프레임 간격 오류: " + failure.get());
                require(callbacks.get() == 2, "프레임 중복 또는 누락");
                require(TimeUnit.NANOSECONDS.toMillis(nextRequestAt.get() - renderedAt.get()) >= 45,
                        "늦은 EDT 반영 뒤 50ms 간격 없이 요청이 누적됩니다");
            } finally { session.close(); }
        }
    }

    private static void verifySlowResponseDeadline() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            writer(socket).println("TETRIS/1 READY");
            reader(socket).readLine();
            for (int i = 0; i < 50; i++) {
                try { socket.getOutputStream().write('O'); socket.getOutputStream().flush(); }
                catch (IOException closed) { return; }
                Thread.sleep(200);
            }
        }); OnlineClient client = new OnlineClient("127.0.0.1", server.port())) {
            long started = System.nanoTime();
            try { client.create("첫번째"); throw new AssertionError("느린 응답에 시간 제한이 없습니다"); }
            catch (IOException expected) {
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                require(elapsed >= 7800 && elapsed < 8800, "응답 전체 8초 제한 오류: " + elapsed);
            }
        }
    }

    private static void verifyRotationDropSequence() throws Exception {
        AtomicReference<OnlineSession> reference = new AtomicReference<OnlineSession>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        AtomicInteger callbacks = new AtomicInteger();
        CountDownLatch finished = new CountDownLatch(1);
        try (FakeServer server = new FakeServer(socket -> {
            PrintWriter writer = writer(socket);
            BufferedReader reader = reader(socket);
            writer.println("TETRIS/1 READY");
            reader.readLine();
            writer.println(frame("PLAYING", -1, "NONE", 0).encode());
            require("INPUT ROTATE_LEFT".equals(reader.readLine()), "회전이 즉시 낙하에 덮어쓰였습니다");
            Thread.sleep(350); // 회전 응답 지연 중 대기하는 HARD_DROP이 유실되지 않아야 합니다.
            writer.println(frame("PLAYING", -1, "NONE", 0).encode());
            require("INPUT HARD_DROP".equals(reader.readLine()), "회전 후 즉시 낙하가 유실되었습니다");
            // 낙하 응답이 오기 전에 누른 키는 새 블록에 적용하지 않아야 합니다.
            reference.get().input("LEFT");
            Thread.sleep(80);
            writer.println(frame("PLAYING", -1, "NONE", 0).encode());
            require("POLL".equals(reader.readLine()), "즉시 낙하 처리 중 키가 새 블록까지 남았습니다");
            writer.println(frame("FINISHED", 1, "SCORE", 0).encode());
        })) {
            OnlineSession session = new OnlineSession(new OnlineClient("127.0.0.1", server.port()), frame -> {
                if (callbacks.incrementAndGet() == 1) {
                    reference.get().input("ROTATE_LEFT");
                    reference.get().input("HARD_DROP");
                }
                if (frame.isFinished()) finished.countDown();
            }, error -> { failure.set(error); finished.countDown(); });
            reference.set(session);
            try {
                session.start("첫번째", null);
                require(finished.await(3, TimeUnit.SECONDS), "회전·낙하 순서 테스트 타임아웃");
                require(failure.get() == null && callbacks.get() == 4, "회전·낙하 통신 오류: " + failure.get());
            } finally { session.close(); }
        }
    }

    private static OnlineFrame frame(String phase, int winner, String reason, long countdown) {
        return new OnlineFrame("AB1234", 1, phase, winner, reason, 181999, countdown,
                new OnlineFrame.Player("첫번째", true, 10, 1, true, 3, new int[220]),
                new OnlineFrame.Player("두번째", true, 40, 3, true, 7, new int[220]));
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
    }
    private static PrintWriter writer(Socket socket) throws IOException { return new PrintWriter(socket.getOutputStream(), true); }
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private interface Conversation { void run(Socket socket) throws Exception; }

    private static final class FakeServer implements AutoCloseable {
        final ServerSocket server;
        final Thread thread;
        volatile Socket connected;
        volatile Throwable failure;
        FakeServer(Conversation conversation) throws IOException {
            server = new ServerSocket(0);
            thread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    connected = socket;
                    socket.setSoTimeout(4000);
                    conversation.run(socket);
                } catch (Throwable error) { failure = error; }
            }, "online-test-server");
            thread.setDaemon(true);
            thread.start();
        }
        int port() { return server.getLocalPort(); }
        @Override public void close() throws Exception {
            server.close();
            if (connected != null) connected.close();
            thread.join(1000);
            if (failure != null && !(failure instanceof java.net.SocketException))
                throw new AssertionError("모의 서버 오류", failure);
        }
    }
}
