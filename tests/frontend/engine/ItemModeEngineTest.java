package frontend.engine;

import frontend.Shape;
import frontend.Tetrominoes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ItemModeEngineTest {
    public static void main(String[] args) throws ReflectiveOperationException {
        GameEngine engine = new GameEngine();
        engine.setItemMode(true);

        if (engine.activateRandomItemForClearedLines(3)) {
            throw new AssertionError("줄 누적 3줄로는 아이템이 발동하면 안 됩니다.");
        }

        if (engine.getLastItemName() == null || !engine.getLastItemName().trim().isEmpty()) {
            throw new AssertionError("줄 누적 조건이 제거되면, 발동된 아이템 이름은 비어 있어야 합니다.");
        }

        if (engine.getLinesUntilItemActivation() != 0) {
            throw new AssertionError("줄 누적 조건이 제거되면 남은 줄 수는 0이어야 합니다.");
        }

        if (engine.getLastItemDescription() == null || !engine.getLastItemDescription().trim().isEmpty()) {
            throw new AssertionError("줄 누적 조건이 제거되면, 발동된 아이템 설명은 비어 있어야 합니다.");
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

        engine.triggerRotationLockItem();
        if (engine.getLastItemName() == null || !engine.getLastItemName().contains("회전 금지")) {
            throw new AssertionError("회전 금지 아이템은 마지막 아이템 이름으로 기록되어야 합니다.");
        }

        Tetrominoes originalShape = engine.getCurrentShape();
        engine.rotateLeft();
        if (engine.getCurrentShape() != originalShape) {
            throw new AssertionError("회전 금지 아이템이 활성화된 상태에서는 블록을 회전시킬 수 없어야 합니다.");
        }

        engine.setItemMode(false);
        if (engine.activateRandomItemForClearedLines(3)) {
            throw new AssertionError("클래식 모드에서는 아이템이 발동하면 안 됩니다.");
        }

        testRotationLockItem();
        testFullLineRemovalCleansBoard();
        testItemCellRemovedOnLineClear();

        System.out.println("ItemModeEngineTest passed");
    }

    private static void testItemCellRemovedOnLineClear() throws ReflectiveOperationException {
        GameEngine engine = new GameEngine();
        Field boardField = GameEngine.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Tetrominoes[] board = (Tetrominoes[]) boardField.get(engine);

        Field itemField = GameEngine.class.getDeclaredField("boardItemTypes");
        itemField.setAccessible(true);
        GameEngine.ItemType[] itemTypes = (GameEngine.ItemType[]) itemField.get(engine);

        for (int x = 0; x < GameEngine.BOARD_WIDTH; x++) {
            board[(GameEngine.BOARD_HEIGHT - 1) * GameEngine.BOARD_WIDTH + x] = Tetrominoes.LineShape;
        }

        int itemCell = (GameEngine.BOARD_HEIGHT - 1) * GameEngine.BOARD_WIDTH + 3;
        itemTypes[itemCell] = GameEngine.ItemType.SCORE_BOOST;

        Method removeFullLines = GameEngine.class.getDeclaredMethod("removeFullLines");
        removeFullLines.setAccessible(true);
        removeFullLines.invoke(engine);

        if (!GameEngine.ItemType.SCORE_BOOST.getLabel().equals(engine.getLastItemName())) {
            throw new AssertionError("줄 제거 시 아이템 셀이 사라지면 해당 아이템 효과가 실행되어야 합니다.");
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
        Field nextPieceField = GameEngine.class.getDeclaredField("nextPiece");
        nextPieceField.setAccessible(true);
        Shape nextPiece = (Shape) nextPieceField.get(engine);

        for (int pieceNumber = 1; pieceNumber <= 3; pieceNumber++) {
            nextPiece.setShape(Tetrominoes.TShape);
            newPiece.invoke(engine);
            engine.oneLineDown();
            boolean expectedLocked = pieceNumber <= 2;
            if (engine.isRotationLocked() != expectedLocked) {
                throw new AssertionError("첫째·둘째 블록은 잠기고 셋째 블록은 해제되어야 합니다.");
            }
            int[] before = pieceCoordinates(engine);
            engine.rotateRight();
            if (sameCoordinates(before, pieceCoordinates(engine)) != expectedLocked) {
                throw new AssertionError("회전 금지는 블록 종류 대신 실제 셀 좌표로 확인해야 합니다.");
            }
            if (expectedLocked) {
                engine.rotateLeft();
                if (!sameCoordinates(before, pieceCoordinates(engine))) {
                    throw new AssertionError("잠긴 블록은 양방향 회전이 모두 막혀야 합니다.");
                }
            }
        }
    }

    private static int[] pieceCoordinates(GameEngine engine) {
        int[] coordinates = new int[8];
        for (int i = 0; i < 4; i++) {
            coordinates[i * 2] = engine.getPieceX(i);
            coordinates[i * 2 + 1] = engine.getPieceY(i);
        }
        return coordinates;
    }

    private static boolean sameCoordinates(int[] first, int[] second) {
        for (int i = 0; i < first.length; i++) {
            if (first[i] != second[i])
                return false;
        }
        return true;
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
            throw new AssertionError("2줄 제거만으로는 아이템이 발동하면 안 됩니다.");
        }

        if (engine.getLinesUntilItemActivation() != 0) {
            throw new AssertionError("줄 누적 조건이 제거되면 남은 줄 수는 0이어야 합니다.");
        }

        if (engine.activateRandomItemForClearedLines(3)) {
            throw new AssertionError("3줄 제거만으로도 아이템이 발동하면 안 됩니다.");
        }

        if (engine.getLinesUntilItemActivation() != 0) {
            throw new AssertionError("아이템 발동 조건이 제거되면 누적 카운트는 0이어야 합니다.");
        }
    }
}
