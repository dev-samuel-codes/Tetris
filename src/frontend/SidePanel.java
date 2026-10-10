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

    public static final int COLS = 6;
    private final String[][] controls;

    private int score;
    private int elapsedSeconds;
    private int linesRemoved;
    private int level = 1;
    private Tetrominoes nextShape = Tetrominoes.NoShape;

    // 클래식 모드 기본 조작키를 사용하는 사이드패널 생성
    public SidePanel() {
<<<<<<< HEAD
<<<<<<< Updated upstream
=======
=======
>>>>>>> 05d2c9b34c0dc542bedeb4fadf0a25831f715173
        this(new String[][] {
                { "← →", "이동" },
                { "↑ ↓", "회전" },
                { "D", "한 칸 하강" },
                { "SPACE", "즉시 낙하" },
                { "P", "일시정지" }
        });
    }

<<<<<<< HEAD
    public SidePanel(String[][] controls) {
        this.controls = controls;
>>>>>>> Stashed changes
=======
    // 전달받은 조작키를 표시하는 사이드패널 생성
    public SidePanel(String[][] controls) {
        this.controls = controls;
>>>>>>> 05d2c9b34c0dc542bedeb4fadf0a25831f715173
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

    public void setItemStatus(int linesUntilItem, String itemName, String itemDescription) {
<<<<<<< Updated upstream
        this.linesUntilItem = Math.max(0, linesUntilItem);
        this.itemName = itemName == null || itemName.trim().isEmpty() ? "아이템 준비" : itemName;
        this.itemDescription = itemDescription == null || itemDescription.trim().isEmpty() ? "3줄 누적 시 랜덤 아이템 발동" : itemDescription;
        repaint();
=======
        // 아이템 모드가 제거되었으므로 더 이상 사이드패널에 아이템 상태를 표시하지 않습니다.
>>>>>>> Stashed changes
    }

    public void setItemMode(boolean itemMode) {
        // 레거시 아이템 모드 상태는 더 이상 사용하지 않습니다.
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        double size = Math.min(getWidth() / (double) COLS, getHeight() / 22.0);
        if (size <= 0)
            return;
        Graphics2D graphics = (Graphics2D) g.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawDivider(graphics, size, 4.0);
            drawStats(graphics, size);
            drawNextShape(graphics, size);
<<<<<<< HEAD
<<<<<<< Updated upstream
            drawItemStatus(graphics, size);
            if (!itemMode) {
                drawControls(graphics, size);
            }
=======
            drawDivider(graphics, size, 13.7);
            drawControls(graphics, size);
>>>>>>> Stashed changes
=======
            // 클래식에서는 아이템 상자를 그리지 않아 조작 안내와 겹치지 않게 표시
            if (itemMode)
                drawItemStatus(graphics, size);
            else
                drawDivider(graphics, size, 13.7);
            drawControls(graphics, size);
>>>>>>> 05d2c9b34c0dc542bedeb4fadf0a25831f715173
        } finally {
            graphics.dispose();
        }
    }

    private void drawDivider(Graphics2D g, double size, double top) {
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

        drawStatRow(g, size, "LINES", String.valueOf(linesRemoved), 5.15);
        String time = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
        drawStatRow(g, size, "TIME", time, 6.6);
    }

    private void drawStatRow(Graphics2D g, double size, String label, String value, double baseline) {
        int inset = (int) (size * .6);
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .4)));
        g.setColor(Style.MUTED_TEXT);
        g.drawString(label, inset, (int) (size * baseline));
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
        g.drawString("NEXT", (int) (size * .6), (int) (size * 8.12));
        int cell = Math.max(1, (int) (size * .85));
        int boxLeft = (getWidth() - 4 * cell) / 2;
<<<<<<< HEAD
<<<<<<< Updated upstream
        int boxTop = (int) (size * 9.5);
=======
        int boxTop = (int) (size * 8.5);
>>>>>>> 05d2c9b34c0dc542bedeb4fadf0a25831f715173
        // 다음 블록만 별도의 어두운 영역에 표시해 보드와 같은 색상 대비 사용
=======
        int boxTop = (int) (size * 8.5);
>>>>>>> Stashed changes
        int frameInset = (int) (size * .45);
        int frameHeight = (int) (size * 4.12);
        g.setColor(Style.BOARD_BACKGROUND);
        g.fillRect(frameInset, boxTop, getWidth() - frameInset * 2, frameHeight);
        g.setColor(Style.BORDER);
        g.drawRect(frameInset, boxTop, getWidth() - frameInset * 2 - 1, frameHeight - 1);

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
<<<<<<< HEAD
<<<<<<< Updated upstream
        // 블록이 차지하는 가로·세로 칸 수를 계산해 4×4 영역 중앙에 표시
=======
>>>>>>> Stashed changes
=======
        // 긴 블록도 테두리에 닿지 않게 여백을 두고 미리보기 중앙에 표시
>>>>>>> 05d2c9b34c0dc542bedeb4fadf0a25831f715173
        int startX = boxLeft + (4 - (maxX - minX + 1)) * cell / 2;
        int startY = boxTop + (frameHeight - (maxY - minY + 1) * cell) / 2;
        for (int i = 0; i < 4; i++)
            BlockPainter.drawSquare(g, startX + (preview.x(i) - minX) * cell,
                    startY + (preview.y(i) - minY) * cell, cell, nextShape);
    }

