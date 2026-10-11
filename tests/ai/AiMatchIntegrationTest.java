import backend.ai.AiDifficulty;
import backend.ai.AiMatchClient;
import backend.ai.MatchFrame;
import java.util.Arrays;

// 실제 모델을 불러와 게임 시작부터 종료, 재시작까지 연결 상태를 확인
public final class AiMatchIntegrationTest {
    public static void main(String[] args) throws Exception {
        try (AiMatchClient client = new AiMatchClient()) {
            for (AiDifficulty difficulty : AiDifficulty.values()) {
                MatchFrame frame = client.start(difficulty, 1000003);
                require(frame.getElapsedTicks() == 0, "시작 시간");
                require(Arrays.equals(frame.getHuman().getBoard(), frame.getAi().getBoard()), "동일 시작 블록");
                for (int tick = 1; tick <= 30; tick++) {
                    frame = client.step(tick % 6 == 0 ? "HARD_DROP" : "LEFT");
                    require(frame.getElapsedTicks() == tick, "공통 틱");
                }
                System.out.println(difficulty.getLabel() + " 실제 모델 왕복 통과");
            }
            MatchFrame frame = client.start(AiDifficulty.EASY, 1000003);
            while (!frame.isEnded() && frame.getElapsedTicks() < 10000) frame = client.step("HARD_DROP");
            require(frame.isEnded(), "승패 확정");
            boolean humanEnded = frame.getHuman().isEnded();
            boolean aiEnded = frame.getAi().isEnded();
            require((humanEnded && aiEnded)
                    || (humanEnded && frame.getAi().getScore() > frame.getHuman().getScore())
                    || (aiEnded && frame.getHuman().getScore() > frame.getAi().getScore()), "종료 또는 추월 승리");
            require((humanEnded ? "top_out" : "-").equals(frame.getHuman().getReason())
                    && (aiEnded ? "top_out" : "-").equals(frame.getAi().getReason()), "각 보드 종료 상태 유지");
            int comparison = Integer.compare(frame.getHuman().getScore(), frame.getAi().getScore());
            require((comparison > 0 ? "HUMAN" : comparison < 0 ? "AI" : "DRAW").equals(frame.getWinner()), "점수 비교 승패");
            require(client.step("WAIT").getElapsedTicks() == frame.getElapsedTicks(), "종료 후 시간 고정");
            MatchFrame restarted = client.start(AiDifficulty.EASY, 42);
            require(restarted.getElapsedTicks() == 0 && !restarted.isEnded(), "종료 후 재시작");
        }
        System.out.println("AI match integration passed: all 5 models, completed match, restart and close");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
