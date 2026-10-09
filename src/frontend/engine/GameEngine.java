package frontend.engine;

import frontend.Shape;
import frontend.Tetrominoes;
import frontend.style.Style;

// 화면과 독립적으로 게임 상태와 블록 이동, 충돌, 줄 제거를 관리
public class GameEngine {

    public enum ItemType {
        ROW_CLEAR("한 줄 제거"),
        COLUMN_CLEAR("열 제거"),
        BOMB_CLEAR("폭탄 제거"),
        BOTTOM_CLEAR("하단 제거"),
        SCORE_BOOST("점수 업");

        private final String label;

        ItemType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
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

	private boolean isFallingFinished = false;  // 현재 코드에서는 줄 제거 후 다음 블록 생성을 기다리는 상태 변수
	private boolean isStarted = false;
	private boolean isPaused = false;
    private boolean itemMode = false;
    private boolean pendingBombItem = false;
	private int numLinesRemoved = 0;
	private int score = 0;
	private int combo = 0;
	private int curX = 0;
	private int curY = 0;
	private Shape curPiece = new Shape();
	private final Shape nextPiece = new Shape(); // 다음에 나올 블록 (미리보기용)
	private final Tetrominoes[] board = new Tetrominoes[BOARD_WIDTH * BOARD_HEIGHT];
    private String lastItemName = "";

	public GameEngine() {
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
        lastItemName = "";
		clearBoard();
		nextPiece.setRandomShape(); // 다음 블록을 먼저 뽑기
		newPiece();
	}

	public void pause() {
		if (isStarted)
			isPaused = !isPaused;
	}

	// 타이머 한 번에 해당하는 게임 진행. 타이머 자체는 화면에서 관리
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

    // 나중에 좌우 컨트롤 자체 분리해도 괜찮을 것 같음
    // 왼쪽으로 움직임
	public void moveLeft() {
		if (canControlPiece())
			tryMove(curPiece, curX - 1, curY); 
	}

    // 오른쪽으로 한 칸 움직임
	public void moveRight() {
		if (canControlPiece())
			tryMove(curPiece, curX + 1, curY); 
	}
     
    // 좌우 회전
	public void rotateLeft() {
		if (canControlPiece())
			tryMove(curPiece.rotateLeft(), curX, curY); // 좌우 회전
	}

    // 좌우 회전
	public void rotateRight() {
		if (canControlPiece())
			tryMove(curPiece.rotateRight(), curX, curY);
	}

    // 좌표에 따른 move
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
		for (int i = 0; i < board.length; ++i)
			board[i] = Tetrominoes.NoShape;
	}

    // 블록 고정 및 완성된 줄을 확인
	private void pieceDropped() {
        // 현재 위치 계산
		for (int i = 0; i < 4; ++i) {
			int x = curX + curPiece.x(i);
			int y = curY - curPiece.y(i);
			board[(y * BOARD_WIDTH) + x] = curPiece.getShape();
		}

        if (curPiece.getShape() == Tetrominoes.BombShape) {
            explodeBomb();
            return;
        }

		removeFullLines(); // 블록 고정 후 줄 검사 (꽉 찼는 지)
		if (!isFallingFinished)
			newPiece();
	}

