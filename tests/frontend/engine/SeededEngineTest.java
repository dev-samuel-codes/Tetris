package frontend.engine;

import frontend.Tetrominoes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// 같은 블록 순번의 공정성과 줄 삭제 후 최상단 중복을 검증합니다.
public final class SeededEngineTest {
    public static void main(String[] args) throws Exception {
        GameEngine first = new GameEngine(101L);
        GameEngine second = new GameEngine(101L);
        GameEngine different = new GameEngine(102L);
        first.start();
        second.start();
        different.start();
        Method newPiece = GameEngine.class.getDeclaredMethod("newPiece");
        newPiece.setAccessible(true);
        boolean diverged = false;
        for (int i = 0; i < 1000; i++) {
            check(first.getCurrentShape() == second.getCurrentShape(), "같은 시드의 현재 블록");
            check(first.getNextShape() == second.getNextShape(), "같은 시드의 다음 블록");
            diverged |= first.getCurrentShape() != different.getCurrentShape();
            // 상대 입력과 순서가 달라도 같은 순번의 난수는 변하지 않아야 합니다.
            first.moveLeft();
            first.rotateRight();
            newPiece.invoke(first);
            newPiece.invoke(different);
            newPiece.invoke(second);
        }
        check(diverged, "서로 다른 시드는 다른 순서를 만들어야 합니다.");
        Method clear = GameEngine.class.getDeclaredMethod("removeFullLines");
        clear.setAccessible(true);
        Field field = GameEngine.class.getDeclaredField("board");
        field.setAccessible(true);
        for (int rows = 1; rows <= 4; rows++) {
            GameEngine engine = new GameEngine(1L);
            engine.start();
            Tetrominoes[] board = (Tetrominoes[]) field.get(engine);
            for (int y = 0; y < rows; y++)
                for (int x = 0; x < 10; x++) board[y * 10 + x] = Tetrominoes.LineShape;
            board[21 * 10] = Tetrominoes.SquareShape;
            clear.invoke(engine);
            int[] points = {0, 10, 25, 40, 60};
            check(engine.getScore() == points[rows], "기존 클래식 줄 점수 보존");
            check(engine.getNumLinesRemoved() == rows, "정확한 제거 줄 수");
            check(engine.shapeAt(0, 21 - rows) == Tetrominoes.SquareShape, "상단 블록이 삭제 줄 수만큼 이동");
            for (int y = 22 - rows; y < 22; y++)
                for (int x = 0; x < 10; x++) check(engine.shapeAt(x, y) == Tetrominoes.NoShape, "상단은 비워야 합니다.");
            for (int x = 0; x < 10; x++) board[x] = Tetrominoes.LineShape;
            clear.invoke(engine);
            check(engine.getScore() == points[rows] + 20, "연속 줄 삭제의 콤보 10점");
        }
        System.out.println("SeededEngineTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
