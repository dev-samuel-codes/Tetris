package frontend;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import frontend.style.Style;
import frontend.engine.GameEngine.ItemType;

public class SidePanel extends JPanel {

    public static final int COLS = 6;
    private final String[][] controls;

    private int score;
    private int elapsedSeconds;
    private int linesRemoved;
    private int level = 1;
    private Tetrominoes nextShape = Tetrominoes.NoShape;
    private ItemType lastItemType;
    private static final ItemType[] ITEM_GUIDE = {
            ItemType.ROW_CLEAR, ItemType.COLUMN_CLEAR, ItemType.BOMB_CLEAR,
            ItemType.ROTATION_LOCK, ItemType.SCORE_BOOST
    };
    private static final String[] ITEM_GUIDE_LABELS = { "한 줄", "열", "폭탄", "회전 금지", "점수 업" };
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

    public void setItemStatus(ItemType itemType) {
        lastItemType = itemType;
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
        int boxHeight = (int) (size * 7.4);
        g.setColor(new Color(Style.ACCENT.getRed(), Style.ACCENT.getGreen(), Style.ACCENT.getBlue(), 22));
        g.fillRect(boxX, boxY, boxWidth, boxHeight);
        g.setColor(Style.BORDER);
        g.drawRect(boxX, boxY, boxWidth - 1, boxHeight - 1);

        int padding = (int) (size * .32);
        int maxTextWidth = boxWidth - padding * 2;

        int padding = Math.max(3, (int) (size * .3));
        int contentX = boxX + padding;
        int contentWidth = Math.max(1, boxWidth - padding * 2);
        if (lastItemType == null) {
            drawCenteredText(g, "아이템 준비", Style.BUTTON_FONT, size * .65,
                    contentX, contentWidth, boxY + (int) (size * .95), Style.TEXT);
            drawCenteredText(g, "블록/줄 제거 시 발동", Style.BODY_FONT, size * .46,
                    contentX, contentWidth, boxY + (int) (size * 1.7), Style.MUTED_TEXT);
            // 두 줄로 나눠 작은 창에서도 다섯 아이템을 구분
            for (int i = 0; i < ITEM_GUIDE.length; i++) {
                int columns = i < 3 ? 3 : 2;
                int column = i < 3 ? i : i - 3;
                int slotWidth = contentWidth / columns;
                int slotX = contentX + column * slotWidth;
                int iconSize = Math.max(1, Math.min(slotWidth - 2, (int) (size * 1.3)));
                int iconY = boxY + (int) (size * (i < 3 ? 2.15 : 4.65));
                drawItemIcon(g, ITEM_GUIDE[i], slotX + (slotWidth - iconSize) / 2,
                        iconY, iconSize);
                drawCenteredText(g, ITEM_GUIDE_LABELS[i], Style.BODY_FONT, size * .46,
                        slotX, slotWidth - 1, iconY + iconSize + (int) (size * .6), Style.MUTED_TEXT);
            }
            return;
        }

        int iconSize = Math.max(1, Math.min(contentWidth, (int) (size * 3.35)));
        drawItemIcon(g, lastItemType, contentX + (contentWidth - iconSize) / 2,
                boxY + (int) (size * .45), iconSize);
        drawCenteredText(g, lastItemType.getLabel(), Style.BUTTON_FONT, size * .68,
                contentX, contentWidth, boxY + (int) (size * 4.55), Style.TEXT);
        g.setColor(Style.MUTED_TEXT);
        drawWrappedText(g, lastItemType.getDescription(), contentX,
                boxY + (int) (size * 5.05), contentWidth, (int) (size * 1.95), size * .5);
    }

    private void drawItemIcon(Graphics2D g, ItemType type, int x, int y, int size) {
        if (!ItemSprites.draw(g, type, x, y, size, size)) {
            g.setColor(Style.ACCENT);
            g.drawRect(x, y, size - 1, size - 1);
            drawCenteredText(g, "?", Style.MONO_FONT, size * .65,
                    x, size, y + (int) (size * .75), Style.ACCENT);
        }
    }

    private void drawCenteredText(Graphics2D g, String text, Font font, double fontSize,
            int x, int width, int baseline, Color color) {
        fitFont(g, font, text, fontSize, width);
        g.setColor(color);
        g.drawString(text, x + (width - g.getFontMetrics().stringWidth(text)) / 2, baseline);
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y,
            int width, int height, double preferredSize) {
        float fontSize = (float) preferredSize;
        List<String> lines;
        FontMetrics metrics;
        // 줄바꿈 후 높이도 확인해 긴 설명이 프레임 밖으로 나가지 않도록 조절
        do {
            g.setFont(Style.BODY_FONT.deriveFont(fontSize));
            metrics = g.getFontMetrics();
            lines = wrapText(text, metrics, width);
            if (lines.size() * metrics.getHeight() <= height || fontSize <= 1f)
                break;
            fontSize = Math.max(1f, fontSize - .5f);
        } while (true);
        int baseline = y + metrics.getAscent();
        for (String line : lines) {
            g.drawString(line, x + (width - metrics.stringWidth(line)) / 2, baseline);
            baseline += metrics.getHeight();
        }
    }

    private List<String> wrapText(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        String remaining = text.trim();
        while (!remaining.isEmpty()) {
            int end = 1;
            while (end < remaining.length() && metrics.stringWidth(remaining.substring(0, end + 1)) <= width)
                end++;
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > 0)
                    end = space;
            }
            lines.add(remaining.substring(0, end).trim());
            remaining = remaining.substring(end).trim();
        }
        return lines;
    }

    private void fitFont(Graphics2D g, Font font, String text, double preferredSize, int maxWidth) {
        float fontSize = (float) preferredSize;
        g.setFont(font.deriveFont(fontSize));
        int width = g.getFontMetrics().stringWidth(text);
        if (width > Math.max(1, maxWidth))
            g.setFont(font.deriveFont(Math.max(1f, fontSize * Math.max(1, maxWidth) / width)));
    }
}
