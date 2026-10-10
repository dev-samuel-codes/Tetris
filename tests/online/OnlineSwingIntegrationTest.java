package online;

import frontend.Main;
import frontend.Resolution;
import frontend.network.ScoreClient;
import frontend.online.OnlineMatchWindow;
import shared.network.OnlineFrame;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

// 실제 Swing 창과 별도 서버로 메뉴 진입부터 경기, 방 이탈, 메뉴 복귀까지 연결 검증
// 그래픽 데스크톱에서 실행하며 개인 프로필과 실제 서버의 기록은 사용하지 않음
public final class OnlineSwingIntegrationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("tetris-online-swing-");
        int port;
        try (ServerSocket free = new ServerSocket(0)) { port = free.getLocalPort(); }
        System.setProperty("tetris.server.host", "127.0.0.1");
        System.setProperty("tetris.server.port", Integer.toString(port));
        System.setProperty("tetris.data.dir", temporary.resolve("profile").toString());
        Process server = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                "backend.network.ScoreServer", Integer.toString(port), temporary.resolve("scores").toString())
                .redirectErrorStream(true).redirectOutput(temporary.resolve("server.log").toFile()).start();
        try {
            ScoreClient score = new ScoreClient("127.0.0.1", port);
            await(() -> { try { score.loadBestScore("준비"); return true; } catch (Exception e) { return false; } }, 3000);
            Main menu = edt(() -> { Main main = new Main(); main.setVisible(true); return main; });
            edt(() -> { button(menu, "멀티플레이").doClick(); button(menu, "네트워크 대전").doClick(); return null; });
            OnlineMatchWindow first = edt(() -> {
                for (Window window : Window.getWindows())
                    if (window instanceof OnlineMatchWindow && window.isVisible()) return (OnlineMatchWindow) window;
                throw new AssertionError("네트워크 메뉴가 실제 화면을 열지 않습니다.");
            });
            require(!edt(menu::isDisplayable), "기존 메뉴 종료");
            save(first, temporary.resolve("01-connect.png"));
            edt(() -> {
                first.setLocation(25, 40);
                text(first, "nicknameField").setText("첫번째");
                ((JButton) field(first, "createButton")).doClick();
                return null;
            });
            await(() -> edt(() -> latest(first) != null), 4000);
            String code = edt(() -> latest(first).getRoomCode());
            OnlineMatchWindow second = edt(() -> {
                OnlineMatchWindow window = new OnlineMatchWindow(Resolution.SMALL);
                window.setLocation(300, 60);
                window.setVisible(true);
                text(window, "nicknameField").setText("두번째");
                text(window, "codeField").setText(code);
                ((JButton) field(window, "joinButton")).doClick();
                return window;
            });
            await(() -> edt(() -> latest(second) != null && !latest(first).getPlayer(1).getNickname().isEmpty()), 4000);
            save(first, temporary.resolve("02-room.png"));
            edt(() -> {
                ((JButton) field(first, "ready")).doClick();
                ((JButton) field(second, "ready")).doClick();
                return null;
            });
            await(() -> edt(() -> "COUNTDOWN".equals(latest(first).getPhase())), 2000);
            require(edt(() -> latest(first).getCountdownMillis() > 0), "카운트다운 표시");
            await(() -> edt(() -> "PLAYING".equals(latest(first).getPhase())
                    && "PLAYING".equals(latest(second).getPhase())), 5000);
            int before = edt(() -> minX(latest(first).getPlayer(0).getCells()));
            key(first, "LEFT");
            await(() -> edt(() -> minX(latest(second).getPlayer(0).getCells()) == before - 1), 2000);
            require(edt(() -> minX(latest(first).getPlayer(0).getCells()) == before - 1), "화면 키바인딩과 양쪽 보드 반영");
            key(first, "UP");
            key(first, "SPACE");
            await(() -> edt(() -> occupied(latest(first).getPlayer(0).getCells()) >= 8), 2000);
            require(edt(() -> occupied(latest(second).getPlayer(1).getCells()) == 4), "내 키는 상대 보드를 움직이지 않음");
            key(second, "RIGHT");
            key(second, "SPACE");
            await(() -> edt(() -> occupied(latest(second).getPlayer(1).getCells()) >= 8), 2000);
            save(first, temporary.resolve("03-playing.png"));
            edt(() -> { first.setSize(680, 620); first.validate(); return null; });
            Thread.sleep(100); // 운영체제 크기 변경 이벤트가 새 레이아웃에 반영된 뒤 캡처
            save(first, temporary.resolve("04-small-window.png"));
            require(edt(() -> button(first, "방 나가기").isShowing()), "작은 창에서도 방 나가기 표시");
            edt(() -> { button(first, "방 나가기").doClick(); return null; });
            await(() -> edt(() -> latest(second).isFinished()), 3000);
            require(edt(() -> latest(second).getWinner() == 1), "실제 방 나가기 후 상대 승리");
            require(edt(() -> field(first, "session") == null && field(first, "latest") == null), "이탈 후 폼과 세션 정리");
            save(second, temporary.resolve("05-result.png"));
            edt(() -> { button(second, "메뉴 복귀").doClick(); return null; });
            require(!edt(second::isDisplayable), "메뉴 복귀 후 게임 창 정리");
            require(edt(() -> {
                for (Window window : Window.getWindows())
                    if (window instanceof Main && window.isVisible()) return true;
                return false;
            }), "실제 메인 메뉴 복원");
            Thread.sleep(200);
            require(!edt(second::isDisplayable), "늦은 응답이 닫힌 창을 복원하지 않음");
            System.out.println("OnlineSwingIntegrationTest: " + checks + " checks passed; screenshots: " + temporary);
        } finally {
            edt(() -> { for (Window window : Window.getWindows()) window.dispose(); return null; });
            server.destroy();
            if (!server.waitFor(3, TimeUnit.SECONDS)) server.destroyForcibly();
        }
    }

    private static void key(OnlineMatchWindow window, String key) throws Exception {
        edt(() -> {
            JComponent content = (JComponent) window.getContentPane();
            content.getActionMap().get("press-" + key).actionPerformed(new ActionEvent(content, 0, key));
            content.getActionMap().get("release-" + key).actionPerformed(new ActionEvent(content, 0, key));
            return null;
        });
    }

    private static JTextField text(Object owner, String name) throws Exception { return (JTextField) field(owner, name); }
    private static OnlineFrame latest(Object owner) throws Exception { return (OnlineFrame) field(owner, "latest"); }
    private static Object field(Object owner, String name) throws Exception {
        Field value = owner.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return value.get(owner);
    }

    private static AbstractButton button(Container parent, String label) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton) {
                String caption = ((AbstractButton) child).getText();
                if (caption != null && caption.contains(label)) return (AbstractButton) child;
            }
            if (child instanceof Container) {
                AbstractButton found = findButton((Container) child, label);
                if (found != null) return found;
            }
        }
        throw new AssertionError("버튼 없음: " + label);
    }

    private static AbstractButton findButton(Container parent, String label) {
        try { return button(parent, label); } catch (AssertionError absent) { return null; }
    }

    private static int minX(int[] cells) {
        int result = 10;
        for (int i = 0; i < cells.length; i++) if (cells[i] != 0) result = Math.min(result, i % 10);
        return result;
    }
    private static int occupied(int[] cells) {
        int result = 0;
        for (int value : cells) if (value != 0) result++;
        return result;
    }

    private static <T> T edt(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<T>(callable);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void save(OnlineMatchWindow window, Path path) throws Exception {
        edt(() -> {
            Container content = window.getContentPane();
            BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try { content.paint(graphics); } finally { graphics.dispose(); }
            ImageIO.write(image, "png", path.toFile());
            return null;
        });
    }

    private static void await(Callable<Boolean> check, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (check.call()) { checks++; return; }
            Thread.sleep(25);
        }
        throw new AssertionError("화면 상태 대기 시간 초과");
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}
