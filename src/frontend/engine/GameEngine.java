package frontend.engine;

import java.util.Random;

import frontend.Shape;
import frontend.Tetrominoes;

// 화면과 독립적으로 게임 상태와 블록 이동, 충돌, 줄 제거를 관리
public class GameEngine {

    public enum ItemType {
        ROW_CLEAR("한 줄 제거", "랜덤 한 줄을 제거합니다."),
        COLUMN_CLEAR("열 제거", "랜덤 열을 제거합니다."),
        BOMB_CLEAR("폭탄 제거", "주변 3x3 범위를 제거합니다."),
<<<<<<< Updated upstream
        BOTTOM_CLEAR("하단 제거", "하단 2줄을 제거합니다."),
        ROTATION_LOCK("회전 금지", "다음 2개 블록은 회전할 수 없습니다."),
        SCORE_BOOST("점수 업", "점수를 30점 추가합니다.");
=======
        BOTTOM_CLEAR("하단 제거", "하단 2줄을 제거합니다.");
>>>>>>> Stashed changes

        private final String label;
        private final String description;

        ItemType(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }
    }

    // 테트리스 가로·세로 칸 수
    public static final int BOARD_WIDTH = 10;
    public static final int BOARD_HEIGHT = 22;

    // 1줄 점수 : 10
    // 연속 점수 : 2줄 : 보너스 +5 -> 25
    // 연속 점수 : 3줄 : 보너스 + 10 -> 40
    // 연속 점수 : 4줄 : 보너스 + 20 -> 60
    private static final int[] LINE_SCORES = { 0, 10, 25, 40, 60 };
    // 콤보 점수 10점 추가
    private static final int COMBO_BONUS = 10;
    private static final int ITEM_SPAWN_MIN = 1;
    private static final int ITEM_SPAWN_MAX = 5;

    private boolean isFallingFinished = false;
    private boolean isStarted = false;
    private boolean isPaused = false;
    private int numLinesRemoved = 0;
    private int score = 0;
    private int combo = 0;
    private int curX = 0;
    private int curY = 0;
    private int piecesSinceLastItem = 0;
    private int nextItemSpawnInterval;
    private boolean processingItemEffect = false;
    private int rotationLockTurns = 0;
    private final Random random;
    private Shape curPiece = new Shape();
    private final Shape nextPiece = new Shape();
    private final Tetrominoes[] board = new Tetrominoes[BOARD_WIDTH * BOARD_HEIGHT];
    private final ItemType[] boardItemTypes = new ItemType[BOARD_WIDTH * BOARD_HEIGHT];
    private ItemType currentItemType = null;
    private String lastItemName = "";
    private String lastItemDescription = "";

    public GameEngine() {
        this(System.nanoTime());
    }

    public GameEngine(long seed) {
        this.random = new Random(seed);
        this.nextItemSpawnInterval = randomBetween(ITEM_SPAWN_MIN, ITEM_SPAWN_MAX);
        clearBoard();
    }

    public void start() {
        if (isPaused)
            return;

        isStarted = true;
        isFallingFinished = false;
        numLinesRemoved = 0;
        score = 0;
        combo = 0;
        rotationLockTurns = 0;
        piecesSinceLastItem = 0;
        nextItemSpawnInterval = randomBetween(ITEM_SPAWN_MIN, ITEM_SPAWN_MAX);
        lastItemName = "";
        lastItemDescription = "";
        clearBoard();
        nextPiece.setRandomShape();
        newPiece();
    }

    public void pause() {
        if (isStarted)
            isPaused = !isPaused;
    }

    public void tick() {
        if (!isStarted || isPaused)
            return;

        if (isFallingFinished) {
            isFallingFinished = false;
            newPiece();
        } else {
            oneLineDown();
        }
    }

    public void moveLeft() {
        if (canControlPiece())
            tryMove(curPiece, curX - 1, curY);
    }

    public void moveRight() {
        if (canControlPiece())
            tryMove(curPiece, curX + 1, curY);
    }

    public void rotateLeft() {
        if (canControlPiece() && rotationLockTurns <= 0)
            tryMove(curPiece.rotateLeft(), curX, curY);
    }

    public void rotateRight() {
        if (canControlPiece() && rotationLockTurns <= 0)
            tryMove(curPiece.rotateRight(), curX, curY);
    }

    public void dropDown() {
        if (!canControlPiece())
            return;

        int newY = curY;
        while (newY > 0) {
            if (!tryMove(curPiece, curX, newY - 1))
                break;
            --newY;
        }
        pieceDropped();
    }

    public void oneLineDown() {
        if (canControlPiece() && !tryMove(curPiece, curX, curY - 1))
            pieceDropped();
    }

