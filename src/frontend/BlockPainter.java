package frontend;

import java.awt.Color;
import java.awt.Graphics;

// 테트리스 칸 하나를 그리는 도구 클래스. Board,블록 미리보기에서 사용
public final class BlockPainter {

    // 블록 색상
    private static final Color[] COLORS = { new Color(0, 0, 0), new Color(204, 102, 102),
            new Color(102, 204, 102), new Color(102, 102, 204), new Color(204, 204, 102),
            new Color(204, 102, 204), new Color(102, 204, 204), new Color(218, 170, 0) };

    // 생성 방지
    private BlockPainter() {
    }

    public static void drawSquare(Graphics g, int x, int y, int size, Tetrominoes shape) {
        Color color = COLORS[shape.ordinal()];

        g.setColor(color);
        g.fillRect(x + 1, y + 1, size - 2, size - 2);

        g.setColor(color.brighter());
        g.drawLine(x, y + size - 1, x, y);
        g.drawLine(x, y, x + size - 1, y);

        g.setColor(color.darker());
        g.drawLine(x + 1, y + size - 1, x + size - 1, y + size - 1);
        g.drawLine(x + size - 1, y + size - 1, x + size - 1, y + 1);
    }
}