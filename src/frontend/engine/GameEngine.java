package frontend.engine;

import frontend.Shape;
import frontend.Tetrominoes;

// 화면과 독립적으로 게임 상태와 블록 이동, 충돌, 줄 제거를 관리
public class GameEngine {

    // 테트리스 가로·세로 칸 수
	public static final int BOARD_WIDTH = 10;
	public static final int BOARD_HEIGHT = 22;

	private boolean isFallingFinished = false;
	private boolean isStarted = false;
	private boolean isPaused = false;
	private int numLinesRemoved = 0;
	private int curX = 0;
	private int curY = 0;
	private Shape curPiece = new Shape();
	private final Tetrominoes[] board = new Tetrominoes[BOARD_WIDTH * BOARD_HEIGHT];

	public GameEngine() {
		clearBoard();
	}

	public void start() {
		if (isPaused)
			return;

		isStarted = true;
		isFallingFinished = false;
		numLinesRemoved = 0;
		clearBoard();
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

	private void pieceDropped() {
		for (int i = 0; i < 4; ++i) {
			int x = curX + curPiece.x(i);
			int y = curY - curPiece.y(i);
			board[(y * BOARD_WIDTH) + x] = curPiece.getShape();
		}

		removeFullLines();
		if (!isFallingFinished)
			newPiece();
	}

	private void newPiece() {
		curPiece.setRandomShape();
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
		for (int i = BOARD_HEIGHT - 1; i >= 0; --i) {
			boolean lineIsFull = true;
			for (int j = 0; j < BOARD_WIDTH; ++j) {
				if (shapeAt(j, i) == Tetrominoes.NoShape) {
					lineIsFull = false;
					break;
				}
			}

			if (lineIsFull) {
				++numFullLines;
				for (int k = i; k < BOARD_HEIGHT - 1; ++k) {
					for (int j = 0; j < BOARD_WIDTH; ++j)
						board[(k * BOARD_WIDTH) + j] = shapeAt(j, k + 1);
				}
			}
		}

		if (numFullLines > 0) {
			numLinesRemoved += numFullLines;
			isFallingFinished = true;
			curPiece.setShape(Tetrominoes.NoShape);
		}
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
}
