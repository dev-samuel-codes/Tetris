package backend.score;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 닉네임마다 최고 점수 한 개만 저장하고 서버를 다시 켜도 이어서 읽음
public final class ScoreRepository implements AutoCloseable {
    public static final int MAX_PLAYERS = 10000;
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_ROW_LENGTH = 100;
    private final Path directory;
    private final Path scoresFile;
    private final FileChannel lockChannel;
    private final FileLock directoryLock;
    private Map<String, Integer> scores = new HashMap<String, Integer>();
    private boolean closed;

    public ScoreRepository(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        this.scoresFile = this.directory.resolve("scores.tsv");
        FileChannel channel = FileChannel.open(this.directory.resolve("scores.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = null;
        try {
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                throw new IOException("같은 저장 폴더를 쓰는 서버가 이미 실행 중입니다.", e);
            }
            if (lock == null)
                throw new IOException("같은 저장 폴더를 쓰는 서버가 이미 실행 중입니다.");
            load();
        } catch (IOException | RuntimeException e) {
            if (lock != null)
                lock.release();
            channel.close();
            throw e;
        }
        this.lockChannel = channel;
        this.directoryLock = lock;
    }

    // Base64 표현도 한 가지로 제한해야 같은 닉네임이 서로 다른 키로 들어가지 않음
    public static String decodeNicknameToken(String token) {
        if (token == null || token.length() < 2 || token.length() > 86
                || !token.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("닉네임 토큰 형식이 잘못되었습니다.");
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            String nickname = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            validateNickname(nickname);
            if (!encodeNickname(nickname).equals(token))
                throw new IllegalArgumentException("닉네임 토큰은 패딩 없는 표준 표현이어야 합니다.");
            return nickname;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("닉네임이 UTF-8 형식이 아닙니다.", e);
        }
    }

    private static void validateNickname(String nickname) {
        if (nickname == null || !Normalizer.isNormalized(nickname, Normalizer.Form.NFC)
                || nickname.codePointCount(0, nickname.length()) < 1
                || nickname.codePointCount(0, nickname.length()) > 16)
            throw new IllegalArgumentException("닉네임은 NFC 형식의 1~16글자여야 합니다.");
        for (int index = 0; index < nickname.length();) {
            int point = nickname.codePointAt(index);
            if (!Character.isLetterOrDigit(point) && point != '_' && point != '-')
                throw new IllegalArgumentException("닉네임은 문자, 숫자, 밑줄, 하이픈만 사용할 수 있습니다.");
            index += Character.charCount(point);
        }
    }

    private static String encodeNickname(String nickname) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(nickname.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized int best(String nickname) throws IOException {
        ensureOpen();
        validateNickname(nickname);
        Integer value = scores.get(nickname);
        return value == null ? 0 : value;
    }

    // 저장이 끝나기 전에는 메모리 점수나 성공 응답을 바꾸지 않음
    public synchronized int submit(String nickname, int score) throws IOException {
        ensureOpen();
        validateNickname(nickname);
        if (score < 0)
            throw new IllegalArgumentException("점수는 0 이상이어야 합니다.");
        Integer previous = scores.get(nickname);
        if (previous != null && previous >= score)
            return previous;
        if (previous == null && scores.size() >= MAX_PLAYERS)
            throw new CapacityException();
        Map<String, Integer> next = new HashMap<String, Integer>(scores);
        next.put(nickname, score);
        persist(next);
        scores = next;
        return score;
    }

    public synchronized RankingPage ranking(int offset, int limit) throws IOException {
        ensureOpen();
        if (offset < 0 || limit < 1 || limit > 100)
            throw new IllegalArgumentException("랭킹 조회 범위가 잘못되었습니다.");
        List<Map.Entry<String, Integer>> sorted = new ArrayList<Map.Entry<String, Integer>>(scores.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> first, Map.Entry<String, Integer> second) {
                int scoreOrder = Integer.compare(second.getValue(), first.getValue());
                return scoreOrder != 0 ? scoreOrder : first.getKey().compareTo(second.getKey());
            }
        });
        List<RankingRow> rows = new ArrayList<RankingRow>();
        int end = offset >= sorted.size() ? sorted.size()
                : offset + Math.min(limit, sorted.size() - offset);
        for (int index = offset; index < end; index++) {
            Map.Entry<String, Integer> entry = sorted.get(index);
            rows.add(new RankingRow(index + 1, encodeNickname(entry.getKey()), entry.getValue()));
        }
        return new RankingPage(sorted.size(), Collections.unmodifiableList(rows));
    }

