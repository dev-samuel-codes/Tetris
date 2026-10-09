package kr.ac.jbnu.se.tetris.game;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import kr.ac.jbnu.se.tetris.ui.Tetris;

public class Board extends JPanel implements ActionListener {

	final int BoardWidth = 10;
	final int BoardHeight = 22;

	Timer timer;
	boolean isFallingFinished = false;
	boolean isStarted = false;
	boolean isPaused = false;
	int numLinesRemoved = 0;
	int curX = 0;
	int curY = 0;
	boolean bombItemReady = false;
	JLabel statusbar;
	Shape curPiece;
	Tetrominoes[] board;

	public Board(Tetris parent) {

		setFocusable(true);
		curPiece = new Shape();
		timer = new Timer(400, this);
		timer.start();

		statusbar = parent.getStatusBar();
		board = new Tetrominoes[BoardWidth * BoardHeight];
		addKeyListener(new TAdapter());
		clearBoard();
	}

	public void actionPerformed(ActionEvent e) {
		if (isFallingFinished) {
			isFallingFinished = false;
			newPiece();
		} else {
			oneLineDown();
		}
	}

	int squareWidth() {
		return (int) getSize().getWidth() / BoardWidth;
	}

	int squareHeight() {
		return (int) getSize().getHeight() / BoardHeight;
	}

	Tetrominoes shapeAt(int x, int y) {
		return board[(y * BoardWidth) + x];
	}

	public void start() {
		if (isPaused)
			return;

		isStarted = true;
		isFallingFinished = false;
		numLinesRemoved = 0;
		clearBoard();

		newPiece();
		timer.start();
	}

