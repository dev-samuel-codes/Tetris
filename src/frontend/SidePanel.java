package frontend;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import javax.swing.JPanel;
import frontend.style.Style;

public class SidePanel extends JPanel {

    // 보드 옆 정보 패널은 가로 6칸 기준으로 표시
    public static final int COLS = 6;
    private final String[][] controls;

    private int score;
    private int elapsedSeconds;
    private int linesRemoved;
    private int level = 1;
    private Tetrominoes nextShape = Tetrominoes.NoShape;

    // 클래식 모드 기본 조작키를 사용하는 사이드패널 생성
    public SidePanel() {
        this(new String[][] {
                { "← →", "이동" },
                { "↑ ↓", "회전" },
                { "D", "한 칸 하강" },
                { "SPACE", "즉시 낙하" },
                { "P", "일시정지" }
        });
    }

    // 전달받은 조작키를 표시하는 사이드패널 생성
    public SidePanel(String[][] controls) {
        this.controls = controls;
        setBackground(Style.BACKGROUND);
    }

    public void setScore(int score) {
        this.score = score;
        repaint();
    }

    public void setElapsedSeconds(int seconds) {
        elapsedSeconds = Math.max(0, seconds);
        repaint();
    }

    public void setLinesRemoved(int lines) {
        linesRemoved = lines;
        repaint();
    }

