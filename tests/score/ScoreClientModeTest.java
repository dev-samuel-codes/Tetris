package score;

import frontend.network.ScoreClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;

public final class ScoreClientModeTest {
    public static void main(String[] args) throws Exception {
        // 모드를 생략한 공개 API도 서버에는 클래식 모드를 명시해야 함
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<String> request = new FutureTask<>(() -> {
                try (Socket socket = server.accept()) {
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    writer.println("TETRIS/1 READY");
                    String command = reader.readLine();
                    writer.println("OK 300");
                    return command;
                }
            });
            Thread responder = new Thread(request, "test-score-client");
            responder.setDaemon(true);
            responder.start();
            ScoreClient client = new ScoreClient("127.0.0.1", server.getLocalPort());
            if (client.submitScore("User", 300) != 300)
                throw new AssertionError("서버가 확인한 최고 점수를 반환해야 합니다.");
            if (!"SUBMIT CLASSIC VXNlcg 300".equals(request.get()))
                throw new AssertionError("점수 전송에는 클래식 모드를 명시해야 합니다.");
        }
        System.out.println("ScoreClientModeTest passed");
    }
}
