package online;

import frontend.network.OnlineClient;
import frontend.network.ScoreClient;
import shared.network.OnlineFrame;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 실제 서버 프로세스에 두 소켓을 연결해 대전과 기존 랭킹이 함께 동작하는지 확인
public final class OnlineNetworkTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        Path temporary = Files.createTempDirectory("tetris-online-network-");
        Process server = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                "backend.network.ScoreServer", Integer.toString(port), temporary.resolve("scores").toString())
                .redirectErrorStream(true).redirectOutput(temporary.resolve("server.log").toFile()).start();
        try {
            ScoreClient scores = new ScoreClient("127.0.0.1", port);
            waitForServer(scores, server);
            verifyMatch(port, scores);
            verifyWaitingDisconnect(port);
            verifyRoomCapacity(port, scores);
            verifyBadRequest(port);
            System.out.println("OnlineNetworkTest: " + checks + " checks passed; logs: " + temporary);
        } finally {
            server.destroy();
            if (!server.waitFor(3, java.util.concurrent.TimeUnit.SECONDS))
                server.destroyForcibly();
        }
    }

    private static void verifyMatch(int port, ScoreClient scores) throws Exception {
        try (OnlineClient first = new OnlineClient("127.0.0.1", port);
                OnlineClient second = new OnlineClient("127.0.0.1", port)) {
            OnlineFrame a = first.create("플레이어_1");
            require("WAITING".equals(a.getPhase()) && a.getSeat() == 0, "방 생성 후 대기");
            String code = a.getRoomCode();
            OnlineFrame b = second.join(code, "플레이어_2");
            require(b.getSeat() == 1 && b.getPlayer(0).getNickname().equals("플레이어_1"), "다른 컴퓨터 참가");
            a = first.poll();
            require(a.getPlayer(1).getNickname().equals("플레이어_2"), "방장에게 상대 입장 표시");
            try (OnlineClient extra = new OnlineClient("127.0.0.1", port)) {
                expectFailure(() -> extra.join(code, "세번째"), "세 번째 플레이어 거부");
            }
            try (OnlineClient unknown = new OnlineClient("127.0.0.1", port)) {
                expectFailure(() -> unknown.join("!!!!!!", "친구"), "잘못된 방 코드 거부");
            }
            a = first.ready();
            require("WAITING".equals(a.getPhase()) && a.getPlayer(0).isReady(), "한 명만 준비하면 대기");
            b = second.ready();
            require("COUNTDOWN".equals(b.getPhase()) && b.getCountdownMillis() > 0, "양쪽 준비 후 카운트다운");
            // 서버 시계는 클라이언트 POLL 횟수와 독립적으로 흘러야 함
            Thread.sleep(3100);
            a = first.poll();
            b = second.poll();
            require("PLAYING".equals(a.getPhase()) && "PLAYING".equals(b.getPhase()), "요청 없는 동안에도 경기 시작");
            require(a.getPlayer(0).getNextShape() == a.getPlayer(1).getNextShape(), "공통 다음 블록");
            require(Arrays.equals(a.getPlayer(0).getCells(), a.getPlayer(1).getCells()), "공통 첫 블록");
            int oldX = minX(a.getPlayer(0).getCells());
            a = first.input("LEFT");
            require(minX(a.getPlayer(0).getCells()) == oldX - 1, "서버가 좌측 입력 처리");
            b = second.poll();
            require(minX(b.getPlayer(0).getCells()) == oldX - 1, "상대 화면에 입력 전달");
            require(minX(b.getPlayer(1).getCells()) == oldX, "내 입력으로 상대 보드 이동 금지");
            int beforeY = minY(b.getPlayer(1).getCells());
            Thread.sleep(850);
            b = second.poll();
            require(minY(b.getPlayer(1).getCells()) < beforeY, "통신 요청 없이 서버 중력 진행");
            a = first.input("HARD_DROP");
            require(occupied(a.getPlayer(0).getCells()) >= 8, "즉시 낙하 후 블록 고정 및 새 블록");
            // 긴 대전 접속이 점수 처리 작업 스레드를 차지하지 않는지 확인
            require(scores.submitScore("네트워크검증", 123) == 123, "대전 중 점수 저장 호환");
            require(scores.loadBestScore("네트워크검증") == 123, "대전 중 최고 점수 조회 호환");
            require(scores.loadRanking(0, 5).getTotalPlayers() == 1, "대전 점수는 클래식 랭킹에 미등록");
            first.close();
            long deadline = System.nanoTime() + 3_000_000_000L;
            do {
                b = second.poll();
                if (!b.isFinished()) Thread.sleep(30);
            } while (!b.isFinished() && System.nanoTime() < deadline);
            require(b.isFinished() && b.getWinner() == 1, "상대 접속 종료 시 남은 사람 승리");
            require("DISCONNECT".equals(b.getReason()), "끊김 결과 이유");
            require(second.poll().getWinner() == 1, "종료 결과 중복 처리 방지");
            try (OnlineClient retry = new OnlineClient("127.0.0.1", port)) {
                expectFailure(() -> retry.join(code, "재참가"), "종료한 방에 중도 참가 거부");
            }
        }
    }

    private static void verifyWaitingDisconnect(int port) throws Exception {
        OnlineClient host = new OnlineClient("127.0.0.1", port);
        String code = host.create("대기방장").getRoomCode();
        host.close();
        Thread.sleep(150);
        try (OnlineClient joiner = new OnlineClient("127.0.0.1", port)) {
            expectFailure(() -> joiner.join(code, "친구"), "방장 없는 방 정리");
        }
        try (OnlineClient retry = new OnlineClient("127.0.0.1", port)) {
            require("WAITING".equals(retry.create("다시연결").getPhase()), "새 연결로 방 다시 생성");
        }
    }

    private static void verifyRoomCapacity(int port, ScoreClient scores) throws Exception {
        Thread.sleep(150);
        List<OnlineClient> clients = new ArrayList<OnlineClient>();
        try {
            for (int i = 0; i < 16; i++) {
                OnlineClient client = new OnlineClient("127.0.0.1", port);
                clients.add(client);
                client.create("방장" + i);
            }
            try (OnlineClient overflow = new OnlineClient("127.0.0.1", port)) {
                expectFailure(() -> overflow.create("초과"), "16개 방 제한");
            }
            require(scores.loadBestScore("네트워크검증") == 123, "방 한도 도달 시에도 랭킹 요청 처리");
        } finally {
            for (OnlineClient client : clients) client.close();
        }
        Thread.sleep(150);
        try (OnlineClient fresh = new OnlineClient("127.0.0.1", port)) {
            require("WAITING".equals(fresh.create("정리후").getPhase()), "방 한도 슬롯 회수");
        }
    }

    private static void verifyBadRequest(int port) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            require("TETRIS/1 READY".equals(reader.readLine()), "기존 배너 보존");
            writer.println("PING");
            require("PONG".equals(reader.readLine()), "기존 PING 호환");
        }
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            reader.readLine();
            writer.println(new String(new char[170]).replace('\0', 'A'));
            require("ERROR BAD_REQUEST".equals(reader.readLine()), "첫 요청 길이 제한");
        }
    }

    private static int minX(int[] cells) {
        int result = 10;
        for (int i = 0; i < cells.length; i++) if (cells[i] != 0) result = Math.min(result, i % 10);
        return result;
    }

    private static int minY(int[] cells) {
        for (int i = 0; i < cells.length; i++) if (cells[i] != 0) return i / 10;
        return 22;
    }

    private static int occupied(int[] cells) {
        int result = 0;
        for (int cell : cells) if (cell != 0) result++;
        return result;
    }

    private static void waitForServer(ScoreClient scores, Process server) throws Exception {
        for (int i = 0; i < 50; i++) {
            if (!server.isAlive()) throw new AssertionError("서버 프로세스 종료");
            try { scores.loadBestScore("준비확인"); return; }
            catch (java.io.IOException expected) { Thread.sleep(50); }
        }
        throw new AssertionError("서버 시작 시간 초과");
    }

    private static void expectFailure(Request request, String reason) throws Exception {
        try {
            request.run();
            throw new AssertionError(reason);
        } catch (java.io.IOException | IllegalArgumentException expected) {
            checks++;
        }
    }

    private interface Request { void run() throws Exception; }

    private static void require(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
        checks++;
    }
}
