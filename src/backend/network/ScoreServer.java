package backend.network;

import backend.score.ScoreRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import shared.score.ScoreMode;

// 개인 서버에서 닉네임별 최고 점수와 전체 랭킹을 관리하는 TCP 서버
public final class ScoreServer {
    private static final Logger LOGGER = Logger.getLogger(ScoreServer.class.getName());
    private static final int DEFAULT_PORT = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_MESSAGE_BYTES = 160;
    private static final String GREETING = "TETRIS/1 READY";
    private static final String BAD_REQUEST = "ERROR BAD_REQUEST";
    private static final String SERVER_FULL = "ERROR SERVER_FULL";
    private static final Pattern UNSIGNED_DECIMAL = Pattern.compile("\\d{1,10}");

    private ScoreServer() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 2)
            throw new IllegalArgumentException("사용법: ScoreServer [포트] [저장 폴더]");
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("포트는 1~65535 사이로 설정해 주세요.");
        String dataDirectory = args.length > 1 ? args[1] : "server-data";
        ThreadPoolExecutor clients = new ThreadPoolExecutor(
                0, 8, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
        try (OnlineMatchService online = new OnlineMatchService();
                ScoreRepository scores = new ScoreRepository(Paths.get(dataDirectory));
                ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("0.0.0.0", port));
            LOGGER.info(() -> "Score server listening on TCP " + port + " (" + dataDirectory + ")");
            while (!server.isClosed()) {
                acceptClient(server.accept(), clients, scores, online);
            }
        } finally {
            clients.shutdownNow();
        }
    }

    private static void acceptClient(Socket client, ThreadPoolExecutor clients,
            ScoreRepository scores, OnlineMatchService online) {
        try {
            clients.execute(() -> handleClient(client, scores, online));
        } catch (RejectedExecutionException e) {
            rejectClient(client);
        }
    }

    private static void rejectClient(Socket client) {
        try (Socket socket = client) {
            respond(socket, GREETING);
            respond(socket, SERVER_FULL);
        } catch (IOException ignored) {
            // 거절 응답을 받기 전에 접속을 닫아도 다른 클라이언트 처리에는 영향 없음
        }
    }

    private static void handleClient(Socket client, ScoreRepository scores, OnlineMatchService online) {
        boolean handedOff = false;
        try {
            respond(client, GREETING);
            String message = readFirstRequest(client);
            if (message == null)
                return;
            // 온라인 지속 소켓은 전용 32스레드 풀이 소유하고 랭킹 풀은 즉시 반환
            handedOff = online.handoff(client, message);
            if (!handedOff)
                respond(client, dispatch(message, scores));
        } catch (IOException ignored) {
            // 연결 종료나 송수신 오류는 해당 접속만 정리
        } finally {
            if (!handedOff)
                closeClient(client);
        }
    }

    private static String readFirstRequest(Socket client) throws IOException {
        try {
            return readMessage(client);
        } catch (IOException e) {
            respond(client, BAD_REQUEST);
            return null;
        }
    }

    private static void closeClient(Socket client) {
        try {
            client.close();
        } catch (IOException ignored) {
            // 연결이 이미 끊겼으면 추가로 회수할 자원이 없습니다.
        }
    }

    private static String dispatch(String message, ScoreRepository scores) {
        String[] parts = message.split(" ", -1);
        try {
            switch (parts[0]) {
                case "PING":
                    return parts.length == 1 ? "PONG" : BAD_REQUEST;
                case "BEST":
                    return best(parts, scores);
                case "SUBMIT":
                    return submit(parts, scores);
                case "RANKING":
                    return ranking(parts, scores);
                default:
                    return BAD_REQUEST;
            }
        } catch (InvalidRequestException e) {
            return e.getMessage();
        } catch (ScoreRepository.CapacityException e) {
            return SERVER_FULL;
        } catch (IOException e) {
            return "ERROR STORAGE";
        }
    }

    private static String best(String[] parts, ScoreRepository scores) throws InvalidRequestException, IOException {
        if (parts.length != 2 && parts.length != 3)
            return BAD_REQUEST;
        ScoreMode mode = parts.length == 3 ? mode(parts[1]) : ScoreMode.CLASSIC;
        return "BEST " + scores.best(mode, nickname(parts[parts.length - 1]));
    }

    private static String submit(String[] parts, ScoreRepository scores) throws InvalidRequestException, IOException {
        // 구버전은 게임 모드를 보내지 않아 아이템 기록이 클래식에 섞일 수 있습니다.
        if (parts.length == 3)
            return "ERROR MODE_REQUIRED";
        if (parts.length != 4)
            return BAD_REQUEST;
        ScoreMode mode = mode(parts[1]);
        String nickname = nickname(parts[2]);
        int score = nonnegativeInteger(parts[3], "ERROR INVALID_SCORE");
        return "OK " + scores.submit(mode, nickname, score);
    }

    private static String ranking(String[] parts, ScoreRepository scores) throws InvalidRequestException, IOException {
        if (parts.length != 3 && parts.length != 4)
            return BAD_REQUEST;
        ScoreMode mode = parts.length == 4 ? mode(parts[1]) : ScoreMode.CLASSIC;
        int offset = nonnegativeInteger(parts[parts.length - 2], BAD_REQUEST);
        int limit = nonnegativeInteger(parts[parts.length - 1], BAD_REQUEST);
        if (limit < 1 || limit > 100)
            return BAD_REQUEST;

        ScoreRepository.RankingPage page = scores.ranking(mode, offset, limit);
        StringBuilder response = new StringBuilder("RANKING ").append(page.totalPlayers)
                .append(' ').append(page.rows.size()).append('\n');
        for (ScoreRepository.RankingRow row : page.rows) {
            response.append("ROW ").append(row.rank).append(' ').append(row.nicknameToken)
                    .append(' ').append(row.score).append('\n');
        }
        return response.append("END").toString();
    }

    private static ScoreMode mode(String token) throws InvalidRequestException {
        try {
            return ScoreMode.valueOf(token);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("ERROR INVALID_MODE", e);
        }
    }

    private static String nickname(String token) throws InvalidRequestException {
        try {
            return ScoreRepository.decodeNicknameToken(token);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("ERROR INVALID_NICKNAME", e);
        }
    }

    private static int nonnegativeInteger(String value, String errorResponse) throws InvalidRequestException {
        if (!UNSIGNED_DECIMAL.matcher(value).matches())
            throw new InvalidRequestException(errorResponse, null);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException(errorResponse, e);
        }
    }

    private static void respond(Socket socket, String response) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write((response + "\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    // 토큰과 명령은 ASCII 범위이며 긴 입력과 조금씩 보내는 접속 모두 제한
    private static String readMessage(Socket socket) throws IOException {
        InputStream input = socket.getInputStream();
        StringBuilder message = new StringBuilder();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_TIMEOUT_MS);
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0)
                throw new IOException("요청 읽기 시간이 초과되었습니다.");
            socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            int value = input.read();
            if (value == -1)
                throw new IOException("줄바꿈 전에 접속이 종료되었습니다.");
            if (value == '\n') {
                if (message.length() > 0 && message.charAt(message.length() - 1) == '\r')
                    message.setLength(message.length() - 1);
                return message.toString();
            }
            if (value > 127 || message.length() >= MAX_MESSAGE_BYTES)
                throw new IOException("메시지 형식이나 길이가 잘못되었습니다.");
            message.append((char) value);
        }
    }

    private static final class InvalidRequestException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidRequestException(String response, Throwable cause) {
            super(response, cause);
        }
    }
}
