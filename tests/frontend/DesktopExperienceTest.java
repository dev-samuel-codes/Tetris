package frontend;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

/** 실제 설정 화면의 캡처 시작/취소, 저장, 가이드 이동을 임시 프로필에서 확인합니다. */
public final class DesktopExperienceTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path profile = Files.createTempDirectory("tetris-desktop-ui-");
        Path images = args.length == 0 ? Files.createTempDirectory("tetris-desktop-images-") : Paths.get(args[0]);
        Files.createDirectories(images);
        System.setProperty("tetris.data.dir", profile.toString());
        System.setProperty("tetris.server.host", "127.0.0.1");
        System.setProperty("tetris.server.port", "1");
        ScoreManager.setNickname("PLAYER");
        final Main[] menuWindow = new Main[1];
        SwingUtilities.invokeAndWait(() -> {
            Main menu = new Main();
            menuWindow[0] = menu;
            try {
                menu.setVisible(true);
                capture(menu, images.resolve("01-home.png"));
                button(menu, "설정").doClick();
                JButton left = (JButton) button(menu, "왼쪽 이동 키");
                press(left, KeyEvent.VK_A);
                require(GameSettings.getLeftKey() == KeyEvent.VK_LEFT, "캡처 시작 전 입력은 설정을 바꾸면 안 됩니다");
                left.doClick();
                press(left, KeyEvent.VK_RIGHT);
                require(GameSettings.getLeftKey() == KeyEvent.VK_LEFT, "중복키 거부");
                press(left, KeyEvent.VK_ESCAPE);
                require(GameSettings.getLeftKey() == KeyEvent.VK_LEFT, "Escape 캡처 취소");
                left.doClick();
                press(left, KeyEvent.VK_A);
                require(GameSettings.getLeftKey() == KeyEvent.VK_A, "선택한 새 키 저장");
                require("A".equals(left.getText()), "저장된 키 표시");
                JSlider music = (JSlider) find(menu, "배경음악 음량");
                music.setValue(25);
                require(GameSettings.getMusicVolume() == 25, "배경음악 음량 저장");
                capture(menu, images.resolve("02-settings.png"));
                menu.setSize(800, 650);
                menu.validate();
                JButton pause = (JButton) button(menu, "일시정지 키");
                require(pause.getVisibleRect().contains(new java.awt.Rectangle(0, 0,
                        pause.getWidth(), pause.getHeight())), "작은 창에서 마지막 조작키까지 스크롤 없이 표시");
                require(button(menu, "기본값 복원").isShowing(), "작은 창에서도 초기화 버튼 표시");
                capture(menu, images.resolve("10-settings-compact.png"));
                menu.setSize(960, 740);
                menu.validate();
                button(menu, "기본값 복원").doClick();
                require(GameSettings.getLeftKey() == KeyEvent.VK_LEFT, "기본 조작키 복원");
                require(Files.isRegularFile(profile.resolve("settings.properties")), "설정 파일 생성");
                button(menu, "← 뒤로가기").doClick();
                button(menu, "플레이 가이드").doClick();
                capture(menu, images.resolve("03-guide.png"));
                button(menu, "← 뒤로가기").doClick();
                menu.setSize(800, 650);
                menu.validate();
                require(button(menu, "클래식").getWidth() > 200, "최소 창 크기에서 모드 카드 폭 확보");
                capture(menu, images.resolve("04-home-compact.png"));
            } catch (Exception | AssertionError error) {
                for (Window window : Window.getWindows()) window.dispose();
                throw new RuntimeException(error);
            }
        });
        verifyKeyboardNavigation(images, menuWindow[0]);
        System.out.println("DesktopExperienceTest: " + checks + " checks passed; screenshots: " + images);
    }

    private static void verifyKeyboardNavigation(Path images, Main menu) throws Exception {
        // macOS에서 마지막 창을 닫은 직후 재개방할 때 생기는 앱 활성화 경합을 피합니다.
        final Main[] window = { menu };
        final JButton[] keyButton = new JButton[1];
        SwingUtilities.invokeAndWait(() -> {
            window[0].setAlwaysOnTop(true);
            window[0].setVisible(true);
            window[0].toFront();
            button(window[0], "설정").doClick();
            keyButton[0] = (JButton) button(window[0], "왼쪽 이동 키");
            keyButton[0].scrollRectToVisible(keyButton[0].getBounds());
            keyButton[0].requestFocusInWindow();
        });
        try {
            awaitFocus(keyButton[0]);
            tap(KeyEvent.VK_ENTER);
            SwingUtilities.invokeAndWait(() -> require("새 키 입력…".equals(keyButton[0].getText()), "Enter로 키 캡처 시작"));
            tap(KeyEvent.VK_ESCAPE);
            SwingUtilities.invokeAndWait(() -> {
                require(find(window[0], "왼쪽 이동 키") != null, "캡처 취소 Escape는 메뉴로 나가지 않음");
                require(GameSettings.getLeftKey() == KeyEvent.VK_LEFT, "실제 Escape 입력으로 변경 취소");
            });
            tap(KeyEvent.VK_ENTER);
            tap(KeyEvent.VK_A);
            require(GameSettings.getLeftKey() == KeyEvent.VK_A, "실제 키 이벤트로 재할당");
            tap(KeyEvent.VK_ENTER);
            tap(KeyEvent.VK_B, KeyEvent.SHIFT_DOWN_MASK);
            settleEvents();
            require(GameSettings.getLeftKey() == KeyEvent.VK_A, "조합키는 저장하지 않음");
            tap(KeyEvent.VK_ESCAPE);
            SwingUtilities.invokeAndWait(() -> {
                button(window[0], "작게").doClick();
            });
            settleEvents();
            SwingUtilities.invokeAndWait(() -> {
                Component focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                require(focused == button(window[0], "작게"), "설정 재구성 뒤 선택 버튼의 포커스 복원");
            });
            final Component[] slider = new Component[1];
            SwingUtilities.invokeAndWait(() -> {
                // 작은 디스플레이에서 사용하는 한 열 배치도 검사합니다.
                window[0].setMinimumSize(new java.awt.Dimension(600, 650));
                window[0].setSize(600, 650);
                window[0].validate();
                button(window[0], "일시정지 키").requestFocusInWindow();
                slider[0] = find(window[0], "배경음악 음량");
            });
            settleEvents();
            SwingUtilities.invokeAndWait(() -> slider[0].requestFocusInWindow());
            awaitFocus(slider[0]);
            SwingUtilities.invokeAndWait(() -> {
                JSlider music = (JSlider) slider[0];
                javax.swing.JComponent row = (javax.swing.JComponent) music.getParent();
                require(row.getVisibleRect().contains(new java.awt.Rectangle(0, 0,
                        row.getWidth(), row.getHeight())), "한 열에서 조작키→음량 포커스 이동 시 이름과 슬라이더 함께 표시");
                try { capture(window[0], images.resolve("11-settings-narrow.png")); }
                catch (Exception error) { throw new RuntimeException(error); }
            });
            tap(KeyEvent.VK_ESCAPE);
            SwingUtilities.invokeAndWait(() -> require(button(window[0], "클래식").isShowing(), "하위 메뉴 Escape로 메인 복귀"));
        } finally {
            SwingUtilities.invokeAndWait(() -> window[0].dispose());
        }
    }

    private static void tap(int key) throws Exception { tap(key, 0); }

    private static void tap(int key, int modifiers) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            java.awt.KeyboardFocusManager manager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
            Component focused = manager.getFocusOwner();
            if (focused == null) throw new AssertionError("키보드 포커스 없음");
            // 리스너를 직접 호출하지 않고 Swing의 키 이벤트/바인딩 처리 전체 경로를 통과합니다.
            manager.redispatchEvent(focused, new KeyEvent(focused, KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(), modifiers, key, KeyEvent.CHAR_UNDEFINED));
            manager.redispatchEvent(focused, new KeyEvent(focused, KeyEvent.KEY_RELEASED,
                    System.currentTimeMillis(), modifiers, key, KeyEvent.CHAR_UNDEFINED));
        });
        // redispatchEvent는 EDT에서 동기 처리됩니다. 포커스 전환을 검사할 때만 별도로 대기합니다.
    }

    private static void settleEvents() throws Exception {
        // 포커스 복원과 네이티브 창 이벤트를 EDT에 반영할 시간을 둡니다.
        Thread.sleep(180);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void awaitFocus(Component component) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            final boolean[] focused = new boolean[1];
            SwingUtilities.invokeAndWait(() -> {
                focused[0] = component.isFocusOwner();
                if (!focused[0]) {
                    // 새 창의 native 활성화가 늦게 도착해도 테스트 대상에 포커스를 확보합니다.
                    Window owner = SwingUtilities.getWindowAncestor(component);
                    owner.toFront();
                    owner.requestFocus();
                    component.requestFocusInWindow();
                }
            });
            if (focused[0]) return;
            Thread.sleep(30);
        }
        throw new AssertionError("키보드 테스트 포커스를 얻지 못했습니다");
    }

    private static void press(JButton button, int key) {
        for (KeyListener listener : button.getKeyListeners()) {
            listener.keyPressed(new KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, key, KeyEvent.CHAR_UNDEFINED));
            listener.keyReleased(new KeyEvent(button, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, key, KeyEvent.CHAR_UNDEFINED));
        }
    }

    private static AbstractButton button(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton && name.equals(child.getAccessibleContext().getAccessibleName()))
                return (AbstractButton) child;
            if (child instanceof Container) {
                AbstractButton nested = findButton((Container) child, name);
                if (nested != null) return nested;
            }
        }
        throw new AssertionError("버튼 없음: " + name);
    }

    private static AbstractButton findButton(Container parent, String name) {
        try { return button(parent, name); } catch (AssertionError absent) { return null; }
    }

    private static Component find(Container parent, String name) {
        for (Component component : parent.getComponents()) {
            if (component.getAccessibleContext() != null
                    && name.equals(component.getAccessibleContext().getAccessibleName())) return component;
            if (component instanceof Container) {
                Component nested = find((Container) component, name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static void capture(Main window, Path file) throws Exception {
        window.validate();
        Container content = window.getContentPane();
        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { content.paint(graphics); } finally { graphics.dispose(); }
        ImageIO.write(image, "png", file.toFile());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
