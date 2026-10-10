package frontend.style;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;

// 메뉴와 게임에서 사용하는 색상, 글꼴, 공통 컴포넌트 모양을 한곳에서 관리
// 나중에 테마가 더 늘어나면 색상과 컴포넌트 그리기를 각각 나눠도 괜찮을 것 같음
public final class Style {
    // 고전 게임 화면처럼 어두운 바탕과 따뜻한 글자색, 구분되는 블록 색상 사용
    public static final Color BACKGROUND = new Color(0x14131C);
    public static final Color PANEL = new Color(0x201F29);
    public static final Color TEXT = new Color(0xE9E2C7);
    public static final Color MUTED_TEXT = new Color(0xA09CA6);
    public static final Color ACCENT = new Color(0xD6A65D);
    public static final Color PINK = new Color(0xB97F91);
    public static final Color GREEN = new Color(0x93AC7C);
    public static final Color GOLD = new Color(0xC9B365);
    public static final Color BORDER = new Color(0x45424F);
    public static final Color CLASSIC_BUTTON = PANEL;
    public static final Color ITEM_BUTTON = PANEL;
    public static final Color MULTIPLAYER_BUTTON = PANEL;
    public static final Color BOARD_BACKGROUND = new Color(0x0D0C12);
    // 어두운 보드에서도 빈칸의 경계가 보이도록 모든 게임 화면에 같은 대비 사용
    public static final Color BOARD_GRID = new Color(0x393644);
    public static final Color BOARD_BORDER = new Color(0x5A5565);
    public static final Color OVERLAY = new Color(13, 12, 18, 240);
    // 나중에 밝은 테마나 고대비 모드를 추가하면 이 색상 묶음을 테마별로 나눠도 괜찮을 것 같음

    // 설치된 한글 글꼴을 선택해 macOS와 Windows에서도 같은 계층으로 표시
    private static final Set<String> FONT_FAMILIES = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    public static final String FONT_FAMILY = findFont("Apple SD Gothic Neo", "Noto Sans CJK KR", "Malgun Gothic", Font.DIALOG);
    public static final Font TITLE_FONT = new Font(FONT_FAMILY, Font.BOLD, 32);
    public static final Font DISPLAY_FONT = new Font(Font.MONOSPACED, Font.BOLD, 44);
    public static final Font BUTTON_FONT = new Font(FONT_FAMILY, Font.BOLD, 16);
    public static final Font ROOM_TITLE_FONT = new Font(FONT_FAMILY, Font.BOLD, 28);
    public static final Font SCORE_FONT = new Font(FONT_FAMILY, Font.BOLD, 20);
    public static final Font BODY_FONT = new Font(FONT_FAMILY, Font.PLAIN, 14);
    public static final Font SMALL_FONT = new Font(FONT_FAMILY, Font.PLAIN, 13);
    public static final Font MONO_FONT = new Font(Font.MONOSPACED, Font.BOLD, 20);

    static {
        // HTML 버튼 글자는 별도 비활성 색을 사용하므로 현재 배경에 맞춰 지정
        UIManager.put("textInactiveText", MUTED_TEXT);
    }

    private Style() { }

    private static String findFont(String... candidates) {
        for (String candidate : candidates) {
            if (FONT_FAMILIES.contains(candidate))
                return candidate;
        }
        return Font.DIALOG; // 후보 글꼴이 없는 컴퓨터에서는 Java 기본 글꼴 사용
    }

    // 메뉴와 게임 창에서 같이 쓰는 배경. 장식을 넣지 않고 게임과 메뉴에 집중
    public static JPanel createBackground(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(BACKGROUND);
        return panel;
    }

