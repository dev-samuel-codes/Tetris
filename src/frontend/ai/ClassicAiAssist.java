package frontend.ai;

import backend.ai.AiMatchClient;
import frontend.Tetrominoes;
import frontend.engine.GameEngine;
import shared.ai.AiAssistPlan;
import shared.ai.AiAssistState;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.swing.SwingWorker;
import javax.swing.Timer;

// 세션 상태와 엔진 변경은 EDT에서, 모델 준비와 추론은 별도 스레드에서 처리합니다.
public final class ClassicAiAssist {
    private static final int DURATION_MILLIS = 5000;
    private final GameEngine engine;
    private final Runnable onChange;
    private final AdvisorFactory factory;
    private final LongSupplier clock;
    private final Timer timer;
    private boolean busy;
    private boolean used;
    private boolean paused;
    private long remaining;
    private long lastAccountedAt;
    private int generation;
    private String message = "";
    private Resource resource;
    private SwingWorker<AiAssistPlan, Void> worker;
    private AiAssistState pendingState;
    private AiAssistPlan pendingPlan;

    public ClassicAiAssist(GameEngine engine, Runnable onChange) {
        this(engine, onChange, () -> {
            final AiMatchClient client = new AiMatchClient();
            return new Advisor() {
                @Override public AiAssistPlan advise(AiAssistState state) throws Exception {
                    return client.assist(state);
                }
                @Override public void close() { client.close(); }
            };
        }, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    // 주입 시계는 단조 증가하는 밀리초입니다.
    ClassicAiAssist(GameEngine engine, Runnable onChange, AdvisorFactory factory, LongSupplier clock) {
        if (engine == null || onChange == null || factory == null || clock == null)
            throw new IllegalArgumentException("AI 도움에 필요한 설정이 없습니다.");
        this.engine = engine;
        this.onChange = onChange;
        this.factory = factory;
        this.clock = clock;
        timer = new Timer(100, event -> tick());
        timer.setCoalesce(true);
    }

    public boolean start() {
        if (busy || used || !engine.isStarted() || engine.isPaused()) return false;
        AiAssistState state = engine.getAiAssistState();
        if (state == null) return false;
        generation++;
        busy = true;
        paused = false;
        remaining = DURATION_MILLIS;
        message = "AI 준비 중";
        resource = new Resource(factory);
        int token = generation;
        request(state);
        timer.start();
        onChange.run();
        // 알림이 게임 종료/화면 전환을 일으켜도 이전 세션을 다시 실행하지 않습니다.
        return token == generation && busy;
    }

    public boolean isBusy() { return busy; }
    public boolean isUsed() { return used; }

    public int getRemainingMillis() {
        if (!busy) return 0;
        long elapsed = used && !paused ? Math.max(0, clock.getAsLong() - lastAccountedAt) : 0;
        return (int) Math.max(0, remaining - elapsed);
    }

    public String getMessage() {
        if (busy && used) {
            return String.format(Locale.ROOT, "AI 도움 · %.1f초", getRemainingMillis() / 1000.0);
        }
        return message;
    }

    // 엔진의 일시정지 상태 변경 직후 호출합니다. 이전 상태로 경과시간을 정산합니다.
    public void pauseChanged() {
        if (!busy) return;
        accountTime();
        paused = engine.isPaused();
        if (used && remaining <= 0) finish("AI 도움 사용 완료");
        else onChange.run();
    }

    public void reset() {
        stop();
        used = false;
        message = "";
    }

    public void stop() {
        generation++;
        busy = false;
        remaining = 0;
        pendingState = null;
        pendingPlan = null;
        timer.stop();
        SwingWorker<AiAssistPlan, Void> previous = worker;
        worker = null;
        if (previous != null) previous.cancel(true);
        Resource previousResource = resource;
        resource = null;
        if (previousResource != null) previousResource.close();
        if (used) message = "AI 도움 사용 완료";
    }

    private void tick() {
        if (!busy) return;
        accountTime();
        paused = engine.isPaused();
        if (used && remaining <= 0) { finish("AI 도움 사용 완료"); return; }
        if (!engine.isStarted()) { finish(used ? "AI 도움 사용 완료" : "AI 도움을 실행할 수 없습니다. 게임이 종료되었습니다."); return; }
        if (paused) return;
        if (pendingPlan != null) {
            AiAssistState state = pendingState;
            AiAssistPlan plan = pendingPlan;
            pendingState = null;
            pendingPlan = null;
            if (!engine.applyAiAssistPlan(state, plan)) {
                finish("AI 도움을 실행할 수 없습니다. 현재 블록 상태 또는 이동 경로를 확인해 주세요.");
                return;
            }
            if (!used) {
                used = true;
                remaining = DURATION_MILLIS;
                lastAccountedAt = clock.getAsLong();
            }
            int token = generation;
            onChange.run();
            if (token != generation || !busy) return;
        }
        if (worker == null && busy) requestNext();
        if (busy && used) onChange.run();
    }

    private void accountTime() {
        long now = clock.getAsLong();
        if (used && !paused) remaining = Math.max(0, remaining - Math.max(0, now - lastAccountedAt));
        lastAccountedAt = now;
    }

    private void requestNext() {
        if (engine.isPaused()) return;
        if (engine.isStarted() && engine.getCurrentShape() == Tetrominoes.NoShape) {
            engine.tick(); // 줄 삭제 뒤 대기 중인 다음 블록만 생성합니다.
            int token = generation;
            onChange.run();
            if (!busy || token != generation) return;
        }
        AiAssistState state = engine.getAiAssistState();
        if (state == null) {
            finish(used ? "AI 도움 사용 완료" : "AI 도움을 실행할 수 없습니다. 현재 블록을 확인해 주세요.");
            return;
        }
        request(state);
    }

    private void request(final AiAssistState state) {
        final int token = generation;
        final Resource currentResource = resource;
        worker = new SwingWorker<AiAssistPlan, Void>() {
            @Override protected AiAssistPlan doInBackground() throws Exception {
                return currentResource.acquire().advise(state);
            }
            @Override protected void done() {
                if (token != generation || !busy) return;
                worker = null;
                // 타이머 통지가 늦더라도 기한 이후 응답과 오류를 세션에 반영하지 않습니다.
                if (used && getRemainingMillis() <= 0) {
                    finish("AI 도움 사용 완료");
                    return;
                }
                try {
                    AiAssistPlan result = get();
                    if (result == null) throw new IllegalStateException("AI 이동 경로가 비어 있습니다.");
                    pendingState = state;
                    pendingPlan = result;
                } catch (CancellationException ignored) {
                    finish("AI 도움을 실행할 수 없습니다. 요청이 취소되었습니다.");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    finish("AI 도움을 실행할 수 없습니다. 요청이 취소되었습니다.");
                } catch (ExecutionException failure) {
                    fail(failure.getCause());
                } catch (RuntimeException failure) {
                    fail(failure);
                }
            }
        };
        worker.execute();
    }

    private void fail(Throwable cause) {
        String detail = cause == null ? null : cause.getMessage();
        finish("AI 도움을 실행할 수 없습니다." + (detail == null || detail.trim().isEmpty() ? " 다시 시도해 주세요." : " " + detail));
    }

    private void finish(String result) {
        stop();
        message = result;
        onChange.run();
    }

    interface Advisor {
        AiAssistPlan advise(AiAssistState state) throws Exception;
        void close();
    }

    interface AdvisorFactory { Advisor create() throws Exception; }

    // 취소가 생성자보다 먼저 끝나도, 나중에 생성된 연결을 반드시 닫습니다.
    private static final class Resource {
        private final AdvisorFactory factory;
        private Advisor advisor;
        private boolean closed;
        Resource(AdvisorFactory factory) { this.factory = factory; }
        Advisor acquire() throws Exception {
            synchronized (this) {
                if (closed) throw new CancellationException();
                if (advisor != null) return advisor;
            }
            Advisor created = factory.create();
            if (created == null) throw new IllegalStateException("AI 연결을 준비할 수 없습니다.");
            synchronized (this) {
                if (!closed) { advisor = created; return created; }
            }
            created.close();
            throw new CancellationException();
        }
        void close() {
            Advisor previous;
            synchronized (this) {
                if (closed) return;
                closed = true;
                previous = advisor;
                advisor = null;
            }
            if (previous != null) previous.close();
        }
    }
}
