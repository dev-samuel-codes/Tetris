package frontend.engine;

import frontend.Tetrominoes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ItemModeEngineTest {
    public static void main(String[] args) throws ReflectiveOperationException {
        GameEngine engine = new GameEngine();
        engine.setItemMode(true);

        if (!engine.activateRandomItemForClearedLines(3)) {
            throw new AssertionError("3줄을 지울 때 아이템이 발동해야 합니다.");
        }

        if (engine.getLastItemName() == null || engine.getLastItemName().trim().isEmpty()) {
            throw new AssertionError("발동된 아이템 이름이 비어 있으면 안 됩니다.");
        }

        engine.triggerBombItem();
        engine.start();
        if (engine.getCurrentShape() != Tetrominoes.BombShape) {
            throw new AssertionError("폭탄 아이템은 폭탄 블록으로 등장해야 합니다.");
        }

        engine.setItemMode(false);
        if (engine.activateRandomItemForClearedLines(3)) {
            throw new AssertionError("클래식 모드에서는 아이템이 발동하면 안 됩니다.");
        }

        testBottomClearItem();

        System.out.println("ItemModeEngineTest passed");
    }

    private static void testBottomClearItem() throws ReflectiveOperationException {
        GameEngine engine = new GameEngine();
        Field boardField = GameEngine.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Tetrominoes[] board = (Tetrominoes[]) boardField.get(engine);

        // 하단과 상단에 블록을 심어 제거 좌표가 뒤바뀌는 경우를 확인
        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            board[x] = Tetrominoes.LineShape;
            board[GameEngine.BOARD_WIDTH + x] = Tetrominoes.LineShape;
            board[(GameEngine.BOARD_HEIGHT - 2) * GameEngine.BOARD_WIDTH + x] = Tetrominoes.SquareShape;
            board[(GameEngine.BOARD_HEIGHT - 1) * GameEngine.BOARD_WIDTH + x] = Tetrominoes.SquareShape;
        }

        Method applyItem = GameEngine.class.getDeclaredMethod("applyItem", GameEngine.ItemType.class);
        applyItem.setAccessible(true);
        applyItem.invoke(engine, GameEngine.ItemType.BOTTOM_CLEAR);

        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            if (engine.shapeAt(x, 0) != Tetrominoes.NoShape
                    || engine.shapeAt(x, 1) != Tetrominoes.NoShape) {
                throw new AssertionError("하단 제거 아이템은 y=0,1의 블록을 제거해야 합니다.");
            }
            if (engine.shapeAt(x, GameEngine.BOARD_HEIGHT - 2) != Tetrominoes.SquareShape
                    || engine.shapeAt(x, GameEngine.BOARD_HEIGHT - 1) != Tetrominoes.SquareShape) {
                throw new AssertionError("하단 제거 아이템은 상단 y=20,21의 블록을 유지해야 합니다.");
            }
        }
    }
}
