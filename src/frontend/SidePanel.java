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
        this.itemDescription = itemDescription == null || itemDescription.trim().isEmpty() ? "아이템은 블록/제거 시 발동" : itemDescription;
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
        if (!itemMode) {
            return;
        }

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

        int padding = (int) (size * .32);
        int maxTextWidth = boxWidth - padding * 2;

        String displayName = itemName == null || itemName.trim().isEmpty() ? "아이템 준비" : itemName;
        fitFont(g, Style.MONO_FONT, displayName, size * .65, maxTextWidth);
        g.setColor(Style.TEXT);
        int nameY = boxY + (int) (size * 1.35);
        g.drawString(displayName, boxX + padding, nameY);

        String descriptionText = itemDescription == null || itemDescription.trim().isEmpty()
                ? "아이템은 블록/제거 시 발동"
                : itemDescription;
        g.setFont(Style.BODY_FONT.deriveFont((float) (size * .32)));
        g.setColor(Style.MUTED_TEXT);
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(descriptionText) <= maxTextWidth) {
            int descY = boxY + (int) (size * 2.6);
            g.drawString(descriptionText, boxX + padding, descY);
        } else {
            int split = descriptionText.length() / 2;
            int spaceIdx = descriptionText.indexOf(' ', split);
            if (spaceIdx == -1) {
                spaceIdx = descriptionText.lastIndexOf(' ', split);
            }
            String line1, line2;
            if (spaceIdx > 0 && spaceIdx < descriptionText.length() - 1) {
                line1 = descriptionText.substring(0, spaceIdx).trim();
                line2 = descriptionText.substring(spaceIdx + 1).trim();
            } else {
                int cut = descriptionText.length() / 2;
                line1 = descriptionText.substring(0, cut);
                line2 = descriptionText.substring(cut);
            }
            int line1Y = boxY + (int) (size * 2.3);
            int line2Y = boxY + (int) (size * 3.3);
            fitFont(g, Style.BODY_FONT, line1, size * .32, maxTextWidth);
            g.drawString(line1, boxX + padding, line1Y);
            fitFont(g, Style.BODY_FONT, line2, size * .32, maxTextWidth);
            g.drawString(line2, boxX + padding, line2Y);
        }
    }

    private void fitFont(Graphics2D g, Font font, String text, double preferredSize, int maxWidth) {
        float fontSize = (float) preferredSize;
        g.setFont(font.deriveFont(fontSize));
        int width = g.getFontMetrics().stringWidth(text);
        if (width > Math.max(1, maxWidth))
            g.setFont(font.deriveFont(Math.max(1f, fontSize * Math.max(1, maxWidth) / width)));
    }
}
