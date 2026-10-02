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
    private static final double NEXT_TITLE_ROW = 6.5;  // "NEXT" : "SCORE" 아래
    private static final int PREVIEW_TOP_ROW = 7;      // 미리보기 상자 시작
    private static final int PREVIEW_CELLS = 4;        // 미리보기 상자 (4x4)

    private int score = 0;
    private Tetrominoes nextShape = Tetrominoes.NoShape; // 다음 도형

    // 사이드 패널 색상(임시)
    public SidePanel() {
        setBackground(new Color(40, 40, 40));
    }

    public void setScore(int score) {
        this.score = score;
        repaint();
    }

    // 다음 도형 그리기
    public void setNextShape(Tetrominoes shape) {
        nextShape = shape;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 칸 크기 = 보드 칸 크기
        int size = getWidth() / COLS;
        g.setColor(Color.WHITE);

        drawScore(g, size);
        drawNextShape(g, size);
    }

    // 점수 표시
    private void drawScore(Graphics g, int size) {
        g.setFont(Style.SCORE_FONT.deriveFont(size * 0.8f));
        drawCentered(g, "SCORE", (int) (SCORE_TITLE_ROW * size));
        drawCentered(g, String.valueOf(score), (int) (SCORE_VALUE_ROW * size));
    }

    // 다음 도형 표시 상자
    private void drawNextShape(Graphics g, int size) {
        g.setFont(Style.SCORE_FONT.deriveFont(size * 0.8f));
        drawCentered(g, "NEXT", (int) (NEXT_TITLE_ROW * size));

        if (nextShape == Tetrominoes.NoShape)
            return;

        // 다음 도형 모양 가져오기
        Shape preview = new Shape();
        preview.setShape(nextShape);

        // 도형 범위
        int minX = preview.x(0), maxX = preview.x(0);
        int minY = preview.y(0), maxY = preview.y(0);
        for (int i = 1; i < 4; i++) {
            minX = Math.min(minX, preview.x(i));
            maxX = Math.max(maxX, preview.x(i));
            minY = Math.min(minY, preview.y(i));
            maxY = Math.max(maxY, preview.y(i));
        }
        int pieceWidth = maxX - minX + 1;
        int pieceHeight = maxY - minY + 1;

        // 4x4 상자 중앙 정렬
        int boxLeft = (getWidth() - PREVIEW_CELLS * size) / 2;
        int boxTop = PREVIEW_TOP_ROW * size;
        int startX = boxLeft + (PREVIEW_CELLS - pieceWidth) * size / 2;
        int startY = boxTop + (PREVIEW_CELLS - pieceHeight) * size / 2;

        for (int i = 0; i < 4; i++) {
            int x = startX + (preview.x(i) - minX) * size;
            int y = startY + (preview.y(i) - minY) * size;
            BlockPainter.drawSquare(g, x, y, size, nextShape);
        }
    }

    // 글자 패널 중앙 그리기
    private void drawCentered(Graphics g, String text, int baselineY) {
        FontMetrics fm = g.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        g.drawString(text, x, baselineY);
    }
}