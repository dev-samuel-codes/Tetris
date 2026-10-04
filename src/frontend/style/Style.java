package frontend.style;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;

// 메뉴와 게임 화면에서 사용하는 공통 폰트와 색상
// 나중에 스타일이 더 늘어나면 폰트·색상·버튼을 각각 나눠도 괜찮을 것 같음
public final class Style {

    public static final Color BACKGROUND = new Color(15, 17, 27);
    public static final Color PANEL = new Color(27, 30, 46);
    public static final Color TEXT = new Color(233, 230, 247);
    public static final Color MUTED_TEXT = new Color(151, 158, 190);
    public static final Color ACCENT = new Color(151, 126, 255);
    public static final Color PINK = new Color(239, 83, 133);
    public static final Color GREEN = new Color(91, 213, 139);
    public static final Color CLASSIC_BUTTON = new Color(34, 33, 54);
    public static final Color ITEM_BUTTON = new Color(29, 52, 43);
    public static final Color MULTIPLAYER_BUTTON = new Color(53, 31, 50);
    public static final Color BOARD_BACKGROUND = new Color(22, 26, 40);
    public static final Color BOARD_GRID = new Color(39, 44, 62);
    public static final Color BOARD_BORDER = new Color(112, 105, 159);
    public static final Color OVERLAY = new Color(9, 10, 17, 210);

    public static final Font TITLE_FONT =
            new Font("Malgun Gothic", Font.BOLD, 40);

    public static final Font BUTTON_FONT =
            new Font("Malgun Gothic", Font.BOLD, 18);

    public static final Font ROOM_TITLE_FONT =
            new Font("Malgun Gothic", Font.BOLD, 30);

    public static final Font SCORE_FONT =
            new Font("Malgun Gothic", Font.BOLD, 18);

    public static final Font BODY_FONT =
            new Font("Malgun Gothic", Font.PLAIN, 13);

    private Style() {
    }

    // 운영체제 기본 버튼 모양 대신 같은 색상과 테두리 사용
    public static void applyButtonStyle(AbstractButton button, Color accent, Color background) {
        button.setUI(new BasicButtonUI() {
            @Override
            protected void paintText(Graphics g, AbstractButton target, Rectangle textRect, String text) {
                if (target.isEnabled()) {
                    super.paintText(g, target, textRect, text);
                } else {
                    // 이전 페이지처럼 비활성화된 버튼도 어두운 배경에서 읽히도록 표시
                    g.setColor(MUTED_TEXT);
                    BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, target.getDisplayedMnemonicIndex(),
                            textRect.x, textRect.y + g.getFontMetrics().getAscent());
                }
            }
        });
        button.setFont(BUTTON_FONT);
        button.setForeground(TEXT);
        button.setBackground(background);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 4, 1, 1, accent),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        // 마우스를 올리거나 설정 버튼을 선택하면 배경색 변경
        button.addChangeListener(e -> updateButtonBackground(button, accent, background));
        button.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                updateButtonBackground(button, accent, background);
            }

            @Override
            public void focusLost(FocusEvent event) {
                updateButtonBackground(button, accent, background);
            }
        });
    }

    private static void updateButtonBackground(AbstractButton button, Color accent, Color background) {
        button.setBackground(button.isSelected() ? accent.darker()
                : button.getModel().isRollover() || button.hasFocus() ? background.brighter() : background);
    }
}
