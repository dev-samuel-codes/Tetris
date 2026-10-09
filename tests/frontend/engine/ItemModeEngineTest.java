package frontend.engine;

import frontend.Tetrominoes;

public class ItemModeEngineTest {
    public static void main(String[] args) {
        GameEngine engine = new GameEngine();
        engine.setItemMode(true);

        if (!engine.activateRandomItemForClearedLines(3)) {
            throw new AssertionError("3줄을 지울 때 아이템이 발동해야 합니다.");
        }

        if (engine.getLastItemName() == null || engine.getLastItemName().isBlank()) {
            throw new AssertionError("발동된 아이템 이름이 비어 있으면 안 됩니다.");
        }

        engine.triggerBombItem();
        engine.start();
        if (engine.getCurrentShape() != Tetrominoes.BombShape) {
            throw new AssertionError("폭탄 아이템은 폭탄 블록으로 등장해야 합니다.");
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

        System.out.println("ItemModeEngineTest passed");
    }
}
