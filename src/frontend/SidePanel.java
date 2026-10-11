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
    private int linesUntilItem;
    private String itemName = "";
    private String itemDescription = "";
    private boolean itemMode = false;

    public SidePanel() {
        this(new String[][] {
                { "← →", "이동" },
                { "↑ ↓", "회전" },
                { "D", "한 칸 하강" },
                { "SPACE", "즉시 낙하" },
                { "P", "일시정지" }
        });
    }

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

    public void setItemStatus(int linesUntilItem, String itemName, String itemDescription) {
        this.linesUntilItem = Math.max(0, linesUntilItem);
        this.itemName = itemName == null || itemName.trim().isEmpty() ? "아이템 준비" : itemName;
        this.itemDescription = itemDescription == null || itemDescription.trim().isEmpty() ? "3줄 누적 시 랜덤 아이템 발동" : itemDescription;
        repaint();
    }

    public void setItemMode(boolean itemMode) {
        this.itemMode = itemMode;
        repaint();
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
            drawItemStatus(graphics, size);
            drawControls(graphics, size);
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
        int boxTop = (int) (size * 8.5);
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

        int startX = boxLeft + (4 - (maxX - minX + 1)) * cell / 2;
        int startY = boxTop + (frameHeight - (maxY - minY + 1) * cell) / 2;
        for (int i = 0; i < 4; i++)
            BlockPainter.drawSquare(g, startX + (preview.x(i) - minX) * cell,
                    startY + (preview.y(i) - minY) * cell, cell, nextShape);
    }

    private void drawItemStatus(Graphics2D g, double size) {
        int inset = (int) (size * .6);
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .42)));
        g.setColor(Style.ACCENT);
        g.drawString("ITEM", inset, (int) (size * 13.5));

        int boxX = inset;
        int boxY = (int) (size * 14.0);
        int boxWidth = getWidth() - inset * 2;
        int boxHeight = (int) (size * 4.2);
        g.setColor(new Color(Style.ACCENT.getRed(), Style.ACCENT.getGreen(), Style.ACCENT.getBlue(), 22));
        g.fillRect(boxX, boxY, boxWidth, boxHeight);
        g.setColor(Style.BORDER);
        g.drawRect(boxX, boxY, boxWidth - 1, boxHeight - 1);

        String displayName = itemName == null || itemName.trim().isEmpty() ? "아이템 준비" : itemName;
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .72)));
        g.setColor(Style.TEXT);
        int nameY = boxY + (int) (size * 1.35);
        g.drawString(displayName, boxX + (int) (size * .32), nameY);

        String descriptionText = itemDescription == null || itemDescription.trim().isEmpty()
                ? "3줄 누적 시 랜덤 아이템 발동"
                : itemDescription;
        g.setFont(Style.BODY_FONT.deriveFont((float) (size * .42)));
        g.setColor(Style.MUTED_TEXT);
        int descY = boxY + (int) (size * 2.6);
        g.drawString(descriptionText, boxX + (int) (size * .32), descY);
    }

    private void drawControls(Graphics2D g, double size) {
        if (itemMode) {
            return;
        }

        double heading = 14.85;
        double firstRow = 15.5;
        double rowGap = 1.12;
        g.setFont(Style.MONO_FONT.deriveFont((float) (size * .43)));
        g.setColor(Style.MUTED_TEXT);
        g.drawString("CONTROLS", (int) (size * .6), (int) (size * heading));

        int left = (int) (size * .55);
        int keyWidth = (int) (size * 1.8);
        int keyHeight = (int) (size * .78);
        for (int i = 0; i < controls.length; i++) {
            int y = (int) (size * (firstRow + i * rowGap));
            String label = controls[i][1];
            Color accent = Style.TEXT;

            g.setColor(Style.PANEL);
            g.fillRect(left, y, keyWidth, keyHeight);
            g.setColor(Style.BORDER);
            g.drawRect(left, y, keyWidth, keyHeight);
            fitFont(g, Style.MONO_FONT, controls[i][0], size * .41, keyWidth - 6);
            int keyBaseline = y + (keyHeight - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
            drawCentered(g, controls[i][0], left + keyWidth / 2, keyBaseline, accent);

            int labelRight = getWidth() - left;
            fitFont(g, Style.BODY_FONT, label, size * .43, labelRight - left - keyWidth - (int) (size * .35));
            g.setColor(Style.MUTED_TEXT);
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
