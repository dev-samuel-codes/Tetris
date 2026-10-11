package backend.network;

import backend.score.ScoreRepository;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// 개인 서버에서 닉네임별 최고 점수와 전체 랭킹을 관리하는 TCP 서버
public final class ScoreServer {
    private static final int DEFAULT_PORT = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_MESSAGE_BYTES = 160;

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
                0, 8, 30, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        try (OnlineMatchService online = new OnlineMatchService();
                ScoreRepository scores = new ScoreRepository(Paths.get(dataDirectory));
                ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("0.0.0.0", port));
            System.out.println("Score server listening on TCP " + port + " (" + dataDirectory + ")");
            while (!server.isClosed()) {
                Socket client = server.accept();
                try {
                    clients.execute(() -> handleClient(client, scores, online));
                } catch (RejectedExecutionException e) {
                    rejectClient(client);
                }
            }
        } finally {
            clients.shutdownNow();
        }
    }

    private static void rejectClient(Socket client) {
        try (Socket socket = client) {
            BufferedWriter writer = writer(socket);
            respond(writer, "TETRIS/1 READY");
            respond(writer, "ERROR SERVER_FULL");
        } catch (IOException ignored) {
            // 거절 응답을 받기 전에 접속을 닫아도 다른 클라이언트 처리에는 영향 없음
        }
    }

    private static void handleClient(Socket client, ScoreRepository scores, OnlineMatchService online) {
        boolean handedOff = false;
        try {
            BufferedWriter writer = writer(client);
            respond(writer, "TETRIS/1 READY");
            String message;
            try {
                message = readMessage(client);
            } catch (IOException e) {
                respond(writer, "ERROR BAD_REQUEST");
                return;
            }
            // 온라인 지속 소켓은 전용 32스레드 풀이 소유하고 랭킹 풀은 즉시 반환
            handedOff = online.handoff(client, message);
            if (!handedOff)
                dispatch(message, writer, scores);
        } catch (IOException ignored) {
            // 연결 종료나 송수신 오류는 해당 접속만 정리
        } finally {
            if (!handedOff) {
                try {
                    client.close();
                } catch (IOException ignored) {
                    // 온라인으로 넘겨진 소켓은 OnlineMatchService에서 정리
                }
            }
        }
    }

    private static BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private static void dispatch(String message, BufferedWriter writer, ScoreRepository scores) throws IOException {
        String[] parts = message.split(" ", -1);
        if (parts.length == 1 && "PING".equals(parts[0])) {
            respond(writer, "PONG");
            return;
        }
        try {
            if (parts.length == 2 && "BEST".equals(parts[0])) {
                String nickname = nickname(parts[1]);
                respond(writer, "BEST " + scores.best(nickname));
            } else if (parts.length == 3 && "SUBMIT".equals(parts[0])) {
                String nickname = nickname(parts[1]);
                int score;
                try {
                    score = nonnegativeInteger(parts[2]);
                } catch (IllegalArgumentException e) {
                    respond(writer, "ERROR INVALID_SCORE");
                    return;
                }
                respond(writer, "OK " + scores.submit(nickname, score));
            } else if (parts.length == 3 && "RANKING".equals(parts[0])) {
                int offset;
                int limit;
                try {
                    offset = nonnegativeInteger(parts[1]);
                    limit = nonnegativeInteger(parts[2]);
                    if (limit < 1 || limit > 100)
                        throw new IllegalArgumentException();
                } catch (IllegalArgumentException e) {
                    respond(writer, "ERROR BAD_REQUEST");
                    return;
                }
                ScoreRepository.RankingPage page = scores.ranking(offset, limit);
                writer.write("RANKING " + page.totalPlayers + " " + page.rows.size() + "\n");
                for (ScoreRepository.RankingRow row : page.rows)
                    writer.write("ROW " + row.rank + " " + row.nicknameToken + " " + row.score + "\n");
                respond(writer, "END");
            } else {
                respond(writer, "ERROR BAD_REQUEST");
            }
        } catch (InvalidNicknameException e) {
            respond(writer, "ERROR INVALID_NICKNAME");
        } catch (ScoreRepository.CapacityException e) {
            respond(writer, "ERROR SERVER_FULL");
        } catch (IOException e) {
            respond(writer, "ERROR STORAGE");
        }
    }

    private static String nickname(String token) throws InvalidNicknameException {
        try {
            return ScoreRepository.decodeNicknameToken(token);
        } catch (IllegalArgumentException e) {
            throw new InvalidNicknameException();
        }
    }

    private static int nonnegativeInteger(String value) {
        if (!value.matches("[0-9]{1,10}"))
            throw new IllegalArgumentException();
        return Integer.parseInt(value);
    }

    private static void respond(BufferedWriter writer, String response) throws IOException {
        writer.write(response);
        writer.write('\n');
        writer.flush();
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

    private static final class InvalidNicknameException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
