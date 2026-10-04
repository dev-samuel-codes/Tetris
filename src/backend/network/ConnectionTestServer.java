package backend.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// PVP를 만들기 전에 외부에서 서버까지 연결되는지 확인하는 서버
public final class ConnectionTestServer {

    // 나중에 서버와 클라이언트가 같이 쓰는 포트와 메시지는 공통 클래스로 나눠도 괜찮을 것 같음
    private static final int DEFAULT_PORT = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_MESSAGE_LENGTH = 16;

    private ConnectionTestServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("포트는 1~65535 사이로 설정해 주세요.");

        // 응답하지 않는 접속이 계속 쌓이지 않도록 동시 처리 수 제한
        ThreadPoolExecutor clients = new ThreadPoolExecutor(
                0, 8, 30, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("0.0.0.0", port));
            System.out.println("Connection test server listening on TCP " + port);

            while (!server.isClosed()) {
                Socket client = server.accept();
                try {
                    clients.execute(() -> handleClient(client));
                } catch (RejectedExecutionException e) {
                    client.close();
                }
            }
        } finally {
            clients.shutdownNow();
        }
    }

    private static void handleClient(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            // 서버에 도착했는지 알 수 있도록 접속 직후 응답
            writer.println("TETRIS/1 READY");
            String message = readMessage(reader);

            // 나중에 방 입장과 게임 입력이 추가되면 메시지 처리 부분을 따로 나눠도 괜찮을 것 같음
            writer.println("PING".equals(message) ? "PONG" : "ERROR EXPECTED_PING");
        } catch (IOException e) {
            // 연결 종료나 응답 시간 초과는 해당 접속만 정리
        }
    }

    // 줄바꿈까지 읽되 너무 긴 메시지는 받지 않음
    private static String readMessage(BufferedReader reader) throws IOException {
        StringBuilder message = new StringBuilder();
        int value;
        while ((value = reader.read()) != -1) {
            if (value == '\n') {
                if (message.length() > 0 && message.charAt(message.length() - 1) == '\r')
                    message.setLength(message.length() - 1);
                return message.toString();
            }
            if (message.length() >= MAX_MESSAGE_LENGTH)
                throw new IOException("메시지가 너무 깁니다.");
            message.append((char) value);
        }
        throw new IOException("메시지를 받기 전에 연결이 종료되었습니다.");
    }
}