	private void pause() {
		if (!isStarted)
			return;

		isPaused = !isPaused;
		if (isPaused) {
			timer.stop();
			statusbar.setText("paused");
		} else {
			timer.start();
			statusbar.setText(String.valueOf(numLinesRemoved));
		}
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int cellWidth = squareWidth();
		int cellHeight = squareHeight();
		int boardWidth = BoardWidth * cellWidth;
		int boardHeight = BoardHeight * cellHeight;
		int boardLeft = (getWidth() - boardWidth) / 2;
		int boardTop = getHeight() - boardHeight;

		g2.setColor(new Color(10, 12, 20));
		g2.fillRoundRect(boardLeft - 5, boardTop - 5, boardWidth + 10, boardHeight + 10, 12, 12);
		g2.setColor(new Color(22, 26, 40));
		g2.fillRect(boardLeft, boardTop, boardWidth, boardHeight);

		g2.setColor(new Color(39, 44, 62));
		g2.setStroke(new BasicStroke(1f));
		for (int column = 0; column <= BoardWidth; column++) {
			int x = boardLeft + column * cellWidth;
			g2.drawLine(x, boardTop, x, boardTop + boardHeight);
		}
		for (int row = 0; row <= BoardHeight; row++) {
			int y = boardTop + row * cellHeight;
			g2.drawLine(boardLeft, y, boardLeft + boardWidth, y);
		}

		for (int i = 0; i < BoardHeight; ++i) {
			for (int j = 0; j < BoardWidth; ++j) {
				Tetrominoes shape = shapeAt(j, BoardHeight - i - 1);
				if (shape != Tetrominoes.NoShape)
					drawSquare(g2, boardLeft + j * cellWidth, boardTop + i * cellHeight, shape);
			}
		}

		if (curPiece.getShape() != Tetrominoes.NoShape) {
			for (int i = 0; i < 4; ++i) {
				int x = curX + curPiece.x(i);
				int y = curY - curPiece.y(i);
				drawSquare(g2, boardLeft + x * cellWidth,
						boardTop + (BoardHeight - y - 1) * cellHeight, curPiece.getShape());
			}
		}

		g2.setColor(new Color(112, 105, 159));
		g2.setStroke(new BasicStroke(2f));
		g2.drawRoundRect(boardLeft - 1, boardTop - 1, boardWidth + 2, boardHeight + 2, 8, 8);

		if (isPaused || !isStarted) {
			g2.setColor(new Color(9, 10, 17, 190));
			g2.fillRect(boardLeft, boardTop, boardWidth, boardHeight);
			String title = isPaused ? "PAUSED" : "GAME OVER";
			String subtitle = isPaused ? "Press P to resume" : "Close the window to exit";
			g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 25));
			g2.setColor(Color.WHITE);
			int titleX = boardLeft + (boardWidth - g2.getFontMetrics().stringWidth(title)) / 2;
			int centerY = boardTop + boardHeight / 2;
			g2.drawString(title, titleX, centerY);
			g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
			g2.setColor(new Color(185, 181, 211));
			int subtitleX = boardLeft + (boardWidth - g2.getFontMetrics().stringWidth(subtitle)) / 2;
			g2.drawString(subtitle, subtitleX, centerY + 26);
		}

		g2.dispose();
	}

	private void dropDown() {
		int newY = curY;
		while (newY > 0) {
			if (!tryMove(curPiece, curX, newY - 1))
				break;
			--newY;
		}
		pieceDropped();
	}

	private void oneLineDown() {
		if (!tryMove(curPiece, curX, curY - 1))
			pieceDropped();
	}

	private void clearBoard() {
		for (int i = 0; i < BoardHeight * BoardWidth; ++i)
			board[i] = Tetrominoes.NoShape;
	}

	private void pieceDropped() {
		for (int i = 0; i < 4; ++i) {
			int x = curX + curPiece.x(i);
			int y = curY - curPiece.y(i);
			board[(y * BoardWidth) + x] = curPiece.getShape();
		}

		if (curPiece.getShape() == Tetrominoes.BombShape) {
			explodeBomb();
			return;
		}

		removeFullLines();

		if (!isFallingFinished)
			newPiece();
	}

	private void explodeBomb() {
		int centerX = curX;
		int centerY = curY;
		for (int y = centerY - 1; y <= centerY + 1; ++y) {
			for (int x = centerX - 1; x <= centerX + 1; ++x) {
				if (x < 0 || x >= BoardWidth || y < 0 || y >= BoardHeight)
					continue;
				board[(y * BoardWidth) + x] = Tetrominoes.NoShape;
			}
		}
		curPiece.setShape(Tetrominoes.NoShape);
		isFallingFinished = true;
		repaint();
		newPiece();
	}

	private void newPiece() {
		if (bombItemReady) {
			curPiece.setShape(Tetrominoes.BombShape);
			bombItemReady = false;
		} else {
			curPiece.setRandomShape();
		}
		curX = BoardWidth / 2 + 1;
		curY = BoardHeight - 1 + curPiece.minY();

		if (!tryMove(curPiece, curX, curY)) {
			curPiece.setShape(Tetrominoes.NoShape);
			timer.stop();
			isStarted = false;
			statusbar.setText("game over");
		}
	}

	public void activateBombItem() {
		bombItemReady = true;
		statusbar.setText("Bomb item ready");
	}

	private boolean tryMove(Shape newPiece, int newX, int newY) {
		for (int i = 0; i < 4; ++i) {
			int x = newX + newPiece.x(i);
			int y = newY - newPiece.y(i);
			if (x < 0 || x >= BoardWidth || y < 0 || y >= BoardHeight)
				return false;
			if (shapeAt(x, y) != Tetrominoes.NoShape)
				return false;
		}

		curPiece = newPiece;
		curX = newX;
		curY = newY;
		repaint();
		return true;
	}

	private void removeFullLines() {
		int numFullLines = 0;

		for (int i = BoardHeight - 1; i >= 0; --i) {
			boolean lineIsFull = true;

			for (int j = 0; j < BoardWidth; ++j) {
				if (shapeAt(j, i) == Tetrominoes.NoShape) {
					lineIsFull = false;
					break;
				}
			}

			if (lineIsFull) {
				++numFullLines;
				for (int k = i; k < BoardHeight - 1; ++k) {
					for (int j = 0; j < BoardWidth; ++j)
						board[(k * BoardWidth) + j] = shapeAt(j, k + 1);
				}
			}
		}

		if (numFullLines > 0) {
			numLinesRemoved += numFullLines;
			statusbar.setText(String.valueOf(numLinesRemoved));
			isFallingFinished = true;
			curPiece.setShape(Tetrominoes.NoShape);
			repaint();
		}
	}

	private void drawSquare(Graphics2D g, int x, int y, Tetrominoes shape) {
		Color[] colors = {
				new Color(0, 0, 0),
				new Color(239, 83, 133),
				new Color(91, 213, 139),
				new Color(92, 133, 255),
				new Color(255, 204, 92),
				new Color(183, 115, 255),
				new Color(66, 211, 220),
				new Color(255, 145, 82),
				new Color(255, 92, 71)
		};

		Color color = colors[shape.ordinal()];
		int width = squareWidth();
		int height = squareHeight();
		int inset = Math.max(2, Math.min(width, height) / 12);
		int blockWidth = Math.max(1, width - inset * 2);
		int blockHeight = Math.max(1, height - inset * 2);

		g.setPaint(new GradientPaint(x, y, color.brighter(), x + width, y + height, color.darker()));
		g.fillRoundRect(x + inset, y + inset, blockWidth, blockHeight, 7, 7);
		g.setColor(new Color(255, 255, 255, 100));
		g.setStroke(new BasicStroke(1.2f));
		g.drawLine(x + inset + 3, y + inset + 2, x + width - inset - 4, y + inset + 2);
		g.setColor(new Color(0, 0, 0, 65));
		g.drawRoundRect(x + inset, y + inset, blockWidth, blockHeight, 7, 7);
	}

	class TAdapter extends KeyAdapter {
		public void keyPressed(KeyEvent e) {

			if (!isStarted || curPiece.getShape() == Tetrominoes.NoShape) {
				return;
			}

			int keycode = e.getKeyCode();

			if (keycode == 'p' || keycode == 'P') {
				pause();
				return;
			}

			if (isPaused)
				return;

			switch (keycode) {
			case KeyEvent.VK_LEFT:
				tryMove(curPiece, curX - 1, curY);
				break;
			case KeyEvent.VK_RIGHT:
				tryMove(curPiece, curX + 1, curY);
				break;
			case KeyEvent.VK_DOWN:
				tryMove(curPiece.rotateRight(), curX, curY);
				break;
			case KeyEvent.VK_UP:
				tryMove(curPiece.rotateLeft(), curX, curY);
				break;
			case KeyEvent.VK_SPACE:
				dropDown();
				break;
			case 'd':
				oneLineDown();
				break;
			case 'D':
				oneLineDown();
				break;
			case 'b':
			case 'B':
				activateBombItem();
				break;
			}

		}
	}
}