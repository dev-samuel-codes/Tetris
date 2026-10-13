package score;

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
import java.util.concurrent.TimeUnit;

// 실제 TCP 서버를 임시 저장 폴더로 켜서 모드별 점수와 구버전 요청을 확인
public final class ScoreNetworkTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        Path directory = Files.createTempDirectory("tetris-score-network-");
        // 기존 파일에는 클래식 기록만 있으며 아이템 모드를 처음 켜도 그대로 유지
        Files.write(directory.resolve("scores.tsv"), "YWxpY2U\t100\n".getBytes(StandardCharsets.UTF_8));
        Process server = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                "backend.network.ScoreServer", Integer.toString(port), directory.toString())
                .redirectErrorStream(true).redirectOutput(directory.resolve("server.log").toFile()).start();
        try {
            waitForServer(port, server);
            expect(port, "SUBMIT YWxpY2U 9999", "ERROR MODE_REQUIRED");
            expect(port, "BEST YWxpY2U", "BEST 100");
            expect(port, "BEST ITEM YWxpY2U", "BEST 0");
            expect(port, "SUBMIT ITEM YWxpY2U 800", "OK 800");
            expect(port, "SUBMIT CLASSIC YWxpY2U 200", "OK 200");
            expect(port, "SUBMIT ITEM YWxpY2U 10", "OK 800");
            expect(port, "BEST ITEM YWxpY2U", "BEST 800");
            expect(port, "BEST CLASSIC YWxpY2U", "BEST 200");
            expect(port, "BEST YWxpY2U", "BEST 200");
            expect(port, "SUBMIT CLASSIC Ym9i 900", "OK 900");
            expect(port, "RANKING CLASSIC 0 10", "RANKING 2 2", "ROW 1 Ym9i 900", "ROW 2 YWxpY2U 200", "END");
            expect(port, "RANKING ITEM 0 10", "RANKING 1 1", "ROW 1 YWxpY2U 800", "END");
            expect(port, "RANKING 0 10", "RANKING 2 2", "ROW 1 Ym9i 900", "ROW 2 YWxpY2U 200", "END");
            expect(port, "BEST UNKNOWN YWxpY2U", "ERROR INVALID_MODE");
            expect(port, "SUBMIT UNKNOWN YWxpY2U 123", "ERROR INVALID_MODE");
            expect(port, "RANKING UNKNOWN 0 10", "ERROR INVALID_MODE");
            expect(port, "BEST classic YWxpY2U", "ERROR INVALID_MODE");
            expect(port, "SUBMIT ITEM YWxpY2U -1", "ERROR INVALID_SCORE");
            expect(port, "SUBMIT ITEM YWxpY2U 2147483648", "ERROR INVALID_SCORE");
            expect(port, "SUBMIT ITEM !!!! 100", "ERROR INVALID_NICKNAME");
            expect(port, "RANKING ITEM 0 0", "ERROR BAD_REQUEST");
            expect(port, "PING", "PONG");
            // 점수 요청 뒤에도 온라인 전용 소켓 처리에 정상적으로 넘겨야 함
            List<String> online = request(port, "ONLINE/1 CREATE YWxpY2U");
            require(online.size() == 1 && online.get(0).startsWith("ONLINE/1 STATE "), "온라인 방 생성 응답 유지: " + online);
            System.out.println("ScoreNetworkTest: " + checks + " checks passed; data: " + directory);
        } finally {
            server.destroy();
            if (!server.waitFor(3, TimeUnit.SECONDS)) {
                server.destroyForcibly();
                server.waitFor(3, TimeUnit.SECONDS);
            }
        }
    }

    private static void waitForServer(int port, Process server) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (!server.isAlive()) throw new AssertionError("점수 서버 시작 실패");
            try {
                if (request(port, "PING").equals(Arrays.asList("PONG"))) return;
            } catch (java.io.IOException ignored) {
                Thread.sleep(30);
            }
        }
        throw new AssertionError("점수 서버 시작 시간 초과");
    }

    private static void expect(int port, String command, String... expected) throws Exception {
        List<String> actual = request(port, command);
        require(actual.equals(Arrays.asList(expected)), command + " 응답: " + actual);
    }

    private static List<String> request(int port, String command) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            if (!"TETRIS/1 READY".equals(reader.readLine())) throw new AssertionError("인사 응답 손상");
            writer.println(command);
            writer.flush();
            List<String> result = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
                if (!command.startsWith("RANKING ") || "END".equals(line) || line.startsWith("ERROR ")) break;
            }
            return result;
        }
    }

    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
