package frontend.engine;

import frontend.Tetrominoes;

public class ItemModeEngineTest {
    public static void main(String[] args) {
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

        System.out.println("ItemModeEngineTest passed");
    }
}