    private boolean canControlPiece() {
        return isStarted && !isPaused && curPiece.getShape() != Tetrominoes.NoShape;
    }

    private void clearBoard() {
        for (int i = 0; i < board.length; ++i) {
            board[i] = Tetrominoes.NoShape;
            boardItemTypes[i] = null;
        }
    }

    private void pieceDropped() {
        for (int i = 0; i < 4; ++i) {
            int x = curX + curPiece.x(i);
            int y = curY - curPiece.y(i);
            int index = toIndex(x, y);
            board[index] = curPiece.getShape();
            boardItemTypes[index] = (curPiece.hasItem() && curPiece.getItemCellIndex() == i) ? currentItemType : null;
        }

        removeFullLines();
        if (!isFallingFinished)
            newPiece();
    }

    private void newPiece() {
        curPiece.setShape(nextPiece.getShape());
        curPiece.clearItem();
        currentItemType = null;

        piecesSinceLastItem++;
        if (piecesSinceLastItem >= nextItemSpawnInterval) {
            int itemCellIndex = randomBetween(0, 3);
            curPiece.setItemCell(itemCellIndex);
            currentItemType = randomItemType();
            piecesSinceLastItem = 0;
            nextItemSpawnInterval = randomBetween(ITEM_SPAWN_MIN, ITEM_SPAWN_MAX);
        }

        if (rotationLockTurns > 0) {
            rotationLockTurns--;
        }

        nextPiece.setRandomShape();
        curX = BOARD_WIDTH / 2 + 1;
        curY = BOARD_HEIGHT - 1 + curPiece.minY();

        if (!tryMove(curPiece, curX, curY)) {
            curPiece.setShape(Tetrominoes.NoShape);
            isStarted = false;
        }
    }

    private boolean tryMove(Shape newPiece, int newX, int newY) {
        for (int i = 0; i < 4; ++i) {
            int x = newX + newPiece.x(i);
            int y = newY - newPiece.y(i);
            if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
                return false;

            if (shapeAt(x, y) != Tetrominoes.NoShape)
                return false;
        }

        curPiece = newPiece;
        curX = newX;
        curY = newY;
        return true;
    }

    private void removeFullLines() {
        int numFullLines = 0;

        for (int y = BOARD_HEIGHT - 1; y >= 0; --y) {
            boolean lineIsFull = true;
            for (int x = 0; x < BOARD_WIDTH; ++x) {
                if (shapeAt(x, y) == Tetrominoes.NoShape) {
                    lineIsFull = false;
                    break;
                }
            }

            if (lineIsFull) {
                ++numFullLines;
                for (int x = 0; x < BOARD_WIDTH; ++x) {
                    clearBoardCell(x, y);
                }
                for (int nextRow = y; nextRow < BOARD_HEIGHT - 1; ++nextRow) {
                    for (int x = 0; x < BOARD_WIDTH; ++x) {
                        Tetrominoes movingShape = shapeAt(x, nextRow + 1);
                        ItemType movingItem = boardItemTypeAt(x, nextRow + 1);
                        setBoardCell(x, nextRow, movingShape, movingItem);
                    }
                }
                for (int x = 0; x < BOARD_WIDTH; ++x) {
                    setBoardCell(x, BOARD_HEIGHT - 1, Tetrominoes.NoShape, null);
                }
                ++y;
            }
        }

        if (numFullLines > 0) {
            numLinesRemoved += numFullLines;
            combo++;
            score += LINE_SCORES[numFullLines];
            if (combo > 1) {
                score += COMBO_BONUS;
            }
            isFallingFinished = true;
            curPiece.setShape(Tetrominoes.NoShape);
        } else {
            combo = 0;
        }
    }

