package frontend.network;

import shared.network.OnlineFrame;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

// 지속 연결의 동기 API. UI는 별도 작업 스레드에서 호출하고 close는 EDT에서도 즉시 취소합니다.
public final class OnlineClient implements AutoCloseable {
    // 원격 서버의 연결 배너와 왕복 지연을 허용하되 서버의 15초 유휴 제한보다 짧게 유지합니다.
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_RESPONSE = 2048;
    private final String host;
    private final int port;
    private final Object lifecycle = new Object();
    private volatile Socket socket;
    private volatile boolean closed;
    private boolean joined;

    public OnlineClient(String host, int port) {
        if (host == null || host.trim().isEmpty() || port < 1 || port > 65535)
            throw new IllegalArgumentException("서버 주소와 포트를 확인해 주세요.");
        this.host = host.trim();
        this.port = port;
    }

    public synchronized OnlineFrame create(String nickname) throws IOException {
        return start("ONLINE/1 CREATE " + ScoreClient.encodeNickname(nickname));
    }

    public synchronized OnlineFrame join(String code, String nickname) throws IOException {
        if (code == null || !code.matches("[A-Z0-9]{6}"))
            throw new IllegalArgumentException("방 코드는 영문 대문자·숫자 6자리로 입력해 주세요.");
        return start("ONLINE/1 JOIN " + code + " " + ScoreClient.encodeNickname(nickname));
    }

    public synchronized OnlineFrame poll() throws IOException { return state("POLL"); }
    public synchronized OnlineFrame ready() throws IOException { return state("READY"); }

    public synchronized OnlineFrame input(String action) throws IOException {
        if (action == null || !action.matches("LEFT|RIGHT|ROTATE_LEFT|ROTATE_RIGHT|SOFT_DROP|HARD_DROP"))
            throw new IllegalArgumentException("올바르지 않은 게임 입력입니다.");
        return state("INPUT " + action);
    }

    public synchronized void leave() throws IOException {
        try {
            requireJoined();
            if (!"ONLINE/1 BYE".equals(exchange("LEAVE")))
                throw new IOException("방 나가기 응답이 올바르지 않습니다.");
        } finally {
            close();
        }
    }

    private OnlineFrame start(String command) throws IOException {
        if (socket != null || closed)
            throw new IOException("새 연결로 다시 참가해 주세요.");
        Socket connection = new Socket();
        synchronized (lifecycle) {
            if (closed) {
                connection.close();
                throw new IOException("연결이 취소되었습니다.");
            }
            socket = connection; // connect 중에도 close가 이 소켓을 취소할 수 있습니다.
        }
        try {
            connection.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            connection.setTcpNoDelay(true);
            if (!"TETRIS/1 READY".equals(readLine()))
                throw new IOException("테트리스 서버의 응답이 아닙니다.");
            OnlineFrame frame = OnlineFrame.parse(exchange(command));
            joined = true;
            return frame;
        } catch (IOException | RuntimeException error) {
            close();
            throw error;
        }
    }

    private OnlineFrame state(String command) throws IOException {
        requireJoined();
        try {
            return OnlineFrame.parse(exchange(command));
        } catch (IOException error) {
            close();
            throw error;
        }
    }

    private void requireJoined() throws IOException {
        if (!joined || closed)
            throw new IOException("온라인 방에 연결되어 있지 않습니다.");
    }

    private String exchange(String command) throws IOException {
        if (closed)
            throw new IOException("연결이 취소되었습니다.");
        OutputStream output = socket.getOutputStream();
        output.write((command + "\n").getBytes(StandardCharsets.US_ASCII));
        output.flush();
        String response = readLine();
        if (response.startsWith("ERROR ")) {
            String code = response.substring(6);
            if ("UNKNOWN_COMMAND".equals(code) || "BAD_COMMAND".equals(code)
                    || "UNSUPPORTED".equals(code) || "UNKNOWN".equals(code)
                    || (!joined && "BAD_REQUEST".equals(code)))
                throw new IOException("온라인 대전을 지원하도록 서버 업데이트가 필요합니다. (" + code + ")");
            if ("ROOM_NOT_FOUND".equals(code) || "NOT_FOUND".equals(code))
                throw new IOException("방을 찾을 수 없습니다. 방 코드를 확인해 주세요.");
            if ("ROOM_FULL".equals(code) || "FULL".equals(code))
                throw new IOException("이미 두 명이 참가한 방입니다. 다른 방 코드를 입력해 주세요.");
            if ("ROOM_CLOSED".equals(code))
                throw new IOException("종료되었거나 경기가 시작된 방입니다. 새 방을 만들어 주세요.");
            if ("SERVER_FULL".equals(code))
                throw new IOException("서버가 가득 찼습니다. 잠시 후 다시 참가해 주세요.");
            throw new IOException("서버 응답: " + code);
        }
        if (response.startsWith("ONLINE/1"))
            return response;
        throw new IOException("온라인 대전을 지원하도록 서버 업데이트가 필요합니다.");
    }

    // 바이트마다 남은 시간을 적용해 느린 한 글자 응답도 8초 이내에 종료합니다.
    private String readLine() throws IOException {
        final Socket connection = socket;
        InputStream input = connection.getInputStream();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
        StringBuilder line = new StringBuilder();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0)
                throw new SocketTimeoutException("서버 응답 시간이 초과되었습니다.");
            connection.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            int value = input.read();
            if (value == -1)
                throw new IOException("서버와 연결이 끊겼습니다.");
            if (value == '\n') {
                String result = line.toString();
                return result.endsWith("\r") ? result.substring(0, result.length() - 1) : result;
            }
            if (value > 127 || (value < 32 && value != '\r') || line.length() >= MAX_RESPONSE)
                throw new IOException("서버 응답의 형식 또는 길이가 올바르지 않습니다.");
            line.append((char) value);
        }
    }

    @Override
    public void close() {
        final Socket previous;
        synchronized (lifecycle) {
            closed = true;
            previous = socket;
        }
        if (previous != null) {
            try { previous.close(); } catch (IOException ignored) { }
        }
    }
}
