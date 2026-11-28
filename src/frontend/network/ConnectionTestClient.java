package frontend.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// 게임 화면을 열기 전에 서버의 주소와 포트가 연결되는지 확인
public final class ConnectionTestClient {

    private ConnectionTestClient() {
    }

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            if (!"TETRIS/1 READY".equals(reader.readLine()))
                throw new IOException("테트리스 연결 확인 서버의 응답이 아닙니다.");

            OutputStream output = socket.getOutputStream();
            output.write("PING\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            if (!"PONG".equals(reader.readLine()))
                throw new IOException("PING에 대한 응답을 확인하지 못했습니다.");

            printSuccess(host, port);
        }
    }

    // 연결 확인 명령의 성공 결과는 스크립트에서도 읽을 수 있도록 표준 출력으로 보냅니다.
    @SuppressWarnings("java:S106")
    private static void printSuccess(String host, int port) {
        System.out.println("연결 성공: " + host + ":" + port + " (PING -> PONG)");
    }
}
