package frontend;

import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.FontMetrics;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import frontend.engine.GameEngine;

// 게임 입력과 타이머를 엔진에 전달하고, 현재 상태를 화면에 표시
public class Board extends JPanel implements ActionListener {

	final static int BOARD_WIDTH = GameEngine.BOARD_WIDTH;
	final static int BOARD_HEIGHT = GameEngine.BOARD_HEIGHT;

	private final GameEngine engine = new GameEngine();
	private final Timer timer;
	private final Timer clockTimer;
	private boolean wasStarted = false;
	private int elapsedSeconds = 0;
	private int previousLinesRemoved = 0;
	private final JLabel statusbar;
	private final SidePanel sidePanel;
	private int currentLevel = 1;
	private String levelMessage = "";

	public Board(Tetris parent, SidePanel sidePanel) {
		setFocusable(true);
		this.sidePanel = sidePanel;
		timer = new Timer(400, this);

		// 1초(1000ms)마다 경과 시간 사이드패널에 표시
		clockTimer = new Timer(1000, e -> {
			elapsedSeconds++;
			sidePanel.setElapsedSeconds(elapsedSeconds);
		});
		statusbar = parent.getStatusBar();
		addKeyListener(new TAdapter());
	}

	public void actionPerformed(ActionEvent e) {
		engine.tick();
		updateView();
	}

	// 칸 하나의 크기를 가로와 세로 중 작은 쪽에 맞춰 정사각형으로 설정
	int squareSize() {
		int byWidth = getWidth() / BOARD_WIDTH;
		int byHeight = getHeight() / BOARD_HEIGHT;
		return Math.min(byWidth, byHeight);
	}

	// 남는 공간의 절반을 여백으로 두어 보드를 가운데 배치
	int boardLeft() {
		return (getWidth() - BOARD_WIDTH * squareSize()) / 2;
	}

	int boardTop() {
		return (getHeight() - BOARD_HEIGHT * squareSize()) / 2;
	}

	public void start() {
		engine.start();
		elapsedSeconds = 0;
		sidePanel.setElapsedSeconds(0);
		updateView();
	}

	// 현재 점수에 따라 블록 낙하 속도 변경
	private void updateDropSpeed() {
		int score = engine.getScore();
		int newLevel = 1;

		if (score >= 200) {
			timer.setDelay(200);
			newLevel = 5;
		} else if (score >= 150) {
			timer.setDelay(250);
			newLevel = 4;
		} else if (score >= 100) {
			timer.setDelay(300);
			newLevel = 3;
		} else if (score >= 50) {
			timer.setDelay(350);
			newLevel = 2;
		} else {
			timer.setDelay(400);
			newLevel = 1;
		}
		// 1.5초 동안 떴다가 사라지는 코드
		if (newLevel != currentLevel) {
			currentLevel = newLevel;
			levelMessage = "LEVEL " + currentLevel;

			Timer levelTimer = new Timer(1500, e -> {
				levelMessage = "";
				repaint();
			});

			levelTimer.setRepeats(false);
			levelTimer.start();
		}
	}

	// 게임 상태에 맞춰 타이머와 상태 표시를 갱신
	private void updateView() {
		if (engine.isStarted() && !engine.isPaused()) {
			timer.start();
			clockTimer.start();
		} else {
			timer.stop();
			clockTimer.stop();
		}
		// 이전보다 제거된 줄 수가 증가했으면 줄 삭제 효과음 재생
		int currentLinesRemoved = engine.getNumLinesRemoved();
		if (currentLinesRemoved > previousLinesRemoved) {
			SoundManager.playLineClear();
		}
		previousLinesRemoved = currentLinesRemoved;

		// 게임종료 시 BGM 종료 및 게임종료 효과음 + 스코어 전달
		if (wasStarted && !engine.isStarted()) {
			SoundManager.stopBgm();
			SoundManager.playGameOver();
			ScoreManager.saveScore(engine.getScore());
		}
		wasStarted = engine.isStarted();

		if (engine.isPaused()) {
			statusbar.setText("paused");
		} else if (!engine.isStarted()) {
			statusbar.setText("game over");
		} else {
			statusbar.setText(String.valueOf(engine.getNumLinesRemoved()));
		}
		sidePanel.setScore(engine.getScore());
		repaint();
		updateDropSpeed();
		sidePanel.setNextShape(engine.getNextShape());
	}

	public void paint(Graphics g) {
		super.paint(g);

		int size = squareSize();
		int left = boardLeft();
		int top = boardTop();

		for (int i = 0; i < BOARD_HEIGHT; ++i) {
			for (int j = 0; j < BOARD_WIDTH; ++j) {
				Tetrominoes shape = engine.shapeAt(j, BOARD_HEIGHT - i - 1);
				if (shape != Tetrominoes.NoShape)
					BlockPainter.drawSquare(g, left + j * size, top + i * size, size, shape);
			}
		}

		if (engine.getCurrentShape() != Tetrominoes.NoShape) {
			for (int i = 0; i < 4; ++i) {
				int x = engine.getCurrentX() + engine.getPieceX(i);
				int y = engine.getCurrentY() - engine.getPieceY(i);
				BlockPainter.drawSquare(g, left + x * size, top + (BOARD_HEIGHT - y - 1) * size,
						size, engine.getCurrentShape());
			}
		}
		// 레벨 메세지 띄우기
		if (!levelMessage.isEmpty()) {
			FontMetrics fm = g.getFontMetrics();

			int x = (getWidth() - fm.stringWidth(levelMessage)) / 2;
			int y = 60;

			g.drawString(levelMessage, x, y);
		}
	}

	class TAdapter extends KeyAdapter {
		public void keyPressed(KeyEvent e) {
			if (!engine.isStarted() || engine.getCurrentShape() == Tetrominoes.NoShape)
				return;

			int keycode = e.getKeyCode();
			if (keycode == KeyEvent.VK_P) {
				engine.pause();
				updateView();
				return;
			}

			if (engine.isPaused())
				return;

			switch (keycode) {
			case KeyEvent.VK_LEFT:
				engine.moveLeft();
				break;
			case KeyEvent.VK_RIGHT:
				engine.moveRight();
				break;
			case KeyEvent.VK_DOWN:
				engine.rotateRight();
				break;
			case KeyEvent.VK_UP:
				engine.rotateLeft();
				break;
			case KeyEvent.VK_SPACE:
				engine.dropDown();
				break;
			case KeyEvent.VK_D:
				engine.oneLineDown();
				break;
			default:
				return;
			}
			updateView();
		}
	}
}
