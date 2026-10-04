package frontend;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.FontMetrics;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import frontend.engine.GameEngine;
import frontend.style.Style;

// 게임 입력과 타이머를 엔진에 전달하고, 현재 상태를 화면에 표시
public class Board extends JPanel implements ActionListener {

	final static int BOARD_WIDTH = GameEngine.BOARD_WIDTH;
	final static int BOARD_HEIGHT = GameEngine.BOARD_HEIGHT;

	private final GameEngine engine = new GameEngine();
	private final Timer timer;
	private final Timer clockTimer;
	private final Timer levelTimer; // 레벨 메세지를 지우는 타이머
	private boolean isStopped = false; // 메뉴로 돌아간 보드가 계속 처리되지 않도록 확인
	private boolean wasStarted = false;
	private int elapsedSeconds = 0;
	private int previousLinesRemoved = 0;
	private final JLabel statusbar;
	private final SidePanel sidePanel;
	private final String playerNickname; // 게임 시작 시 닉네임으로 기록을 저장
	private int currentLevel = 1;
	private String levelMessage = "";
	private String scoreSaveStatus = "";

	public Board(Tetris parent, SidePanel sidePanel) {
		setFocusable(true);
		setBackground(Style.BACKGROUND);
		this.sidePanel = sidePanel;
		playerNickname = ScoreManager.getNickname();
		timer = new Timer(400, this);

		// 1초(1000ms)마다 경과 시간 사이드패널에 표시
		clockTimer = new Timer(1000, e -> {
			// 게임을 정지한 뒤 들어온 이벤트면 시간도 늘리지 않음
			if (isStopped)
				return;
			elapsedSeconds++;
			sidePanel.setElapsedSeconds(elapsedSeconds);
		});
		// 1.5초 뒤 레벨 메세지를 지우고 화면 갱신
		levelTimer = new Timer(1500, e -> {
			levelMessage = "";
			repaint();
		});
		levelTimer.setRepeats(false); // 반복하지 않고 한 번만 실행
		statusbar = parent.getStatusBar();
		addKeyListener(new TAdapter());
	}

	public void actionPerformed(ActionEvent e) {
		// 정지 전에 들어온 타이머 이벤트도 무시
		if (isStopped)
			return;
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
		isStopped = false; // 게임 시작 시 다시 입력과 타이머 처리 허용
		engine.start();
		elapsedSeconds = 0;
		scoreSaveStatus = "";
		statusbar.setToolTipText(null);
		sidePanel.setElapsedSeconds(0);
		updateView();
	}

	// 메뉴로 돌아가거나 창을 닫을 때 게임 관련 타이머를 모두 정지
	// 나중에 타이머가 더 늘어나면 타이머 관리 코드를 따로 나눠도 괜찮을 것 같음
	public void stop() {
		// 현재는 중간에 나간 게임의 점수는 저장하지 않음
		isStopped = true;
		timer.stop();
		clockTimer.stop();
		levelTimer.stop();
		levelMessage = "";
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

			levelTimer.restart(); // 레벨이 다시 바뀌면 표시 시간도 처음부터 계산
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
			scoreSaveStatus = "점수 저장 중";
			ScoreManager.saveScore(playerNickname, engine.getScore(), result -> {
				// 메뉴로 돌아간 창에는 늦게 도착한 응답을 표시하지 않음
				if (!isStopped) {
					scoreSaveStatus = result.isSuccess() ? "서버 저장 완료"
							: result.isPending() ? "재전송 대기" : "저장 실패";
					statusbar.setText(scoreSaveStatus);
					statusbar.setToolTipText(result.getMessage() + " · " + result.getDetail());
				}
			});
		}
		wasStarted = engine.isStarted();

		if (engine.isPaused()) {
			statusbar.setText("일시정지");
		} else if (!engine.isStarted()) {
			statusbar.setText(scoreSaveStatus.isEmpty() ? "게임 종료" : scoreSaveStatus);
		} else {
			statusbar.setText("제거한 줄: " + currentLinesRemoved);
		}
		sidePanel.setScore(engine.getScore());
		sidePanel.setLinesRemoved(currentLinesRemoved);
		repaint();
		updateDropSpeed();
		sidePanel.setNextShape(engine.getNextShape());
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		int size = squareSize();
		if (size <= 0)
			return;

		int left = boardLeft();
		int top = boardTop();
		Graphics2D graphics = (Graphics2D) g.create();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			drawGrid(graphics, left, top, size);
			drawPieces(graphics, left, top, size);

			// 일시정지와 게임 종료 상태를 게임판 위에 표시
			if (engine.isPaused()) {
				drawOverlay(graphics, left, top, size, "PAUSED", "P 키를 눌러 계속하기");
			} else if (!engine.isStarted()) {
				drawOverlay(graphics, left, top, size, "GAME OVER", "메뉴로 돌아가서 다시 시작하세요");
			} else if (!levelMessage.isEmpty()) {
				graphics.setFont(Style.SCORE_FONT.deriveFont(size * 0.8f));
				graphics.setColor(Style.OVERLAY);
				graphics.fillRoundRect(left + size * 2, top + size, size * 6, size * 2, size / 2, size / 2);
				graphics.setColor(Style.TEXT);
				drawCentered(graphics, levelMessage, left + BOARD_WIDTH * size / 2, top + (int) (2.3 * size));
			}
		} finally {
			graphics.dispose();
		}
	}

	// 보드 배경과 격자도 블록과 같은 정사각형 칸 크기로 그림
	private void drawGrid(Graphics2D g, int left, int top, int size) {
		int width = BOARD_WIDTH * size;
		int height = BOARD_HEIGHT * size;
		g.setColor(Style.BOARD_BACKGROUND);
		g.fillRect(left, top, width, height);
		g.setColor(Style.BOARD_GRID);
		for (int x = 1; x < BOARD_WIDTH; x++)
			g.drawLine(left + x * size, top, left + x * size, top + height - 1);
		for (int y = 1; y < BOARD_HEIGHT; y++)
			g.drawLine(left, top + y * size, left + width - 1, top + y * size);
		g.setColor(Style.BOARD_BORDER);
		g.drawRect(left, top, width - 1, height - 1);
	}

	// 엔진의 상태를 읽어서 고정된 블록과 현재 블록을 표시
	// 나중에 렌더링 효과가 더 늘어나면 보드 그리기를 별도 클래스로 나눠도 괜찮을 것 같음
	private void drawPieces(Graphics2D g, int left, int top, int size) {
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
	}

	private void drawOverlay(Graphics2D g, int left, int top, int size, String title, String description) {
		g.setColor(Style.OVERLAY);
		g.fillRect(left, top, BOARD_WIDTH * size, BOARD_HEIGHT * size);
		int centerX = left + BOARD_WIDTH * size / 2;
		int centerY = top + BOARD_HEIGHT * size / 2;
		g.setFont(Style.ROOM_TITLE_FONT.deriveFont(size * 0.9f));
		g.setColor(Style.TEXT);
		drawCentered(g, title, centerX, centerY);
		g.setFont(Style.BODY_FONT.deriveFont(size * 0.43f));
		g.setColor(Style.MUTED_TEXT);
		drawCentered(g, description, centerX, centerY + size);
	}

	private void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
		FontMetrics fm = g.getFontMetrics();
		g.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
	}

	class TAdapter extends KeyAdapter {
		public void keyPressed(KeyEvent e) {
			// 메뉴로 돌아간 보드나 조작할 블록이 없는 상태면 입력 무시
			if (isStopped || !engine.isStarted() || engine.getCurrentShape() == Tetrominoes.NoShape)
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
