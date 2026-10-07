package frontend.ui;

import frontend.style.Style;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

// 5×7 픽셀 문자로 TETRIS를 표시하는 공통 로고
// 메뉴와 게임 헤더에서 같은 비트맵을 사용하고 셀 크기만 조절
// 나중에 다른 로고가 필요하면 문자 비트맵과 색상 정보를 나눠도 괜찮을 것 같음
public final class PixelLogo implements Icon {
    private static final String[][] LETTERS = {
        { "11111", "00100", "00100", "00100", "00100", "00100", "00100" },
        { "11111", "10000", "10000", "11110", "10000", "10000", "11111" },
        { "11111", "00100", "00100", "00100", "00100", "00100", "00100" },
        { "11110", "10001", "10001", "11110", "10100", "10010", "10001" },
        { "11111", "00100", "00100", "00100", "00100", "00100", "11111" },
        { "01111", "10000", "10000", "01110", "00001", "00001", "11110" }
    };
    private final int cell;

    public PixelLogo(int cell) {
        if (cell < 2)
            throw new IllegalArgumentException("픽셀 로고의 셀 크기는 2 이상이어야 합니다.");
        this.cell = cell;
    }

    @Override public int getIconWidth() { return (LETTERS.length * 6 - 1) * cell; }
    @Override public int getIconHeight() { return 7 * cell; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Color[] colors = { Style.ACCENT, Style.GOLD, Style.GREEN, Style.PINK, Style.GOLD, Style.ACCENT };
        Graphics g = graphics.create();
        try {
            for (int letter = 0; letter < LETTERS.length; letter++) {
                for (int row = 0; row < 7; row++) {
                    for (int col = 0; col < 5; col++) {
                        if (LETTERS[letter][row].charAt(col) != '1') continue;
                        int bx = x + (letter * 6 + col) * cell;
                        int by = y + row * cell;
                        // 사각 점 사이에 검은 한 픽셀 틈을 두고 번짐이나 점멸 효과는 사용하지 않음
                        g.setColor(Color.BLACK);
                        g.fillRect(bx, by, cell, cell);
                        g.setColor(colors[letter]);
                        g.fillRect(bx, by, cell - 1, cell - 1);
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }
}
