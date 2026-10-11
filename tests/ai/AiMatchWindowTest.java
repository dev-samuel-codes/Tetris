package frontend.ai;

import frontend.style.Style;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

// 화면을 열지 않고 보드와 경기 진행을 확인하는 테스트
public final class AiMatchWindowTest {
    private AiMatchWindowTest() { }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                verifyBoard();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        verifyElapsedTime();
        verifyPacingDelays();
        verifyPauseResume();
        System.out.println("AiMatchWindowTest: 보드 방향·크기 변경·상태 표시·경과 시간·지연 후 틱 간격·일시정지 통과");
    }

    private static void verifyElapsedTime() {
        require("00:00".equals(AiMatchWindow.formatElapsedTime(0)), "경과 시간은 00:00부터 시작");
        require("00:00".equals(AiMatchWindow.formatElapsedTime(19)), "첫 1초 전 시간 표시 오류");
        require("00:01".equals(AiMatchWindow.formatElapsedTime(20)), "1초 시간 표시 오류");
        require("03:01".equals(AiMatchWindow.formatElapsedTime(3620)), "3분 이후 경과 시간 오류");
        require("5000000:00".equals(AiMatchWindow.formatElapsedTime(6000000000L)),
                "긴 경기의 경과 시간 오류");
    }

    private static void verifyPacingDelays() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger requested = new AtomicInteger();
        AtomicInteger rendered = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        AtomicReference<FramePacer> reference = new AtomicReference<FramePacer>();
        CountDownLatch finished = new CountDownLatch(1);
        long[] starts = new long[5];
        long[] renders = new long[5];
        FramePacer pacer = new FramePacer(executor, 50, () -> {
            int index = requested.getAndIncrement();
            if (index >= starts.length || index != rendered.get())
                throw new IllegalStateException("이전 프레임을 반영하기 전에 다음 틱이 실행됨");
            starts[index] = System.nanoTime();
            if (index == 0)
                Thread.sleep(140); // 모델 응답이 늦어지는 경우
            return () -> {
                if (index == 1) {
                    try {
                        Thread.sleep(120); // 화면 표시가 늦어지는 경우
                    } catch (InterruptedException error) {
                        throw new RuntimeException(error);
                    }
                }
                renders[index] = System.nanoTime();
                rendered.incrementAndGet();
                if (index == 4) {
                    reference.get().close();
                    finished.countDown();
                }
            };
        }, error -> {
            failure.set(error);
            finished.countDown();
        });
        reference.set(pacer);
        try {
            pacer.setPaused(false);
            require(finished.await(4, TimeUnit.SECONDS), "지연 주입 경기 타임아웃");
            require(failure.get() == null, "경기 예약 오류: " + failure.get());
            require(requested.get() == 5 && rendered.get() == 5, "중복 또는 누락된 경기 틱");
            require(millis(starts[1] - renders[0]) >= 45, "모델 지연 뒤 누적 틱 실행");
            require(millis(starts[2] - renders[1]) >= 45, "EDT 지연 뒤 누적 틱 실행");
            require(millis(starts[4] - starts[3]) >= 45, "정상 프레임 간격이 50ms보다 짧음");
            System.out.println("지연 검증(ms): 모델 지연 후 " + millis(starts[1] - renders[0])
                    + ", EDT 지연 후 " + millis(starts[2] - renders[1])
                    + ", 정상 시작 간격 " + millis(starts[4] - starts[3]));
        } finally {
            pacer.close();
            executor.shutdownNow();
        }
    }

    private static void verifyPauseResume() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger requested = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        AtomicReference<FramePacer> reference = new AtomicReference<FramePacer>();
        AtomicLong firstRenderedAt = new AtomicLong();
        AtomicLong secondStartedAt = new AtomicLong();
        AtomicLong thirdStartedAt = new AtomicLong();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch finishSecond = new CountDownLatch(1);
        CountDownLatch secondRendered = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        FramePacer pacer = new FramePacer(executor, 50, () -> {
            final int index = requested.incrementAndGet();
            if (index == 1) {
                firstEntered.countDown();
                finishFirst.await(3, TimeUnit.SECONDS);
            } else if (index == 2) {
                secondStartedAt.set(System.nanoTime());
                secondEntered.countDown();
                finishSecond.await(3, TimeUnit.SECONDS);
            } else if (index == 3) {
                thirdStartedAt.set(System.nanoTime());
            } else {
                throw new IllegalStateException("정지 후 예약이 중복됨");
            }
            return () -> {
                if (index == 1) {
                    firstRenderedAt.set(System.nanoTime());
                } else if (index == 2) {
                    secondRendered.countDown();
                } else {
                    reference.get().close();
                    finished.countDown();
                }
            };
        }, error -> {
            failure.set(error);
            finished.countDown();
        });
        reference.set(pacer);
        try {
            // 예약 취소를 반복해도 한 번만 실행되는지 확인
            for (int i = 0; i < 20; i++) {
                pacer.setPaused(false);
                pacer.setPaused(true);
            }
            pacer.setPaused(false);
            require(firstEntered.await(2, TimeUnit.SECONDS), "첫 틱 타임아웃");
            pacer.setPaused(true);
            pacer.setPaused(false); // 응답을 기다리는 도중 일시정지 해제
            Thread.sleep(80);
            require(requested.get() == 1, "응답 대기 중 틱이 누적됨");
            finishFirst.countDown();
            require(secondEntered.await(2, TimeUnit.SECONDS), "두 번째 틱 타임아웃");
            require(millis(secondStartedAt.get() - firstRenderedAt.get()) >= 45,
                    "응답 대기 중 정지/재개 후 간격 누락");
            pacer.setPaused(true);
            finishSecond.countDown();
            require(secondRendered.await(2, TimeUnit.SECONDS), "정지 중 프레임 반영 타임아웃");
            Thread.sleep(80);
            require(requested.get() == 2, "일시정지 중 STEP 실행");
            long resumedAt = System.nanoTime();
            pacer.setPaused(false);
            require(finished.await(2, TimeUnit.SECONDS), "재개 타임아웃");
            require(failure.get() == null, "정지/재개 오류: " + failure.get());
            require(millis(thirdStartedAt.get() - resumedAt) >= 45, "재개 직후 누적 틱 실행");
        } finally {
            pacer.close();
            finishFirst.countDown();
            finishSecond.countDown();
            executor.shutdownNow();
        }
    }

    private static long millis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private static void verifyBoard() throws Exception {
        Class<?> type = Class.forName("frontend.ai.AiMatchWindow$BoardCanvas");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        JPanel canvas = (JPanel) constructor.newInstance();
        int[] cells = new int[220];
        cells[0] = 1; // 맨 아래 왼쪽 칸
        cells[219] = 7; // 맨 위 오른쪽 칸
        set(type, canvas, "cells", cells);
        verifyAtSize(canvas, 202, 442);
        verifyAtSize(canvas, 267, 589);
        canvas.setSize(202, 442);
        int normal = render(canvas).getRGB(11, 431);
        set(type, canvas, "paused", true);
        int paused = render(canvas).getRGB(11, 431);
        require(new Color(paused).getRed() < new Color(normal).getRed(), "일시정지 오버레이 누락");
        set(type, canvas, "paused", false);
        set(type, canvas, "ended", true);
        require(render(canvas).getRGB(11, 431) == paused, "종료 오버레이 누락");
    }

    private static void verifyAtSize(JPanel canvas, int width, int height) {
        canvas.setSize(width, height);
        BufferedImage image = render(canvas);
        int cell = Math.min((width - 2) / 10, (height - 2) / 22);
        int left = (width - 10 * cell) / 2;
        int top = (height - 22 * cell) / 2;
        int empty = Style.BOARD_BACKGROUND.getRGB();
        require(image.getRGB(left + cell / 2, top + 21 * cell + cell / 2) != empty,
                "y=0 블록은 맨 아래에 표시되어야 함");
        require(image.getRGB(left + cell / 2, top + cell / 2) == empty,
                "맨 위 왼쪽 빈칸의 방향 오류");
        require(image.getRGB(left + 9 * cell + cell / 2, top + cell / 2) != empty,
                "y=21 블록은 맨 위에 표시되어야 함");
    }

    private static BufferedImage render(JPanel canvas) {
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
