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
        if (engine.getCurrentShape() != Tetrominoes.BombShape) {
            throw new AssertionError("폭탄 아이템은 폭탄 블록으로 등장해야 합니다.");
        }

        engine.setItemMode(false);
        if (engine.activateRandomItemForClearedLines(3)) {
            throw new AssertionError("클래식 모드에서는 아이템이 발동하면 안 됩니다.");
        }

        testEarthquakeItem();
        testRotationLockItem();
        testFullLineRemovalCleansBoard();
        testBottomClearItem();

        System.out.println("ItemModeEngineTest passed");
    }

    private static void testEarthquakeItem() throws ReflectiveOperationException {
        GameEngine engine = new GameEngine();
        Method applyItem = GameEngine.class.getDeclaredMethod("applyItem", GameEngine.ItemType.class);
        applyItem.setAccessible(true);
        applyItem.invoke(engine, GameEngine.ItemType.EARTHQUAKE);

        int totalTrashRows = 0;
        for (int y = GameEngine.BOARD_HEIGHT - 4; y < GameEngine.BOARD_HEIGHT; y++) {
            boolean rowHasBlock = false;
            for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
                if (engine.shapeAt(x, y) != Tetrominoes.NoShape) {
                    rowHasBlock = true;
                    break;
                }
            }
            if (rowHasBlock) {
                totalTrashRows++;
            }
        }

        if (totalTrashRows < 2 || totalTrashRows > 4) {
            throw new AssertionError("지진 아이템은 바닥에서 위로 2~4줄의 방해 줄을 생성해야 합니다.");
        }

        for (int y = 0; y < GameEngine.BOARD_HEIGHT - 4; y++) {
            for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
                if (engine.shapeAt(x, y) != Tetrominoes.NoShape) {
                    throw new AssertionError("지진 아이템은 바닥에서 위로 생성되어야 하며, 위쪽 행에는 방해 줄이 생기면 안 됩니다.");
                }
            }
        }

        boolean hasHole = false;
        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            if (engine.shapeAt(x, GameEngine.BOARD_HEIGHT - 1) == Tetrominoes.NoShape) {
                hasHole = true;
                break;
            }
        }
        if (!hasHole) {
            throw new AssertionError("지진 아이템은 맨 밑 줄에 구멍이 있어야 합니다.");
        }
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

        // 하단 2줄을 제거한 뒤 위 블록이 아래로 떨어져 빈 자리를 채워야 함
        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            board[(2 * GameEngine.BOARD_WIDTH) + x] = Tetrominoes.LineShape;
            board[(5 * GameEngine.BOARD_WIDTH) + x] = Tetrominoes.SquareShape;
            board[(GameEngine.BOARD_HEIGHT - 2) * GameEngine.BOARD_WIDTH + x] = Tetrominoes.TShape;
        }

        Method applyItem = GameEngine.class.getDeclaredMethod("applyItem", GameEngine.ItemType.class);
        applyItem.setAccessible(true);
        applyItem.invoke(engine, GameEngine.ItemType.BOTTOM_CLEAR);

        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            boolean seenFilledCell = false;
            boolean seenGapAfterFill = false;
            for (int y = 0; y < GameEngine.BOARD_HEIGHT; y++) {
                Tetrominoes cell = engine.shapeAt(x, y);
                if (cell == null) {
                    throw new AssertionError("하단 제거 후 빈 칸은 null이 아니고 NoShape이어야 합니다.");
                }
                if (cell == Tetrominoes.NoShape) {
                    if (seenFilledCell) {
                        seenGapAfterFill = true;
                    }
                } else {
                    if (seenGapAfterFill) {
                        throw new AssertionError("하단 제거 후 블록이 공중에 떠 있으면 안 됩니다.");
                    }
                    seenFilledCell = true;
                }
            }
        }
    }
}
