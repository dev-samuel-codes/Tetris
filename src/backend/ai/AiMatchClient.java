package backend.ai;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Python AI 서비스를 실행하고 입력에 따른 보드 상태를 받아옴
// 화면이 멈추지 않도록 실행과 응답 대기는 별도 스레드에서 처리
public final class AiMatchClient implements Closeable {
    private static final int MAX_LINE = 4096;
    private static final int STDERR_LIMIT = 4096;
    private static final Set<String> COMMANDS = new HashSet<String>(Arrays.asList(
            "WAIT", "LEFT", "RIGHT", "ROTATE_LEFT", "ROTATE_RIGHT", "SOFT_DROP", "HARD_DROP"));
    private static final String SETUP_HELP =
            " scripts/setup-ai.sh(macOS/Linux) 또는 scripts/setup-ai.ps1(Windows)을 실행해 주세요.";

    private final Process process;
    private final BufferedWriter writer;
    private final BlockingQueue<Reply> replies = new ArrayBlockingQueue<Reply>(32);
    private final StringBuilder stderr = new StringBuilder();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object requestLock = new Object();
    private final Thread stderrThread;
    private final long startupTimeoutMillis;
    private final long stepTimeoutMillis;
    private boolean started;

    public AiMatchClient() throws IOException {
        this(launchService(), timeoutProperty("tetris.ai.startupTimeoutMillis", 30000),
                timeoutProperty("tetris.ai.stepTimeoutMillis", 5000));
    }

    // 테스트에서는 실제 Python 대신 응답을 흉내 내는 프로세스를 전달
    AiMatchClient(Process process, long startupTimeoutMillis, long stepTimeoutMillis) throws IOException {
        this.process = process;
        this.startupTimeoutMillis = startupTimeoutMillis;
        this.stepTimeoutMillis = stepTimeoutMillis;
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        stderrThread = startStderrReader();
        startStdoutReader();
        try {
            String ready = readReply(startupTimeoutMillis, "AI 서비스 시작");
            if (!"READY\t2".equals(ready)) {
                throw failure("AI 서비스의 시작 응답이 올바르지 않습니다: " + brief(ready), null);
            }
        } catch (IOException failure) {
            close();
            throw failure;
        }
    }

    public MatchFrame start(AiDifficulty difficulty, long seed) throws IOException {
        if (difficulty == null) throw new IllegalArgumentException("AI 난이도가 필요합니다.");
        synchronized (requestLock) {
            MatchFrame frame = exchange("START\t" + difficulty.getId() + "\t" + seed,
                    startupTimeoutMillis, "AI 모델 불러오기");
            started = true;
            return frame;
        }
    }

    public MatchFrame step(String command) throws IOException {
        if (!COMMANDS.contains(command)) throw new IllegalArgumentException("지원하지 않는 AI 대전 입력입니다: " + command);
        synchronized (requestLock) {
            if (!started) throw new IOException("AI 대전을 먼저 시작해 주세요.");
            return exchange("STEP\t" + command, stepTimeoutMillis, "AI 대전 진행");
        }
    }

    private MatchFrame exchange(String request, long timeoutMillis, String operation) throws IOException {
        if (closed.get()) throw new IOException("AI 대전 연결이 종료되었습니다.");
        try {
            writer.write(request);
            writer.newLine();
            writer.flush();
            return parseFrame(readReply(timeoutMillis, operation));
        } catch (IOException failure) {
            String message = operation + "에 실패했습니다. " + failure.getMessage();
            IOException detailed = failure.getMessage().endsWith(SETUP_HELP)
                    ? new IOException(message, failure) : failure(message, failure);
            close();
            throw detailed;
        }
    }

    private String readReply(long timeoutMillis, String operation) throws IOException {
        Reply reply;
        try {
            reply = replies.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(operation + " 대기가 취소되었습니다.", interrupted);
        }
        if (closed.get()) throw new IOException("AI 대전 연결이 종료되었습니다.");
        if (reply == null) throw failure(operation + " 응답 시간이 초과되었습니다.", null);
        if (reply.error != null) throw failure(reply.error.getMessage(), reply.error);
        if (reply.line.startsWith("ERROR\t")) {
            throw failure("AI 서비스 오류: " + brief(reply.line.substring(6)), null);
        }
        return reply.line;
    }

