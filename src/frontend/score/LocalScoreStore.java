package frontend.score;

import frontend.network.ScoreClient;
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
        if (nickname == null || nickname.isEmpty())
            return 0;
        return number("best." + ScoreClient.encodeNickname(nickname));
    }

    public synchronized void cacheBestScore(String nickname, int score) throws IOException {
        String key = "best." + ScoreClient.encodeNickname(nickname);
        if (score <= number(key))
            return;
        Properties updated = copy();
        updated.setProperty(key, String.valueOf(score));
        persist(updated);
    }

    // 먼저 디스크에 기록하여 업로드 중 앱을 닫아도 다음 실행에서 재전송 가능
    public synchronized void queueScore(String nickname, int score) throws IOException {
        if (score < 0)
            throw new IllegalArgumentException("점수는 0 이상이어야 합니다.");
        String token = ScoreClient.encodeNickname(nickname);
        Properties updated = copy();
        updated.setProperty("best." + token, String.valueOf(Math.max(score, number("best." + token))));
        updated.setProperty("pending." + token, String.valueOf(Math.max(score, number("pending." + token))));
        persist(updated);
    }

    public synchronized Map<String, Integer> pendingScores() throws IOException {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith("pending."))
                result.put(ScoreClient.decodeNickname(key.substring(8)), number(key));
        }
        return result;
    }

    // 서버의 저장 확인을 받은 점수만 대기 목록에서 제거
    public synchronized void acknowledge(String nickname, int submittedScore, int serverBest) throws IOException {
        String token = ScoreClient.encodeNickname(nickname);
        Properties updated = copy();
        updated.setProperty("best." + token, String.valueOf(Math.max(serverBest, number("best." + token))));
        if (number("pending." + token) <= submittedScore)
            updated.remove("pending." + token);
        persist(updated);
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
                    ScoreClient.decodeNickname(key.substring(key.indexOf('.') + 1));
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
