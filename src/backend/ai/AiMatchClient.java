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
public final class AiMatchClient {
    private static final int MAX_LINE = 4096;

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
}
