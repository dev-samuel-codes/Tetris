package score;

import frontend.Board;
import frontend.Main;
import frontend.ScoreManager;
import frontend.SidePanel;
import frontend.SoundManager;
import frontend.engine.GameEngine;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

// 임시 프로필과 로컬 응답 서버로 실제 Swing 화면의 모드 선택·저장 경로 확인
public final class ScoreUiTest {
    private static Main menu;
    private static int checks;
    private static Path screenshots;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless())
            throw new AssertionError("ScoreUiTest는 화면이 있는 환경에서 실행해야 합니다.");
        try (LocalServer server = new LocalServer()) {
            screenshots = Files.createTempDirectory("tetris-score-ui-screens-");
            System.setProperty("tetris.data.dir", Files.createTempDirectory("tetris-score-ui-").toString());
            System.setProperty("tetris.server.host", "127.0.0.1");
            System.setProperty("tetris.server.port", Integer.toString(server.socket.getLocalPort()));
            SoundManager.setMuted(true);
            for (int i = 0; i < 7; i++) {
                server.scores.put("CLASSIC/User" + i, 100 + i);
                server.scores.put("ITEM/User" + i, 500 + i);
            }
            try {
                edt(() -> {
                    menu = new Main();
                    menu.setVisible(true);
                    require(!button("클래식").isEnabled(), "닉네임 없는 클래식 시작 차단");
                    require(!button("아이템").isEnabled(), "닉네임 없는 아이템 시작 차단");
                    find(JTextField.class, null).setText("User0");
                    button("적용").doClick();
                });
                await(() -> labelText("서버 기록을 불러왔습니다"));
                edt(() -> {
                    require("User0".equals(find(JTextField.class, null).getText()), "메뉴에 적용한 닉네임 표시");
                    require("100".equals(find(JLabel.class, "클래식 최고 점수").getText()), "메뉴 클래식 최고 점수");
                    require("500".equals(find(JLabel.class, "아이템 최고 점수").getText()), "메뉴 아이템 최고 점수");
                    require(button("아이템").isEnabled(), "닉네임 적용 후 아이템 시작 허용");
                    capture("home.png");
                    button("랭킹").doClick();
                });
                await(() -> labelText("전체 7명 · 클래식 모드 최고 점수"));
                edt(() -> {
                    capture("classic-ranking.png");
                    button("아이템 랭킹").doClick();
                });
                await(() -> labelText("전체 7명 · 아이템 모드 최고 점수"));
                edt(() -> {
                    capture("item-ranking.png");
                    button("다음 →").doClick();
                });
                await(() -> labelText("2 / 2"));
                edt(() -> button("새로고침").doClick());
                await(() -> labelText("2 / 2") && labelText("전체 7명 · 아이템 모드 최고 점수"));
                edt(() -> {
                    require(button("아이템 랭킹").isSelected(), "페이지·새로고침 후 아이템 선택 유지");
                    button("클래식 랭킹").doClick();
                });
                await(() -> labelText("1 / 2") && labelText("전체 7명 · 클래식 모드 최고 점수"));
                require(server.requests.contains("RANKING ITEM 5 5"), "아이템 2페이지를 서버에서 조회");

                // 이전 모드 응답을 지연시킨 뒤 새 모드로 전환해 응답이 현재 화면을 덮지 않는지 확인
                server.holdRanking = true;
                edt(() -> button("새로고침").doClick());
                require(server.held.await(3, TimeUnit.SECONDS), "지연된 클래식 요청 도착");
                final Container[] itemPage = new Container[1];
                edt(() -> {
                    button("아이템 랭킹").doClick();
                    itemPage[0] = menu.getContentPane();
                });
                server.release.countDown();
                await(() -> labelText("전체 7명 · 아이템 모드 최고 점수"));
                edt(() -> require(menu.getContentPane() == itemPage[0], "늦은 클래식 응답 이후 아이템 화면 유지"));

                // 클래식 실패 후 아이템 성공이 도착해도 통합 상태의 오류 안내는 남아 있어야 함
                server.failClassicBest = true;
                int itemBestRequests = server.requestCount("BEST ITEM VXNlcjA");
                edt(() -> {
                    menu.dispose();
                    menu = new Main();
                    menu.setVisible(true);
                });
                await(() -> server.requestCount("BEST ITEM VXNlcjA") > itemBestRequests
                        && labelText("서버 조회 실패 · 이 기기에 보관된 기록입니다."));
                // 같은 worker의 다음 콜백까지 기다리면 앞선 두 조회의 EDT 반영도 끝난 상태
                CountDownLatch menuQueriesCompleted = new CountDownLatch(1);
                ScoreManager.loadBestScore("UiBarrier", result -> menuQueriesCompleted.countDown());
                if (!menuQueriesCompleted.await(3, TimeUnit.SECONDS))
                    throw new AssertionError("모드별 조회의 화면 반영 대기 시간 초과");
                edt(() -> require(labelText("서버 조회 실패 · 이 기기에 보관된 기록입니다."),
                        "다른 모드 조회 성공 후에도 실패 상태 유지"));
                server.failClassicBest = false;

                ScoreManager.setNickname("BoardUser");
                endBoard(false, false, 231);
                await(() -> server.scores.getOrDefault("CLASSIC/BoardUser", 0) == 231);
                endBoard(true, false, 987);
                await(() -> server.scores.getOrDefault("ITEM/BoardUser", 0) == 987);
                require(server.scores.get("CLASSIC/BoardUser") == 231, "아이템 게임 종료가 클래식 기록을 유지");
                int submits = server.submitCount();
                endBoard(true, true, 9999);
                // 동일 worker의 조회 완료는 앞선 게임 종료 저장 작업이 모두 처리된 시점
                CountDownLatch barrier = new CountDownLatch(1);
                ScoreManager.loadBestScore("BoardUser", result -> barrier.countDown());
                require(barrier.await(3, TimeUnit.SECONDS), "종료 저장 작업 완료 대기");
                require(server.submitCount() == submits, "1PC 대전 점수는 싱글 랭킹에 저장하지 않음");
                System.out.println("ScoreUiTest: " + checks + " checks passed; screenshots: " + screenshots);
            } finally {
                server.release.countDown();
                edt(() -> { if (menu != null) menu.dispose(); });
            }
        }
    }

    private static void endBoard(boolean item, boolean battle, int score) throws Exception {
        edt(() -> {
            try {
                Board board = new Board(new JLabel(), new SidePanel(), item);
                board.setBattleMode(battle);
                board.start();
                Field points = GameEngine.class.getDeclaredField("score");
                points.setAccessible(true);
                points.setInt(board.getEngine(), score);
                Field started = GameEngine.class.getDeclaredField("isStarted");
                started.setAccessible(true);
                started.setBoolean(board.getEngine(), false);
                board.actionPerformed(null);
                board.stop();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    private static AbstractButton button(String name) {
        AbstractButton button = find(AbstractButton.class, name);
        if (button == null) throw new AssertionError("버튼을 찾지 못함: " + name);
        return button;
    }

    private static <T extends Component> T find(Class<T> type, String name) {
        for (Component component : components(menu.getContentPane())) {
            if (type.isInstance(component) && (name == null || name.equals(component.getAccessibleContext().getAccessibleName())))
                return type.cast(component);
        }
        return null;
    }

    private static boolean labelText(String text) {
        for (Component component : components(menu.getContentPane()))
            if (component instanceof JLabel && text.equals(((JLabel) component).getText())) return true;
        return false;
    }

    private static List<Component> components(Container parent) {
        List<Component> children = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            children.add(child);
            if (child instanceof Container) children.addAll(components((Container) child));
        }
        return children;
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            FutureTask<Boolean> check = new FutureTask<>(condition::getAsBoolean);
            SwingUtilities.invokeAndWait(check);
            if (check.get()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("화면 또는 저장 결과 대기 시간 초과");
    }

    private static void edt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }
    private static void capture(String name) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(menu.getWidth(), menu.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            menu.printAll(graphics);
            javax.imageio.ImageIO.write(image, "png", screenshots.resolve(name).toFile());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        } finally {
            graphics.dispose();
        }
    }
    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static final class LocalServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 10, java.net.InetAddress.getLoopbackAddress());
        final Map<String, Integer> scores = new ConcurrentHashMap<>();
        final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch held = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean holdRanking;
        volatile boolean failClassicBest;

        LocalServer() throws Exception {
            Thread responder = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket client = socket.accept();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                         PrintWriter writer = new PrintWriter(client.getOutputStream(), true)) {
                        writer.println("TETRIS/1 READY");
                        String request = reader.readLine();
                        requests.add(request);
                        String[] fields = request.split(" ");
                        String mode = fields[1];
                        if ("RANKING".equals(fields[0])) {
                            if (holdRanking && "CLASSIC".equals(mode)) {
                                holdRanking = false;
                                held.countDown();
                                release.await(3, TimeUnit.SECONDS);
                            }
                            List<Map.Entry<String, Integer>> ranked = new ArrayList<>();
                            for (Map.Entry<String, Integer> entry : scores.entrySet())
                                if (entry.getKey().startsWith(mode + "/")) ranked.add(entry);
                            ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                            int offset = Integer.parseInt(fields[2]);
                            int end = Math.min(offset + Integer.parseInt(fields[3]), ranked.size());
                            writer.println("RANKING " + ranked.size() + " " + Math.max(0, end - offset));
                            for (int i = offset; i < end; i++) {
                                Map.Entry<String, Integer> entry = ranked.get(i);
                                writer.println("ROW " + (i + 1) + " " + encode(entry.getKey().substring(mode.length() + 1)) + " " + entry.getValue());
                            }
                            writer.println("END");
                        } else {
                            String nickname = new String(Base64.getUrlDecoder().decode(fields[2]), StandardCharsets.UTF_8);
                            String key = mode + "/" + nickname;
                            if ("SUBMIT".equals(fields[0])) {
                                scores.merge(key, Integer.parseInt(fields[3]), Math::max);
                                writer.println("OK " + scores.get(key));
                            } else if (failClassicBest && "CLASSIC".equals(mode)) {
                                writer.println("ERROR TEST_FAILURE");
                            } else {
                                writer.println("BEST " + scores.getOrDefault(key, 0));
                            }
                        }
                    } catch (Exception e) {
                        if (!socket.isClosed()) throw new AssertionError(e);
                    }
                }
            }, "score-ui-test-server");
            responder.setDaemon(true);
            responder.start();
        }

        int submitCount() {
            synchronized (requests) {
                return (int) requests.stream().filter(request -> request.startsWith("SUBMIT ")).count();
            }
        }
        int requestCount(String expected) {
            synchronized (requests) {
                return java.util.Collections.frequency(requests, expected);
            }
        }
        private static String encode(String text) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        }
        public void close() throws Exception { socket.close(); }
    }
}