    // 내용 배치는 전달받은 레이아웃이 맡고, 배경과 직각 테두리만 공통으로 그림
    public static JPanel framedPanel(LayoutManager layout, Color background) {
        JPanel panel = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    g.setColor(getBackground());
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(BORDER);
                    g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                } finally {
                    g.dispose();
                }
            }
        };
        panel.setOpaque(false);
        panel.setBackground(background);
        return panel;
    }

    // 운영체제에 관계없이 같은 모양으로 표시하고 마우스와 키보드 상태를 구분
    public static void applyButtonStyle(AbstractButton button, Color accent, Color background) {
        button.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, JComponent component) {
                AbstractButton target = (AbstractButton) component;
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    boolean active = target.isEnabled();
                    boolean pressed = active && target.getModel().isPressed();
                    boolean hover = active && target.getModel().isRollover();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    // 보조 메뉴는 글자만 표시하고 마우스나 키보드로 가리키면 밑줄 표시
                    if (Boolean.TRUE.equals(target.getClientProperty("textButton"))) {
                        if (active && (hover || target.hasFocus())) {
                            g.setColor(accent);
                            g.drawLine(12, target.getHeight() - 6, target.getWidth() - 13, target.getHeight() - 6);
                        }
                    } else {
                        boolean primary = Boolean.TRUE.equals(target.getClientProperty("primary"));
                        // 선택, 누름, 마우스 올림 상태를 배경색 차이로 구분
                        Color base = primary ? ACCENT : background;
                        Color fill = target.isSelected() ? blend(base, accent, .14f)
                                : pressed ? blend(base, primary ? Color.BLACK : accent, .12f)
                                : hover ? blend(base, primary ? Color.BLACK : accent, .06f) : base;
                        if (!active)
                            fill = blend(background, BACKGROUND, .6f);
                        g.setColor(fill);
                        g.fillRect(1, 1, target.getWidth() - 2, target.getHeight() - 2);
                        // 키보드로 이동한 버튼과 현재 선택한 설정은 테두리도 강조
                        boolean emphasized = active && (target.hasFocus() || target.isSelected());
                        g.setStroke(new BasicStroke(emphasized ? 2f : 1f));
                        g.setColor(emphasized || hover || (primary && active) ? accent : BORDER);
                        g.drawRect(1, 1, target.getWidth() - 3, target.getHeight() - 3);
                    }
                } finally {
                    g.dispose();
                }
                super.paint(graphics, component); // 배경 위의 글자와 아이콘은 Swing에서 배치
            }

            @Override
            protected void paintText(Graphics graphics, AbstractButton target, Rectangle textRect, String text) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    boolean primary = Boolean.TRUE.equals(target.getClientProperty("primary"));
                    g.setColor(!target.isEnabled() ? MUTED_TEXT : primary ? BACKGROUND : target.getForeground());
                    BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, target.getDisplayedMnemonicIndex(),
                            textRect.x, textRect.y + g.getFontMetrics().getAscent());
                } finally {
                    g.dispose();
                }
            }
        });
        button.setFont(BUTTON_FONT);
        button.setForeground(TEXT);
        button.setBackground(background);
        button.setOpaque(false);
        button.setContentAreaFilled(false); // 기본 버튼 배경이 위에서 그린 테두리를 덮지 않도록 함
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        button.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) { button.repaint(); }
            @Override
            public void focusLost(FocusEvent event) { button.repaint(); }
        });
    }

    // 입력 중인 칸은 테두리로 구분하고 드래그한 글자도 읽히도록 표시
    public static void applyTextFieldStyle(JTextField field) {
        field.setFont(BUTTON_FONT);
        field.setForeground(TEXT);
        field.setBackground(BOARD_BACKGROUND);
        field.setCaretColor(ACCENT);
        field.setSelectionColor(ACCENT);
        field.setSelectedTextColor(BACKGROUND);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        field.addFocusListener(new FocusAdapter() {
            private void update(boolean focused) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(focused ? ACCENT : BORDER),
                        BorderFactory.createEmptyBorder(10, 12, 10, 12)));
            }
            @Override
            public void focusGained(FocusEvent event) { update(true); }
            @Override
            public void focusLost(FocusEvent event) { update(false); }
        });
    }

    // amount가 클수록 두 번째 색을 더 많이 섞음. 버튼 상태마다 새 색을 따로 만들지 않도록 사용
    private static Color blend(Color first, Color second, float amount) {
        return new Color((int) (first.getRed() * (1 - amount) + second.getRed() * amount),
                (int) (first.getGreen() * (1 - amount) + second.getGreen() * amount),
                (int) (first.getBlue() * (1 - amount) + second.getBlue() * amount));
    }
}
