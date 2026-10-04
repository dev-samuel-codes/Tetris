package frontend;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import frontend.style.Style;

public class SidePanel extends JPanel {

    // 정보 패널의 가로 칸 수
    public static final int COLS = 6;
    private static final int PREVIEW_TOP_ROW = 9;
    private static final int PREVIEW_CELLS = 4;
    private static final String[][] CONTROLS = {
            { "← →", "이동" }, { "↑ ↓", "회전" }, { "D", "한 칸 하강" },
            { "SPACE", "즉시 낙하" }, { "P", "일시정지" }
    };

    private int score = 0;
    private int elapsedSeconds = 0;
    private int linesRemoved = 0;
    private Tetrominoes nextShape = Tetrominoes.NoShape;

    public SidePanel() {
        setBackground(Style.BACKGROUND);
    }

    public void setScore(int score) {
        this.score = score;
        repaint();
    }

    public void setElapsedSeconds(int seconds) {
        elapsedSeconds = seconds;
        repaint();
    }

    public void setLinesRemoved(int lines) {
        linesRemoved = lines;
        repaint();
    }

    public void setNextShape(Tetrominoes shape) {
        nextShape = shape;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int size = getWidth() / COLS;
        if (size <= 0)
            return;

        // 카드와 글자 위치도 보드의 칸 크기를 기준으로 확대
        Graphics2D graphics = (Graphics2D) g.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawCard(graphics, size, 0, 3.4);
            drawCard(graphics, size, 3.8, 3.0);
            drawCard(graphics, size, 7.2, 6.1);
            drawCard(graphics, size, 13.8, 8.2);
            drawStats(graphics, size);
            drawNextShape(graphics, size);
            drawControls(graphics, size);
        } finally {
            graphics.dispose();
        }
    }

    private void drawCard(Graphics2D g, int size, double top, double height) {
        int arc = Math.max(4, size / 2);
        g.setColor(Style.PANEL);
        g.fillRoundRect(0, (int) (top * size), getWidth(), (int) (height * size), arc, arc);
    }

    // 점수, 누적 제거 줄 수, 경과 시간 표시
    private void drawStats(Graphics2D g, int size) {
        g.setFont(Style.SCORE_FONT.deriveFont(size * 0.5f));
        drawCentered(g, "SCORE", getWidth() / 2, (int) (0.95 * size), Style.MUTED_TEXT);
        g.setFont(Style.SCORE_FONT.deriveFont(size * 1.25f));
        drawCentered(g, String.valueOf(score), getWidth() / 2, (int) (2.55 * size), Style.TEXT);

        g.setFont(Style.SCORE_FONT.deriveFont(size * 0.43f));
        drawCentered(g, "LINES", getWidth() / 4, (int) (4.75 * size), Style.MUTED_TEXT);
        drawCentered(g, "TIME", getWidth() * 3 / 4, (int) (4.75 * size), Style.MUTED_TEXT);
        g.setFont(Style.SCORE_FONT.deriveFont(size * 0.7f));
        drawCentered(g, String.valueOf(linesRemoved), getWidth() / 4, (int) (6.05 * size), Style.TEXT);
        String time = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
        g.setFont(Style.SCORE_FONT.deriveFont(size * 0.6f));
        drawCentered(g, time, getWidth() * 3 / 4, (int) (6.05 * size), Style.TEXT);
    }

    private void drawNextShape(Graphics2D g, int size) {
        g.setFont(Style.SCORE_FONT.deriveFont(size * 0.5f));
        drawCentered(g, "NEXT", getWidth() / 2, (int) (8.25 * size), Style.MUTED_TEXT);
        if (nextShape == Tetrominoes.NoShape)
            return;

        Shape preview = new Shape();
        preview.setShape(nextShape);
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

        // 4x4 미리보기 영역 가운데에 블록 배치
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

    // 실제 키 입력과 같은 조작 방법을 오른쪽 아래에 표시
    // 나중에 키 설정 기능이 생기면 입력 처리와 안내 문구를 함께 관리해도 괜찮을 것 같음
    private void drawControls(Graphics2D g, int size) {
        g.setFont(Style.SCORE_FONT.deriveFont(size * 0.5f));
        drawCentered(g, "CONTROLS", getWidth() / 2, (int) (14.95 * size), Style.TEXT);
        for (int i = 0; i < CONTROLS.length; i++) {
            int baseline = (int) ((16.4 + i * 1.05) * size);
            g.setFont(Style.SCORE_FONT.deriveFont(size * 0.43f));
            g.setColor(Style.ACCENT);
            g.drawString(CONTROLS[i][0], (int) (size * 0.6), baseline);
            g.setFont(Style.BODY_FONT.deriveFont(size * 0.43f));
            g.setColor(Style.MUTED_TEXT);
            int textWidth = g.getFontMetrics().stringWidth(CONTROLS[i][1]);
            g.drawString(CONTROLS[i][1], getWidth() - (int) (size * 0.6) - textWidth, baseline);
        }
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int baselineY, Color color) {
        FontMetrics fm = g.getFontMetrics();
        g.setColor(color);
        g.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
    }
}