    public void setNextShape(Tetrominoes shape) {
        nextShape = shape == null ? Tetrominoes.NoShape : shape;
        repaint();
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // 가로 6칸과 세로 22칸 중 작은 배율을 사용해 점수판 전체 표시
        // 나중에 표시 항목이 추가되면 구역 위치와 높이를 한곳에서 관리해도 괜찮을 것 같음
        double size = Math.min(getWidth() / (double) COLS, getHeight() / 22.0);
        if (size <= 0)
            return;
        Graphics2D graphics = (Graphics2D) g.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawDivider(graphics, size, 4.0);
            drawDivider(graphics, size, 13.8);
            drawStats(graphics, size);
            drawNextShape(graphics, size);
            drawControls(graphics, size);
        } finally {
            graphics.dispose();
        }
    }

    private void drawDivider(Graphics2D g, double size, double top) {
        // 배경을 덮는 상자 대신 얇은 구분선으로 정보 구역 표시
        int inset = (int) (size * .6);
        g.setColor(Style.BORDER);
        g.drawLine(inset, (int) (top * size), getWidth() - inset, (int) (top * size));
    }

    private void drawStats(Graphics2D g, double size) {
        int inset = (int) (size * .6);
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .43)));
        g.setColor(Style.MUTED_TEXT);
        g.drawString("SCORE", inset, (int) (size * .95));
        String scoreText = NumberFormat.getIntegerInstance().format(score);
        fitFont(g, Style.DISPLAY_FONT, scoreText, size * 1.48, getWidth() - inset * 2);
        g.setColor(Style.TEXT);
        g.drawString(scoreText, inset, (int) (size * 2.65));

        // 점수보다 작은 레벨 표식을 사용해 숫자에 먼저 시선이 가도록 표시
        String levelText = "LEVEL " + level;
        int chipPadding = Math.max(3, (int) (size * .3));
        fitFont(g, Style.MONO_FONT, levelText, size * .4, getWidth() - inset * 2 - chipPadding * 2);
        int chipWidth = g.getFontMetrics().stringWidth(levelText) + chipPadding * 2;
        int chipY = (int) (size * 3.06);
        int chipHeight = (int) (size * .7);
        g.setColor(new Color(Style.ACCENT.getRed(), Style.ACCENT.getGreen(), Style.ACCENT.getBlue(), 24));
        g.fillRect(inset, chipY, chipWidth, chipHeight);
        g.setColor(Style.ACCENT);
        g.drawString(levelText, inset + chipPadding,
                chipY + (chipHeight - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());

        drawStatRow(g, size, "LINES", String.valueOf(linesRemoved), 5.6);
        String time = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
        drawStatRow(g, size, "TIME", time, 7.15);
    }

    private void drawStatRow(Graphics2D g, double size, String label, String value, double baseline) {
        int inset = (int) (size * .6);
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .4)));
        g.setColor(Style.MUTED_TEXT);
        g.drawString(label, inset, (int) (size * baseline));
        // 항목 이름의 실제 글자 폭을 빼고 남은 공간에 숫자 표시
        int labelWidth = g.getFontMetrics().stringWidth(label);
        int available = getWidth() - inset * 2 - labelWidth - (int) (size * .45);
        fitFont(g, Style.MONO_FONT, value, size * .7, available);
        g.setColor(Style.TEXT);
        g.drawString(value, getWidth() - inset - g.getFontMetrics().stringWidth(value),
                (int) (size * baseline));
    }

    private void drawNextShape(Graphics2D g, double size) {
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .43)));
        g.setColor(Style.MUTED_TEXT);
        g.drawString("NEXT", (int) (size * .6), (int) (size * 9.12));
        int cell = Math.max(1, (int) size);
        int boxLeft = (getWidth() - 4 * cell) / 2;
        int boxTop = (int) (size * 9.5);
        // 다음 블록만 별도의 어두운 영역에 표시해 보드와 같은 색상 대비 사용
        int frameInset = (int) (size * .45);
        int frameHeight = (int) (size * 4.12);
        g.setColor(Style.BOARD_BACKGROUND);
        g.fillRect(frameInset, boxTop, getWidth() - frameInset * 2, frameHeight);
        g.setColor(Style.BORDER);
        g.drawRect(frameInset, boxTop, getWidth() - frameInset * 2 - 1, frameHeight - 1);
        if (level >= 4) {
            // 숨긴 블록이 빈 화면이나 오류처럼 보이지 않도록 난이도 효과 표시
            g.setFont(Style.DISPLAY_FONT.deriveFont((float) size));
            drawCentered(g, "—", getWidth() / 2, (int) (size * 11.05), Style.MUTED_TEXT);
            fitFont(g, Style.BODY_FONT, "미리보기 숨김", size * .45, getWidth() - cell);
            drawCentered(g, "미리보기 숨김", getWidth() / 2, (int) (size * 12.1), Style.TEXT);
            g.setFont(Style.BODY_FONT.deriveFont((float) (size * .37)));
            drawCentered(g, "레벨 4부터 적용", getWidth() / 2, (int) (size * 12.85), Style.MUTED_TEXT);
            return;
        }
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
        // 블록이 차지하는 가로·세로 칸 수를 계산해 4×4 영역 중앙에 표시
        int startX = boxLeft + (4 - (maxX - minX + 1)) * cell / 2;
        int startY = boxTop + (4 - (maxY - minY + 1)) * cell / 2;
        for (int i = 0; i < 4; i++)
            BlockPainter.drawSquare(g, startX + (preview.x(i) - minX) * cell,
                    startY + (preview.y(i) - minY) * cell, cell, nextShape);
    }

    // 나중에 키 설정 기능이 생기면 입력 처리와 안내 문구를 함께 관리해도 괜찮을 것 같음
    private void drawControls(Graphics2D g, double size) {
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .43)));
        g.setColor(Style.MUTED_TEXT);
        g.drawString("CONTROLS", (int) (size * .6), (int) (size * 15.17));
        int left = (int) (size * .55);
        int keyWidth = (int) (size * 1.8);
        int keyHeight = (int) (size * .78);
        for (int i = 0; i < controls.length; i++) {
            int y = (int) (size * (15.82 + i * 1.15));
            // 실제 키 반전 처리는 Board에서 하고 여기서는 바뀐 조작 방법만 표시
            boolean reversed = i == 0 && level == 5;
            String label = reversed ? "좌우 반전" : controls[i][1];
            Color accent = reversed ? Style.GOLD : Style.TEXT;
            // 키 안내는 작은 직각 표식으로 표시하고 실제 입력 처리는 Board가 담당
            g.setColor(Style.PANEL);
            g.fillRect(left, y, keyWidth, keyHeight);
            g.setColor(Style.BORDER);
            g.drawRect(left, y, keyWidth, keyHeight);
            fitFont(g, Style.MONO_FONT, controls[i][0], size * .41, keyWidth - 6);
            int keyBaseline = y + (keyHeight - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
            drawCentered(g, controls[i][0], left + keyWidth / 2, keyBaseline, accent);
            int labelRight = getWidth() - left;
            fitFont(g, Style.BODY_FONT, label, size * .43, labelRight - left - keyWidth - (int) (size * .35));
            g.setColor(reversed ? Style.GOLD : Style.MUTED_TEXT);
            int baseline = y + (keyHeight - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
            g.drawString(label, labelRight - g.getFontMetrics().stringWidth(label), baseline);
        }
    }

    // 현재 폰트의 실제 글자 폭을 사용해 큰 점수와 긴 시간도 점수판 안에 표시
    private void fitFont(Graphics2D g, Font font, String text, double preferredSize, int maxWidth) {
        float fontSize = (float) preferredSize;
        g.setFont(font.deriveFont(fontSize));
        int width = g.getFontMetrics().stringWidth(text);
        if (width > Math.max(1, maxWidth))
            g.setFont(font.deriveFont(Math.max(1f, fontSize * Math.max(1, maxWidth) / width)));
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int baselineY, Color color) {
        FontMetrics fm = g.getFontMetrics();
        g.setColor(color);
        g.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
    }
}
