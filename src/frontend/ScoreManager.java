package frontend;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScoreManager {

    private static final String SCORE_FILE = "scores.txt";

    // 점수를 파일에 저장하고 TOP 5만 유지
    public static void saveScore(int score) {
        List<Integer> scores = loadScores();
        scores.add(score);
        Collections.sort(scores, Collections.reverseOrder());

        if (scores.size() > 5) {
            scores = scores.subList(0, 5);
        }
        // 파일 내용을 top5로 재작성함
        try {
            BufferedWriter writer = new BufferedWriter(
                    new FileWriter(SCORE_FILE)
            );
            for (int savedScore : scores) {
                writer.write(String.valueOf(savedScore));
                writer.newLine();
            }
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 저장된 점수를 파일에서 불러오기
    public static List<Integer> loadScores() {
        List<Integer> scores = new ArrayList<>();
        File file = new File(SCORE_FILE);
        if (!file.exists()) {
            return scores;
        }
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader(file)
            );
            String line;
            while ((line = reader.readLine()) != null) {
                scores.add(Integer.parseInt(line));
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return scores;
    }

    // 최고 점수 반환
    public static int getBestScore() {
        List<Integer> scores = loadScores();
        if (scores.isEmpty()) {
            return 0;
        }
        return scores.get(0);
    }
}