package frontend;

import frontend.engine.GameEngine;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

// 실제 키 처리 경로를 화면과 운영 프로필 없이 확인하는 테스트
public final class BoardControlsTest {
    private BoardControlsTest() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("tetris.data.dir", Files.createTempDirectory("tetris-controls-").toString());
        System.setProperty("tetris.server.host", "127.0.0.1");
        System.setProperty("tetris.server.port", "1");
        SwingUtilities.invokeAndWait(() -> {
            try {
                verifyClassic();
                verifyTwoPlayer();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        System.out.println("BoardControlsTest: 클래식·2인 좌우 이동, 레벨 5 반전, 입력 차단, 화면 갱신 통과");
    }

    private static void verifyClassic() throws Exception {
        Board board = createBoard();
        try {
            verifyDirections(board, key -> classicKey(board, key), KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                    "클래식");
            verifyBlocked(board, key -> classicKey(board, key), KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                    "클래식");
        } finally {
            board.stop();
        }
    }

    private static void verifyTwoPlayer() throws Exception {
        Board first = createBoard();
        Board second = createBoard();
        first.disableDefaultKeys();
        second.disableDefaultKeys();
        try {
            // JFrame 생성은 headless에서 불가능하므로 입력 메서드와 보드 참조만 준비
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Object unsafe = get(unsafeType, null, "theUnsafe");
            TwoPlayerTetris window = (TwoPlayerTetris) unsafeType.getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, TwoPlayerTetris.class);
            set(TwoPlayerTetris.class, window, "player1Board", first);
            set(TwoPlayerTetris.class, window, "player2Board", second);
            Method input = TwoPlayerTetris.class.getDeclaredMethod("handleKeyInput", KeyEvent.class);
            input.setAccessible(true);
            Keys keys = key -> input.invoke(window, event(first, key));
            verifyDirections(first, keys, KeyEvent.VK_A, KeyEvent.VK_D, "2인 1P");
            verifyDirections(second, keys, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, "2인 2P");
            verifyBlocked(first, keys, KeyEvent.VK_A, KeyEvent.VK_D, "2인 1P");
            verifyBlocked(second, keys, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, "2인 2P");
        } finally {
            first.stop();
            second.stop();
        }
    }

    private static Board createBoard() {
        Board board = new Board(new JLabel(), new SidePanel(), false);
        board.getEngine().start();
        return board;
    }

    private static void verifyDirections(Board board, Keys keys, int left, int right, String mode)
            throws Exception {
        for (int score : new int[] { 0, 199, 200, 250 }) {
            prepare(board, score);
            SidePanel panel = (SidePanel) get(Board.class, board, "sidePanel");
            panel.setScore(-1);
            panel.setLevel(2); // 이동 후 공통 화면 갱신이 실행되는지 확인
            int direction = score >= 200 ? 1 : -1;
            int x = board.getEngine().getCurrentX();
            keys.press(left);
            require(board.getEngine().getCurrentX() == x + direction, mode + " " + score + "점 왼쪽 키 방향 오류");
            keys.press(right);
            require(board.getEngine().getCurrentX() == x, mode + " " + score + "점 오른쪽 키 방향 오류");
            require(((Integer) get(SidePanel.class, panel, "score")) == score, mode + " 점수 표시 갱신 누락");
            require(((Integer) get(SidePanel.class, panel, "level")) == (score >= 200 ? 5 : score >= 150 ? 4 : 1),
                    mode + " 레벨 표시 갱신 누락");
        }
    }

    private static void verifyBlocked(Board board, Keys keys, int left, int right, String mode)
            throws Exception {
        prepare(board, 200);
        board.getEngine().pause();
        assertUnmoved(board, keys, left, right, mode + " 일시정지");
        board.getEngine().pause();
        set(GameEngine.class, board.getEngine(), "isStarted", false);
        assertUnmoved(board, keys, left, right, mode + " 게임 종료");
        set(GameEngine.class, board.getEngine(), "isStarted", true);
        Shape piece = (Shape) get(GameEngine.class, board.getEngine(), "curPiece");
        piece.setShape(Tetrominoes.NoShape);
        assertUnmoved(board, keys, left, right, mode + " 블록 없음");
        piece.setShape(Tetrominoes.SquareShape);
        board.stop();
        assertUnmoved(board, keys, left, right, mode + " 메뉴 복귀 후 정지");
        require(!((Timer) get(Board.class, board, "timer")).isRunning(), mode + " 정지 후 타이머 재시작");
    }

    private static void assertUnmoved(Board board, Keys keys, int left, int right, String message)
            throws Exception {
        int x = board.getEngine().getCurrentX();
        keys.press(left);
        require(board.getEngine().getCurrentX() == x, message + " 중 왼쪽 이동");
        keys.press(right);
        require(board.getEngine().getCurrentX() == x, message + " 중 오른쪽 이동");
    }

    private static void prepare(Board board, int score) throws Exception {
        GameEngine engine = board.getEngine();
        ((Shape) get(GameEngine.class, engine, "curPiece")).setShape(Tetrominoes.SquareShape);
        set(GameEngine.class, engine, "curX", 4);
        set(GameEngine.class, engine, "curY", 10);
        set(GameEngine.class, engine, "score", score);
        Method update = Board.class.getDeclaredMethod("updateView");
        update.setAccessible(true);
        update.invoke(board);
    }

    private static void classicKey(Board board, int key) {
        board.getKeyListeners()[0].keyPressed(event(board, key));
    }

    private static KeyEvent event(Board board, int key) {
        return new KeyEvent(board, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED);
    }

    private static Object get(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private interface Keys {
        void press(int key) throws Exception;
    }
}