    public boolean isItemCell(int x, int y) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
            return false;
        return boardItemTypes[toIndex(x, y)] != null;
    }

    public ItemType getCurrentItemType() {
        return currentItemType;
    }

    public boolean isCurrentPieceItem() {
        return curPiece.hasItem();
    }

    public int getCurrentItemCellIndex() {
        return curPiece.getItemCellIndex();
    }

    public boolean isItemMode() {
        return currentItemType != null || curPiece.hasItem();
    }

    public void setItemMode(boolean itemMode) {
        // Legacy item-mode flag is intentionally ignored; item behavior is driven by item cells on the active tetromino.
    }

    public int getLinesUntilItemActivation() {
        return 0;
    }

    public String getLastItemName() {
        return lastItemName;
    }

    public String getLastItemDescription() {
        return lastItemDescription;
    }

    private void handleRemovedItemCell(ItemType itemType) {
        if (itemType == null)
            return;

        if (processingItemEffect)
            return;

        processingItemEffect = true;
        try {
            triggerItemEffect(itemType);
        } finally {
            processingItemEffect = false;
        }
    }

    private void triggerItemEffect(ItemType itemType) {
        lastItemName = itemType.getLabel();
        lastItemDescription = itemType.getDescription();

        switch (itemType) {
            case ROW_CLEAR:
                clearRandomRow();
                break;
            case COLUMN_CLEAR:
                clearRandomColumn();
                break;
            case BOMB_CLEAR:
                clearAreaAroundCenter();
                break;
            case BOTTOM_CLEAR:
                clearBottomRows(2);
                break;
<<<<<<< Updated upstream
            case ROTATION_LOCK:
                rotationLockRemaining += 2;
                break;
            case SCORE_BOOST:
                score += 30;
                break;
=======
>>>>>>> Stashed changes
            default:
                break;
        }
    }

    private void clearBoardCell(int x, int y) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
            return;

        int index = toIndex(x, y);
        ItemType removedItem = boardItemTypes[index];
        board[index] = Tetrominoes.NoShape;
        boardItemTypes[index] = null;

        if (removedItem != null) {
            handleRemovedItemCell(removedItem);
        }
    }

    private void setBoardCell(int x, int y, Tetrominoes shape, ItemType itemType) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
            return;
        int index = toIndex(x, y);
        board[index] = shape == null ? Tetrominoes.NoShape : shape;
        boardItemTypes[index] = itemType;
    }

    private ItemType boardItemTypeAt(int x, int y) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
            return null;
        return boardItemTypes[toIndex(x, y)];
    }

    private void clearRandomRow() {
        int row = randomBetween(0, BOARD_HEIGHT - 1);
        for (int x = 0; x < BOARD_WIDTH; x++) {
            clearBoardCell(x, row);
        }
    }

    private void clearRandomColumn() {
        int column = randomBetween(0, BOARD_WIDTH - 1);
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            clearBoardCell(column, y);
        }
    }

    private void clearAreaAroundCenter() {
        int centerX = BOARD_WIDTH / 2;
        int centerY = BOARD_HEIGHT / 2;
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                clearBoardCell(x, y);
            }
        }
    }

    private void clearBottomRows(int rowsToClear) {
        if (rowsToClear <= 0 || rowsToClear >= BOARD_HEIGHT) {
            return;
        }

<<<<<<< Updated upstream
        Tetrominoes[] compactedBoard = new Tetrominoes[BOARD_WIDTH * BOARD_HEIGHT];
        for (int i = 0; i < compactedBoard.length; i++) {
            compactedBoard[i] = Tetrominoes.NoShape;
        }

        for (int x = 0; x < BOARD_WIDTH; x++) {
            int writeIndex = x;
            for (int y = rowsToClear; y < BOARD_HEIGHT; y++) {
                Tetrominoes shape = board[(y * BOARD_WIDTH) + x];
                if (shape != Tetrominoes.NoShape && shape != null) {
                    compactedBoard[writeIndex] = shape;
                    writeIndex += BOARD_WIDTH;
                }
            }
        }

        System.arraycopy(compactedBoard, 0, board, 0, board.length);
=======
        for (int y = BOARD_HEIGHT - rowsToClear; y < BOARD_HEIGHT; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                clearBoardCell(x, y);
            }
        }
>>>>>>> Stashed changes
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private ItemType randomItemType() {
        return ItemType.values()[randomBetween(0, ItemType.values().length - 1)];
    }

    public void applyBattleRotationLock() {
        rotationLockTurns = 2;
    }

    public void applyBattleScorePenalty() {
        score = Math.max(0, score - 20);
    }

    public void applyBattleColumnClear() {
        int column = randomBetween(0, BOARD_WIDTH - 1);
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            clearBoardCell(column, y);
        }
    }

    private int toIndex(int x, int y) {
        return (y * BOARD_WIDTH) + x;
    }

    public Tetrominoes shapeAt(int x, int y) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
            return Tetrominoes.NoShape;
        return board[toIndex(x, y)];
    }

    public boolean isStarted() {
        return isStarted;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public int getNumLinesRemoved() {
        return numLinesRemoved;
    }

    public int getCurrentX() {
        return curX;
    }

    public int getCurrentY() {
        return curY;
    }

    public Tetrominoes getCurrentShape() {
        return curPiece.getShape();
    }

    public int getPieceX(int index) {
        return curPiece.x(index);
    }

    public int getPieceY(int index) {
        return curPiece.y(index);
    }

    public int getScore() {
        return score;
    }

    public Tetrominoes getNextShape() {
        return nextPiece.getShape();
    }
}
