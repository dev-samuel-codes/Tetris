package frontend;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import frontend.style.Style;

// 테트리스 칸 하나를 그리는 도구 클래스. Board,블록 미리보기에서 사용
public final class BlockPainter {

    // 블록 색상
    private static final Color[] COLORS = { Color.BLACK, Style.PINK, Style.GREEN,
            new Color(92, 133, 255), new Color(255, 204, 92), new Color(183, 115, 255),
            new Color(66, 211, 220), new Color(255, 145, 82) };

    // 생성 방지
    private BlockPainter() {
    }

    public static void drawSquare(Graphics g, int x, int y, int size, Tetrominoes shape) {
        if (size <= 0 || shape == Tetrominoes.NoShape)
            return;

        Color color = COLORS[shape.ordinal()];
        int inset = Math.max(1, size / 12);
        int blockSize = Math.max(1, size - inset * 2);
        int arc = Math.max(2, size / 4);

        // 보드와 다음 블록 미리보기에 같은 그라데이션 적용
        Graphics2D graphics = (Graphics2D) g.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setPaint(new GradientPaint(x, y, color.brighter(),
                    x + size, y + size, color.darker()));
            graphics.fillRoundRect(x + inset, y + inset, blockSize, blockSize, arc, arc);
            graphics.setColor(new Color(255, 255, 255, 100));
            graphics.drawLine(x + inset + blockSize / 5, y + inset + 2,
                    x + inset + blockSize * 4 / 5, y + inset + 2);
            graphics.setColor(new Color(0, 0, 0, 65));
            graphics.drawRoundRect(x + inset, y + inset, blockSize, blockSize, arc, arc);
        } finally {
            graphics.dispose();
        }
    }
}