    private void startStdoutReader() {
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                    StringBuilder line = new StringBuilder();
                    int next;
                    while (!closed.get() && (next = reader.read()) != -1) {
                        if (next == '\n') {
                            if (line.length() > 0 && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
                            if (!replies.offer(new Reply(line.toString(), null))) {
                                terminal(new IOException("AI 서비스가 요청 없이 너무 많은 응답을 보냈습니다."));
                                process.destroy();
                                return;
                            }
                            line.setLength(0);
                        } else {
                            if (line.length() >= MAX_LINE) {
                                terminal(new IOException("AI 서비스 응답이 허용된 길이를 초과했습니다."));
                                process.destroy();
                                return;
                            }
                            line.append((char) next);
                        }
                    }
                    if (!closed.get()) {
                        // 필요한 패키지가 없어 종료된 경우 오류 내용도 함께 확인
                        try { stderrThread.join(100); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    }
                    terminal(new IOException("AI 서비스 프로세스가 응답을 보내기 전에 종료되었습니다."));
                } catch (IOException failure) {
                    terminal(new IOException("AI 서비스 출력 연결이 끊어졌습니다.", failure));
                }
            }
        }, "tetris-ai-stdout");
        thread.setDaemon(true);
        thread.start();
    }

    private Thread startStderrReader() {
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                try (Reader reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
                    char[] buffer = new char[512];
                    int count;
                    while ((count = reader.read(buffer)) != -1) {
                        synchronized (stderr) {
                            stderr.append(buffer, 0, count);
                            if (stderr.length() > STDERR_LIMIT) stderr.delete(0, stderr.length() - STDERR_LIMIT);
                        }
                    }
                } catch (IOException ignored) {
                    // 프로세스가 종료되면 출력 연결도 함께 닫힘
                }
            }
        }, "tetris-ai-stderr");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void terminal(IOException error) {
        Reply reply = new Reply(null, error);
        if (!replies.offer(reply)) {
            replies.clear();
            replies.offer(reply);
        }
    }

    private IOException failure(String message, Throwable cause) {
        String detail;
        synchronized (stderr) { detail = stderr.toString().trim(); }
        if (!detail.isEmpty()) message += "\n" + detail;
        message += SETUP_HELP;
        return new IOException(message, cause);
    }

    // 응답을 기다리는 중에도 Python을 종료해 대기 상태를 해제
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        replies.clear();
        terminal(new IOException("AI 대전 연결이 종료되었습니다."));
        process.destroy();
        try {
            if (!process.waitFor(200, TimeUnit.MILLISECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        closeQuietly(process.getOutputStream());
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
    }

    private static void closeQuietly(Closeable stream) {
        try { stream.close(); } catch (IOException ignored) { }
    }

    static MatchFrame parseFrame(String line) throws IOException {
        if (line == null || line.length() > MAX_LINE) throw invalid("응답 길이");
        String[] fields = line.split("\t", -1);
        if (fields.length != 14 || !"FRAME".equals(fields[0])) throw invalid("필드 수 또는 FRAME 표식");
        long elapsed = elapsedTicks(fields[1]);
        boolean ended = flag(fields[2]);
        String winner = fields[3];
        if (!("NONE".equals(winner) || "HUMAN".equals(winner) || "AI".equals(winner) || "DRAW".equals(winner))) {
            throw invalid("승자");
        }
        if (ended == "NONE".equals(winner)) throw invalid("종료 및 승자 상태");
        MatchFrame.BoardState human = board(fields, 4);
        MatchFrame.BoardState ai = board(fields, 9);
        // 한쪽이 끝나도 살아 있는 쪽이 점수를 넘기면 승패가 확정됨
        boolean settled = (human.isEnded() && ai.isEnded())
                || (human.isEnded() && ai.getScore() > human.getScore())
                || (ai.isEnded() && human.getScore() > ai.getScore());
        if (ended != settled) throw invalid("진행 상태");
        if (ended) {
            int comparison = Integer.compare(human.getScore(), ai.getScore());
            String expectedWinner = comparison > 0 ? "HUMAN" : comparison < 0 ? "AI" : "DRAW";
            if (!expectedWinner.equals(winner)) throw invalid("점수 및 승자 상태");
        }
        return new MatchFrame(elapsed, ended, winner, human, ai);
    }

    private static MatchFrame.BoardState board(String[] fields, int index) throws IOException {
        int score = number(fields[index], Integer.MAX_VALUE, "점수");
        int lines = number(fields[index + 1], Integer.MAX_VALUE, "제거 줄 수");
        boolean ended = flag(fields[index + 2]);
        String reason = fields[index + 3];
        if (!("-".equals(reason) || "top_out".equals(reason))) throw invalid("보드 종료 원인");
        if (ended == "-".equals(reason)) throw invalid("보드 종료 상태");
        String digits = fields[index + 4];
        if (digits.length() != MatchFrame.BOARD_WIDTH * MatchFrame.BOARD_HEIGHT) throw invalid("보드 크기");
        int[] board = new int[digits.length()];
        for (int i = 0; i < digits.length(); i++) {
            char digit = digits.charAt(i);
            if (digit < '0' || digit > '7') throw invalid("블록 번호");
            board[i] = digit - '0';
        }
        return new MatchFrame.BoardState(score, lines, ended, reason, board);
    }

    private static boolean flag(String token) throws IOException {
        if ("0".equals(token)) return false;
        if ("1".equals(token)) return true;
        throw invalid("불리언 값");
    }

    private static int number(String token, int maximum, String name) throws IOException {
        if (token.isEmpty() || token.length() > 10) throw invalid(name);
        for (int i = 0; i < token.length(); i++) if (token.charAt(i) < '0' || token.charAt(i) > '9') throw invalid(name);
        try {
            int result = Integer.parseInt(token);
            if (result > maximum) throw invalid(name);
            return result;
        } catch (NumberFormatException failure) {
            throw invalid(name);
        }
    }

    private static long elapsedTicks(String token) throws IOException {
        if (token.isEmpty() || token.length() > 19) throw invalid("경과 시간");
        for (int i = 0; i < token.length(); i++)
            if (token.charAt(i) < '0' || token.charAt(i) > '9') throw invalid("경과 시간");
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException failure) { throw invalid("경과 시간"); }
    }

    private static IOException invalid(String field) {
        return new IOException("AI 대전 응답 형식이 올바르지 않습니다: " + field);
    }

    private static String brief(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    private static long timeoutProperty(String name, long defaultValue) {
        String configured = System.getProperty(name);
        if (configured == null) return defaultValue;
        try {
            long value = Long.parseLong(configured);
            return value > 0 && value <= defaultValue ? value : defaultValue;
        } catch (NumberFormatException ignored) { return defaultValue; }
    }

    private static Process launchService() throws IOException {
        Path root = findProjectRoot();
        String python = System.getProperty("tetris.ai.python");
        if (python == null || python.trim().isEmpty()) python = System.getenv("TETRIS_AI_PYTHON");
        if (python == null || python.trim().isEmpty()) {
            boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
            Path bundled = root.resolve(windows ? ".venv-ai/Scripts/python.exe" : ".venv-ai/bin/python");
            python = Files.isRegularFile(bundled) ? bundled.toString() : "python3";
        }
        try {
            return new ProcessBuilder(python.trim(), "-u", root.resolve("src/backend/ai/match_service.py").toString())
                    .directory(root.toFile()).start();
        } catch (IOException failure) {
            throw new IOException("Python AI 서비스를 실행할 수 없습니다. Python 경로(tetris.ai.python 또는 TETRIS_AI_PYTHON)를 확인해 주세요."
                    + SETUP_HELP, failure);
        }
    }

    static Path findProjectRoot() throws IOException {
        String configured = System.getProperty("tetris.projectRoot");
        if (configured != null && !configured.trim().isEmpty()) {
            Path root = Paths.get(configured).toAbsolutePath().normalize();
            if (hasService(root)) return root;
            throw new IOException("지정한 프로젝트 폴더에 src/backend/ai/match_service.py가 없습니다: " + root);
        }
        Path found = findAncestor(Paths.get("").toAbsolutePath());
        if (found != null) return found;
        try {
            Path location = Paths.get(AiMatchClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            found = findAncestor(location);
            if (found != null) return found;
        } catch (Exception ignored) { }
        throw new IOException("AI 서비스가 있는 프로젝트 폴더를 찾을 수 없습니다. -Dtetris.projectRoot로 저장소 경로를 지정해 주세요.");
    }

    private static Path findAncestor(Path location) {
        for (Path current = location; current != null; current = current.getParent()) if (hasService(current)) return current;
        return null;
    }

    private static boolean hasService(Path root) { return Files.isRegularFile(root.resolve("src/backend/ai/match_service.py")); }

    private static final class Reply {
        final String line;
        final IOException error;
        Reply(String line, IOException error) { this.line = line; this.error = error; }
    }
}
