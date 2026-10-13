package frontend;

import frontend.engine.GameEngine;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** 실제 Board와 엔진을 사용해 일시정지, 재시작, 대전 종료를 검증합니다. */
public final class GameplayLifecycleTest {
    private GameplayLifecycleTest() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("tetris.data.dir", Files.createTempDirectory("tetris-lifecycle-").toString());
        System.setProperty("tetris.server.host", "127.0.0.1");
        System.setProperty("tetris.server.port", "1");
        SoundManager.setMuted(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                verifyPauseAndRestart();
                verifyWindowBindings();
                verifyFinishedMatch();
                verifyConfiguredGuide();
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        System.out.println("GameplayLifecycleTest: 자동 일시정지·재개, 창 키 바인딩, 대전 종료 차단, 설정 키 안내 통과");
    }

    private static void verifyPauseAndRestart() throws Exception {
        Board board = new Board(new JLabel(), new SidePanel(), true);
        try {
            board.start();
            int y = board.getEngine().getCurrentY();
            board.setPaused(true, "포커스 이탈");
            board.setPaused(true, "포커스 이탈");
            require(board.getEngine().isPaused(), "포커스 이탈을 두 번 받으면 재개됨");
            require(!timer(board, "timer").isRunning() && !timer(board, "clockTimer").isRunning(),
                    "일시정지 중 중력 또는 게임 시간 타이머 실행");
            timer(board, "clockTimer").getActionListeners()[0]
                    .actionPerformed(new ActionEvent(board, 0, "queued clock"));
            require(((Integer) field(Board.class, board, "elapsedSeconds")) == 0, "일시정지 뒤 대기 시계 이벤트로 시간 증가");
            board.actionPerformed(new ActionEvent(board, 0, "queued tick"));
            board.hardDrop();
            require(board.getEngine().getCurrentY() == y, "일시정지 중 대기 tick 또는 하드드롭 실행");
            require(((JLabel) field(Board.class, board, "statusbar")).getText().contains("일시정지"),
                    "일시정지 상태 안내 누락");
            board.setPaused(false, "계속하기");
            require(timer(board, "timer").isRunning() && timer(board, "clockTimer").isRunning(), "명시적 재개 실패");
            board.setPaused(true, "쉬기");
            board.start();
            require(!board.getEngine().isPaused() && board.getEngine().getScore() == 0, "일시정지 상태에서 새 게임 초기화 실패");
            board.stop();
            int x = board.getEngine().getCurrentX();
            y = board.getEngine().getCurrentY();
            board.rotateLeft(); board.rotateRight(); board.hardDrop(); board.softDrop(); board.moveLeft();
            require(board.getEngine().getCurrentX() == x && board.getEngine().getCurrentY() == y,
                    "종료 보드에서 공통 조작 실행");
            require(!timer(board, "timer").isRunning(), "종료 조작으로 타이머 재시작");
        } finally { board.stop(); }
    }

    private static void verifyWindowBindings() throws Exception {
        Board board = new Board(new JLabel(), new SidePanel());
        try {
            board.start();
            Object pauseAction = board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false));
            require(pauseAction != null, "Board 포커스가 없어도 사용 가능한 ESC 바인딩 누락");
            board.getActionMap().get(pauseAction).actionPerformed(new ActionEvent(board, 0, "ESC"));
            require(board.getEngine().isPaused(), "창 ESC 바인딩으로 일시정지 실패");
            board.disableDefaultKeys();
            require(board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false)) == null, "2인 모드에 싱글 키 바인딩 잔존");
        } finally { board.stop(); }
    }

    private static void verifyFinishedMatch() throws Exception {
        Board first = new Board(new JLabel(), new SidePanel(), true);
        Board second = new Board(new JLabel(), new SidePanel(), true);
        first.setBattleMode(true); second.setBattleMode(true);
        first.disableDefaultKeys(); second.disableDefaultKeys();
        first.start(); second.start();
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Object unsafe = field(unsafeType, null, "theUnsafe");
            TwoPlayerTetris match = (TwoPlayerTetris) unsafeType.getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, TwoPlayerTetris.class);
            set(match, "player1Board", first); set(match, "player2Board", second);
            Timer check = new Timer(200, event -> { });
            check.start();
            set(match, "gameCheckTimer", check);
            set(match, "pauseButton", new JButton());
            Method stop = TwoPlayerTetris.class.getDeclaredMethod("stopMatch");
            stop.setAccessible(true); stop.invoke(match);
            require(!check.isRunning() && !timer(first, "timer").isRunning() && !timer(second, "timer").isRunning(),
                    "대전 종료 후 승패 또는 보드 타이머 실행");
            Method input = TwoPlayerTetris.class.getDeclaredMethod("handleKeyInput", KeyEvent.class);
            input.setAccessible(true);
            int x = first.getEngine().getCurrentX();
            int y = first.getEngine().getCurrentY();
            for (int key : new int[] { KeyEvent.VK_A, KeyEvent.VK_W, KeyEvent.VK_SPACE })
                input.invoke(match, new KeyEvent(first, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
            require(first.getEngine().getCurrentX() == x && first.getEngine().getCurrentY() == y,
                    "결과 표시 중 승자 입력 실행");
        } finally { first.stop(); second.stop(); }
    }

    private static void verifyConfiguredGuide() {
        int original = GameSettings.getSoftDropKey();
        try {
            GameSettings.setSoftDropKey(KeyEvent.VK_X);
            SidePanel panel = new SidePanel();
            require(panel.getAccessibleContext().getAccessibleDescription().contains("X: 한 칸 하강"),
                    "변경한 키가 조작 안내에 반영되지 않음");
            SidePanel custom = new SidePanel(new String[][] { { "A D", "1P 이동" } });
            require(custom.getAccessibleContext().getAccessibleDescription().contains("A D: 1P 이동"),
                    "2인 전용 조작 안내 누락");
        } finally { GameSettings.setSoftDropKey(original); }
    }

    private static Timer timer(Board board, String name) throws Exception { return (Timer) field(Board.class, board, name); }
    private static Object field(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(TwoPlayerTetris target, String name, Object value) throws Exception {
        Field field = TwoPlayerTetris.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
