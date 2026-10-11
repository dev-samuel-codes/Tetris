package backend.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// 별도 라이브러리 없이 AI 서비스 연결과 응답 형식을 확인
public final class AiMatchClientTest {
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 2 && "fake".equals(arguments[0])) {
            fakeService(arguments[1]);
            return;
        }
        testDifficulty();
        testFrame();
        testEarlyVictory();
        testInvalidFrames();
        testPersistentProcess();
        testFailures();
        testCloseCancelsRequest();
        System.out.println("AiMatchClientTest: " + checks + " checks passed");
    }

    private static void testDifficulty() {
        check(AiDifficulty.values().length == 5, "난이도 개수");
        check("쉬움".equals(AiDifficulty.EASY.toString()), "난이도 표시");
        check("very_hard".equals(AiDifficulty.VERY_HARD.getId()), "난이도 ID");
        check(AiDifficulty.EASY.getCheckpointScore() == 200, "쉬움 체크포인트");
        check(AiDifficulty.NORMAL.getCheckpointScore() == 500, "보통 체크포인트");
        check(AiDifficulty.HARD.getCheckpointScore() == 1000, "어려움 체크포인트");
        check(AiDifficulty.VERY_HARD.getCheckpointScore() == 5000, "매우 어려움 체크포인트");
        check(AiDifficulty.HELL.getCheckpointScore() == 10000, "지옥 체크포인트");
    }

    private static void testFrame() throws IOException {
        String[] fields = tokens(frame(1));
        fields[8] = "7" + fields[8].substring(1);
        MatchFrame parsed = AiMatchClient.parseFrame(join(fields));
        check(parsed.getElapsedTicks() == 1, "경과 시간");
        check(!parsed.isEnded() && "NONE".equals(parsed.getWinner()), "진행 상태");
        check(parsed.getHuman().getScore() == 20 && parsed.getAi().getLines() == 2, "점수와 줄 수");
        int[] board = parsed.getHuman().getBoard();
        check(board.length == 220 && board[0] == 7, "보드 순서");
        board[0] = 0;
        check(parsed.getHuman().getBoard()[0] == 7, "보드 방어적 복사");
        parsed = AiMatchClient.parseFrame(frame(3601));
        check(parsed.getElapsedTicks() == 3601 && !parsed.isEnded(), "3분 이후에도 진행");
        parsed = AiMatchClient.parseFrame(frame(2147483648L));
        check(parsed.getElapsedTicks() == 2147483648L, "긴 경기의 경과 시간");
        fields = tokens(frame(9000));
        fields[2] = "1";
        fields[3] = "AI";
        fields[6] = "1";
        fields[7] = "top_out";
        fields[11] = "1";
        fields[12] = "top_out";
        parsed = AiMatchClient.parseFrame(join(fields));
        check(parsed.isEnded() && "AI".equals(parsed.getWinner()), "양쪽 게임오버 후 종료");
    }

    private static void testInvalidFrames() throws IOException {
        expectInvalid(null);
        expectInvalid(frame(0) + "\textra");
        expectInvalid(frame(0).replaceFirst("FRAME", "INVALID"));
        invalidField(1, "-1");
        invalidField(1, "9223372036854775808");
        invalidField(2, "true");
        invalidField(3, "UNKNOWN");
        invalidField(3, "AI");
        invalidField(4, "2147483648");
        invalidField(4, "+2");
        invalidField(5, "2147483648");
        invalidField(6, "1");
        invalidField(7, "top_out");
        invalidField(7, "timeout");
        invalidField(7, "unexpected");
        invalidField(8, "0");
        invalidField(8, repeat('8', 220));
        invalidField(13, repeat('a', 220));
        String[] fields = tokens(frame(3600));
        fields[2] = "1";
        fields[3] = "AI";
        expectInvalid(join(fields));
        expectInvalid(repeat('0', 4097));
    }

    private static void testEarlyVictory() throws IOException {
        String aiVictory = outcome(100, true, 200, false, true, "AI");
        MatchFrame parsed = AiMatchClient.parseFrame(aiVictory);
        check(parsed.isEnded() && "AI".equals(parsed.getWinner()), "인간 종료 뒤 AI 추월 승리");
        check(!parsed.getAi().isEnded() && "-".equals(parsed.getAi().getReason()), "승리한 AI 보드 유지");
        expectInvalid(aiVictory.replace("\tAI\t", "\tHUMAN\t"));
        expectInvalid(aiVictory.replace("\tAI\t", "\tDRAW\t"));
        expectInvalid(outcome(100, true, 200, false, false, "NONE"));

        String humanVictory = outcome(200, false, 100, true, true, "HUMAN");
        parsed = AiMatchClient.parseFrame(humanVictory);
        check(parsed.isEnded() && "HUMAN".equals(parsed.getWinner()), "AI 종료 뒤 인간 추월 승리");
        check(!parsed.getHuman().isEnded() && "-".equals(parsed.getHuman().getReason()), "승리한 인간 보드 유지");
        expectInvalid(humanVictory.replace("\tHUMAN\t", "\tAI\t"));
        expectInvalid(humanVictory.replace("\tHUMAN\t", "\tDRAW\t"));
        expectInvalid(outcome(200, false, 100, true, false, "NONE"));

        check(!AiMatchClient.parseFrame(outcome(100, true, 100, false, false, "NONE")).isEnded(), "인간 종료 후 동점 진행");
        check(!AiMatchClient.parseFrame(outcome(200, true, 100, false, false, "NONE")).isEnded(), "인간 종료 후 AI 추격 진행");
        check(!AiMatchClient.parseFrame(outcome(100, false, 100, true, false, "NONE")).isEnded(), "AI 종료 후 동점 진행");
        check(!AiMatchClient.parseFrame(outcome(100, false, 200, true, false, "NONE")).isEnded(), "AI 종료 후 인간 추격 진행");
        check(!AiMatchClient.parseFrame(outcome(100, false, 200, false, false, "NONE")).isEnded(), "양쪽 진행 유지");
        expectInvalid(outcome(100, true, 100, false, true, "DRAW"));
        expectInvalid(outcome(200, true, 100, false, true, "HUMAN"));
        expectInvalid(outcome(100, false, 100, true, true, "DRAW"));
        expectInvalid(outcome(100, false, 200, true, true, "AI"));
        expectInvalid(outcome(100, false, 200, false, true, "AI"));

        check("HUMAN".equals(AiMatchClient.parseFrame(outcome(200, true, 100, true, true, "HUMAN")).getWinner()), "양쪽 종료 인간 승리");
        check("AI".equals(AiMatchClient.parseFrame(outcome(100, true, 200, true, true, "AI")).getWinner()), "양쪽 종료 AI 승리");
        check("DRAW".equals(AiMatchClient.parseFrame(outcome(100, true, 100, true, true, "DRAW")).getWinner()), "양쪽 종료 무승부");
        expectInvalid(outcome(200, true, 100, true, true, "AI"));
        expectInvalid(outcome(100, true, 200, true, true, "HUMAN"));
        expectInvalid(outcome(100, true, 100, true, true, "HUMAN"));
        expectInvalid(outcome(100, true, 100, true, false, "NONE"));
    }

    private static String outcome(int humanScore, boolean humanEnded, int aiScore, boolean aiEnded,
                                  boolean ended, String winner) {
        String[] fields = tokens(frame(100));
        fields[2] = ended ? "1" : "0";
        fields[3] = winner;
        fields[4] = Integer.toString(humanScore);
        fields[6] = humanEnded ? "1" : "0";
        fields[7] = humanEnded ? "top_out" : "-";
        fields[9] = Integer.toString(aiScore);
        fields[11] = aiEnded ? "1" : "0";
        fields[12] = aiEnded ? "top_out" : "-";
        return join(fields);
    }

    private static void testPersistentProcess() throws Exception {
        Process service = fakeProcess("normal");
        try (AiMatchClient client = new AiMatchClient(service, 2000, 500)) {
            MatchFrame initial = client.start(AiDifficulty.NORMAL, 42L);
            check(initial.getElapsedTicks() == 0, "시작 프레임");
            check(client.step("WAIT").getElapsedTicks() == 1, "첫 번째 진행");
            check(client.step("LEFT").getElapsedTicks() == 2, "동일 프로세스 재사용");
            boolean invalid = false;
            try { client.step("WAIT\nQUIT"); } catch (IllegalArgumentException expected) { invalid = true; }
            check(invalid && service.isAlive(), "명령 삽입 거부");
            check(client.start(AiDifficulty.HELL, Long.MIN_VALUE).getElapsedTicks() == 0, "새 대전 시작");
        }
        check(service.waitFor(2, TimeUnit.SECONDS), "close 후 자식 프로세스 종료");
    }

    private static void testFailures() throws Exception {
        Process wrongReady = fakeProcess("wrong-ready");
        expectStartupFailure(wrongReady, "시작 응답");
        Process earlyExit = fakeProcess("exit");
        expectStartupFailure(earlyExit, "종료");
        Process importFailure = fakeProcess("import-failure");
        try { new AiMatchClient(importFailure, 2000, 100); throw new AssertionError("의존성 실패 미처리"); }
        catch (IOException expected) {
            check(expected.getMessage().contains("ModuleNotFoundError"), "시작 stderr 진단 전달");
            check(expected.getMessage().length() < 5000, "오류 출력 크기 제한");
        }
        check(importFailure.waitFor(2, TimeUnit.SECONDS), "의존성 실패 프로세스 정리");
        Process hangingStartup = fakeProcess("hang-startup");
        long before = System.nanoTime();
        try { new AiMatchClient(hangingStartup, 100, 100); throw new AssertionError("시작 제한 미적용"); }
        catch (IOException expected) { check(expected.getMessage().contains("시간이 초과"), "시작 시간 제한"); }
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 2000, "시작 대기 제한");
        check(hangingStartup.waitFor(2, TimeUnit.SECONDS), "시작 실패 프로세스 정리");

        try (AiMatchClient client = new AiMatchClient(fakeProcess("error"), 2000, 100)) {
            try { client.start(AiDifficulty.EASY, 1L); throw new AssertionError("ERROR 미처리"); }
            catch (IOException expected) {
                check(expected.getMessage().contains("ModuleNotFoundError"), "서비스 오류 전달");
                check(expected.getMessage().contains("scripts/setup-ai"), "설치 도움말");
            }
        }
        try (AiMatchClient client = new AiMatchClient(fakeProcess("hang-step"), 2000, 100)) {
            client.start(AiDifficulty.EASY, 1L);
            try { client.step("WAIT"); throw new AssertionError("STEP 제한 미적용"); }
            catch (IOException expected) { check(expected.getMessage().contains("시간이 초과"), "STEP 시간 제한"); }
        }
        try (AiMatchClient client = new AiMatchClient(fakeProcess("invalid-frame"), 2000, 100)) {
            try { client.start(AiDifficulty.EASY, 1L); throw new AssertionError("프레임 미검증"); }
            catch (IOException expected) { check(expected.getMessage().contains("응답 형식"), "잘못된 프로세스 프레임"); }
        }
        try (AiMatchClient client = new AiMatchClient(fakeProcess("oversized"), 2000, 100)) {
            try { client.start(AiDifficulty.EASY, 1L); throw new AssertionError("긴 줄 미검증"); }
            catch (IOException expected) { check(expected.getMessage().contains("길이"), "응답 길이 제한"); }
        }
        Process stderrService = fakeProcess("stderr");
        try (AiMatchClient client = new AiMatchClient(stderrService, 2000, 100)) {
            check(client.start(AiDifficulty.EASY, 1L).getElapsedTicks() == 0, "stderr와 프로토콜 분리");
        }
        check(stderrService.waitFor(2, TimeUnit.SECONDS), "stderr 프로세스 정리");
    }

    private static void testCloseCancelsRequest() throws Exception {
        Process process = fakeProcess("hang-step");
        final AiMatchClient client = new AiMatchClient(process, 2000, 5000);
        client.start(AiDifficulty.EASY, 1L);
        final CountDownLatch entered = new CountDownLatch(1);
        final AtomicReference<Throwable> outcome = new AtomicReference<Throwable>();
        Thread request = new Thread(new Runnable() {
            @Override public void run() {
                entered.countDown();
                try { client.step("WAIT"); outcome.set(new AssertionError("STEP 완료")); }
                catch (Throwable failure) { outcome.set(failure); }
            }
        });
        request.start();
        check(entered.await(1, TimeUnit.SECONDS), "진행 대기 시작");
        Thread.sleep(50);
        long before = System.nanoTime();
        client.close();
        client.close();
        request.join(1500);
        check(!request.isAlive() && outcome.get() instanceof IOException, "close가 진행 대기 취소");
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 1500, "close 잠금 경합 없음");
        check(process.waitFor(2, TimeUnit.SECONDS), "취소 자식 프로세스 종료");
        try { client.start(AiDifficulty.EASY, 1L); throw new AssertionError("종료 후 시작"); }
        catch (IOException expected) { check(expected.getMessage().contains("종료"), "종료 후 요청 거부"); }
    }

    private static void expectStartupFailure(Process process, String text) throws Exception {
        try { new AiMatchClient(process, 2000, 100); throw new AssertionError("시작 오류 미검증"); }
        catch (IOException expected) { check(expected.getMessage().contains(text), "시작 실패: " + text); }
        check(process.waitFor(2, TimeUnit.SECONDS), "실패 프로세스 정리");
    }

    private static void invalidField(int index, String value) throws IOException {
        String[] fields = tokens(frame(0));
        fields[index] = value;
        expectInvalid(join(fields));
    }

    private static void expectInvalid(String line) throws IOException {
        try { AiMatchClient.parseFrame(line); throw new AssertionError("잘못된 프레임 허용"); }
        catch (IOException expected) { checks++; }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }

    private static Process fakeProcess(String mode) throws IOException {
        String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
        return new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", System.getProperty("java.class.path"), AiMatchClientTest.class.getName(), "fake", mode).start();
    }

    private static void fakeService(String mode) throws Exception {
        if ("exit".equals(mode)) return;
        if ("import-failure".equals(mode)) {
            System.err.println(repeat('w', 9000) + "ModuleNotFoundError: missing_dependency");
            System.err.flush();
            return;
        }
        if ("hang-startup".equals(mode)) { Thread.sleep(10000); return; }
        if ("wrong-ready".equals(mode)) { System.out.println("READY\t1"); System.out.flush(); return; }
        System.out.println("READY\t2");
        System.out.flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int ticks = 0;
        String request;
        while ((request = reader.readLine()) != null) {
            if ("QUIT".equals(request)) return;
            if ("error".equals(mode)) { System.out.println("ERROR\tModuleNotFoundError: test dependency"); }
            else if ("invalid-frame".equals(mode)) { System.out.println("FRAME\tbroken"); }
            else if ("oversized".equals(mode)) { System.out.println(repeat('x', 5000)); }
            else {
                if (request.startsWith("START\t")) ticks = 0;
                else if (request.startsWith("STEP\t")) {
                    if ("hang-step".equals(mode)) Thread.sleep(10000);
                    ticks++;
                }
                if ("stderr".equals(mode)) { System.err.println(repeat('w', 9000)); System.err.flush(); }
                System.out.println(frame(ticks));
            }
            System.out.flush();
        }
    }

    private static String frame(long ticks) {
        return "FRAME\t" + ticks + "\t0\tNONE\t20\t1\t0\t-\t"
                + repeat('0', 220) + "\t40\t2\t0\t-\t" + repeat('0', 220);
    }

    private static String[] tokens(String line) { return line.split("\t", -1); }
    private static String join(String[] fields) { return String.join("\t", fields); }
    private static String repeat(char value, int length) {
        char[] characters = new char[length];
        Arrays.fill(characters, value);
        return new String(characters);
    }
}
