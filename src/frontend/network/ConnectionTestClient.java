package frontend.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// 게임 화면을 열기 전에 서버의 주소와 포트가 연결되는지 확인
public final class ConnectionTestClient {

    private ConnectionTestClient() {
    }

    // 나중에 게임 화면에 연결하면 접속과 송수신을 전용 클래스로 나누고 별도 스레드에서 처리하면 좋을 것 같음
    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            if (!"TETRIS/1 READY".equals(reader.readLine()))
                throw new IOException("테트리스 연결 확인 서버의 응답이 아닙니다.");

            writer.println("PING");
            if (!"PONG".equals(reader.readLine()))
                throw new IOException("PING에 대한 응답을 확인하지 못했습니다.");

            System.out.println("연결 성공: " + host + ":" + port + " (PING -> PONG)");
        }
    }
}
