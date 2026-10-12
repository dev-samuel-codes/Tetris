package frontend.engine;

import java.util.Random;

import frontend.Shape;
import frontend.Tetrominoes;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

// 화면과 독립적으로 게임 상태와 블록 이동, 충돌, 줄 제거를 관리
public class GameEngine {

    public enum ItemType {
        ROW_CLEAR("한 줄 제거", "랜덤 한 줄을 제거합니다."),
        COLUMN_CLEAR("열 제거", "랜덤 열을 제거합니다."),
        BOMB_CLEAR("폭탄 제거", "주변 3x3 범위를 제거합니다."),
        BOTTOM_CLEAR("하단 제거", "하단 2줄을 제거합니다."),
        ROTATION_LOCK("회전 금지", "다음 2개 블록은 회전할 수 없습니다."),
        SCORE_BOOST("점수 업", "점수를 30점 추가합니다."),
        COLOR_CLEAR("같은 색 제거", "아이템 블록과 같은 색 칸을 모두 제거합니다."),
        FOG("안개", "다음 3개 블록 동안 보드 위쪽 절반이 가려집니다.");

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

    private boolean isFallingFinished = false;
    private boolean isStarted = false;
    private boolean isPaused = false;
    private boolean itemMode = false;
    private boolean battleItemMode = false;
    private boolean pendingBombItem = false;
	private int numLinesRemoved = 0;
	private int score = 0;
	private int classicHighScore = 0;
	private int itemModeHighScore = 0;
	private int combo = 0;
    private int maxCombo = 0;
	private int curX = 0;
	private int curY = 0;
	private int accumulatedItemLines = 0;
	private int rotationLockRemaining = 0;
	private boolean rotationLocked = false;
    private Tetrominoes removedItemShape = Tetrominoes.NoShape;
    private static final int FOG_PIECES = 3;
    private int fogRemaining = 0;
    private boolean fogActive = false;
	private Shape curPiece = new Shape();
	private final Shape nextPiece = new Shape(); // 다음에 나올 블록 (미리보기용)
	private final Tetrominoes[] board = new Tetrominoes[BOARD_WIDTH * BOARD_HEIGHT];
    private final ItemType[] boardItemTypes = new ItemType[BOARD_WIDTH * BOARD_HEIGHT];
    private final Random random;
    // 아이템 사용 횟수가 달라도 같은 시드의 블록 순서는 유지하도록 효과 난수 분리
    private final Random itemEffectRandom;
    private List<Tetrominoes> bag;
    private int lastClearedRow = -1; // 방금 몇번째 줄을 지웠는지
    private void refillBag() {
        bag = new ArrayList<>();
        for (Tetrominoes t : Tetrominoes.values()) {
            if (t != Tetrominoes.NoShape && t != Tetrominoes.BombShape) {
                bag.add(t);
            }
        }
        Collections.shuffle(bag, random);
    }
    private int piecesSinceLastItem = 0;
    private int nextItemSpawnInterval = ITEM_SPAWN_MIN;
    private ItemType currentItemType = null;
    private ItemType lastItemType = null;
    private final Queue<ItemType> pendingBattleItems = new ArrayDeque<>();

    public GameEngine() {
        this(new Random(), new Random());
    }

    public GameEngine(long seed) {
        this(new Random(seed), new Random(~seed));
    }

    private GameEngine(Random random, Random itemEffectRandom) {
        this.random = random;
        this.itemEffectRandom = itemEffectRandom;
        refillBag();
        clearBoard();
    }

    private void chooseNextPiece() {
        if (bag.isEmpty()) {
            refillBag();
        }
        Tetrominoes t = bag.remove(random.nextInt(bag.size()));
        nextPiece.setShape(t);
    }

    public boolean activateRandomItemForClearedLines(int linesCleared) {
        if (!itemMode || linesCleared <= 0)
            return false;

        accumulatedItemLines = 0;
        return false;
    }

    public void triggerBombItem() {
        pendingBombItem = true;
        lastItemType = ItemType.BOMB_CLEAR;
    }

    public void triggerRotationLockItem() {
        if (!itemMode)
            return;
        handleRemovedItemCell(ItemType.ROTATION_LOCK);
    }

