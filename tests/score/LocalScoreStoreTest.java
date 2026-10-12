package score;

import frontend.score.LocalScoreStore;
import shared.score.ScoreMode;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

public final class LocalScoreStoreTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("tetris-score-store-test-");
        try {
            Path file = directory.resolve("scores.properties");
            Files.write(file, ("nickname=User\nbest.VXNlcg=900\npending.VXNlcg=700\n")
                    .getBytes(StandardCharsets.UTF_8));
            LocalScoreStore store = new LocalScoreStore(file);
            equal(900, store.getBestScore(ScoreMode.CLASSIC, "User"), "기존 최고 점수는 클래식에 남아야 합니다.");
            equal(0, store.getBestScore(ScoreMode.ITEM, "User"), "아이템 기록은 새로 시작해야 합니다.");
            equal(700, store.pendingScores(ScoreMode.CLASSIC).get("User"), "기존 대기 점수는 클래식으로 전송해야 합니다.");
            check(store.pendingScores(ScoreMode.ITEM).isEmpty(), "기존 대기 점수가 아이템에 섞이면 안 됩니다.");

            store.queueScore(ScoreMode.ITEM, "User", 1200);
            store.queueScore(ScoreMode.ITEM, "User", 100);
            store.queueScore(ScoreMode.CLASSIC, "Other", 300);
            store = new LocalScoreStore(file);
            equal(1200, store.getBestScore(ScoreMode.ITEM, "User"), "재실행 후 아이템 최고 점수를 유지해야 합니다.");
            equal(900, store.getBestScore("User"), "기존 API는 클래식 최고 점수를 반환해야 합니다.");
            equal(1200, store.pendingScores(ScoreMode.ITEM).get("User"), "낮은 아이템 점수가 대기 최고 점수를 덮어쓰면 안 됩니다.");
            check(store.pendingScores().size() == 2, "기존 API는 클래식 대기 목록만 반환해야 합니다.");

            store.queueScore(ScoreMode.ITEM, "User", 1500);
            store.acknowledge(ScoreMode.ITEM, "User", 1200, 1200);
            equal(1500, store.pendingScores(ScoreMode.ITEM).get("User"), "전송 중 생긴 더 높은 대기 점수는 남아야 합니다.");
            equal(700, store.pendingScores(ScoreMode.CLASSIC).get("User"), "아이템 확인 응답이 클래식 대기 점수를 지우면 안 됩니다.");
            store.acknowledge(ScoreMode.ITEM, "User", 1500, 1700);
            check(store.pendingScores(ScoreMode.ITEM).isEmpty(), "저장 확인된 아이템 점수만 대기 목록에서 제거해야 합니다.");
            equal(1700, store.getBestScore(ScoreMode.ITEM, "User"), "아이템 서버 최고 점수를 저장해야 합니다.");
            store.cacheBestScore(ScoreMode.CLASSIC, "User", 800);
            equal(900, store.getBestScore(ScoreMode.CLASSIC, "User"), "낮은 서버 기록이 로컬 최고 점수를 덮어쓰면 안 됩니다.");

            Properties saved = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                saved.load(reader);
            }
            check("900".equals(saved.getProperty("best.VXNlcg")), "기존 클래식 저장 키를 유지해야 합니다.");
            check("1700".equals(saved.getProperty("best.ITEM.VXNlcg")), "아이템 최고 점수는 별도 키에 저장해야 합니다.");
            check(!saved.containsKey("pending.ITEM.VXNlcg"), "확인된 아이템 대기 키는 디스크에서도 없어져야 합니다.");
            Files.write(file, "best.ITEM.bad.token=1\n".getBytes(StandardCharsets.UTF_8));
            try {
                new LocalScoreStore(file);
                throw new AssertionError("손상된 아이템 닉네임 토큰을 거부해야 합니다.");
            } catch (IOException expected) {
                // 손상된 파일은 새 기록으로 덮어쓰지 않음
            }
            System.out.println("LocalScoreStoreTest passed");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }

    private static void equal(int expected, int actual, String message) {
        if (expected != actual)
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }

    private static void check(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
