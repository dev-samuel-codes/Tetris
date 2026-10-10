package shared.network;

import java.io.IOException;

// 네트워크로 받은 문자열이 잘못된 경우 화면에 반영하지 않는지 확인
public final class OnlineFrameTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        int[] cells = new int[220];
        cells[0] = 7;
        cells[219] = 1;
        OnlineFrame.Player player = new OnlineFrame.Player("사무엘", true, 120, 8, true, 3, cells);
        OnlineFrame.Player opponent = new OnlineFrame.Player("친구_2", false, 80, 5, true, 4, new int[220]);
        OnlineFrame source = new OnlineFrame("ABC123", 1, "PLAYING", -1, "NONE", 54321, 0, player, opponent);
        String wire = source.encode();
        OnlineFrame copy = OnlineFrame.parse(wire);
        require(copy.getSeat() == 1 && "ABC123".equals(copy.getRoomCode()), "방 및 좌석 왕복");
        require(copy.getPlayer(0).getNickname().equals("사무엘"), "한글 닉네임 왕복");
        require(copy.getPlayer(0).getScore() == 120 && copy.getPlayer(0).getLines() == 8, "점수 및 줄수");
        require(copy.getPlayer(0).getCells()[0] == 7 && copy.getPlayer(0).getCells()[219] == 1, "보드 좌표 왕복");
        require(copy.getElapsedMillis() == 54321 && copy.getCountdownMillis() == 0, "서버 시간 왕복");
        cells[0] = 0;
        require(player.getCells()[0] == 7, "생성 시 원본 배열 변경 방어");
        int[] returned = player.getCells();
        returned[0] = 0;
        require(player.getCells()[0] == 7, "조회 시 내부 배열 노출 방지");
        OnlineFrame empty = new OnlineFrame("123456", 0, "WAITING", -1, "NONE", 0, 0,
                player, new OnlineFrame.Player(null, false, 0, 0, false, 0, new int[220]));
        require(OnlineFrame.parse(empty.encode()).getPlayer(1).getNickname().isEmpty(), "빈 자리 왕복");
        OnlineFrame result = new OnlineFrame("ABC123", 1, "FINISHED", 0, "SCORE", 1000, 0, player, opponent);
        require(OnlineFrame.parse(result.encode()).isFinished(), "종료 결과 왕복");
        for (String phase : new String[] { "WAITING", "COUNTDOWN", "PLAYING", "FINISHED" }) {
            OnlineFrame frame = new OnlineFrame("ABC123", 0, phase, -1, "NONE", 0,
                    phase.equals("COUNTDOWN") ? 3000 : 0, player, opponent);
            require(phase.equals(OnlineFrame.parse(frame.encode()).getPhase()), "경기 단계 왕복");
        }
        reject(null);
        reject(wire + " extra");
        reject(wire.substring(0, wire.length() - 1));
        reject(wire.replace("ONLINE/1", "ONLINE/2"));
        reject(wire.replace("ABC123", "abc123"));
        reject(wire.replace("PLAYING", "PAUSED"));
        reject(wire.replace("NONE", "OTHER"));
        String[] parts = wire.split(" ");
        badToken(parts, 3, "2");
        badToken(parts, 5, "2");
        badToken(parts, 5, "0"); // 진행 중 승자가 있을 수 없음
        badToken(parts, 7, "-1");
        badToken(parts, 7, "9999999999999999999");
        badToken(parts, 8, "3001");
        badToken(parts, 9, "/w"); // UTF-8 및 URL-safe 토큰 검증
        badToken(parts, 9, "QQ==");
        badToken(parts, 10, "2");
        badToken(parts, 11, "2147483648");
        badToken(parts, 12, "1.0");
        badToken(parts, 14, "8");
        badToken(parts, 15, "8" + parts[15].substring(1));
        badToken(parts, 22, parts[22] + "0");
        reject(new String(new char[OnlineFrame.MAX_LINE_LENGTH + 1]));
        System.out.println("OnlineFrameTest: " + checks + " checks passed");
    }

    private static void badToken(String[] parts, int index, String value) throws Exception {
        String[] changed = parts.clone();
        changed[index] = value;
        reject(String.join(" ", changed));
    }

    private static void reject(String line) throws Exception {
        try {
            OnlineFrame.parse(line);
            throw new AssertionError("잘못된 프레임 허용");
        } catch (IOException expected) {
            checks++;
        }
    }

    private static void require(boolean ok, String reason) {
        if (!ok)
            throw new AssertionError(reason);
        checks++;
    }
}