    private void explodeBomb() {
        int centerX = curX;
        int centerY = curY;

        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
                    continue;
                board[(y * BOARD_WIDTH) + x] = Tetrominoes.NoShape;
            }
        }

        curPiece.setShape(Tetrominoes.NoShape);
        isFallingFinished = true;
        nextPiece.setRandomShape();
        if (itemMode) {
            lastItemName = "폭탄 제거";
        }
    }

	private void newPiece() {
        if (pendingBombItem) {
            curPiece.setShape(Tetrominoes.BombShape);
            pendingBombItem = false;
            nextPiece.setRandomShape();
        } else {
            curPiece.setShape(nextPiece.getShape()); // 미리 뽑아 둔 블록을 현재 블록으로
            nextPiece.setRandomShape();               // 다음 블록 만들기
        }
        // 떨어지는 위치 설정; 현재 10칸이라 curX는 으로 설정되어 있음
		curX = BOARD_WIDTH / 2 + 1;
		curY = BOARD_HEIGHT - 1 + curPiece.minY();

        // 들어갈 공간이 없으면 game over시킴
		if (!tryMove(curPiece, curX, curY)) {
			curPiece.setShape(Tetrominoes.NoShape);
			isStarted = false;
		}
	}

    // 블록을 원하는 위치에 배치할 수 있는 지 검사 & 이동
	private boolean tryMove(Shape newPiece, int newX, int newY) {
		for (int i = 0; i < 4; ++i) {
			int x = newX + newPiece.x(i);
			int y = newY - newPiece.y(i);
			if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT)
				return false;
            
            // 쌓인 블록과 겹치는 지 검사
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
				for (int k = i; k < BOARD_HEIGHT - 1; ++k) {
					for (int j = 0; j < BOARD_WIDTH; ++j)
						board[(k * BOARD_WIDTH) + j] = shapeAt(j, k + 1);
				}
			}
		}

		// 콤보 보너스 점수, 연속 보너스 점수 추가
		if (numFullLines > 0) {
			numLinesRemoved += numFullLines;
			combo++;
			score += LINE_SCORES[numFullLines];
			if ((combo > 1)) {
				score += COMBO_BONUS;
			}
			isFallingFinished = true;
			curPiece.setShape(Tetrominoes.NoShape);
            if (itemMode && numFullLines >= 3) {
                activateRandomItemForClearedLines(numFullLines);
            }
		} else {
			combo = 0;
		}
	}

    public void setItemMode(boolean itemMode) {
        this.itemMode = itemMode;
        if (!itemMode) {
            lastItemName = "";
        }
    }

    public boolean isItemMode() {
        return itemMode;
    }

    public void triggerBombItem() {
        if (!itemMode) {
            return;
        }
        pendingBombItem = true;
        lastItemName = ItemType.BOMB_CLEAR.getLabel();
    }

    public boolean activateRandomItemForClearedLines(int linesCleared) {
        if (!itemMode || linesCleared < 3) {
            return false;
        }

        ItemType item = ItemType.values()[(int) (Math.random() * ItemType.values().length)];
        if (item == ItemType.BOMB_CLEAR) {
            triggerBombItem();
        } else {
            applyItem(item);
            lastItemName = item.getLabel();
        }
        return true;
    }

    public String getLastItemName() {
        return lastItemName;
    }

    private void applyItem(ItemType item) {
        switch (item) {
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
            case SCORE_BOOST:
                score += 30;
                break;
            default:
                break;
        }
    }

    private void clearRandomRow() {
        int row = randomBetween(0, BOARD_HEIGHT - 1);
        for (int x = 0; x < BOARD_WIDTH; x++) {
            board[row * BOARD_WIDTH + x] = Tetrominoes.NoShape;
        }
    }

    private void clearRandomColumn() {
        int column = randomBetween(0, BOARD_WIDTH - 1);
        for (int y = 0; y < BOARD_HEIGHT; y++) {
            board[y * BOARD_WIDTH + column] = Tetrominoes.NoShape;
        }
    }

    private void clearAreaAroundCenter() {
        int centerX = BOARD_WIDTH / 2;
        int centerY = BOARD_HEIGHT / 2;
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                if (x >= 0 && x < BOARD_WIDTH && y >= 0 && y < BOARD_HEIGHT) {
                    board[y * BOARD_WIDTH + x] = Tetrominoes.NoShape;
                }
            }
        }
    }

    private void clearBottomRows(int rowsToClear) {
        // 보드 좌표는 y=0부터 하단이므로 낮은 행부터 제거
        for (int y = 0; y < rowsToClear; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                board[y * BOARD_WIDTH + x] = Tetrominoes.NoShape;
            }
        }
    }

    private int randomBetween(int min, int max) {
        return min + (int) (Math.random() * ((max - min) + 1));
    }

	// 화면에서 표시에 필요한 게임 상태를 조회
	public Tetrominoes shapeAt(int x, int y) {
		return board[(y * BOARD_WIDTH) + x];
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

	public int getScore() {return score;}

	// 다음에 나올 블록 종류 (사이드패널 미리보기용)
	public Tetrominoes getNextShape() {
		return nextPiece.getShape();
	}
}
