package frontend.score;

import frontend.network.ScoreClient;
import shared.score.ScoreMode;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

// 닉네임과 로컬 최고 기록, 아직 서버에 보내지 못한 점수를 보관
// 예전 scores.txt는 닉네임 정보가 없으므로 그대로 두고 새 기록부터 동기화
public final class LocalScoreStore {
    private final Path file;
    private Properties values = new Properties();

    public LocalScoreStore(Path file) throws IOException {
        this.file = file.toAbsolutePath();
        if (Files.exists(this.file)) {
            if (Files.size(this.file) > 1024 * 1024)
                throw new IOException("로컬 점수 파일이 너무 큽니다.");
            try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
                values.load(reader);
            } catch (IllegalArgumentException e) {
                throw new IOException("로컬 점수 파일을 읽을 수 없습니다.", e);
            }
            validate();
        }
    }

    public synchronized String getNickname() {
        return values.getProperty("nickname", "");
    }

    public synchronized void setNickname(String nickname) throws IOException {
        Properties updated = copy();
        updated.setProperty("nickname", ScoreClient.normalizeNickname(nickname));
        persist(updated);
    }

    public synchronized int getBestScore(String nickname) {
        return getBestScore(ScoreMode.CLASSIC, nickname);
    }

    public synchronized int getBestScore(ScoreMode mode, String nickname) {
        String prefix = prefix("best", mode);
        if (nickname == null || nickname.isEmpty())
            return 0;
        return number(prefix + ScoreClient.encodeNickname(nickname));
    }

    public synchronized void cacheBestScore(String nickname, int score) throws IOException {
        cacheBestScore(ScoreMode.CLASSIC, nickname, score);
    }

    public synchronized void cacheBestScore(ScoreMode mode, String nickname, int score) throws IOException {
        String key = prefix("best", mode) + ScoreClient.encodeNickname(nickname);
        if (score < 0)
            throw new IllegalArgumentException("점수는 0 이상이어야 합니다.");
        if (score <= number(key))
            return;
        Properties updated = copy();
        updated.setProperty(key, String.valueOf(score));
        persist(updated);
    }

    // 먼저 디스크에 기록하여 업로드 중 앱을 닫아도 다음 실행에서 재전송 가능
    public synchronized void queueScore(String nickname, int score) throws IOException {
        queueScore(ScoreMode.CLASSIC, nickname, score);
    }

    public synchronized void queueScore(ScoreMode mode, String nickname, int score) throws IOException {
        if (score < 0)
            throw new IllegalArgumentException("점수는 0 이상이어야 합니다.");
        String token = ScoreClient.encodeNickname(nickname);
        String bestKey = prefix("best", mode) + token;
        String pendingKey = prefix("pending", mode) + token;
        Properties updated = copy();
        updated.setProperty(bestKey, String.valueOf(Math.max(score, number(bestKey))));
        updated.setProperty(pendingKey, String.valueOf(Math.max(score, number(pendingKey))));
        persist(updated);
    }

    public synchronized Map<String, Integer> pendingScores() throws IOException {
        return pendingScores(ScoreMode.CLASSIC);
    }

    public synchronized Map<String, Integer> pendingScores(ScoreMode mode) throws IOException {
        String prefix = prefix("pending", mode);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String token = key.substring(prefix.length());
                if (token.indexOf('.') == -1)
                    result.put(ScoreClient.decodeNickname(token), number(key));
            }
        }
        return result;
    }

    // 서버의 저장 확인을 받은 점수만 대기 목록에서 제거
    public synchronized void acknowledge(String nickname, int submittedScore, int serverBest) throws IOException {
        acknowledge(ScoreMode.CLASSIC, nickname, submittedScore, serverBest);
    }

    public synchronized void acknowledge(ScoreMode mode, String nickname, int submittedScore, int serverBest) throws IOException {
        if (submittedScore < 0 || serverBest < 0)
            throw new IllegalArgumentException("점수는 0 이상이어야 합니다.");
        String token = ScoreClient.encodeNickname(nickname);
        String bestKey = prefix("best", mode) + token;
        String pendingKey = prefix("pending", mode) + token;
        Properties updated = copy();
        updated.setProperty(bestKey, String.valueOf(Math.max(serverBest, number(bestKey))));
        if (number(pendingKey) <= submittedScore)
            updated.remove(pendingKey);
        persist(updated);
    }

    // 예전 저장 키를 클래식에 그대로 사용하고 아이템 기록만 별도 구분
    private String prefix(String kind, ScoreMode mode) {
        if (mode == null)
            throw new IllegalArgumentException("점수 모드를 확인해 주세요.");
        return kind + (mode == ScoreMode.CLASSIC ? "." : "." + mode.name() + ".");
    }

    private int number(String key) {
        return Integer.parseInt(values.getProperty(key, "0"));
    }

    private Properties copy() {
        Properties updated = new Properties();
        updated.putAll(values);
        return updated;
    }

    private void validate() throws IOException {
        try {
            String nickname = getNickname();
            if (!nickname.isEmpty())
                ScoreClient.normalizeNickname(nickname);
            for (String key : values.stringPropertyNames()) {
                if (key.startsWith("best.") || key.startsWith("pending.")) {
                    String token = key.substring(key.indexOf('.') + 1);
                    if (token.startsWith("ITEM."))
                        token = token.substring(5);
                    ScoreClient.decodeNickname(token);
                    if (number(key) < 0)
                        throw new IllegalArgumentException();
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("로컬 점수 파일의 내용이 올바르지 않습니다.", e);
        }
    }

    // 저장 중 종료되어도 기존 파일이 반쯤 덮어써지지 않도록 임시 파일로 교체
    private void persist(Properties updated) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "scores-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                updated.store(writer, "Tetris nickname and pending best scores");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            values = updated;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
