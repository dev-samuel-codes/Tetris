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

        if (engine.getLinesUntilItemActivation() != 3) {
            throw new AssertionError("아이템 초기 누적 목표는 3줄이어야 합니다.");
        }

        if (engine.getLastItemDescription() == null || engine.getLastItemDescription().trim().isEmpty()) {
            throw new AssertionError("발동된 아이템 설명이 비어 있으면 안 됩니다.");
        }

        testCumulativeItemActivation();

        engine.triggerBombItem();
        engine.start();
        if (engine.getCurrentShape() == Tetrominoes.BombShape) {
            throw new AssertionError("폭탄 아이템은 1x1 폭탄 미노로 등장하면 안 됩니다.");
        }

        if (engine.getLastItemName() == null || !engine.getLastItemName().contains("폭탄")) {
            throw new AssertionError("폭탄 아이템은 효과 이름이 기록되어야 합니다.");
        }

        engine.setItemMode(false);
        if (engine.activateRandomItemForClearedLines(3)) {
            throw new AssertionError("클래식 모드에서는 아이템이 발동하면 안 됩니다.");
        }

        testRotationLockItem();
        testFullLineRemovalCleansBoard();
        testBottomClearItem();

        System.out.println("ItemModeEngineTest passed");
    }

    private static void testRotationLockItem() throws ReflectiveOperationException {
        GameEngine engine = new GameEngine();
        engine.setItemMode(true);

        engine.start();

        Method applyItem = GameEngine.class.getDeclaredMethod("applyItem", GameEngine.ItemType.class);
        applyItem.setAccessible(true);
        applyItem.invoke(engine, GameEngine.ItemType.ROTATION_LOCK);

        if (engine.getRotationLockRemaining() != 2) {
            throw new AssertionError("회전 금지 아이템은 다음 2개 블록을 잠가야 합니다.");
        }

        Method newPiece = GameEngine.class.getDeclaredMethod("newPiece");
        newPiece.setAccessible(true);
        newPiece.invoke(engine);

        if (!engine.isRotationLocked()) {
            throw new AssertionError("회전 금지 아이템은 다음 블록 생성 시 회전이 막혀야 합니다.");
        }
    }

    private static void testFullLineRemovalCleansBoard() throws ReflectiveOperationException {
        GameEngine engine = new GameEngine();
        Field boardField = GameEngine.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Tetrominoes[] board = (Tetrominoes[]) boardField.get(engine);

        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            board[(GameEngine.BOARD_HEIGHT - 1) * GameEngine.BOARD_WIDTH + x] = Tetrominoes.LineShape;
        }

        Method removeFullLines = GameEngine.class.getDeclaredMethod("removeFullLines");
        removeFullLines.setAccessible(true);
        removeFullLines.invoke(engine);

        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            if (engine.shapeAt(x, GameEngine.BOARD_HEIGHT - 1) != Tetrominoes.NoShape) {
                throw new AssertionError("줄 삭제 후 마지막 행은 비워야 합니다.");
            }
        }
    }

    private static void testCumulativeItemActivation() {
        GameEngine engine = new GameEngine();
        engine.setItemMode(true);

        if (engine.activateRandomItemForClearedLines(2)) {
            throw new AssertionError("2줄 누적은 아직 아이템 발동 조건이 아닙니다.");
        }

        if (engine.getLinesUntilItemActivation() != 1) {
            throw new AssertionError("2줄 누적 후 남은 줄 수는 1줄이어야 합니다.");
        }

        if (!engine.activateRandomItemForClearedLines(1)) {
            throw new AssertionError("누적 3줄에서 아이템이 발동해야 합니다.");
        }

        if (engine.getLinesUntilItemActivation() != 3) {
            throw new AssertionError("아이템 발동 후 누적 카운트는 다시 3줄로 초기화되어야 합니다.");
        }
    }

    private static void testBottomClearItem() throws ReflectiveOperationException {
        GameEngine engine = new GameEngine();
        Field boardField = GameEngine.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Tetrominoes[] board = (Tetrominoes[]) boardField.get(engine);

        // 하단 2줄 제거는 마지막 2줄을 비우고, 위칸을 아래로 당겨 채워야 합니다.
        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            board[(GameEngine.BOARD_HEIGHT - 3) * GameEngine.BOARD_WIDTH + x] = Tetrominoes.LineShape;
            board[(GameEngine.BOARD_HEIGHT - 2) * GameEngine.BOARD_WIDTH + x] = Tetrominoes.TShape;
            board[(GameEngine.BOARD_HEIGHT - 1) * GameEngine.BOARD_WIDTH + x] = Tetrominoes.SquareShape;
        }

        Method applyItem = GameEngine.class.getDeclaredMethod("applyItem", GameEngine.ItemType.class);
        applyItem.setAccessible(true);
        applyItem.invoke(engine, GameEngine.ItemType.BOTTOM_CLEAR);

        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            if (engine.shapeAt(x, GameEngine.BOARD_HEIGHT - 1) != Tetrominoes.NoShape) {
                throw new AssertionError("하단 제거 후 가장 아래 행은 비어 있어야 합니다.");
            }
            if (engine.shapeAt(x, GameEngine.BOARD_HEIGHT - 2) != Tetrominoes.NoShape) {
                throw new AssertionError("하단 제거 후 두 번째 아래 행도 비어 있어야 합니다.");
            }
        }

        boolean foundDroppedBlock = false;
        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            for (int y = 0; y < GameEngine.BOARD_HEIGHT - 2; y++) {
                if (engine.shapeAt(x, y) != Tetrominoes.NoShape) {
                    foundDroppedBlock = true;
                    break;
                }
            }
            if (foundDroppedBlock) {
                break;
            }
        }
        if (!foundDroppedBlock) {
            throw new AssertionError("하단 제거 후 위 블록이 아래로 내려와야 합니다.");
        }
    }
}
