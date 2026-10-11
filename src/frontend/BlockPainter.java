package frontend;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import frontend.engine.GameEngine.ItemType;
import frontend.style.Style;

// 테트리스 칸 하나를 그리는 도구 클래스. Board,블록 미리보기에서 사용
public final class BlockPainter {

    // Tetrominoes 순서에 맞춘 색상표. 첫 번째는 빈 칸이고 나머지는 블록 종류별 색상
    private static final Color[] COLORS = { Color.BLACK, new Color(0xC8736C), new Color(0x86A66F),
            new Color(0x7395C5), new Color(0xC5B468), new Color(0xA986BB),
            new Color(0x6CA9A5), new Color(0xC9975E), new Color(0xFF6B57) };

    private BlockPainter() {
    }

    public static void drawSquare(Graphics g, int x, int y, int size, Tetrominoes shape) {
        drawSquare(g, x, y, size, shape, false);
    }

    public static void drawSquare(Graphics g, int x, int y, int size, Tetrominoes shape, boolean itemCell) {
        drawSquare(g, x, y, size, shape, itemCell, null);
    }

    public static void drawSquare(Graphics g, int x, int y, int size, Tetrominoes shape, ItemType itemType) {
        drawSquare(g, x, y, size, shape, itemType != null, itemType);
    }

    private static void drawSquare(Graphics g, int x, int y, int size, Tetrominoes shape,
            boolean itemCell, ItemType itemType) {
        if (size <= 0 || shape == Tetrominoes.NoShape)
            return;

        int index = shape.ordinal();
        if (index < 0 || index >= COLORS.length) {
            return;
        }

        Color color = COLORS[index];
        int inset = size > 1 ? 1 : 0;
        int blockSize = Math.max(1, size - inset);

        Graphics2D graphics = (Graphics2D) g.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setColor(color);
            graphics.fillRect(x + inset, y + inset, blockSize, blockSize);
            if (blockSize > 4) {
                graphics.setColor(new Color(255, 255, 255, 40));
                graphics.drawLine(x + inset + 2, y + inset + 1,
                        x + inset + blockSize - 3, y + inset + 1);
                graphics.setColor(new Color(15, 20, 25, 54));
                graphics.fillRect(x + inset + 1, y + inset + blockSize - 3, blockSize - 2, 2);
            }
            graphics.setColor(new Color(15, 20, 25, 45));
            graphics.drawRect(x + inset, y + inset, blockSize - 1, blockSize - 1);

            if (itemCell && blockSize > 3) {
                if (itemType != null) {
                    // 블록 색은 가장자리에 남기고, 그림 뒤를 어둡게 해 작은 칸에서도 구분
                    graphics.setColor(Style.BOARD_BACKGROUND);
                    graphics.fillRect(x + inset + 1, y + inset + 1, blockSize - 2, blockSize - 2);
                    graphics.setColor(Style.ACCENT);
                    graphics.drawRect(x + inset, y + inset, blockSize - 1, blockSize - 1);
                    if (ItemSprites.draw(graphics, itemType, x + inset + 2, y + inset + 2,
                            blockSize - 4, blockSize - 4))
                        return;
                }
                // 리소스가 빠진 실행 환경에서도 아이템 위치는 기존 표시로 확인 가능
                int dotSize = Math.max(3, Math.min(blockSize - 2, blockSize / 3));
                int dotX = x + inset + (blockSize - dotSize) / 2;
                int dotY = y + inset + (blockSize - dotSize) / 2;
                graphics.setColor(new Color(0xFF, 0xF3, 0x9C));
                graphics.fillOval(dotX, dotY, dotSize, dotSize);
                graphics.setColor(new Color(0xFF, 0xD1, 0x3D));
                graphics.drawOval(dotX, dotY, dotSize - 1, dotSize - 1);
            }
        } finally {
            graphics.dispose();
        }
    }
}