    public void start() {
        if (isPaused)
            return;

        isStarted = true;
        isFallingFinished = false;
        numLinesRemoved = 0;
        score = 0;
        combo = 0;
        maxCombo = 0;
        rotationLockRemaining = 0;
        rotationLocked = false;
        fogRemaining = 0;
        fogActive = false;
        accumulatedItemLines = 0;
        piecesSinceLastItem = 0;
        nextItemSpawnInterval = randomBetween(ITEM_SPAWN_MIN, ITEM_SPAWN_MAX);
        currentItemType = null;
        lastItemType = null;
        lastClearedRow = -1;
		clearBoard();
		chooseNextPiece(); // 다음 블록을 먼저 뽑기
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

    // 벽면 회전 버그 수정 : 벽에서 회전 시도 시 좌 우로 1~2칸 밀고 회전 시도
    public void rotateLeft() {
        if (canControlPiece() && !rotationLocked)
            tryRotate(curPiece.rotateLeft());
    }

    public void rotateRight() {
        if (canControlPiece() && !rotationLocked)
            tryRotate(curPiece.rotateRight());
    }
    // 벽 차기
    private static final int[][] WALL_KICK_OFFSETS = {
            { 0, 0 }, { -1, 0 }, { 1, 0 }, { -2, 0 }, { 2, 0 }
    };

    private boolean tryRotate(Shape rotated) {
        for (int[] offset : WALL_KICK_OFFSETS) {
            if (tryMove(rotated, curX + offset[0], curY + offset[1]))
                return true;
        }
        return false;
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
            lastItemType = ItemType.BOMB_CLEAR;
        }

        curPiece.setShape(nextPiece.getShape()); // 미리 뽑아 둔 블록을 현재 블록으로
        chooseNextPiece(); // 다음 블록 만들기

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

        fogActive = fogRemaining > 0;
        if (fogRemaining > 0) {
            fogRemaining--;
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

        // 줄이 가등 찼는 지 검사
		for (int i = BOARD_HEIGHT - 1; i >= 0; --i) {
			boolean lineIsFull = true;
			for (int j = 0; j < BOARD_WIDTH; ++j) {
				if (shapeAt(j, i) == Tetrominoes.NoShape) {
					lineIsFull = false;
					break;
				}
			}

            // 가득 찬 줄을 위쪽 줄로 덮어씀
			if (lineIsFull) {
				++numFullLines;
                if (lineIsFull)
                    lastClearedRow = i;
                    for (int j = 0; j < BOARD_WIDTH; ++j) {
                    ItemType removedItem = boardItemTypeAt(j, i); // 제거 줄 블록 기억
                    boardItemTypes[toIndex(j, i)] = null;
                    if (removedItem != null) {
                        removedItemShape = shapeAt(j, i);
                        handleRemovedItemCell(removedItem);
                    }
                }
				for (int k = i; k < BOARD_HEIGHT - 1; ++k) {
					for (int j = 0; j < BOARD_WIDTH; ++j) {
                        setBoardCell(j, k, shapeAt(j, k + 1), boardItemTypeAt(j, k + 1));
                    }
				}
				for (int j = 0; j < BOARD_WIDTH; ++j) {
                    setBoardCell(j, BOARD_HEIGHT - 1, Tetrominoes.NoShape, null);
				}
                i++;
			}
		}

		// 콤보 보너스 점수, 연속 보너스 점수 추가
		if (numFullLines > 0) {
			numLinesRemoved += numFullLines;
			combo++;
            maxCombo = Math.max(maxCombo, combo);
			score += LINE_SCORES[numFullLines];
			if ((combo > 1)) {
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
        if (itemType == null || itemType == ItemType.BOTTOM_CLEAR)
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
                break;
            case ROTATION_LOCK:
                if (battleItemMode) {
                    pendingBattleItems.offer(ItemType.ROTATION_LOCK);
                } else {
                    rotationLockRemaining += 2;
                    rotationLocked = true;
                }
                break;

            case SCORE_BOOST:
                score += 30;
                break;
            case COLOR_CLEAR:
                clearSameShape(removedItemShape);
                break;
            case FOG:
            if (battleItemMode) {
                pendingBattleItems.offer(ItemType.FOG);
            } else {
                fogRemaining += FOG_PIECES;
                fogActive = true;
            }
            break;

            default:
                break;
        }
    }

    public boolean isItemCell(int x, int y) {
        return getItemTypeAt(x, y) != null;
    }

    // 착지한 아이템도 이동 중인 블록과 같은 이미지로 표시할 수 있도록 종류를 전달
    public ItemType getItemTypeAt(int x, int y) {
        return boardItemTypeAt(x, y);
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

    // 대전에서는 방해 아이템을 상대에게 적용하도록 설정
    public void setBattleItemMode(boolean battleItemMode) {
        this.battleItemMode = battleItemMode;
    }

    // 상대에게 전달할 방해 아이템을 하나 꺼냄
    public ItemType pollBattleItem() {
        return pendingBattleItems.poll();
    }

    public int getLinesUntilItemActivation() {
        return 0;
    }

    public String getLastItemName() {
        return lastItemType == null ? "" : lastItemType.getLabel();
    }

    public String getLastItemDescription() {
        return lastItemType == null ? "" : lastItemType.getDescription();
    }

    public ItemType getLastItemType() {
        return lastItemType;
    }

    public int getRotationLockRemaining() {
        return rotationLockRemaining;
    }

    public boolean isRotationLocked() {
        return rotationLocked;
    }

    public boolean isFogActive() {
        return fogActive;
    }

    public int getFogRemaining() {
        return fogRemaining;
    }

    public void applyBattleRotationLock() {
        rotationLockRemaining += 2;
        rotationLocked = true;
    }

    // 상대에게 안개 효과 적용
    public void applyBattleFog() {
        fogRemaining += FOG_PIECES;
        fogActive = true;
    }

    public void applyBattleScorePenalty() {
        score = Math.max(0, score - 20);
    }

    public void applyBattleColumnClear() {
        int column = itemEffectRandom.nextInt(BOARD_WIDTH);
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            clearBoardCell(column, y);
        }
    }

    private void applyItem(ItemType itemType) {
        triggerItemEffect(itemType);
    }

    // 같은 색 제거 아이템 : 보드에서 target과 같은 색 지움
    private void clearSameShape(Tetrominoes target) {
        if (target == null || target == Tetrominoes.NoShape)
            return;
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (shapeAt(x, y) == target)
                    clearBoardCell(x, y);
            }
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

    private void handleRemovedItemCell(ItemType itemType) {
        if (itemType == null)
            return;

        triggerItemEffect(itemType);
        lastItemType = itemType;
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
        int row = itemEffectRandom.nextInt(BOARD_HEIGHT);
        for (int x = 0; x < BOARD_WIDTH; x++) {
            clearBoardCell(x, row);
        }
    }

    private void clearRandomColumn() {
        int column = itemEffectRandom.nextInt(BOARD_WIDTH);
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

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private ItemType randomItemType() {
        ItemType[] pool = {
            ItemType.ROW_CLEAR,
            ItemType.COLUMN_CLEAR,
            ItemType.BOMB_CLEAR,
            ItemType.ROTATION_LOCK,
            ItemType.SCORE_BOOST,
            ItemType.COLOR_CLEAR,
            ItemType.COLOR_CLEAR,
            ItemType.FOG
        };
        return pool[randomBetween(0, pool.length - 1)];
    }

    private int toIndex(int x, int y) {
        return (y * BOARD_WIDTH) + x;
    }

    public Tetrominoes shapeAt(int x, int y) {
        if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
            return Tetrominoes.NoShape;
        return board[toIndex(x, y)];
    }

    // 현재 블록이 바닥에 착지할 Y 위치 계산(고스트피스)
    public int getGhostY() {
        if (!canControlPiece())
            return curY;
        int ghostY = curY;
        while (canPlace(curPiece, curX, ghostY - 1)) {
            ghostY--;
        }
        return ghostY;
    }

    // 블록을 해당 위치에 놓을 수 있는지 확인(고스트피스)
    private boolean canPlace(Shape piece, int x, int y) {
        for (int i = 0; i < 4; i++) {
            int boardX = x + piece.x(i);
            int boardY = y - piece.y(i);
            if (boardX < 0 || boardX >= BOARD_WIDTH
                    || boardY < 0 || boardY >= BOARD_HEIGHT)
                return false;
            if (shapeAt(boardX, boardY) != Tetrominoes.NoShape)
                return false;
        }
        return true;
    }

    // 마지막으로 삭제된 줄 위치 반환
    public int getLastClearedRow() {
        return lastClearedRow;
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
    public int getCombo() {
        return combo;
    }

    public int getMaxCombo() {
        return maxCombo;
    }

    public int getScore() {
        return score;
    }

    public Tetrominoes getNextShape() {
        return nextPiece.getShape();
    }
}
