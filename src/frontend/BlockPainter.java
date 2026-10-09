package frontend;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

// 테트리스 칸 하나를 그리는 도구 클래스. Board,블록 미리보기에서 사용
public final class BlockPainter {

    // Tetrominoes 순서에 맞춘 색상표. 첫 번째는 빈 칸이고 나머지는 블록 종류별 색상
    // 나중에 블록 색까지 테마별로 바꾸려면 이 색상표를 Style로 옮겨도 괜찮을 것 같음
    private static final Color[] COLORS = { Color.BLACK, new Color(0xC8736C), new Color(0x86A66F),
            new Color(0x7395C5), new Color(0xC5B468), new Color(0xA986BB),
            new Color(0x6CA9A5), new Color(0xC9975E) };

    // 생성 방지
    private BlockPainter() {
    }

    public static void drawSquare(Graphics g, int x, int y, int size, Tetrominoes shape) {
        // 빈 칸이나 아직 크기가 정해지지 않은 화면은 그리지 않음
        if (size <= 0 || shape == Tetrominoes.NoShape)
            return;

        Color color = COLORS[shape.ordinal()];
        int inset = size > 1 ? 1 : 0; // 붙어 있는 블록 사이에 1픽셀 여백 적용
        int blockSize = Math.max(1, size - inset);

        // 보드와 미리보기에 같은 직각 타일 사용. 얇은 윗면과 어두운 아랫면으로 깊이 표시
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
        } finally {
            graphics.dispose();
        }
    }
}
