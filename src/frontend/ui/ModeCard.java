package frontend.ui;

import frontend.style.Style;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;

// 아이콘·제목·설명·행동을 같은 위치에 표시하는 실제 메뉴 버튼
// 나중에 모드별 안내가 늘어나면 설명 데이터와 그리기를 나눠도 괜찮을 것 같음
public final class ModeCard extends JButton {
    private final String title;
    private final String[] description;
    private final String action;
    private final String disabledAction;
    private final Color surface;
    private final Color ink;
    private final Color block;
    private final int shape;

    public ModeCard(String title, String[] description, String action, String disabledAction,
                    Color surface, Color ink, Color block, int shape) {
        super(title);
        this.title = title;
        this.description = description.clone();
        this.action = action;
        this.disabledAction = disabledAction;
        this.surface = surface;
        this.ink = ink;
        this.block = block;
        this.shape = shape;
        setUI(new BasicButtonUI());
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorder(BorderFactory.createEmptyBorder());
        setRolloverEnabled(true);
        setFont(Style.BUTTON_FONT);
        setForeground(ink);
        setBackground(surface);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(260, 260));
        setMinimumSize(new Dimension(200, 210));
        // 버튼 모델과 기본 Enter 동작은 그대로 사용하고 그림만 카드 형태로 표시
        getModel().addChangeListener(e -> repaint());
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { repaint(); }
            @Override public void focusLost(FocusEvent e) { repaint(); }
        });
        getAccessibleContext().setAccessibleName(title);
        updateAccessibleDescription();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (title != null)
            updateAccessibleDescription();
    }

    private void updateAccessibleDescription() {
        StringBuilder text = new StringBuilder();
        for (String line : description) text.append(line).append(". ");
        text.append(isEnabled() ? action : disabledAction);
        getAccessibleContext().setAccessibleDescription(text.toString());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean active = isEnabled();
            boolean hover = active && getModel().isRollover();
            boolean pressed = active && getModel().isArmed() && getModel().isPressed();
            Color fill = pressed ? mix(surface, ink, .06f) : hover ? mix(surface, ink, .025f) : surface;
            Color text = active ? ink : mix(ink, surface, .30f);
            g.setColor(fill);
            g.fillRect(1, 1, getWidth() - 2, getHeight() - 2);
            // 마우스 위치와 키보드 포커스는 직각 테두리와 픽셀 커서로 표시하고 점멸 타이머는 사용하지 않음
            g.setColor(hasFocus() && active ? Style.ACCENT : hover ? mix(Style.BORDER, Style.ACCENT, .6f) : Style.BORDER);
            g.setStroke(new BasicStroke(hasFocus() && active ? 2f : 1f));
            g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);

            // 낮은 카드에서는 블록과 제목을 한 줄로 배치해 설명·행동 영역과 겹치지 않게 표시
            // 기본 크기에서는 기존 세로 배치와 여백을 그대로 사용
            boolean compact = getHeight() <= 210;
            int inset = compact ? 18 : 24;
            int unit = compact ? (getHeight() <= 145 ? 14 : 18) : getHeight() < 235 ? 23 : 28;
            int blockY = (compact ? 14 : 26) + (pressed ? 1 : 0);
            drawBlocks(g, inset, blockY, unit, active ? block : mix(block, surface, .24f));
            Font titleFont = Style.BUTTON_FONT.deriveFont(Font.BOLD, compact ? 21f : 25f);
            g.setFont(titleFont);
            g.setColor(text);
            int titleY;
            int lineY;
            if (compact) {
                FontMetrics titleMetrics = g.getFontMetrics();
                titleY = blockY + unit + (titleMetrics.getAscent() - titleMetrics.getDescent()) / 2;
                int titleX = inset + unit * 3 + 12;
                drawFitted(g, title, titleX, titleY, getWidth() - titleX - inset);
                lineY = blockY + unit * 2 + 21;
            } else {
                titleY = blockY + unit * 2 + 39;
                g.drawString(title, inset, titleY);
                lineY = titleY + 28;
            }
            if (active && (hover || hasFocus())) {
                g.setColor(Style.ACCENT);
                int cursorY = titleY - g.getFontMetrics(titleFont).getAscent() / 2 - 4;
                int[] widths = { 2, 4, 6, 8, 6, 4, 2 };
                for (int i = 0; i < widths.length; i++)
                    g.fillRect(6, cursorY + i * 2, widths[i], 2);
            }
            // 작은 번호는 모드 순서를 표시하고 선택 상태를 대신하지 않음
            g.setFont(Style.MONO_FONT.deriveFont(12f));
            g.setColor(Style.MUTED_TEXT);
            String number = String.format("%02d", shape + 1);
            g.drawString(number, getWidth() - inset - g.getFontMetrics().stringWidth(number), compact ? 14 : 38);
            g.setFont(Style.BODY_FONT.deriveFont(13f));
            g.setColor(active ? mix(ink, surface, .25f) : mix(ink, surface, .38f));
            for (String line : description) {
                drawFitted(g, line, inset, lineY, getWidth() - inset * 2);
                lineY += compact ? 18 : 20;
            }

            int actionY = getHeight() - (compact ? 16 : 25);
            g.setColor(text);
            g.setFont(Style.BUTTON_FONT.deriveFont(compact ? 13f : 14f));
            String caption = active ? action : disabledAction;
            drawFitted(g, caption, inset, actionY, getWidth() - inset * 2 - 30);
            if (active) {
                int x = getWidth() - inset - 8, y = actionY - 5;
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(x - 12, y, x + 2, y);
                g.drawLine(x - 3, y - 5, x + 2, y);
                g.drawLine(x - 3, y + 5, x + 2, y);
            }
        } finally {
            g.dispose();
        }
    }

    // 실제 4칸 블록을 표시하고 한쪽 면만 살짝 어둡게 그려 깊이 표현
    private void drawBlocks(Graphics2D g, int x, int y, int unit, Color color) {
        int[][] cells = shape == 0 ? new int[][] {{0, 0}, {1, 0}, {2, 0}, {1, 1}}
                : shape == 1 ? new int[][] {{1, 0}, {2, 0}, {0, 1}, {1, 1}}
                : new int[][] {{0, 0}, {1, 0}, {1, 1}, {2, 1}};
        for (int[] cell : cells) {
            int bx = x + cell[0] * unit, by = y + cell[1] * unit, size = unit - 3;
            g.setColor(mix(color, Color.BLACK, .18f));
            g.fillRect(bx, by, size, size);
            g.setColor(color);
            g.fillRect(bx, by, size - 3, size - 2);
        }
    }

    // 작은 화면에서도 문구를 생략하지 않고 가용 폭을 실측해 크기 조절
    private void drawFitted(Graphics2D g, String text, int x, int y, int width) {
        Font original = g.getFont();
        Font fitted = original;
        float size = original.getSize2D();
        while (size > 10.5f && g.getFontMetrics(fitted).stringWidth(text) > width) {
            size = Math.max(10.5f, size - .5f);
            fitted = original.deriveFont(size);
        }
        g.setFont(fitted);
        g.drawString(text, x, y);
        g.setFont(original);
    }

    private static Color mix(Color from, Color to, float amount) {
        return new Color(Math.round(from.getRed() * (1 - amount) + to.getRed() * amount),
                Math.round(from.getGreen() * (1 - amount) + to.getGreen() * amount),
                Math.round(from.getBlue() * (1 - amount) + to.getBlue() * amount));
    }
}
