package frontend.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

// 점수 등록과 랭킹 조회 요청을 서버로 전달
// 화면이 멈추지 않도록 ScoreManager의 작업 스레드에서 호출
public final class ScoreClient {
    private static final int TIMEOUT_MS = 3000;
    private static final int MAX_LINE_LENGTH = 256;
    private final String host;
    private final int port;

    public ScoreClient(String host, int port) {
        if (host == null || host.trim().isEmpty() || port < 1 || port > 65535)
            throw new IllegalArgumentException("서버 주소와 포트를 확인해 주세요.");
        this.host = host.trim();
        this.port = port;
    }

    public int submitScore(String nickname, int score) throws IOException {
        if (score < 0)
            throw new IllegalArgumentException("점수는 0 이상이어야 합니다.");
        return exchange("SUBMIT " + encodeNickname(nickname) + " " + score,
                reader -> readValue(reader, "OK"));
    }

    public int loadBestScore(String nickname) throws IOException {
        return exchange("BEST " + encodeNickname(nickname), reader -> readValue(reader, "BEST"));
    }

    public RankingPage loadRanking(int offset, int limit) throws IOException {
        if (offset < 0 || limit < 1 || limit > 100)
            throw new IllegalArgumentException("랭킹 조회 범위를 확인해 주세요.");
        return exchange("RANKING " + offset + " " + limit, reader -> {
            String[] header = readLine(reader).split(" ");
            if (header.length != 3 || !"RANKING".equals(header[0]))
                throw new IOException("랭킹 응답을 확인하지 못했습니다.");
            int total = parseNumber(header[1]);
            int count = parseNumber(header[2]);
            if (count > limit || count > Math.max(0, total - offset))
                throw new IOException("랭킹 응답의 개수가 올바르지 않습니다.");
            List<RankingEntry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String[] row = readLine(reader).split(" ");
                if (row.length != 4 || !"ROW".equals(row[0]))
                    throw new IOException("랭킹 항목이 올바르지 않습니다.");
                int rank = parseNumber(row[1]);
                if (rank != offset + i + 1)
                    throw new IOException("랭킹 순서가 올바르지 않습니다.");
                entries.add(new RankingEntry(rank, decodeNickname(row[2]), parseNumber(row[3])));
            }
            if (!"END".equals(readLine(reader)))
                throw new IOException("랭킹 응답이 끝나지 않았습니다.");
            return new RankingPage(total, entries);
        });
    }

    // 닉네임은 한글 등 문자, 숫자, 밑줄, 하이픈을 사용하며 대소문자는 구분
    public static String normalizeNickname(String nickname) {
        String normalized = Normalizer.normalize(nickname == null ? "" : nickname.trim(), Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 16)
            throw new IllegalArgumentException("닉네임은 문자·숫자·_·-로 1~16자 입력해 주세요.");
        for (int index = 0; index < normalized.length();) {
            int point = normalized.codePointAt(index);
            if (!Character.isLetterOrDigit(point) && point != '_' && point != '-')
                throw new IllegalArgumentException("닉네임은 문자·숫자·_·-로 1~16자 입력해 주세요.");
            index += Character.charCount(point);
        }
        return normalized;
    }

    public static String encodeNickname(String nickname) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                normalizeNickname(nickname).getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeNickname(String token) throws IOException {
        try {
            String nickname = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(token))).toString();
            if (!normalizeNickname(nickname).equals(nickname) || !encodeNickname(nickname).equals(token))
                throw new IllegalArgumentException();
            return nickname;
        } catch (IllegalArgumentException | CharacterCodingException e) {
            throw new IOException("닉네임 응답이 올바르지 않습니다.", e);
        }
    }

    // 요청마다 연결을 정리하므로 메뉴 이동 후에도 사용하지 않는 소켓이 남지 않음
    private <T> T exchange(String command, ReplyReader<T> reply) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            if (!"TETRIS/1 READY".equals(readLine(reader)))
                throw new IOException("테트리스 서버의 응답이 아닙니다.");
            writer.println(command);
            if (writer.checkError())
                throw new IOException("서버에 요청을 보내지 못했습니다.");
            return reply.read(reader);
        }
    }

    private int readValue(BufferedReader reader, String kind) throws IOException {
        String[] values = readLine(reader).split(" ");
        if (values.length != 2 || !kind.equals(values[0]))
            throw new IOException("서버가 점수 요청을 처리하지 못했습니다.");
        return parseNumber(values[1]);
    }

    private int parseNumber(String text) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (value < 0)
                throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IOException("서버 응답의 숫자가 올바르지 않습니다.", e);
        }
    }

    // 예상보다 긴 응답을 계속 메모리에 쌓지 않도록 제한
    private String readLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = reader.read()) != -1) {
            if (value == '\n') {
                String result = line.toString();
                if (result.endsWith("\r"))
                    result = result.substring(0, result.length() - 1);
                if (result.startsWith("ERROR "))
                    throw new IOException("서버 응답: " + result.substring(6));
                return result;
            }
            if (line.length() >= MAX_LINE_LENGTH)
                throw new IOException("서버 응답이 너무 깁니다.");
            line.append((char) value);
        }
        throw new IOException("서버가 응답 전에 연결을 종료했습니다.");
    }

    private interface ReplyReader<T> {
        T read(BufferedReader reader) throws IOException;
    }

    public static final class RankingEntry {
        private final int rank;
        private final String nickname;
        private final int score;

        public RankingEntry(int rank, String nickname, int score) {
            this.rank = rank;
            this.nickname = nickname;
            this.score = score;
        }

        public int getRank() { return rank; }
        public String getNickname() { return nickname; }
        public int getScore() { return score; }
    }

    public static final class RankingPage {
        private final int totalPlayers;
        private final List<RankingEntry> entries;

        public RankingPage(int totalPlayers, List<RankingEntry> entries) {
            this.totalPlayers = totalPlayers;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }

        public int getTotalPlayers() { return totalPlayers; }
        public List<RankingEntry> getEntries() { return entries; }
    }
}
