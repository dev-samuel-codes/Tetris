package frontend;

import java.awt.Container;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** 실제 창에 등록된 포커스 이탈 콜백과 버튼·재시작·대전 결과 흐름을 검증합니다.
 * OS의 native 포커스 전환은 별도 화면 검증에서 확인합니다. */
public final class GameplayWindowTest {
    private static int checks;
    private static Path images;

    public static void main(String[] args) throws Exception {
        System.setProperty("tetris.data.dir", Files.createTempDirectory("tetris-game-window-").toString());
        System.setProperty("tetris.server.host", "127.0.0.1");
        System.setProperty("tetris.server.port", "1");
        SoundManager.setMuted(true);
        if (args.length > 0 && "--focus-regression".equals(args[0])) {
            try {
                verifyResumeFocus(false);
                verifyResumeFocus(true);
            } finally {
                SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) window.dispose(); });
            }
            System.out.println("GameplayWindowTest focus regression: " + checks + " checks passed");
            return;
        }
        images = args.length == 0 ? Files.createTempDirectory("tetris-game-images-") : Paths.get(args[0]);
        Files.createDirectories(images);
        SwingUtilities.invokeAndWait(() -> {
            try {
                verifySingleWindow();
                verifyResultDialog();
                verifyLocalMatch();
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { for (Window window : Window.getWindows()) window.dispose(); }
        });
        System.out.println("GameplayWindowTest: " + checks + " checks passed; screenshots: " + images);
    }

    private static void verifySingleWindow() throws Exception {
        Tetris game = new Tetris(Resolution.MEDIUM, "아이템");
        game.setVisible(true);
        Board board = (Board) field(Tetris.class, game, "board");
        JButton pause = (JButton) field(Tetris.class, game, "pauseButton");
        JButton menu = findButton(game.getContentPane(), "← 메뉴로 돌아가기");
        require(!pause.isFocusable() && !menu.isFocusable(), "싱글 진행 중 버튼의 게임 키 가로채기 방지");
        deliverRegisteredFocusLoss(game);
        deliverRegisteredFocusLoss(game);
        require(board.getEngine().isPaused(), "등록된 싱글 포커스 이탈 콜백의 자동 일시정지");
        require(pause.isFocusable() && menu.isFocusable(), "싱글 일시정지 중 버튼 키보드 접근 유지");
        require(!timer(board, "timer").isRunning() && !timer(board, "clockTimer").isRunning(), "일시정지 시 중력·시간 정지");
        capture(game.getContentPane(), images.resolve("05-item-paused.png"));
        pause.doClick();
        require(!board.getEngine().isPaused(), "계속하기 버튼으로 명시적 재개");
        require(!pause.isFocusable() && !menu.isFocusable(), "싱글 재개 뒤 버튼 포커스 해제");
        capture(game.getContentPane(), images.resolve("06-item-play.png"));
        invoke(Tetris.class, game, "restartGame");
        require(!game.isDisplayable(), "재시작 시 이전 창 해제");
        Tetris next = null;
        for (Window window : Window.getWindows())
            if (window instanceof Tetris && window.isDisplayable()) next = (Tetris) window;
        require(next != null, "재시작 시 새 게임 창 표시");
        Board nextBoard = (Board) field(Tetris.class, next, "board");
        require(nextBoard.getEngine().isItemMode() && nextBoard.getEngine().getScore() == 0, "재시작 시 모드 유지·점수 초기화");
        require(!timer(board, "timer").isRunning(), "이전 게임 타이머 해제");
        next.dispose();
    }

    private static void verifyResultDialog() throws Exception {
        final int[] actions = { 0, 0 };
        GameOverDialog result = new GameOverDialog(null, 12345, 24, 4, 97,
                () -> actions[0]++, () -> actions[1]++);
        require(result.isModal(), "결과 화면 집중 유지");
        require(result.getRootPane().getDefaultButton() != null, "Enter 기본 재시작 버튼");
        capture(result.getContentPane(), images.resolve("07-single-result.png"));
        JButton restart = result.getRootPane().getDefaultButton();
        restart.doClick();
        restart.doClick();
        require(actions[0] == 1, "재시작 중복 클릭 방지");
        GameOverDialog menuResult = new GameOverDialog(null, 0, 0, 0, 0,
                () -> actions[0]++, () -> actions[1]++);
        Object action = menuResult.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        menuResult.getRootPane().getActionMap().get(action).actionPerformed(new ActionEvent(menuResult, 0, "Escape"));
        require(actions[1] == 1, "결과 Escape 메뉴 복귀");
        result.dispose(); menuResult.dispose();
    }

    private static void verifyLocalMatch() throws Exception {
        TwoPlayerTetris match = new TwoPlayerTetris();
        match.setVisible(true);
        Board first = (Board) field(TwoPlayerTetris.class, match, "player1Board");
        Board second = (Board) field(TwoPlayerTetris.class, match, "player2Board");
        JButton pause = (JButton) field(TwoPlayerTetris.class, match, "pauseButton");
        JButton menu = findButton(match.getContentPane(), "메뉴로 돌아가기");
        require(!pause.isFocusable() && !menu.isFocusable(), "2인 진행 중 버튼의 게임 키 가로채기 방지");
        deliverRegisteredFocusLoss(match);
        require(first.getEngine().isPaused() && second.getEngine().isPaused(), "등록된 2인 포커스 이탈 콜백의 동시 일시정지");
        require(pause.isFocusable() && menu.isFocusable(), "2인 일시정지 중 버튼 키보드 접근 유지");
        pause.doClick();
        require(!first.getEngine().isPaused() && !second.getEngine().isPaused(), "2인 계속하기 동시 재개");
        require(!pause.isFocusable() && !menu.isFocusable(), "2인 재개 뒤 버튼 포커스 해제");
        capture(match.getContentPane(), images.resolve("08-local-match.png"));
        Field alive = first.getEngine().getClass().getDeclaredField("isStarted");
        alive.setAccessible(true); alive.setBoolean(first.getEngine(), false);
        // 결과 창의 modal 이벤트 루프에서 실제 재대전 버튼을 선택합니다.
        final Throwable[] callbackFailure = { null };
        SwingUtilities.invokeLater(() -> {
            try {
                JDialog result = (JDialog) field(TwoPlayerTetris.class, match, "resultDialog");
                require(!timer(first, "timer").isRunning() && !timer(second, "timer").isRunning(), "대전 결과 표시 전 두 게임판 정지");
                capture(result.getContentPane(), images.resolve("09-local-result.png"));
                result.getRootPane().getDefaultButton().doClick();
            } catch (Throwable error) {
                callbackFailure[0] = error;
                match.dispose();
            }
        });
        invoke(TwoPlayerTetris.class, match, "checkGameOver");
        if (callbackFailure[0] != null) throw new AssertionError("대전 결과 콜백 실패", callbackFailure[0]);
        require(!match.isDisplayable(), "재대전 시 이전 창 해제");
        TwoPlayerTetris next = null;
        for (Window window : Window.getWindows())
            if (window instanceof TwoPlayerTetris && window.isDisplayable()) next = (TwoPlayerTetris) window;
        require(next != null, "결과 화면에서 새 대전 표시");
        next.dispose();
    }

    // EDT를 비워 native 포커스 변경을 처리한 뒤, 실제 포커스 소유자의 Swing 키 처리 경로를 검증합니다.
    private static void verifyResumeFocus(boolean localMatch) throws Exception {
        Window[] window = new Window[1];
        Board[] boards = new Board[2];
        JButton[] pause = new JButton[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                if (localMatch) {
                    TwoPlayerTetris match = new TwoPlayerTetris();
                    window[0] = match;
                    boards[0] = (Board) field(TwoPlayerTetris.class, match, "player1Board");
                    boards[1] = (Board) field(TwoPlayerTetris.class, match, "player2Board");
                    pause[0] = (JButton) field(TwoPlayerTetris.class, match, "pauseButton");
                } else {
                    Tetris game = new Tetris(Resolution.MEDIUM);
                    window[0] = game;
                    boards[0] = (Board) field(Tetris.class, game, "board");
                    pause[0] = (JButton) field(Tetris.class, game, "pauseButton");
                }
                window[0].setVisible(true);
                window[0].toFront();
                boards[0].requestFocusInWindow();
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        awaitFocus(boards[0]);
        SwingUtilities.invokeAndWait(() -> {
            if (!boards[0].getEngine().isPaused()) pause[0].doClick();
            require(pause[0].isFocusable(), "정지 상태에서 계속하기 버튼 키보드 접근");
            pause[0].requestFocusInWindow();
        });
        awaitFocus(pause[0]);
        dispatchFocusedKey(KeyEvent.VK_SPACE);
        awaitFocus(boards[0]);
        SwingUtilities.invokeAndWait(() -> require(!boards[0].getEngine().isPaused(), "Space로 계속하기 버튼 실행 후 재개"));
        dispatchFocusedKey(KeyEvent.VK_SPACE);
        if (localMatch) dispatchFocusedKey(KeyEvent.VK_ENTER);
        SwingUtilities.invokeAndWait(() -> {
            require(!boards[0].getEngine().isPaused(), "재개 뒤 Space가 버튼을 재실행하지 않음");
            require(occupiedCells(boards[0]) == 4, "재개 뒤 Space가 1P 하드드롭 실행");
            if (localMatch) {
                require(!boards[1].getEngine().isPaused(), "재개 뒤 Enter가 버튼을 재실행하지 않음");
                require(occupiedCells(boards[1]) == 4, "재개 뒤 Enter가 2P 하드드롭 실행");
            }
            window[0].dispose();
        });
    }

    private static void awaitFocus(Component expected) throws Exception {
        // macOS의 새 Java 창 활성화가 늦어져도 포커스 이벤트를 처리할 시간을 확보합니다.
        long deadline = System.nanoTime() + 10_000_000_000L;
        boolean[] focused = { false };
        do {
            SwingUtilities.invokeAndWait(() -> {
                focused[0] = expected.isFocusOwner();
                if (!focused[0]) {
                    Window owner = SwingUtilities.getWindowAncestor(expected);
                    owner.toFront();
                    owner.requestFocus();
                    expected.requestFocusInWindow();
                }
            });
            if (focused[0]) break;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        require(focused[0], "실제 포커스 이동: " + expected.getClass().getSimpleName());
    }

    private static void dispatchFocusedKey(int key) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            Component focused = manager.getFocusOwner();
            require(focused != null, "키 입력 대상 포커스 존재");
            for (int type : new int[] { KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED })
                manager.redispatchEvent(focused, new KeyEvent(focused, type, System.currentTimeMillis(),
                        0, key, KeyEvent.CHAR_UNDEFINED));
        });
    }

    private static int occupiedCells(Board board) {
        int occupied = 0;
        for (int y = 0; y < Board.BOARD_HEIGHT; y++)
            for (int x = 0; x < Board.BOARD_WIDTH; x++)
                if (board.getEngine().shapeAt(x, y) != Tetrominoes.NoShape) occupied++;
        return occupied;
    }

    private static JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton && text.equals(((JButton) component).getText())) return (JButton) component;
            if (component instanceof Container) {
                JButton button = findButton((Container) component, text);
                if (button != null) return button;
            }
        }
        return null;
    }

    private static void deliverRegisteredFocusLoss(Window window) {
        require(window.getWindowFocusListeners().length > 0, "자동 일시정지 포커스 리스너 등록");
        WindowEvent event = new WindowEvent(window, WindowEvent.WINDOW_LOST_FOCUS);
        // native KFM 이벤트 주입 대신 등록된 콜백의 상태 전환 계약을 검증합니다.
        for (java.awt.event.WindowFocusListener listener : window.getWindowFocusListeners())
            listener.windowLostFocus(event);
    }

    private static void capture(Container content, Path file) throws Exception {
        content.validate();
        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { content.paint(graphics); } finally { graphics.dispose(); }
        ImageIO.write(image, "png", file.toFile());
    }
    private static Timer timer(Board board, String name) throws Exception { return (Timer) field(Board.class, board, name); }
    private static Object field(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void invoke(Class<?> type, Object target, String name) throws Exception {
        Method method = type.getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
