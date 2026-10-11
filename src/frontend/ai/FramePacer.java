package frontend.ai;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

// 응답을 화면에 표시한 뒤 다음 게임 진행을 예약
final class FramePacer implements AutoCloseable {
    private static final int IDLE = 0;
    private static final int SCHEDULED = 1;
    private static final int RUNNING = 2;
    private static final int RENDERING = 3;
    private final ScheduledExecutorService executor;
    private final long intervalNanos;
    private final Callable<Runnable> requestFrame;
    private final Consumer<Throwable> onFailure;
    private ScheduledFuture<?> scheduled;
    private boolean paused = true;
    private boolean closed;
    private int phase = IDLE;
    private long pauseVersion;
    private long scheduleVersion;

    FramePacer(ScheduledExecutorService executor, int intervalMillis,
               Callable<Runnable> requestFrame, Consumer<Throwable> onFailure) {
        this.executor = executor;
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
        this.requestFrame = requestFrame;
        this.onFailure = onFailure;
    }

    synchronized void setPaused(boolean paused) {
        if (closed || this.paused == paused)
            return;
        this.paused = paused;
        pauseVersion++;
        if (paused) {
            if (phase == SCHEDULED) {
                scheduled.cancel(false);
                scheduleVersion++;
                scheduled = null;
                phase = IDLE;
            }
        } else if (phase == IDLE) {
            schedule(intervalNanos);
        }
    }

    private void schedule(long delayNanos) {
        phase = SCHEDULED;
        final long scheduledVersion = ++scheduleVersion;
        scheduled = executor.schedule(() -> request(scheduledVersion), delayNanos, TimeUnit.NANOSECONDS);
    }

    private void request(long scheduledVersion) {
        final long startedAt;
        final long version;
        synchronized (this) {
            if (closed || paused || scheduledVersion != scheduleVersion || phase != SCHEDULED)
                return;
            phase = RUNNING;
            startedAt = System.nanoTime();
            version = pauseVersion;
        }
        try {
            final Runnable render = requestFrame.call();
            synchronized (this) {
                if (closed) {
                    phase = IDLE;
                    return;
                }
                phase = RENDERING;
            }
            SwingUtilities.invokeLater(() -> apply(render, startedAt, version));
        } catch (Exception error) {
            fail(error);
        }
    }

    private void apply(Runnable render, long startedAt, long version) {
        synchronized (this) {
            if (closed) {
                phase = IDLE;
                return;
            }
        }
        try {
            render.run();
            synchronized (this) {
                phase = IDLE;
                if (closed || paused)
                    return;
                long elapsed = System.nanoTime() - startedAt;
                // 응답이 늦었을 때는 밀린 동작을 한꺼번에 실행하지 않음
                long delay = version == pauseVersion && elapsed < intervalNanos
                        ? intervalNanos - elapsed : intervalNanos;
                schedule(delay);
            }
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    private void fail(Throwable error) {
        synchronized (this) {
            if (closed)
                return;
            close();
        }
        onFailure.accept(error);
    }

    @Override
    public synchronized void close() {
        if (closed)
            return;
        closed = true;
        paused = true;
        if (scheduled != null)
            scheduled.cancel(false);
        scheduled = null;
    }
}