<<<<<<< Updated upstream
    private void drawItemStatus(Graphics2D g, double size) {
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .38)));
        g.setColor(Style.MUTED_TEXT);
        g.drawString("ITEM", (int) (size * .6), (int) (size * 13.35));

        int boxLeft = (int) (size * .55);
        int boxTop = (int) (size * 13.7);
        int boxWidth = getWidth() - boxLeft * 2;
        int boxHeight = (int) (size * 3.2);
        g.setColor(Style.PANEL);
        g.fillRoundRect(boxLeft, boxTop, boxWidth, boxHeight, 6, 6);
        g.setColor(Style.BORDER);
        g.drawRoundRect(boxLeft, boxTop, boxWidth - 1, boxHeight - 1, 6, 6);

        String chargeText = "다음 발동까지 " + linesUntilItem + "줄";
        fitFont(g, Style.MONO_FONT, chargeText, size * .42, boxWidth - 12);
        g.setColor(Style.TEXT);
        g.drawString(chargeText, boxLeft + 6, boxTop + (int) (size * .95));

        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .35)));
        g.setColor(Style.ACCENT);
        fitFont(g, Style.MONO_FONT, itemName, size * .45, boxWidth - 12);
        g.drawString(itemName, boxLeft + 6, boxTop + (int) (size * 1.8));

        g.setColor(Style.MUTED_TEXT);
        fitFont(g, Style.BODY_FONT, itemDescription, size * .38, boxWidth - 12);
        g.drawString(itemDescription, boxLeft + 6, boxTop + (int) (size * 2.56));
    }

    // 나중에 키 설정 기능이 생기면 입력 처리와 안내 문구를 함께 관리해도 괜찮을 것 같음
    private void drawControls(Graphics2D g, double size) {
<<<<<<< HEAD
=======
    private void drawControls(Graphics2D g, double size) {
        double heading = 14.85;
        double firstRow = 15.5;
        double rowGap = 1.12;
>>>>>>> Stashed changes
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .43)));
=======
        // 아이템 모드에서는 안내 간격을 줄여 아이템 상자 아래에도 조작키 표시
        double heading = itemMode ? 17.65 : 14.85;
        double firstRow = itemMode ? 18.1 : 15.5;
        double rowGap = itemMode ? .74 : 1.12;
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * (itemMode ? .36 : .43))));
>>>>>>> 05d2c9b34c0dc542bedeb4fadf0a25831f715173
        g.setColor(Style.MUTED_TEXT);
        g.drawString("CONTROLS", (int) (size * .6), (int) (size * heading));
        int left = (int) (size * .55);
        int keyWidth = (int) (size * 1.8);
<<<<<<< HEAD
        int keyHeight = (int) (size * .78);
<<<<<<< Updated upstream
        for (int i = 0; i < CONTROLS.length; i++) {
            int y = (int) (size * (15.82 + i * 1.15));
            // 실제 키 반전 처리는 Board에서 하고 여기서는 바뀐 조작 방법만 표시
            boolean reversed = i == 0 && level == 5;
            String label = reversed ? "좌우 반전" : CONTROLS[i][1];
            Color accent = reversed ? Style.GOLD : Style.TEXT;
=======
        int keyHeight = (int) (size * (itemMode ? .6 : .78));
        for (int i = 0; i < controls.length; i++) {
            int y = (int) (size * (firstRow + i * rowGap));

            String label = controls[i][1];
            Color accent = Style.TEXT;
>>>>>>> 05d2c9b34c0dc542bedeb4fadf0a25831f715173
            // 키 안내는 작은 직각 표식으로 표시하고 실제 입력 처리는 Board가 담당
=======
        for (int i = 0; i < controls.length; i++) {
            int y = (int) (size * (firstRow + i * rowGap));

            String label = controls[i][1];
            Color accent = Style.TEXT;
>>>>>>> Stashed changes
            g.setColor(Style.PANEL);
            g.fillRect(left, y, keyWidth, keyHeight);
            g.setColor(Style.BORDER);
            g.drawRect(left, y, keyWidth, keyHeight);
<<<<<<< HEAD
<<<<<<< Updated upstream
            fitFont(g, Style.MONO_FONT, CONTROLS[i][0], size * .41, keyWidth - 6);
=======
            fitFont(g, Style.MONO_FONT, controls[i][0], size * .41, keyWidth - 6);
>>>>>>> Stashed changes
=======
            fitFont(g, Style.MONO_FONT, controls[i][0], size * (itemMode ? .36 : .41), keyWidth - 6);
>>>>>>> 05d2c9b34c0dc542bedeb4fadf0a25831f715173
            int keyBaseline = y + (keyHeight - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
            drawCentered(g, controls[i][0], left + keyWidth / 2, keyBaseline, accent);
            int labelRight = getWidth() - left;
<<<<<<< HEAD
            fitFont(g, Style.BODY_FONT, label, size * .43, labelRight - left - keyWidth - (int) (size * .35));
<<<<<<< Updated upstream
            g.setColor(reversed ? Style.GOLD : Style.MUTED_TEXT);
=======
            g.setColor(Style.MUTED_TEXT);
>>>>>>> Stashed changes
=======
            fitFont(g, Style.BODY_FONT, label, size * (itemMode ? .38 : .43), labelRight - left - keyWidth - (int) (size * .35));
            g.setColor(Style.MUTED_TEXT);
>>>>>>> 05d2c9b34c0dc542bedeb4fadf0a25831f715173
            int baseline = y + (keyHeight - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
            g.drawString(label, labelRight - g.getFontMetrics().stringWidth(label), baseline);
        }
    }

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
