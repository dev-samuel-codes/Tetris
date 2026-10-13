package backend.ai;

import shared.ai.AiAssistPlan;
import shared.ai.AiAssistState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// 실제 자식 프로세스로 탭 프로토콜과 시간 제한, 종료 취소를 확인합니다.
public final class AiAssistClientTest {
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 2 && "fake".equals(arguments[0])) {
            fakeService(arguments[1]);
            return;
        }
        testStateValidation();
        testPlanValidation();
        testAssistWithoutStart();
        testInvalidReplies();
        testSquareReply();
        testModelLoadingTimeout();
        testFailures();
        testCloseCancellation();
        System.out.println("AiAssistClientTest: " + checks + " checks passed");
    }

    private static void testStateValidation() {
        AiAssistState value = state();
        check(value.getBoard().charAt(0) == '7' && value.getBoard().charAt(219) == '1', "보드 아래쪽부터 전달");
        check(value.getPiece() == 4 && value.getRotation() == 2 && value.getX() == 6 && value.getY() == 20, "현재 블록 상태");
        check(value.equals(state()) && value.hashCode() == state().hashCode(), "상태 값 비교");
        check(!value.equals(null) && !value.equals("state"), "다른 타입 비교");
        check(!value.equals(new AiAssistState(repeat('0', 220), 4, 2, 6, 20)), "보드 변화 비교");
        check(!value.equals(new AiAssistState(value.getBoard(), 3, 2, 6, 20)), "블록 변화 비교");
        check(!value.equals(new AiAssistState(value.getBoard(), 4, 1, 6, 20)), "회전 변화 비교");
        check(!value.equals(new AiAssistState(value.getBoard(), 4, 2, 5, 20)), "가로 위치 변화 비교");
        check(!value.equals(new AiAssistState(value.getBoard(), 4, 2, 6, 19)), "세로 위치 변화 비교");
        invalidState(null, 4, 0, 0, 0);
        invalidState(repeat('0', 219), 4, 0, 0, 0);
        invalidState(repeat('8', 220), 4, 0, 0, 0);
        invalidState(repeat('\t', 220), 4, 0, 0, 0);
        invalidState(value.getBoard(), 0, 0, 0, 0);
        invalidState(value.getBoard(), 8, 0, 0, 0);
        invalidState(value.getBoard(), 4, -1, 0, 0);
        invalidState(value.getBoard(), 4, 4, 0, 0);
        invalidState(value.getBoard(), 5, 1, 0, 0);
        invalidState(value.getBoard(), 4, 0, -1, 0);
        invalidState(value.getBoard(), 4, 0, 10, 0);
        invalidState(value.getBoard(), 4, 0, 0, -1);
        invalidState(value.getBoard(), 4, 0, 0, 22);
        check(new AiAssistState(value.getBoard(), 5, 0, 9, 21).getRotation() == 0, "사각 블록과 위치 경계");
    }

    private static void testPlanValidation() {
        AiAssistPlan plan = new AiAssistPlan(3, 9, 21, "LRACDH");
        check(plan.getRotation() == 3 && plan.getX() == 9 && plan.getY() == 21
                && "LRACDH".equals(plan.getPath()), "목표와 모든 경로 토큰");
        check("H".equals(new AiAssistPlan(0, 0, 0, "H").getPath()), "즉시 하드 드롭");
        check(new AiAssistPlan(0, 0, 0, repeat('D', 255) + "H").getPath().length() == 256, "경로 길이 경계");
        invalidPlan(-1, 0, 0, "H");
        invalidPlan(4, 0, 0, "H");
        invalidPlan(0, -1, 0, "H");
        invalidPlan(0, 10, 0, "H");
        invalidPlan(0, 0, -1, "H");
        invalidPlan(0, 0, 22, "H");
        for (String path : new String[] { null, "", "LR", "HH", "HL", "WH", "L\nH", repeat('D', 256) + "H" }) {
            invalidPlan(0, 0, 0, path);
        }
    }

    private static void testAssistWithoutStart() throws Exception {
        Process process = fakeProcess("normal");
        try (AiMatchClient client = new AiMatchClient(process, 2000, 500)) {
            AiAssistPlan plan = client.assist(state());
            check(plan.getRotation() == 1 && plan.getX() == 3 && plan.getY() == 0
                    && "LACDDH".equals(plan.getPath()), "START 없이 정상 도움 요청과 응답");
            check("LACDDH".equals(client.assist(state()).getPath()), "프로세스 재사용");
            try { client.assist(null); throw new AssertionError("빈 상태 허용"); }
            catch (IllegalArgumentException expected) { check(process.isAlive(), "잘못된 상태에 연결 유지"); }
            try { client.step("WAIT"); throw new AssertionError("도움 요청이 대전을 시작함"); }
            catch (IOException expected) { check(expected.getMessage().contains("먼저 시작"), "도움 요청과 대전 시작 상태 분리"); }
        }
        check(process.waitFor(2, TimeUnit.SECONDS), "도움 프로세스 정리");
    }

    private static void testInvalidReplies() throws Exception {
        for (String mode : new String[] { "checkpoint", "rotation", "x", "y", "path", "token", "extra", "numeric", "oversized" }) {
            Process process = fakeProcess(mode);
            try (AiMatchClient client = new AiMatchClient(process, 2000, 200)) {
                try { client.assist(state()); throw new AssertionError("잘못된 응답 허용: " + mode); }
                catch (IOException expected) {
                    check(expected.getMessage().contains("응답 형식") || expected.getMessage().contains("길이"), "응답 거부: " + mode);
                    check(expected.getMessage().contains("scripts/setup-ai"), "응답 오류 설치 도움말: " + mode);
                }
            }
            check(process.waitFor(2, TimeUnit.SECONDS), "오류 프로세스 정리: " + mode);
        }
    }

    private static void testModelLoadingTimeout() throws Exception {
        Process process = fakeProcess("slow");
        try (AiMatchClient client = new AiMatchClient(process, 2000, 100)) {
            check(client.assist(state()).getX() == 3, "첫 모델 로딩은 시작 시간 제한 사용");
            try { client.assist(state()); throw new AssertionError("두 번째 도움 시간 제한 미적용"); }
            catch (IOException expected) { check(expected.getMessage().contains("시간이 초과"), "이후 도움은 진행 시간 제한 사용"); }
        }
        check(process.waitFor(2, TimeUnit.SECONDS), "도움 시간 초과 프로세스 정리");
    }

    private static void testSquareReply() throws Exception {
        Process process = fakeProcess("square");
        try (AiMatchClient client = new AiMatchClient(process, 2000, 200)) {
            AiAssistState square = new AiAssistState(state().getBoard(), 5, 0, 6, 20);
            try { client.assist(square); throw new AssertionError("사각 블록 회전 응답 허용"); }
            catch (IOException expected) { check(expected.getMessage().contains("사각 블록 회전"), "사각 블록 목표 회전 검증"); }
        }
        check(process.waitFor(2, TimeUnit.SECONDS), "사각 블록 오류 프로세스 정리");
    }

    private static void testFailures() throws Exception {
        for (String mode : new String[] { "error", "hang", "exit" }) {
            Process process = fakeProcess(mode);
            try (AiMatchClient client = new AiMatchClient(process, 1000, 100)) {
                try { client.assist(state()); throw new AssertionError("서비스 실패 미처리: " + mode); }
                catch (IOException expected) {
                    String fragment = "error".equals(mode) ? "test assist failure" : "hang".equals(mode) ? "시간이 초과" : "종료";
                    check(expected.getMessage().contains(fragment), "서비스 실패 전달: " + mode);
                    check(expected.getMessage().contains("scripts/setup-ai"), "서비스 실패 설치 도움말: " + mode);
                }
            }
            check(process.waitFor(2, TimeUnit.SECONDS), "실패 자식 프로세스 정리: " + mode);
        }
    }

    private static void testCloseCancellation() throws Exception {
        Process process = fakeProcess("hang");
        final AiMatchClient client = new AiMatchClient(process, 5000, 5000);
        final CountDownLatch entered = new CountDownLatch(1);
        final AtomicReference<Throwable> outcome = new AtomicReference<Throwable>();
        Thread request = new Thread(new Runnable() {
            @Override public void run() {
                entered.countDown();
                try { client.assist(state()); outcome.set(new AssertionError("도움 완료")); }
                catch (Throwable failure) { outcome.set(failure); }
            }
        });
        request.start();
        check(entered.await(1, TimeUnit.SECONDS), "도움 대기 시작");
        Thread.sleep(50);
        long before = System.nanoTime();
        client.close();
        client.close();
        request.join(1500);
        check(!request.isAlive() && outcome.get() instanceof IOException, "close로 도움 대기 취소");
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 1500, "취소 시 잠금 경합 없음");
        check(process.waitFor(2, TimeUnit.SECONDS), "취소한 자식 프로세스 종료");
        try { client.assist(state()); throw new AssertionError("종료 후 도움 허용"); }
        catch (IOException expected) { check(expected.getMessage().contains("종료"), "종료 후 도움 거부"); }
    }

    private static AiAssistState state() {
        return new AiAssistState("7" + repeat('0', 218) + "1", 4, 2, 6, 20);
    }

    private static void invalidState(String board, int piece, int rotation, int x, int y) {
        try { new AiAssistState(board, piece, rotation, x, y); throw new AssertionError("잘못된 상태 허용"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static void invalidPlan(int rotation, int x, int y, String path) {
        try { new AiAssistPlan(rotation, x, y, path); throw new AssertionError("잘못된 경로 허용"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static Process fakeProcess(String mode) throws IOException {
        String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
        return new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", System.getProperty("java.class.path"), AiAssistClientTest.class.getName(), "fake", mode).start();
    }

    private static void fakeService(String mode) throws Exception {
        System.out.println("READY\t2");
        System.out.flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String request;
        while ((request = reader.readLine()) != null) {
            String expected = "ASSIST\t" + state().getBoard()
                    + ("square".equals(mode) ? "\t5\t0\t6\t20" : "\t4\t2\t6\t20");
            if (!expected.equals(request)) {
                System.out.println("ERROR\tUnexpected request: " + request);
            } else if ("hang".equals(mode)) { Thread.sleep(10000); }
            else if ("exit".equals(mode)) { return; }
            else if ("error".equals(mode)) { System.out.println("ERROR\ttest assist failure"); }
            else if ("oversized".equals(mode)) { System.out.println(repeat('L', 5000)); }
            else {
                if ("slow".equals(mode)) Thread.sleep(250);
                String[] fields = { "ASSIST", "100000", "1", "3", "0", "LACDDH" };
                if ("checkpoint".equals(mode)) fields[1] = "50000";
                if ("rotation".equals(mode)) fields[2] = "4";
                if ("x".equals(mode)) fields[3] = "10";
                if ("y".equals(mode)) fields[4] = "22";
                if ("path".equals(mode)) fields[5] = "LHH";
                if ("token".equals(mode)) fields[0] = "FRAME";
                if ("numeric".equals(mode)) fields[3] = "+3";
                System.out.println(String.join("\t", fields) + ("extra".equals(mode) ? "\textra" : ""));
            }
            System.out.flush();
        }
    }

    private static String repeat(char value, int length) {
        char[] characters = new char[length];
        Arrays.fill(characters, value);
        return new String(characters);
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
