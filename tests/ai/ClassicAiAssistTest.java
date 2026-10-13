package frontend.ai;

import frontend.Shape;
import frontend.Tetrominoes;
import frontend.engine.GameEngine;
import shared.ai.AiAssistPlan;
import shared.ai.AiAssistState;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

// 느린 외부 추론만 대체하고, 실제 엔진에 배치되는 블록과 생명주기를 검증합니다.
public final class ClassicAiAssistTest {
    public static void main(String[] args) throws Exception {
        testUnavailable();
        testPreparationAndOncePerGame();
        testFirstFailureRetry();
        testStaleFirstPlanRetry();
        testDeadlineAndLateReply();
        testLateCompletionChecksDeadline();
        testPauseFreezesBudget();
        testResetDropsOldReply();
        testRestartIgnoresPreviousWorker();
        testLineClearSpawnsNext();
        testCancelledConstructorClosesResource();
        System.out.println("ClassicAiAssistTest passed: 준비·한 판 1회·실패 재시도·5초·일시정지·늦은 응답·줄 삭제·자원 정리");
    }

    private static void testUnavailable() throws Exception {
        Fixture f = new Fixture(new GateAdvisor(1));
        edt(() -> {
            f.engine.pause();
            check(!f.assist.start(), "일시정지 중 도움 시작 거부");
            f.engine.pause();
            f.engine.setItemMode(true);
            check(!f.assist.start(), "아이템 모드 도움 시작 거부");
            f.engine.setItemMode(false);
            check(f.assist.start(), "클래식 진행 중 도움 시작");
            check(!f.assist.start(), "중복 준비 시작 거부");
            f.assist.stop();
        });
        f.advisor.release.countDown();
    }

    private static void testPreparationAndOncePerGame() throws Exception {
        Fixture f = new Fixture(new GateAdvisor(1));
        edt(() -> check(f.assist.start(), "도움 시작"));
        check(f.advisor.entered.await(2, TimeUnit.SECONDS), "추론 시작");
        edt(() -> {
            f.clock.set(20000);
            check(f.assist.isBusy() && !f.assist.isUsed(), "준비는 사용 횟수 소모 전");
            check(occupied(f.engine) == 0, "추론 동안 보드 유지");
        });
        f.advisor.release.countDown();
        await(() -> f.assist.isUsed());
        edt(() -> {
            check(occupied(f.engine) >= 4, "첫 응답은 실제 블록 고정");
            check(f.assist.getRemainingMillis() == 5000, "준비 시간이 5초에 포함되지 않음");
            f.assist.stop();
            check(f.assist.isUsed() && !f.assist.isBusy(), "중단 후 사용 상태 유지");
            check(!f.assist.start(), "한 판의 두 번째 도움 거부");
            f.assist.reset();
            check(!f.assist.isUsed(), "새 판은 도움 횟수 초기화");
        });
        check(f.callbacks.get() > 0 && !f.nonEdtCallback, "변경 알림은 EDT에서 전달");
    }

