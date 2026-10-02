package frontend;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;

import javax.swing.JPanel;

import frontend.style.Style;

public class SidePanel extends JPanel {

    // 정보 패널의 가로 칸 수
    public static final int COLS = 6;

    // 배치 위치
    private static final double SCORE_TITLE_ROW = 3.0; // "SCORE" 가운데 위
    private static final double SCORE_VALUE_ROW = 4.2; // 점수 숫자 기준선

    private int score = 0;

    // 사이드 패널 색상 (임시)
    public SidePanel() {
        setBackground(new Color(40, 40, 40));
    }

    public void setScore(int score) {
        this.score = score;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 칸 크기 = 보드 칸 크기
        int size = getWidth() / COLS;
        g.setColor(Color.WHITE);

        drawScore(g, size);
    }

    // 점수 표시
    private void drawScore(Graphics g, int size) {
        g.setFont(Style.SCORE_FONT.deriveFont(size * 0.8f));
        drawCentered(g, "SCORE", (int) (SCORE_TITLE_ROW * size));
        drawCentered(g, String.valueOf(score), (int) (SCORE_VALUE_ROW * size));
    }

    // 글자를 패널 가로 가운데에 그리기
    private void drawCentered(Graphics g, String text, int baselineY) {
        FontMetrics fm = g.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        g.drawString(text, x, baselineY);
    }
}