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
        BOTTOM_CLEAR("하단 제거", "하단 2줄을 제거합니다."),
        ROTATION_LOCK("회전 금지", "다음 2개 블록은 회전할 수 없습니다."),
        SCORE_BOOST("점수 업", "점수를 30점 추가합니다.");

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

    public static final int BOARD_WIDTH = 10;
    public static final int BOARD_HEIGHT = 22;

    private static final int[] LINE_SCORES = { 0, 10, 25, 40, 60 };
    private static final int COMBO_BONUS = 10;
    private static final int ITEM_SPAWN_MIN = 1;
    private static final int ITEM_SPAWN_MAX = 5;
    private static final int ITEM_ACTIVATION_LINES = 3;

    private boolean isFallingFinished = false;
    private boolean isStarted = false;
    private boolean isPaused = false;
    private boolean itemMode = false;
    private boolean pendingBombItem = false;
    private int numLinesRemoved = 0;
    private int score = 0;
    private int combo = 0;
    private int curX = 0;
    private int curY = 0;
    private int rotationLockRemaining = 0;
    private boolean rotationLocked = false;
    private int accumulatedItemLines = 0;
    private Shape curPiece = new Shape();
    private final Shape nextPiece = new Shape();
    private final Tetrominoes[] board = new Tetrominoes[BOARD_WIDTH * BOARD_HEIGHT];
    private final ItemType[] boardItemTypes = new ItemType[BOARD_WIDTH * BOARD_HEIGHT];
    private final Random random;
    private int piecesSinceLastItem = 0;
    private int nextItemSpawnInterval = ITEM_SPAWN_MIN;
    private ItemType currentItemType = null;
    private String lastItemName = "";
    private String lastItemDescription = "";

    public GameEngine() {
        this(new Random());
    }

    public GameEngine(long seed) {
        this(new Random(seed));
    }

    private GameEngine(Random random) {
        this.random = random;
        clearBoard();
    }

    private void chooseNextPiece() {
        Tetrominoes[] values = Tetrominoes.values();
        int index;
        do {
            index = randomBetween(1, values.length - 1);
        } while (values[index] == Tetrominoes.BombShape);
        nextPiece.setShape(values[index]);
    }

    public boolean activateRandomItemForClearedLines(int linesCleared) {
        if (!itemMode || linesCleared <= 0)
            return false;

        accumulatedItemLines += linesCleared;
        if (accumulatedItemLines >= ITEM_ACTIVATION_LINES) {
            ItemType itemType = randomItemType();
            triggerItemEffect(itemType);
            lastItemName = itemType.getLabel();
            lastItemDescription = itemType.getDescription();
            accumulatedItemLines = 0;
            return true;
        }
        return false;
    }

    public void triggerBombItem() {
        pendingBombItem = true;
        lastItemName = ItemType.BOMB_CLEAR.getLabel();
        lastItemDescription = ItemType.BOMB_CLEAR.getDescription();
    }

    public void start() {
        if (isPaused)
            return;

        isStarted = true;
        isFallingFinished = false;
        numLinesRemoved = 0;
        score = 0;
        combo = 0;
        rotationLockRemaining = 0;
        rotationLocked = false;
        accumulatedItemLines = 0;
        piecesSinceLastItem = 0;
        nextItemSpawnInterval = randomBetween(ITEM_SPAWN_MIN, ITEM_SPAWN_MAX);
        currentItemType = null;
        lastItemName = "";
        lastItemDescription = "";
        clearBoard();
        chooseNextPiece();
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
        if (canControlPiece() && rotationLockRemaining <= 0)
            tryMove(curPiece.rotateLeft(), curX, curY);
    }

    public void rotateRight() {
        if (canControlPiece() && rotationLockRemaining <= 0)
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

        currentItemType = null;
        removeFullLines();
        if (!isFallingFinished)
            newPiece();
    }

    private void newPiece() {
        if (!isStarted)
            return;

        if (pendingBombItem) {
            pendingBombItem = false;
            clearAreaAroundCenter();
            lastItemName = ItemType.BOMB_CLEAR.getLabel();
            lastItemDescription = ItemType.BOMB_CLEAR.getDescription();
        }

        curPiece.setShape(nextPiece.getShape());
        chooseNextPiece();

        currentItemType = null;
        if (itemMode) {
            piecesSinceLastItem++;
            if (piecesSinceLastItem >= nextItemSpawnInterval) {
                currentItemType = randomItemType();
                piecesSinceLastItem = 0;
                nextItemSpawnInterval = randomBetween(ITEM_SPAWN_MIN, ITEM_SPAWN_MAX);
                int itemCell = randomBetween(0, 3);
                curPiece.setItemCell(itemCell);
            } else {
                curPiece.clearItem();
            }
        } else {
            curPiece.clearItem();
        }

        rotationLocked = rotationLockRemaining > 0;
        if (rotationLockRemaining > 0) {
            rotationLockRemaining--;
        }

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
        for (int y = BOARD_HEIGHT - 1; y >= 0; y--) {
            boolean lineIsFull = true;
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (shapeAt(x, y) == Tetrominoes.NoShape) {
                    lineIsFull = false;
                    break;
                }
            }
            if (!lineIsFull)
                continue;

            numFullLines++;
            for (int clearY = y; clearY < BOARD_HEIGHT - 1; clearY++) {
                for (int x = 0; x < BOARD_WIDTH; x++) {
                    Tetrominoes shape = shapeAt(x, clearY + 1);
                    ItemType itemType = boardItemTypeAt(x, clearY + 1);
                    setBoardCell(x, clearY, shape, itemType);
                }
            }
            for (int x = 0; x < BOARD_WIDTH; x++) {
                setBoardCell(x, BOARD_HEIGHT - 1, Tetrominoes.NoShape, null);
            }
            y++;
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
            if (itemMode) {
                activateRandomItemForClearedLines(numFullLines);
            }
        } else {
            combo = 0;
        }
    }

    private void triggerItemEffect(ItemType itemType) {
        if (itemType == null)
            return;

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
            case ROTATION_LOCK:
                rotationLockRemaining += 2;
                rotationLocked = true;
                break;
            case SCORE_BOOST:
                score += 30;
                break;
            default:
                break;
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
        return itemMode || currentItemType != null || curPiece.hasItem();
    }

    public void setItemMode(boolean itemMode) {
        this.itemMode = itemMode;
        if (!itemMode)
            accumulatedItemLines = 0;
    }

    public int getLinesUntilItemActivation() {
        if (!itemMode)
            return 0;
        return Math.max(0, ITEM_ACTIVATION_LINES - accumulatedItemLines);
    }

    public String getLastItemName() {
        return lastItemName;
    }

    public String getLastItemDescription() {
        return lastItemDescription;
    }

    public int getRotationLockRemaining() {
        return rotationLockRemaining;
    }

    public boolean isRotationLocked() {
        return rotationLocked;
    }

    public void applyBattleRotationLock() {
        rotationLockRemaining += 2;
        rotationLocked = true;
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

    private void applyItem(ItemType itemType) {
        triggerItemEffect(itemType);
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

    private void handleRemovedItemCell(ItemType itemType) {
        if (itemType == null)
            return;

        triggerItemEffect(itemType);
        lastItemName = itemType.getLabel();
        lastItemDescription = itemType.getDescription();
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
                if (x >= 0 && x < BOARD_WIDTH && y >= 0 && y < BOARD_HEIGHT)
                    clearBoardCell(x, y);
            }
        }
    }

    private void clearBottomRows(int rowsToClear) {
        if (rowsToClear <= 0 || rowsToClear >= BOARD_HEIGHT)
            return;

        for (int x = 0; x < BOARD_WIDTH; x++) {
            for (int y = BOARD_HEIGHT - rowsToClear; y < BOARD_HEIGHT; y++) {
                ItemType removedItem = boardItemTypeAt(x, y);
                if (removedItem != null) {
                    handleRemovedItemCell(removedItem);
                }
            }
        }

        for (int x = 0; x < BOARD_WIDTH; x++) {
            int writeY = BOARD_HEIGHT - 1;
            for (int y = BOARD_HEIGHT - 1; y >= 0; y--) {
                if (y >= BOARD_HEIGHT - rowsToClear) {
                    continue;
                }

                Tetrominoes shape = shapeAt(x, y);
                ItemType itemType = boardItemTypeAt(x, y);
                if (shape != Tetrominoes.NoShape) {
                    setBoardCell(x, writeY, shape, itemType);
                    writeY--;
                }
            }

            for (int y = writeY; y >= 0; y--) {
                setBoardCell(x, y, Tetrominoes.NoShape, null);
            }
        }
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private ItemType randomItemType() {
        return ItemType.values()[randomBetween(0, ItemType.values().length - 1)];
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