    private void load() throws IOException {
        if (!Files.exists(scoresFile))
            return;
        if (Files.size(scoresFile) > MAX_FILE_BYTES)
            throw new IOException("점수 파일이 허용된 크기를 넘었습니다. 원본 파일을 확인해 주세요.");
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                Files.newInputStream(scoresFile), StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            String line;
            while ((line = readStoredRow(reader)) != null) {
                String[] parts = line.split("\t", -1);
                if (parts.length != 2 || !parts[1].matches("[0-9]{1,10}"))
                    throw new IOException("점수 파일의 행 형식이 잘못되었습니다.");
                try {
                    String nickname = decodeNicknameToken(parts[0]);
                    int score = Integer.parseInt(parts[1]);
                    if (score < 0 || scores.containsKey(nickname) || scores.size() >= MAX_PLAYERS)
                        throw new IllegalArgumentException("중복 닉네임 또는 허용 범위를 넘은 점수입니다.");
                    scores.put(nickname, score);
                } catch (IllegalArgumentException e) {
                    throw new IOException("점수 파일에 잘못된 데이터가 있습니다. 원본을 덮어쓰지 않습니다.", e);
                }
            }
        }
    }

    // 파일도 길이를 제한해서 손상된 한 줄 때문에 메모리를 많이 쓰지 않도록 함
    private static String readStoredRow(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = reader.read()) != -1) {
            if (value == '\n')
                return line.toString();
            if (line.length() >= MAX_ROW_LENGTH)
                throw new IOException("점수 파일에 너무 긴 행이 있습니다.");
            line.append((char) value);
        }
        return line.length() == 0 ? null : line.toString();
    }

    private void persist(Map<String, Integer> next) throws IOException {
        Path temporary = Files.createTempFile(directory, "scores-", ".tmp");
        try {
            List<String> names = new ArrayList<String>(next.keySet());
            Collections.sort(names);
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (String name : names) {
                    writer.write(encodeNickname(name));
                    writer.write('\t');
                    writer.write(Integer.toString(next.get(name)));
                    writer.write('\n');
                }
            }
            try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                file.force(true);
            }
            // 원자 교체를 지원하지 않는 파일 시스템에서는 오류로 끝내고 기존 파일을 유지
            Files.move(temporary, scoresFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            // 운영체제에서 디렉터리 fsync를 지원하면 교체된 이름까지 디스크에 반영
            try (FileChannel folder = FileChannel.open(directory, StandardOpenOption.READ)) {
                folder.force(true);
            } catch (IOException | UnsupportedOperationException ignored) {
                // 일부 운영체제에서는 디렉터리 채널을 지원하지 않아 파일 fsync까지만 적용
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void ensureOpen() throws IOException {
        if (closed)
            throw new IOException("점수 저장소가 닫혔습니다.");
    }

    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                directoryLock.release();
            } finally {
                lockChannel.close();
            }
        }
    }

    // 나중에 랭킹 출력 방식이 늘어나면 응답 객체를 공통 클래스로 나눠도 괜찮을 것 같음
    public static final class RankingPage {
        public final int totalPlayers;
        public final List<RankingRow> rows;
        private RankingPage(int totalPlayers, List<RankingRow> rows) {
            this.totalPlayers = totalPlayers;
            this.rows = rows;
        }
    }

    public static final class RankingRow {
        public final int rank;
        public final String nicknameToken;
        public final int score;
        private RankingRow(int rank, String nicknameToken, int score) {
            this.rank = rank;
            this.nicknameToken = nicknameToken;
            this.score = score;
        }
    }

    public static final class CapacityException extends IOException {
        private static final long serialVersionUID = 1L;

        public CapacityException() {
            super("저장 가능한 닉네임 수를 모두 사용했습니다.");
        }
    }
}