    private static void testFirstFailureRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicLong clock = new AtomicLong();
        GameEngine engine = engine();
        ClassicAiAssist[] assist = new ClassicAiAssist[1];
        edt(() -> assist[0] = new ClassicAiAssist(engine, () -> {}, () -> new ClassicAiAssist.Advisor() {
            public AiAssistPlan advise(AiAssistState state) throws Exception {
                if (attempts.getAndIncrement() == 0) throw new IOException("모델 로딩 실패");
                return drop(state);
            }
            public void close() {}
        }, clock::get));
        edt(() -> check(assist[0].start(), "첫 시도 시작"));
        await(() -> !assist[0].isBusy());
        edt(() -> {
            check(!assist[0].isUsed() && occupied(engine) == 0, "첫 실패는 사용 차감·배치 없음");
            check(assist[0].getMessage().contains("모델 로딩 실패"), "실패 원인 표시");
            check(assist[0].start(), "첫 실패 뒤 재시도 허용");
        });
        await(() -> assist[0].isUsed());
        edt(() -> assist[0].stop());
    }

    private static void testDeadlineAndLateReply() throws Exception {
        Fixture f = new Fixture(new GateAdvisor(2));
        edt(() -> f.assist.start());
        await(() -> f.assist.isUsed());
        check(f.advisor.entered.await(2, TimeUnit.SECONDS), "두 번째 추론 시작");
        final int[] before = new int[1];
        edt(() -> { before[0] = occupied(f.engine); f.clock.set(5000); });
        await(() -> !f.assist.isBusy());
        f.advisor.release.countDown();
        Thread.sleep(150);
        edt(() -> {
            check(occupied(f.engine) == before[0], "기한 후 늦은 응답은 배치되지 않음");
            check(f.assist.getRemainingMillis() == 0 && f.assist.isUsed(), "5초 후 완료 상태");
        });
        check(f.advisor.closed.get() == 1, "기한 종료 연결 정리");
    }

    private static void testStaleFirstPlanRetry() throws Exception {
        Fixture f = new Fixture(new GateAdvisor(1));
        edt(() -> f.assist.start());
        check(f.advisor.entered.await(2, TimeUnit.SECONDS), "첫 블록 추론 시작");
        edt(() -> f.engine.oneLineDown());
        f.advisor.release.countDown();
        await(() -> !f.assist.isBusy());
        edt(() -> {
            check(!f.assist.isUsed() && occupied(f.engine) == 0, "오래된 첫 배치 계획은 사용 차감·고정 없음");
            check(f.assist.start(), "오래된 첫 응답 거부 뒤 재시도 허용");
        });
        await(() -> f.assist.isUsed());
        edt(() -> f.assist.stop());
    }

    private static void testLateCompletionChecksDeadline() throws Exception {
        Fixture f = new Fixture(new GateAdvisor(2));
        edt(() -> f.assist.start());
        check(f.advisor.entered.await(2, TimeUnit.SECONDS), "두 번째 추론 대기");
        edt(() -> {
            try {
                Field field = ClassicAiAssist.class.getDeclaredField("timer"); field.setAccessible(true);
                ((javax.swing.Timer) field.get(f.assist)).stop();
            } catch (Exception error) { throw new RuntimeException(error); }
            f.clock.set(5000);
        });
        // EDT 타이머 통지가 지연되어도 응답 자체가 기한을 확인해야 합니다.
        f.advisor.release.countDown();
        await(() -> !f.assist.isBusy());
        edt(() -> check(occupied(f.engine) == 4 && f.assist.getRemainingMillis() == 0,
                "타이머 통지 이전에 끝난 늦은 응답도 배치하지 않음"));
    }

    private static void testPauseFreezesBudget() throws Exception {
        Fixture f = new Fixture(new GateAdvisor(2));
        edt(() -> f.assist.start());
        await(() -> f.assist.isUsed());
        edt(() -> {
            f.clock.set(1000); f.engine.pause(); f.assist.pauseChanged();
            check(f.assist.getRemainingMillis() == 4000, "일시정지 직전 경과 정산");
            f.clock.set(20000);
            check(f.assist.getRemainingMillis() == 4000, "일시정지 시간은 예산 동결");
        });
        f.advisor.release.countDown();
        Thread.sleep(150);
        edt(() -> {
            check(occupied(f.engine) == 4 && f.assist.isBusy(), "정지 중 응답이 와도 배치 없음");
            f.engine.pause(); f.assist.pauseChanged(); f.clock.set(23500);
            check(f.assist.getRemainingMillis() == 500, "재개 후에만 경과시간 반영");
        });
        await(() -> occupied(f.engine) >= 8);
        edt(() -> { f.clock.set(24000); });
        await(() -> !f.assist.isBusy());
    }

    private static void testResetDropsOldReply() throws Exception {
        Fixture f = new Fixture(new GateAdvisor(1));
        edt(() -> f.assist.start());
        check(f.advisor.entered.await(2, TimeUnit.SECONDS), "이전 판 요청 시작");
        edt(() -> { f.assist.reset(); f.engine.start(); });
        f.advisor.release.countDown();
        Thread.sleep(150);
        edt(() -> check(!f.assist.isBusy() && !f.assist.isUsed() && occupied(f.engine) == 0,
                "새 판에 이전 응답 적용 금지"));
        check(f.advisor.closed.get() == 1, "reset 연결 정리");
    }

    private static void testLineClearSpawnsNext() throws Exception {
        Fixture f = new Fixture(new GateAdvisor(2));
        edt(() -> {
            try {
                Field current = GameEngine.class.getDeclaredField("curPiece"); current.setAccessible(true);
                ((Shape) current.get(f.engine)).setShape(Tetrominoes.SquareShape);
                Field x = GameEngine.class.getDeclaredField("curX"); x.setAccessible(true); x.setInt(f.engine, 4);
                Field y = GameEngine.class.getDeclaredField("curY"); y.setAccessible(true); y.setInt(f.engine, 12);
                Field board = GameEngine.class.getDeclaredField("board"); board.setAccessible(true);
                Tetrominoes[] cells = (Tetrominoes[]) board.get(f.engine);
                for (int i = 0; i < 10; i++) if (i != 4 && i != 5) cells[i] = Tetrominoes.LineShape;
            } catch (Exception error) { throw new RuntimeException(error); }
            f.assist.start();
        });
        check(f.advisor.entered.await(2, TimeUnit.SECONDS), "줄 삭제 후 다음 추론 요청");
        edt(() -> {
            check(f.engine.getNumLinesRemoved() == 1 && f.engine.getScore() == 10, "기존 줄 점수 유지");
            check(f.engine.getCurrentShape() != Tetrominoes.NoShape, "줄 삭제 대기 상태에서 다음 블록 생성");
            f.assist.stop();
        });
        f.advisor.release.countDown();
    }

    private static void testRestartIgnoresPreviousWorker() throws Exception {
        GateAdvisor previous = new GateAdvisor(1), current = new GateAdvisor(2);
        AtomicInteger created = new AtomicInteger();
        AtomicLong clock = new AtomicLong();
        GameEngine engine = engine();
        ClassicAiAssist[] assist = new ClassicAiAssist[1];
        edt(() -> {
            assist[0] = new ClassicAiAssist(engine, () -> {},
                    () -> created.getAndIncrement() == 0 ? previous : current, clock::get);
            assist[0].start();
        });
        check(previous.entered.await(2, TimeUnit.SECONDS), "이전 요청 추론 시작");
        edt(() -> { assist[0].reset(); engine.start(); check(assist[0].start(), "새 판 도움 시작"); });
        check(current.entered.await(2, TimeUnit.SECONDS), "새 판 첫 배치와 다음 요청 시작");
        previous.release.countDown();
        Thread.sleep(150);
        edt(() -> {
            check(assist[0].isBusy() && assist[0].isUsed() && occupied(engine) == 4,
                    "이전 판 done은 새 요청/배치 상태에 영향 없음");
            assist[0].stop();
        });
        current.release.countDown();
        check(previous.closed.get() == 1 && current.closed.get() == 1, "두 세션 연결 모두 정리");
    }

    private static void testCancelledConstructorClosesResource() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        GateAdvisor advisor = new GateAdvisor(99);
        ClassicAiAssist[] assist = new ClassicAiAssist[1];
        GameEngine engine = engine();
        edt(() -> {
            assist[0] = new ClassicAiAssist(engine, () -> {}, () -> {
                entered.countDown(); uninterruptibleAwait(release); return advisor;
            }, () -> 0L);
            assist[0].start();
        });
        check(entered.await(2, TimeUnit.SECONDS), "느린 생성자 진입");
        edt(() -> assist[0].stop());
        release.countDown();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (advisor.closed.get() == 0 && System.nanoTime() < end) Thread.sleep(10);
        check(advisor.closed.get() == 1 && occupied(engine) == 0, "취소 뒤 생성된 연결도 한 번 정리");
    }

    private static final class Fixture {
        final GameEngine engine;
        final GateAdvisor advisor;
        final AtomicLong clock = new AtomicLong();
        final AtomicInteger callbacks = new AtomicInteger();
        ClassicAiAssist assist;
        volatile boolean nonEdtCallback;
        Fixture(GateAdvisor advisor) throws Exception {
            this.advisor = advisor; engine = engine();
            edt(() -> assist = new ClassicAiAssist(engine, () -> {
                nonEdtCallback |= !SwingUtilities.isEventDispatchThread(); callbacks.incrementAndGet();
            }, () -> advisor, clock::get));
        }
    }

    private static final class GateAdvisor implements ClassicAiAssist.Advisor {
        final int gateCall;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicInteger closed = new AtomicInteger();
        int calls;
        GateAdvisor(int gateCall) { this.gateCall = gateCall; }
        public AiAssistPlan advise(AiAssistState state) {
            check(!SwingUtilities.isEventDispatchThread(), "추론은 EDT 밖에서 실행");
            if (++calls == gateCall) { entered.countDown(); uninterruptibleAwait(release); }
            return drop(state);
        }
        public void close() { closed.incrementAndGet(); }
    }

    private static AiAssistPlan drop(AiAssistState state) {
        Shape shape = new Shape(); shape.setShape(Tetrominoes.values()[state.getPiece()]);
        for (int i = 0; i < state.getRotation(); i++) shape = shape.rotateRight();
        int y = state.getY();
        while (fits(state, shape, y - 1)) y--;
        return new AiAssistPlan(state.getRotation(), state.getX(), y, "H");
    }
    private static boolean fits(AiAssistState state, Shape shape, int y) {
        for (int i = 0; i < 4; i++) {
            int cellX = state.getX() + shape.x(i), cellY = y - shape.y(i);
            if (cellX < 0 || cellX >= 10 || cellY < 0 || cellY >= 22
                    || state.getBoard().charAt(cellY * 10 + cellX) != '0') return false;
        }
        return true;
    }
    private static GameEngine engine() throws Exception {
        GameEngine engine = new GameEngine(23L); edt(engine::start); return engine;
    }
    private static int occupied(GameEngine engine) {
        int count = 0;
        for (int y = 0; y < 22; y++) for (int x = 0; x < 10; x++)
            if (engine.shapeAt(x, y) != Tetrominoes.NoShape) count++;
        return count;
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        boolean[] result = {false};
        while (System.nanoTime() < end) {
            edt(() -> result[0] = condition.getAsBoolean());
            if (result[0]) return;
            Thread.sleep(10);
        }
        throw new AssertionError("세션 상태 대기 시간 초과");
    }
    private static void uninterruptibleAwait(CountDownLatch latch) {
        boolean interrupted = false;
        for (;;) { try { latch.await(); break; } catch (InterruptedException ignored) { interrupted = true; } }
        if (interrupted) Thread.currentThread().interrupt();
    }
    private static void edt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
