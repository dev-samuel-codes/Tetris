package score;

import frontend.ScoreManager;
import frontend.ScoreManager.Result;
import frontend.network.ScoreClient.RankingPage;
import frontend.score.LocalScoreStore;
import shared.score.ScoreMode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;

public final class ScoreManagerModeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("tetris-score-manager-test-");
        try (TestServer server = new TestServer()) {
            Path file = directory.resolve("scores.properties");
            LocalScoreStore initial = new LocalScoreStore(file);
            initial.cacheBestScore(ScoreMode.CLASSIC, "User", 900);
            initial.queueScore(ScoreMode.ITEM, "User", 1200);
            initial.queueScore(ScoreMode.CLASSIC, "Other", 250);
            System.setProperty("tetris.data.dir", directory.toString());
            System.setProperty("tetris.server.host", "127.0.0.1");
            System.setProperty("tetris.server.port", String.valueOf(server.socket.getLocalPort()));

            server.failItem = true;
            Result<Integer> classicBest = await(callback -> ScoreManager.loadBestScore(ScoreMode.CLASSIC, "User", callback));
            check(classicBest.isSuccess() && classicBest.getValue() == 900,
                    "아이템 대기 점수의 오류가 클래식 최고 점수 조회를 막으면 안 됩니다.");
            Result<RankingPage> classicRanking = await(callback -> ScoreManager.loadRanking(0, 10, callback));
            check(classicRanking.isSuccess() && classicRanking.getValue().getEntries().get(0).getScore() == 900,
                    "기존 랭킹 API는 클래식 랭킹을 조회해야 합니다.");

            Result<Integer> itemSave = await(callback -> ScoreManager.saveScore(ScoreMode.ITEM, "User", 1300, callback));
            check(itemSave.isPending() && itemSave.getValue() == 1300,
                    "구서버 오류에도 아이템 점수는 재전송 대기에 남아야 합니다.");
            check(new LocalScoreStore(file).pendingScores(ScoreMode.ITEM).get("User") == 1300,
                    "실패한 아이템 점수가 디스크에 보관되어야 합니다.");
            check(ScoreManager.getCachedBestScore("User") == 900,
                    "아이템 저장이 클래식 최고 점수를 변경하면 안 됩니다.");

            server.failClassic = true;
            Result<Integer> classicSave = await(callback -> ScoreManager.saveScore(ScoreMode.CLASSIC, "Other", 350, callback));
            check(classicSave.isPending(), "클래식 전송 실패는 클래식 대기 기록을 남겨야 합니다.");
            server.failItem = false;
            synchronizeAll();
            check(new LocalScoreStore(file).pendingScores(ScoreMode.ITEM).isEmpty(),
                    "주기적 동기화는 클래식 오류 이후에도 아이템 점수를 전송해야 합니다.");
            Result<Integer> itemBest = await(callback -> ScoreManager.loadBestScore(ScoreMode.ITEM, "User", callback));
            check(itemBest.isSuccess() && itemBest.getValue() == 1300,
                    "클래식 대기 오류와 관계없이 아이템 점수를 재전송하고 조회해야 합니다.");
            Result<RankingPage> itemRanking = await(callback -> ScoreManager.loadRanking(ScoreMode.ITEM, 0, 10, callback));
            check(itemRanking.isSuccess() && itemRanking.getValue().getEntries().get(0).getScore() == 1300,
                    "클래식 대기 오류가 아이템 랭킹 조회를 막으면 안 됩니다.");
            LocalScoreStore afterItem = new LocalScoreStore(file);
            check(afterItem.pendingScores(ScoreMode.ITEM).isEmpty(), "확인된 아이템 점수는 대기 목록에서 없어져야 합니다.");
            check(afterItem.pendingScores(ScoreMode.CLASSIC).get("Other") == 350,
                    "아이템 동기화는 실패한 클래식 대기 점수를 남겨야 합니다.");

            server.failClassic = false;
            Result<Integer> retry = await(callback -> ScoreManager.loadBestScore("User", callback));
            check(retry.isSuccess(), "서버 복구 후 클래식 점수를 재전송해야 합니다.");
            check(new LocalScoreStore(file).pendingScores(ScoreMode.CLASSIC).isEmpty(),
                    "서버가 확인한 클래식 점수는 대기 목록에서 제거해야 합니다.");
            for (String command : server.commands)
                check(command.matches("(SUBMIT|BEST|RANKING) (CLASSIC|ITEM) .*"),
                        "모드 없는 요청으로 구서버에 폴백하면 안 됩니다: " + command);
            check(server.commands.contains("SUBMIT ITEM VXNlcg 1300"), "아이템 대기 점수를 아이템으로 다시 보내야 합니다.");
            System.out.println("ScoreManagerModeTest passed");
        } finally {
            // 테스트가 임시 폴더를 지운 뒤 주기적 재전송을 실행하지 않도록 정리
            Field instance = ScoreManager.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            Field worker = ScoreManager.class.getDeclaredField("worker");
            worker.setAccessible(true);
            ((ScheduledExecutorService) worker.get(instance.get(null))).shutdownNow();
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }

    private static void synchronizeAll() throws Exception {
        Field instance = ScoreManager.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Method synchronize = ScoreManager.class.getDeclaredMethod("uploadPending");
        synchronize.setAccessible(true);
        try {
            synchronize.invoke(instance.get(null));
            throw new AssertionError("주기적 동기화는 실패한 클래식 기록의 오류를 보고해야 합니다.");
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof IOException))
                throw e;
        }
    }

    private static <T> Result<T> await(Consumer<Consumer<Result<T>>> action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Result<T>> result = new AtomicReference<>();
        action.accept(value -> {
            if (SwingUtilities.isEventDispatchThread())
                result.set(value);
            latch.countDown();
        });
        if (!latch.await(5, TimeUnit.SECONDS) || result.get() == null)
            throw new AssertionError("점수 결과를 Swing UI 스레드에서 전달해야 합니다.");
        return result.get();
    }

    private static void check(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }

    // 외부 서버 대신 빈 루프백 포트에서 프로토콜 응답만 제공
    private static final class TestServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0);
        final List<String> commands = new CopyOnWriteArrayList<>();
        final Thread thread;
        volatile boolean failClassic;
        volatile boolean failItem;
        int itemBest;

        TestServer() throws IOException {
            thread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket peer = socket.accept()) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8), true);
                        writer.println("TETRIS/1 READY");
                        String command = reader.readLine();
                        commands.add(command);
                        String[] parts = command.split(" ");
                        boolean item = parts.length > 1 && "ITEM".equals(parts[1]);
                        if ("SUBMIT".equals(parts[0])) {
                            if ((item && failItem) || (!item && failClassic)) {
                                writer.println("ERROR BAD_REQUEST");
                            } else {
                                int score = Integer.parseInt(parts[3]);
                                if (item)
                                    itemBest = Math.max(itemBest, score);
                                writer.println("OK " + (item ? itemBest : score));
                            }
                        } else if ("BEST".equals(parts[0])) {
                            writer.println("BEST " + (item ? itemBest : 900));
                        } else if ("RANKING".equals(parts[0])) {
                            writer.println("RANKING 1 1");
                            writer.println("ROW 1 VXNlcg " + (item ? itemBest : 900));
                            writer.println("END");
                        } else {
                            writer.println("ERROR BAD_REQUEST");
                        }
                    } catch (IOException e) {
                        if (!socket.isClosed())
                            throw new RuntimeException(e);
                    }
                }
            }, "test-score-manager-server");
            thread.setDaemon(true);
            thread.start();
        }

        public void close() throws Exception {
            socket.close();
            thread.join(2000);
        }
    }
}
