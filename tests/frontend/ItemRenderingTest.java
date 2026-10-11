package frontend;

import frontend.engine.GameEngine;
import frontend.engine.GameEngine.ItemType;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/** 실제 PNG, 셀 렌더링과 엔진에서 패널까지의 타입 전달을 검증합니다. */
public final class ItemRenderingTest {
    private static final ItemType[] ITEMS = { ItemType.ROW_CLEAR, ItemType.COLUMN_CLEAR,
            ItemType.BOMB_CLEAR, ItemType.ROTATION_LOCK, ItemType.SCORE_BOOST };
    private static final String[] FILES = { "row-clear", "column-clear", "bomb-clear",
            "rotation-lock", "score-boost" };

    private ItemRenderingTest() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("tetris.data.dir", Files.createTempDirectory("tetris-item-rendering-").toString());
        // Board의 점수 관리자 초기화에서 실제 네트워크 클라이언트를 만들지 않습니다.
        System.setProperty("tetris.server.host", "127.0.0.1");
        System.setProperty("tetris.server.port", "0");
        verifyPackagedImages();
        verifyCellRendering();
        SwingUtilities.invokeAndWait(() -> {
            try {
                verifyPanelRendering();
                for (ItemType type : ITEMS)
                    verifyItemLifecycle(type);
                verifyClassicEngine();
                if (args.length > 0) {
                    writeSnapshot(Paths.get(args[0]));
                    writePanelSnapshots(Paths.get(args[0]));
                }
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        System.out.println("ItemRenderingTest: PNG 투명도·셀 경계·종류 구분·착지 및 패널 전달·클래식 회귀 통과");
    }

    private static void verifyPackagedImages() throws Exception {
        for (String file : FILES) {
            String path = "/frontend/images/items/" + file + ".png";
            URL resource = ItemRenderingTest.class.getResource(path);
            require(resource != null, "클래스패스에 아이템 리소스가 없습니다: " + path);
            BufferedImage image = ImageIO.read(resource);
            require(image != null && image.getWidth() > 0 && image.getHeight() > 0,
                    "아이템 PNG를 읽을 수 없습니다: " + path);
            require(image.getColorModel().hasAlpha(), "PNG에 알파 채널이 없습니다: " + path);
            boolean transparent = false;
            boolean visible = false;
            for (int pixel : pixels(image)) {
                int alpha = pixel >>> 24;
                transparent |= alpha == 0;
                visible |= alpha > 0;
            }
            require(transparent && visible, "PNG에 실제 투명 배경과 보이는 도형이 있어야 합니다: " + path);
        }
    }

    private static void verifyCellRendering() {
        for (int size : new int[] { 20, 32, 48 }) {
            int[][] spritePixels = new int[ITEMS.length][];
            int[][] cellPixels = new int[ITEMS.length][];
            for (int i = 0; i < ITEMS.length; i++) {
                BufferedImage sprite = canvas(size);
                Graphics2D graphics = sprite.createGraphics();
                try {
                    require(ItemSprites.draw(graphics, ITEMS[i], 7, 9, size, size),
                            "정상 아이템 그리기가 실패했습니다: " + ITEMS[i] + "/" + size);
                } finally {
                    graphics.dispose();
                }
                assertPaintInside(sprite, 7, 9, size, size, "스프라이트 " + ITEMS[i]);
                spritePixels[i] = pixels(sprite);
                BufferedImage cell = drawCell(size, ITEMS[i]);
                assertPaintInside(cell, 7, 9, size, size, "아이템 셀 " + ITEMS[i]);
                cellPixels[i] = pixels(cell);
                require(!Arrays.equals(cellPixels[i], pixels(drawCell(size, null))),
                        "아이템 셀이 일반 셀과 같습니다: " + ITEMS[i] + "/" + size);
            }
            assertDistinct(spritePixels, "스프라이트", size);
            assertDistinct(cellPixels, "아이템 셀", size);

            BufferedImage classic = canvas(size);
            Graphics2D graphics = classic.createGraphics();
            try {
                BlockPainter.drawSquare(graphics, 7, 9, size, Tetrominoes.TShape);
            } finally {
                graphics.dispose();
            }
            require(Arrays.equals(pixels(classic), pixels(drawCell(size, null))),
                    "타입이 없는 셀에 아이템 표시가 추가되었습니다.");
        }

        BufferedImage invalid = canvas(48);
        Graphics2D graphics = invalid.createGraphics();
        try {
            require(!ItemSprites.draw(graphics, null, 7, 9, 32, 32), "null 타입은 그리면 안 됩니다.");
            require(!ItemSprites.draw(graphics, ItemType.BOTTOM_CLEAR, 7, 9, 32, 32),
                    "비활성 하단 제거 아이템은 그리면 안 됩니다.");
            require(!ItemSprites.draw(graphics, ITEMS[0], 7, 9, 0, 32), "너비 0은 거부해야 합니다.");
            require(!ItemSprites.draw(graphics, ITEMS[0], 7, 9, 32, -1), "음수 높이는 거부해야 합니다.");
        } finally {
            graphics.dispose();
        }
        for (int pixel : pixels(invalid))
            require(pixel == 0, "거부된 그리기가 이미지를 변경했습니다.");
    }

    private static BufferedImage drawCell(int size, ItemType type) {
        BufferedImage image = canvas(size);
        Graphics2D graphics = image.createGraphics();
        try {
            BlockPainter.drawSquare(graphics, 7, 9, size, Tetrominoes.TShape, type);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void verifyPanelRendering() {
        SidePanel panel = new SidePanel();
        int[] classic = pixels(paint(panel, 192, 704));
        for (ItemType type : ITEMS) {
            panel.setItemStatus(type);
            require(Arrays.equals(classic, pixels(paint(panel, 192, 704))),
                    "클래식 패널에 아이템 상태가 표시되었습니다: " + type);
        }
        panel.setItemMode(true);
        int[][] itemPanels = new int[ITEMS.length][];
        for (int i = 0; i < ITEMS.length; i++) {
            panel.setItemStatus(ITEMS[i]);
            itemPanels[i] = pixels(paint(panel, 192, 704));
            require(!Arrays.equals(classic, itemPanels[i]), "아이템 패널이 클래식 패널과 같습니다.");
        }
        assertDistinct(itemPanels, "아이템 패널", 32);
    }

    private static void verifyItemLifecycle(ItemType type) throws Exception {
        RecordingPanel panel = new RecordingPanel();
        Board board = new Board(new JLabel(), panel, true);
        try {
            GameEngine engine = board.getEngine();
            engine.start();
            preparePiece(engine, type);
            int beforeX = engine.getPieceX(2);
            int beforeY = engine.getPieceY(2);
            engine.rotateRight();
            require(engine.getCurrentItemType() == type && engine.getCurrentItemCellIndex() == 2,
                    "회전 중 아이템 종류 또는 셀 번호가 소실되었습니다: " + type);
            require(beforeX != engine.getPieceX(2) || beforeY != engine.getPieceY(2),
                    "회전 테스트에서 실제 블록 좌표가 바뀌지 않았습니다.");
            int itemX = engine.getCurrentX() + engine.getPieceX(2);
            int itemY = engine.getGhostY() - engine.getPieceY(2);
            engine.dropDown();
            require(engine.getItemTypeAt(itemX, itemY) == type,
                    "착지한 아이템 셀의 종류가 유지되지 않았습니다: " + type);
            require(engine.shapeAt(itemX, itemY) == Tetrominoes.TShape,
                    "착지한 아이템 셀의 미노 종류가 변경되었습니다.");

            Tetrominoes[] cells = (Tetrominoes[]) get(GameEngine.class, engine, "board");
            for (int x = 0; x < GameEngine.BOARD_WIDTH; x++)
                cells[itemY * GameEngine.BOARD_WIDTH + x] = Tetrominoes.TShape;
            invoke(GameEngine.class, engine, "removeFullLines");
            require(engine.getLastItemType() == type, "줄 제거 후 마지막 아이템 타입 누락: " + type);
            for (int y = 0; y < GameEngine.BOARD_HEIGHT; y++)
                for (int x = 0; x < GameEngine.BOARD_WIDTH; x++)
                    require(engine.getItemTypeAt(x, y) == null, "제거한 아이템 표식이 남았습니다.");
            invoke(Board.class, board, "updateView");
            require(panel.lastType == type, "Board에서 SidePanel로 아이템 종류가 전달되지 않았습니다: " + type);
        } finally {
            board.stop();
        }
    }

    private static void preparePiece(GameEngine engine, ItemType type) throws Exception {
        Shape piece = (Shape) get(GameEngine.class, engine, "curPiece");
        piece.setShape(Tetrominoes.TShape);
        piece.setItemCell(2);
        set(GameEngine.class, engine, "currentItemType", type);
        set(GameEngine.class, engine, "curX", 4);
        set(GameEngine.class, engine, "curY", 10);
    }

    private static void verifyClassicEngine() {
        GameEngine engine = new GameEngine(17L);
        engine.start();
        for (int i = 0; i < 10; i++) {
            require(engine.getCurrentItemType() == null && !engine.isCurrentPieceItem(),
                    "클래식 블록에 아이템이 생성되었습니다.");
            engine.dropDown();
        }
        require(engine.getLastItemType() == null, "클래식에 마지막 아이템 상태가 생겼습니다.");
        for (int y = 0; y < GameEngine.BOARD_HEIGHT; y++)
            for (int x = 0; x < GameEngine.BOARD_WIDTH; x++)
                require(engine.getItemTypeAt(x, y) == null, "클래식 고정 셀에 아이템 표시가 생겼습니다.");
        require(engine.getItemTypeAt(-1, 0) == null && engine.getItemTypeAt(10, 22) == null,
                "보드 밖 아이템 조회는 null이어야 합니다.");
    }

    private static void writeSnapshot(Path directory) throws Exception {
        Files.createDirectories(directory);
        SidePanel panel = new SidePanel();
        Board board = new Board(new JLabel(), panel, true);
        try {
            GameEngine engine = board.getEngine();
            engine.start();
            preparePiece(engine, ItemType.BOMB_CLEAR);
            Tetrominoes[] cells = (Tetrominoes[]) get(GameEngine.class, engine, "board");
            ItemType[] types = (ItemType[]) get(GameEngine.class, engine, "boardItemTypes");
            for (int i = 0; i < ITEMS.length; i++) {
                int index = GameEngine.BOARD_WIDTH + 2 * i;
                cells[index] = Tetrominoes.TShape;
                types[index] = ITEMS[i];
            }
            invoke(Board.class, board, "updateView");
            panel.setItemStatus(ItemType.SCORE_BOOST);
            BufferedImage image = new BufferedImage(512, 704, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.drawImage(paint(board, 320, 704), 0, 0, null);
                graphics.drawImage(paint(panel, 192, 704), 320, 0, null);
            } finally {
                graphics.dispose();
            }
            ImageIO.write(image, "png", directory.resolve("item-rendering.png").toFile());
        } finally {
            board.stop();
        }
    }

    private static void writePanelSnapshots(Path directory) throws Exception {
        BufferedImage panels = new BufferedImage(120 * (ITEMS.length + 1), 440,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = panels.createGraphics();
        try {
            for (int i = 0; i <= ITEMS.length; i++) {
                SidePanel panel = snapshotPanel(i == 0 ? null : ITEMS[i - 1]);
                graphics.drawImage(paint(panel, 120, 440), i * 120, 0, null);
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(panels, "png", directory.resolve("item-panels.png").toFile());
        ImageIO.write(paint(snapshotPanel(null), 192, 704), "png",
                directory.resolve("item-ready.png").toFile());
    }

    private static SidePanel snapshotPanel(ItemType type) {
        SidePanel panel = new SidePanel();
        panel.setItemMode(true);
        panel.setScore(123);
        panel.setLevel(3);
        panel.setLinesRemoved(12);
        panel.setElapsedSeconds(65);
        panel.setNextShape(Tetrominoes.TShape);
        panel.setItemStatus(type);
        return panel;
    }

    private static BufferedImage paint(JComponent component, int width, int height) {
        component.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage canvas(int size) {
        return new BufferedImage(size + 20, size + 22, BufferedImage.TYPE_INT_ARGB);
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static void assertPaintInside(BufferedImage image, int left, int top, int width, int height,
            String label) {
        boolean painted = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0)
                    continue;
                painted = true;
                require(x >= left && x < left + width && y >= top && y < top + height,
                        label + " 그리기가 요청 영역을 벗어났습니다.");
            }
        }
        require(painted, label + "에 보이는 픽셀이 없습니다.");
    }

    private static void assertDistinct(int[][] images, String label, int size) {
        for (int i = 0; i < images.length; i++)
            for (int j = 0; j < i; j++)
                require(!Arrays.equals(images[i], images[j]),
                        label + " 종류를 구분할 수 없습니다: " + ITEMS[j] + "/" + ITEMS[i] + "/" + size);
    }

    private static Object get(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invoke(Class<?> owner, Object target, String name) throws Exception {
        Method method = owner.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private static final class RecordingPanel extends SidePanel {
        private ItemType lastType;

        @Override
        public void setItemStatus(ItemType type) {
            lastType = type;
            super.setItemStatus(type);
        }
    }
}
