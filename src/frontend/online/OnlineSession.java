package frontend.online;

import frontend.network.OnlineClient;
import shared.network.OnlineFrame;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

// 한 요청과 한 EDT 반영만 진행합니다. 늦어진 응답 뒤에도 요청이 쌓이지 않습니다.
final class OnlineSession implements AutoCloseable {
    final OnlineClient client;
    final InputMailbox commands = new InputMailbox();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "tetris-online-session");
        thread.setDaemon(true);
        return thread;
    });
    private final Consumer<OnlineFrame> frames;
    private final Consumer<Throwable> failures;
    private final AtomicBoolean readyRequested = new AtomicBoolean();
    private volatile boolean cancelled;
    private volatile boolean playing;

    OnlineSession(OnlineClient client, Consumer<OnlineFrame> frames, Consumer<Throwable> failures) {
        this.client = client;
        this.frames = frames;
        this.failures = failures;
    }

    void start(String nickname, String roomCode) {
        executor.execute(() -> request(() -> roomCode == null ? client.create(nickname) : client.join(roomCode, nickname)));
    }

    void ready() { readyRequested.set(true); }

    void input(String command) {
        if (playing && !cancelled)
            commands.offer(command);
    }

    private void tick() {
        request(() -> {
            if (readyRequested.getAndSet(false))
                return client.ready();
            String command = playing ? commands.poll() : null;
            OnlineFrame frame = command == null ? client.poll() : client.input(command);
            if ("HARD_DROP".equals(command))
                commands.clear();
            return frame;
        });
    }

    private void request(Request request) {
        if (cancelled)
            return;
        try {
            OnlineFrame frame = request.run();
            playing = "PLAYING".equals(frame.getPhase()) && frame.getPlayer(frame.getSeat()).isAlive();
            // 경기 시작 전과 게임오버 이후에는 남아 있는 입력을 버립니다.
            if (!playing)
                commands.clear();
            SwingUtilities.invokeLater(() -> {
                if (cancelled)
                    return;
                frames.accept(frame);
                if (frame.isFinished()) {
                    close();
                } else if (!cancelled) {
                    executor.schedule(this::tick, 50, TimeUnit.MILLISECONDS);
                }
            });
        } catch (Throwable failure) {
            if (cancelled)
                return;
            playing = false;
            commands.clear();
            client.close();
            executor.shutdown();
            SwingUtilities.invokeLater(() -> {
                if (!cancelled)
                    failures.accept(failure);
            });
        }
    }

    @Override
    public void close() {
        cancelled = true;
        playing = false;
        commands.clear();
        client.close(); // 동기 요청의 모니터를 기다리지 않고 connect/read를 취소합니다.
        executor.shutdownNow();
    }

    private interface Request { OnlineFrame run() throws Exception; }

    // 회전 후 즉시 낙하의 순서는 유지하되 최대 네 개, 1초 이내 입력만 보관합니다.
    // 방향키 자동 반복 때문에 회전·즉시 낙하가 밀리지 않도록 이동 입력부터 버립니다.
    // 후속 개선에서는 서버 스냅숏과 맞추는 클라이언트 예측으로 원격 입력 지연을 줄일 수 있습니다.
    static final class InputMailbox {
        private static final int CAPACITY = 4;
        private static final long MAX_AGE = TimeUnit.MILLISECONDS.toNanos(1000);
        private final ArrayDeque<Entry> queue = new ArrayDeque<Entry>();
        synchronized void offer(String value) {
            long now = System.nanoTime();
            expire(now);
            Entry last = queue.peekLast();
            if (isMovement(value) && last != null && value.equals(last.command)) {
                queue.removeLast();
                queue.addLast(new Entry(value, now));
                return;
            }
            if (queue.size() == CAPACITY) {
                boolean removed = removeFirstMovement();
                if (!removed) {
                    if (isMovement(value))
                        return;
                    // 모든 입력이 회전·낙하라면 가장 오래된 회전을 버리며 낙하는 보존합니다.
                    Iterator<Entry> entries = queue.iterator();
                    while (entries.hasNext()) {
                        if (!"HARD_DROP".equals(entries.next().command)) {
                            entries.remove();
                            removed = true;
                            break;
                        }
                    }
                    if (!removed)
                        return;
                }
            }
            queue.addLast(new Entry(value, now));
        }
        synchronized String poll() {
            expire(System.nanoTime());
            Entry first = queue.pollFirst();
            return first == null ? null : first.command;
        }
        synchronized void clear() { queue.clear(); }
        private void expire(long now) {
            while (!queue.isEmpty() && now - queue.peekFirst().offeredAt > MAX_AGE)
                queue.removeFirst();
        }
        private boolean removeFirstMovement() {
            Iterator<Entry> entries = queue.iterator();
            while (entries.hasNext()) {
                if (isMovement(entries.next().command)) {
                    entries.remove();
                    return true;
                }
            }
            return false;
        }
        private static boolean isMovement(String command) {
            return "LEFT".equals(command) || "RIGHT".equals(command) || "SOFT_DROP".equals(command);
        }
        private static final class Entry {
            final String command;
            final long offeredAt;
            Entry(String command, long offeredAt) {
                this.command = command;
                this.offeredAt = offeredAt;
            }
        }
    }
}
