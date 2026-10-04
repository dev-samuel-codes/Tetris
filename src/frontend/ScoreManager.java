package frontend;

import frontend.network.ScoreClient;
import frontend.network.ScoreClient.RankingPage;
import frontend.score.LocalScoreStore;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

// 로컬 임시 기록과 서버 점수 동기화를 관리
// 나중에 로그인 기능이 생기면 닉네임 대신 인증된 사용자 ID로 구분해도 괜찮을 것 같음
public final class ScoreManager {
    private static final ScoreManager INSTANCE = new ScoreManager();
    private final LocalScoreStore local;
    private final ScoreClient client;
    private final String initializationError;
    private final ScheduledExecutorService worker;

    private ScoreManager() {
        LocalScoreStore loaded = null;
        ScoreClient connection = null;
        String error = null;
        try {
            loaded = new LocalScoreStore(Paths.get(System.getProperty("tetris.data.dir", ".tetris"), "scores.properties"));
            connection = new ScoreClient(System.getProperty("tetris.server.host", "131.186.39.107"),
                    Integer.parseInt(System.getProperty("tetris.server.port", "5000")));
        } catch (IOException | IllegalArgumentException e) {
            // 손상된 로컬 파일을 새 파일로 덮어쓰지 않고 화면에 오류 전달
            error = e.getMessage();
        }
        local = loaded;
        client = connection;
        initializationError = error;
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "tetris-score-sync");
            thread.setDaemon(true);
            return thread;
        });
        // 서버가 잠시 끊겨도 대기 중인 최고 점수를 주기적으로 다시 전송
        worker.scheduleWithFixedDelay(() -> {
            try {
                uploadPending();
            } catch (IOException e) {
                // 대기 기록은 디스크에 남겨 두고 다음 주기나 메뉴 조회 시 재시도
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public static String getNickname() {
        return INSTANCE.local == null ? "" : INSTANCE.local.getNickname();
    }

    public static void setNickname(String nickname) throws IOException {
        INSTANCE.requireLocal().setNickname(nickname);
    }

    public static int getCachedBestScore(String nickname) {
        try {
            return INSTANCE.local == null ? 0 : INSTANCE.local.getBestScore(nickname);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    public static void loadBestScore(String nickname, Consumer<Result<Integer>> callback) {
        INSTANCE.worker.execute(() -> {
            try {
                INSTANCE.uploadPending();
                int best = INSTANCE.requireClient().loadBestScore(nickname);
                INSTANCE.requireLocal().cacheBestScore(nickname, best);
                deliver(callback, Result.success(best, "서버 기록을 불러왔습니다."));
            } catch (IOException | IllegalArgumentException e) {
                deliver(callback, Result.failure(getCachedBestScore(nickname),
                        "서버 조회 실패 · 이 기기에 보관된 기록입니다.", e));
            }
        });
    }

    public static void loadRanking(int offset, int limit, Consumer<Result<RankingPage>> callback) {
        INSTANCE.worker.execute(() -> {
            try {
                INSTANCE.uploadPending();
                RankingPage page = INSTANCE.requireClient().loadRanking(offset, limit);
                deliver(callback, Result.success(page, "서버 랭킹을 불러왔습니다."));
            } catch (IOException | IllegalArgumentException e) {
                deliver(callback, Result.failure(null, "랭킹을 불러오지 못했습니다. 새로고침해 주세요.", e));
            }
        });
    }

    public static void saveScore(String nickname, int score, Consumer<Result<Integer>> callback) {
        try {
            // 네트워크 전송 전에 저장하므로 업로드 도중 종료해도 기록이 남음
            INSTANCE.requireLocal().queueScore(nickname, score);
        } catch (IOException | IllegalArgumentException e) {
            deliver(callback, Result.failure(score, "점수 임시 저장 실패", e));
            return;
        }
        INSTANCE.worker.execute(() -> {
            try {
                INSTANCE.uploadPending();
                deliver(callback, Result.success(getCachedBestScore(nickname), "서버 저장 완료"));
            } catch (IOException e) {
                deliver(callback, Result.pending(getCachedBestScore(nickname), "임시 저장 · 재연결 대기", e));
            }
        });
    }

    private LocalScoreStore requireLocal() throws IOException {
        if (local == null)
            throw new IOException(initializationError == null ? "로컬 점수 저장소를 열 수 없습니다." : initializationError);
        return local;
    }

    private ScoreClient requireClient() throws IOException {
        if (client == null)
            throw new IOException(initializationError == null ? "서버 설정을 확인해 주세요." : initializationError);
        return client;
    }

    // 최고 점수만 전송하므로 응답이 유실되어 다시 보내도 랭킹이 중복되지 않음
    private void uploadPending() throws IOException {
        for (Map.Entry<String, Integer> entry : requireLocal().pendingScores().entrySet()) {
            int best = requireClient().submitScore(entry.getKey(), entry.getValue());
            if (best < entry.getValue())
                throw new IOException("서버가 전송한 점수를 저장하지 못했습니다.");
            requireLocal().acknowledge(entry.getKey(), entry.getValue(), best);
        }
    }

    // 화면을 바꾸는 콜백은 Swing UI 스레드에서 실행
    private static <T> void deliver(Consumer<Result<T>> callback, Result<T> result) {
        SwingUtilities.invokeLater(() -> callback.accept(result));
    }

    public static final class Result<T> {
        private final T value;
        private final boolean success;
        private final boolean pending;
        private final String message;
        private final String detail;

        private Result(T value, boolean success, boolean pending, String message, String detail) {
            this.value = value;
            this.success = success;
            this.pending = pending;
            this.message = message;
            this.detail = detail;
        }

        private static <T> Result<T> success(T value, String message) {
            return new Result<>(value, true, false, message, message);
        }

        private static <T> Result<T> failure(T value, String message, Exception error) {
            return new Result<>(value, false, false, message, error.getMessage());
        }

        private static <T> Result<T> pending(T value, String message, Exception error) {
            return new Result<>(value, false, true, message, error.getMessage());
        }

        public T getValue() { return value; }
        public boolean isSuccess() { return success; }
        public boolean isPending() { return pending; }
        public String getMessage() { return message; }
        public String getDetail() { return detail; }
    }
}
