package frontend.ai;

import frontend.Board;
import frontend.SidePanel;
import frontend.SoundManager;
import frontend.Tetrominoes;
import shared.ai.AiAssistState;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

// 실제 100000 모델과 Java 클래식 보드를 연결하며 개인 기록은 사용하지 않습니다.
public final class ClassicAssistIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("tetris.data.dir", Files.createTempDirectory("tetris-classic-assist-").toString());
        System.setProperty("tetris.server.host", "127.0.0.1");
        System.setProperty("tetris.server.port", "1");
        SoundManager.setMuted(true);
        Board board = edt(() -> new Board(new JLabel(), new SidePanel()));
        try {
            edt(() -> {
                board.start();
                require(board.canUseAiAssist(), "클래식 도움 사용 가능");
                board.startAiAssist();
                board.startAiAssist();
                require(board.isAiAssistBusy(), "중복 클릭에도 준비 유지");
                require(!board.isAiAssistUsed(), "모델 준비 중 횟수 미차감");
                return null;
            });
            await(() -> board.isAiAssistUsed() || !board.isAiAssistBusy(), 35000);
            require(edt(board::isAiAssistUsed), "실제 모델 첫 배치 실패: " + edt(board::getAiAssistMessage));
            long activeStart = System.nanoTime();
            edt(() -> {
                AiAssistState before = board.getEngine().getAiAssistState();
                board.moveLeft(); board.moveRight(); board.rotateLeft(); board.hardDrop();
                require(before == null || before.equals(board.getEngine().getAiAssistState()), "AI 동작 중 수동 입력 차단");
                board.setPaused(true, "도움 시간 검증");
                return null;
            });
            String paused = edt(board::getAiAssistButtonText);
            Thread.sleep(300);
            require(paused.equals(edt(board::getAiAssistButtonText)), "일시정지 중 남은 도움 시간 유지");
            edt(() -> { board.setPaused(false, "계속하기"); return null; });
            await(() -> !board.isAiAssistBusy(), 9000);
            long activeMillis = (System.nanoTime() - activeStart) / 1_000_000L - 300;
            int placed = edt(() -> {
                int cells = 0;
                for (int y = 0; y < 22; y++)
                    for (int x = 0; x < 10; x++)
                        if (board.getEngine().shapeAt(x, y) != Tetrominoes.NoShape) cells++;
                require(board.isAiAssistUsed() && !board.canUseAiAssist(), "한 판 두 번째 사용 차단");
                int total = (cells + board.getEngine().getNumLinesRemoved() * 10) / 4;
                require(total > 1, "5초 동안 여러 블록 배치");
                if (board.getEngine().isStarted())
                    require(activeMillis >= 4700 && activeMillis < 7500, "준비 시간을 제외한 약 5초 실행: " + activeMillis);
                board.start();
                require(board.canUseAiAssist() && !board.isAiAssistUsed(), "새 게임에서 사용 횟수 복원");
                return total;
            });
            Board item = edt(() -> new Board(new JLabel(), new SidePanel(), true));
            try {
                edt(() -> {
                    item.start(); item.startAiAssist();
                    require(!item.canUseAiAssist() && !item.isAiAssistBusy(), "아이템 모드 도움 차단");
                    board.setBattleMode(true); board.startAiAssist();
                    require(!board.canUseAiAssist() && !board.isAiAssistBusy(), "2인 보드 도움 차단");
                    return null;
                });
            } finally { edt(() -> { item.stop(); return null; }); }
            System.out.println("ClassicAssistIntegrationTest: 실제 100000 모델 · " + placed
                    + "블록 · 활성 " + activeMillis + "ms · 일시정지 · 한 판 1회 · 모드 제한 통과");
        } finally { edt(() -> { board.stop(); return null; }); }
    }

    private static void await(BooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (edt(condition::getAsBoolean)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("AI 도움 응답 대기 시간 초과");
    }

    private static <T> T edt(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
