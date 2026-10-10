package online;

import frontend.network.OnlineClient;
import frontend.network.ScoreClient;
import shared.network.OnlineFrame;

// 배포한 서버에 임시 대전 방만 만들고 정리. 실제 점수 데이터는 조회만 수행
public final class OnlineServerSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("사용법: OnlineServerSmokeTest 서버주소 포트");
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        ScoreClient scores = new ScoreClient(host, port);
        int count = scores.loadRanking(0, 1).getTotalPlayers();
        try (OnlineClient first = new OnlineClient(host, port);
                OnlineClient second = new OnlineClient(host, port)) {
            OnlineFrame state = first.create("접속확인1");
            second.join(state.getRoomCode(), "접속확인2");
            first.ready();
            state = second.ready();
            require("COUNTDOWN".equals(state.getPhase()), "준비 후 카운트다운");
            Thread.sleep(3100);
            state = first.poll();
            require("PLAYING".equals(state.getPhase()), "서버 타이머의 경기 시작");
            require(state.getPlayer(0).getNextShape() == state.getPlayer(1).getNextShape(), "동일 블록 순서");
            first.input("LEFT");
            state = second.poll();
            require(state.getPlayer(0).isAlive() && state.getPlayer(1).isAlive(), "양쪽 보드 조회");
            require(scores.loadRanking(0, 1).getTotalPlayers() >= count, "대전 중 랭킹 조회");
            first.leave();
            state = second.poll();
            require(state.isFinished() && state.getWinner() == 1 && "LEFT".equals(state.getReason()), "이탈 후 승패 및 정리");
        }
        System.out.println("OnlineServerSmokeTest: 배포 서버 생성·참가·준비·동일 블록·조작·랭킹·승패 통과");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
