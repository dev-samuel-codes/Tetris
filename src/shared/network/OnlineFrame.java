package shared.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;

// 서버가 계산한 두 보드를 한 묶음으로 전달. 화면에서는 받은 상태만 그림
// 나중에 관전이나 재접속을 추가하면 프로토콜 버전별 코덱을 나눠도 괜찮을 것 같음
public final class OnlineFrame {
    public static final int WIDTH = 10;
    public static final int HEIGHT = 22;
    public static final int MAX_LINE_LENGTH = 2048;
    private final String roomCode;
    private final int seat;
    private final String phase;
    private final int winner;
    private final String reason;
    private final long elapsedMillis;
    private final long countdownMillis;
    private final Player[] players;

    public OnlineFrame(String roomCode, int seat, String phase, int winner, String reason,
            long elapsedMillis, long countdownMillis, Player first, Player second) {
        if (roomCode == null || !roomCode.matches("[A-Z0-9]{6}") || seat < 0 || seat > 1
                || !oneOf(phase, "WAITING", "COUNTDOWN", "PLAYING", "FINISHED")
                || winner < -1 || winner > 1
                || !oneOf(reason, "NONE", "SCORE", "DISCONNECT", "LEFT", "EXPIRED")
                || elapsedMillis < 0 || countdownMillis < 0 || countdownMillis > 3000
                || first == null || second == null)
            throw new IllegalArgumentException("온라인 경기 상태가 올바르지 않습니다.");
        if (!"FINISHED".equals(phase) && (winner != -1 || !"NONE".equals(reason)))
            throw new IllegalArgumentException("진행 중인 경기에는 승패 결과가 없습니다.");
        this.roomCode = roomCode;
        this.seat = seat;
        this.phase = phase;
        this.winner = winner;
        this.reason = reason;
        this.elapsedMillis = elapsedMillis;
        this.countdownMillis = countdownMillis;
        this.players = new Player[] { first, second };
    }

    public String getRoomCode() { return roomCode; }
    public int getSeat() { return seat; }
    public String getPhase() { return phase; }
    public int getWinner() { return winner; }
    public String getReason() { return reason; }
    public long getElapsedMillis() { return elapsedMillis; }
    public long getCountdownMillis() { return countdownMillis; }
    public Player getPlayer(int index) { return players[index]; }
    public boolean isFinished() { return "FINISHED".equals(phase); }

    public String encode() {
        StringBuilder line = new StringBuilder("ONLINE/1 STATE ");
        line.append(roomCode).append(' ').append(seat).append(' ').append(phase).append(' ')
                .append(winner).append(' ').append(reason).append(' ').append(elapsedMillis)
                .append(' ').append(countdownMillis);
        for (Player player : players) {
            line.append(' ').append(player.nickname.isEmpty() ? "-" : encodeNickname(player.nickname))
                    .append(' ').append(player.ready ? 1 : 0).append(' ').append(player.score)
                    .append(' ').append(player.lines).append(' ').append(player.alive ? 1 : 0)
                    .append(' ').append(player.nextShape).append(' ');
            for (int cell : player.cells)
                line.append((char) ('0' + cell));
        }
        return line.toString();
    }

    // 길이·토큰·칸 범위를 확인한 뒤에만 화면에 넘겨 잘못된 응답으로 화면이 깨지는 경우 방지
    public static OnlineFrame parse(String line) throws IOException {
        try {
            if (line == null || line.length() > MAX_LINE_LENGTH)
                throw new IllegalArgumentException();
            String[] parts = line.split(" ", -1);
            if (parts.length != 23 || !"ONLINE/1".equals(parts[0]) || !"STATE".equals(parts[1]))
                throw new IllegalArgumentException();
            return new OnlineFrame(parts[2], integer(parts[3]), parts[4],
                    "-1".equals(parts[5]) ? -1 : integer(parts[5]), parts[6],
                    number(parts[7]), number(parts[8]), player(parts, 9), player(parts, 16));
        } catch (IllegalArgumentException e) {
            throw new IOException("온라인 경기 응답 형식이 올바르지 않습니다.", e);
        }
    }

    private static Player player(String[] parts, int offset) throws IOException {
        String nickname = "-".equals(parts[offset]) ? "" : decodeNickname(parts[offset]);
        String board = parts[offset + 6];
        if (board.length() != WIDTH * HEIGHT)
            throw new IllegalArgumentException();
        int[] cells = new int[board.length()];
        for (int i = 0; i < cells.length; i++)
            cells[i] = board.charAt(i) - '0';
        return new Player(nickname, bit(parts[offset + 1]), integer(parts[offset + 2]),
                integer(parts[offset + 3]), bit(parts[offset + 4]), integer(parts[offset + 5]), cells);
    }

    private static boolean bit(String value) {
        if (!"0".equals(value) && !"1".equals(value))
            throw new IllegalArgumentException();
        return "1".equals(value);
    }

    private static long number(String value) {
        if (!value.matches("[0-9]{1,19}"))
            throw new IllegalArgumentException();
        return Long.parseLong(value);
    }

    private static int integer(String value) {
        long result = number(value);
        if (result > Integer.MAX_VALUE)
            throw new IllegalArgumentException();
        return (int) result;
    }

    private static boolean oneOf(String value, String... choices) {
        for (String choice : choices)
            if (choice.equals(value))
                return true;
        return false;
    }

    private static String encodeNickname(String nickname) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nickname.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeNickname(String token) throws IOException {
        String name = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(token))).toString();
        checkNickname(name);
        if (name.isEmpty() || !encodeNickname(name).equals(token))
            throw new IllegalArgumentException();
        return name;
    }

    private static void checkNickname(String name) {
        if (!Normalizer.isNormalized(name, Normalizer.Form.NFC)
                || name.codePointCount(0, name.length()) > 16)
            throw new IllegalArgumentException("닉네임이 올바르지 않습니다.");
        for (int i = 0; i < name.length();) {
            int point = name.codePointAt(i);
            if (!Character.isLetterOrDigit(point) && point != '_' && point != '-')
                throw new IllegalArgumentException("닉네임이 올바르지 않습니다.");
            i += Character.charCount(point);
        }
    }

    public static final class Player {
        private final String nickname;
        private final boolean ready;
        private final int score;
        private final int lines;
        private final boolean alive;
        private final int nextShape;
        private final int[] cells;

        public Player(String nickname, boolean ready, int score, int lines, boolean alive,
                int nextShape, int[] cells) {
            this.nickname = nickname == null ? "" : nickname;
            checkNickname(this.nickname);
            if (score < 0 || lines < 0 || nextShape < 0 || nextShape > 7
                    || cells == null || cells.length != WIDTH * HEIGHT)
                throw new IllegalArgumentException("플레이어 상태가 올바르지 않습니다.");
            for (int cell : cells)
                if (cell < 0 || cell > 7)
                    throw new IllegalArgumentException("보드의 블록 값이 올바르지 않습니다.");
            this.ready = ready;
            this.score = score;
            this.lines = lines;
            this.alive = alive;
            this.nextShape = nextShape;
            this.cells = cells.clone();
        }

        public String getNickname() { return nickname; }
        public boolean isReady() { return ready; }
        public int getScore() { return score; }
        public int getLines() { return lines; }
        public boolean isAlive() { return alive; }
        public int getNextShape() { return nextShape; }
        public int[] getCells() { return cells.clone(); }
    }
}
