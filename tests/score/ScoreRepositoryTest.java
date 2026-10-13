package score;

import backend.score.ScoreRepository;
import shared.score.ScoreMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

// 모드가 다른 기록의 덮어쓰기와 재시작 시 데이터 유실을 임시 파일로 확인
public final class ScoreRepositoryTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        verifyModePersistence();
        verifyFailedSaveIsolation();
        verifyCapacityIsolation();
        verifyCorruptFileAndLock();
        System.out.println("ScoreRepositoryTest: " + checks + " checks passed");
    }

    private static void verifyModePersistence() throws Exception {
        Path directory = Files.createTempDirectory("tetris-score-repository-");
        byte[] original = "YWxpY2U\t120\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("scores.tsv"), original);
        try (ScoreRepository scores = new ScoreRepository(directory)) {
            require(scores.best(ScoreMode.CLASSIC, "alice") == 120, "기존 클래식 파일 읽기");
            require(scores.best(ScoreMode.ITEM, "alice") == 0, "아이템 최고기록은 새로 시작");
            require(scores.submit(ScoreMode.ITEM, "alice", 900) == 900, "동일 닉네임 아이템 저장");
            require(Arrays.equals(original, Files.readAllBytes(directory.resolve("scores.tsv"))), "아이템 저장 후 기존 클래식 파일 유지");
            require(scores.submit(ScoreMode.ITEM, "alice", 10) == 900, "아이템 낮은 점수 덮어쓰기 방지");
            require(scores.submit(ScoreMode.CLASSIC, "alice", 300) == 300, "클래식 별도 최고점수");
            require(scores.submit(ScoreMode.CLASSIC, "bob", 500) == 500, "클래식 추가 닉네임");
            ScoreRepository.RankingPage classic = scores.ranking(ScoreMode.CLASSIC, 0, 10);
            require(classic.totalPlayers == 2 && classic.rows.size() == 2
                    && "Ym9i".equals(classic.rows.get(0).nicknameToken) && classic.rows.get(0).score == 500,
                    "클래식 랭킹은 클래식 기록으로 정렬");
            ScoreRepository.RankingPage item = scores.ranking(ScoreMode.ITEM, 0, 10);
            require(item.totalPlayers == 1 && item.rows.get(0).score == 900
                    && "YWxpY2U".equals(item.rows.get(0).nicknameToken), "아이템 랭킹 별도 조회");
            require(scores.ranking(ScoreMode.CLASSIC, 1, 1).rows.get(0).rank == 2, "모드별 페이지 순위 유지");
            require(scores.ranking(ScoreMode.ITEM, Integer.MAX_VALUE, 1).rows.isEmpty(), "범위 밖 페이지 안전 처리");
            require(scores.best("alice") == 300 && scores.ranking(0, 10).totalPlayers == 2, "기존 Java 조회는 클래식 유지");
            require(scores.submit("alice", 320) == 320, "기존 Java 저장은 클래식 유지");
            expectIllegal(() -> scores.best(null, "alice"), "모드 누락 거부");
            expectIllegal(() -> scores.submit(ScoreMode.ITEM, "alice", -1), "음수 점수 거부");
            expectIllegal(() -> scores.submit(ScoreMode.ITEM, "bad name", 50), "닉네임 검증 유지");
            expectIllegal(() -> scores.ranking(ScoreMode.ITEM, 0, 101), "랭킹 범위 검증 유지");
        }
        try (ScoreRepository restarted = new ScoreRepository(directory)) {
            require(restarted.best(ScoreMode.CLASSIC, "alice") == 320, "재시작 후 클래식 최고기록");
            require(restarted.best(ScoreMode.ITEM, "alice") == 900, "재시작 후 아이템 최고기록");
            require(restarted.ranking(ScoreMode.ITEM, 0, 10).totalPlayers == 1, "재시작 후 아이템 랭킹");
        }
    }

    private static void verifyFailedSaveIsolation() throws Exception {
        Path directory = Files.createTempDirectory("tetris-score-storage-failure-");
        try (ScoreRepository scores = new ScoreRepository(directory)) {
            scores.submit(ScoreMode.CLASSIC, "alice", 50);
            // 대상 파일을 디렉터리로 막아서 실제 원자 교체 실패를 발생시킴
            Files.createDirectory(directory.resolve("scores-item.tsv"));
            expectIo(() -> scores.submit(ScoreMode.ITEM, "alice", 999), "아이템 저장 실패 전달");
            require(scores.best(ScoreMode.ITEM, "alice") == 0, "저장 실패한 점수는 메모리에 미반영");
            require(scores.best(ScoreMode.CLASSIC, "alice") == 50, "다른 모드 메모리 보존");
            require(scores.submit(ScoreMode.CLASSIC, "alice", 60) == 60, "아이템 실패 후 클래식 저장 가능");
        }
    }

    private static void verifyCapacityIsolation() throws Exception {
        Path directory = Files.createTempDirectory("tetris-score-capacity-");
        StringBuilder rows = new StringBuilder();
        for (int index = 0; index < 10000; index++) {
            String token = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(("player" + index).getBytes(StandardCharsets.UTF_8));
            rows.append(token).append("\t1\n");
        }
        Files.write(directory.resolve("scores.tsv"), rows.toString().getBytes(StandardCharsets.UTF_8));
        try (ScoreRepository scores = new ScoreRepository(directory)) {
            try {
                scores.submit(ScoreMode.CLASSIC, "overflow", 10);
                throw new AssertionError("클래식 10000명 제한 누락");
            } catch (ScoreRepository.CapacityException expected) {
                checks++;
            }
            require(scores.submit(ScoreMode.ITEM, "overflow", 20) == 20, "클래식 만원이어도 아이템은 저장 가능");
            require(scores.submit(ScoreMode.CLASSIC, "player0", 2) == 2, "만원 상태에서도 기존 최고점수 갱신");
        }
    }

    private static void verifyCorruptFileAndLock() throws Exception {
        Path directory = Files.createTempDirectory("tetris-score-lock-");
        try (ScoreRepository scores = new ScoreRepository(directory)) {
            expectIo(() -> { try (ScoreRepository duplicate = new ScoreRepository(directory)) { } }, "중복 저장소 락 유지");
            scores.submit(ScoreMode.CLASSIC, "alice", 77);
        }
        byte[] classic = Files.readAllBytes(directory.resolve("scores.tsv"));
        byte[] corrupt = "YWxpY2U\tbad\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("scores-item.tsv"), corrupt);
        expectIo(() -> { try (ScoreRepository invalid = new ScoreRepository(directory)) { } }, "손상된 아이템 파일 거부");
        require(Arrays.equals(classic, Files.readAllBytes(directory.resolve("scores.tsv"))), "아이템 로딩 실패 후 클래식 파일 보존");
        require(Arrays.equals(corrupt, Files.readAllBytes(directory.resolve("scores-item.tsv"))), "손상 파일을 임의로 덮어쓰지 않음");
        Files.delete(directory.resolve("scores-item.tsv"));
        try (ScoreRepository reopened = new ScoreRepository(directory)) {
            require(reopened.best(ScoreMode.CLASSIC, "alice") == 77, "실패한 생성자의 락 해제");
        }
    }

    private static void expectIllegal(Action action, String message) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static void expectIo(Action action, String message) throws Exception {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private interface Action { void run() throws Exception; }
}
