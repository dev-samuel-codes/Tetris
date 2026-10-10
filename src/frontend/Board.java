package frontend;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

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
    private final Timer levelTimer;
    private boolean isStopped = false;
    private boolean wasStarted = false;
    private int elapsedSeconds = 0;
    private int previousLinesRemoved = 0;
    private final JLabel statusbar;
    private final SidePanel sidePanel;
    private final String playerNickname;
    private int currentLevel = 1;
    private String levelMessage = "";
    private String scoreSaveStatus = "";
    private boolean defaultKeysEnabled = true;
    private String battleMessage = "";
    private final Timer battleMessageTimer;
    private boolean battleMode = false;

    public Board(JLabel statusbar, SidePanel sidePanel) {
        this(statusbar, sidePanel, false);
    }

    public Board(JLabel statusbar, SidePanel sidePanel, boolean itemMode) {
        setFocusable(true);
        setBackground(Style.BACKGROUND);
        this.sidePanel = sidePanel;
        this.statusbar = statusbar;
        this.engine.setItemMode(itemMode);
        this.playerNickname = ScoreManager.getNickname();

        timer = new Timer(400, this);
        clockTimer = new Timer(1000, e -> {
            if (isStopped)
                return;
            elapsedSeconds++;
            sidePanel.setElapsedSeconds(elapsedSeconds);
        });
        levelTimer = new Timer(1500, e -> {
            levelMessage = "";
            repaint();
        });
        levelTimer.setRepeats(false);
        battleMessageTimer = new Timer(3000, e -> {
            battleMessage = "";
            repaint();
        });
        battleMessageTimer.setRepeats(false);
        addKeyListener(new TAdapter());
    }

    public void actionPerformed(ActionEvent e) {
        if (isStopped)
            return;
        engine.tick();
        updateView();
    }

    int squareSize() {
        int byWidth = getWidth() / BOARD_WIDTH;
        int byHeight = getHeight() / BOARD_HEIGHT;
        return Math.min(byWidth, byHeight);
    }

    int boardLeft() {
        return (getWidth() - BOARD_WIDTH * squareSize()) / 2;
    }

    int boardTop() {
        return (getHeight() - BOARD_HEIGHT * squareSize()) / 2;
    }

    public void disableDefaultKeys() {
        defaultKeysEnabled = false;
    }

    public GameEngine getEngine() {
        return engine;
    }

    public void moveLeft() {
        moveHorizontally(true);
    }

    public void moveRight() {
        moveHorizontally(false);
    }

    private void moveHorizontally(boolean left) {
        if (isStopped || !engine.isStarted() || engine.isPaused() || engine.getCurrentShape() == Tetrominoes.NoShape)
            return;

        if (left)
            engine.moveLeft();
        else
            engine.moveRight();
        updateView();
    }

    public void start() {
        isStopped = false;
        engine.start();
        elapsedSeconds = 0;
        scoreSaveStatus = "";
        statusbar.setToolTipText(null);
        sidePanel.setElapsedSeconds(0);
        updateView();
    }

    public void stop() {
        isStopped = true;
        timer.stop();
        clockTimer.stop();
        levelTimer.stop();
        battleMessageTimer.stop();
        levelMessage = "";
    }

    public void setBattleMode(boolean battleMode) {
        this.battleMode = battleMode;
    }

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

        if (newLevel != currentLevel) {
            currentLevel = newLevel;
            levelMessage = "LEVEL " + currentLevel;
            levelTimer.restart();
        }

        if (battleMode) {
            timer.setDelay(250);
            currentLevel = 1;
        }
    }

    private void updateView() {
        if (engine.isStarted() && !engine.isPaused()) {
            timer.start();
            clockTimer.start();
        } else {
            timer.stop();
            clockTimer.stop();
        }

        int currentLinesRemoved = engine.getNumLinesRemoved();
        if (currentLinesRemoved > previousLinesRemoved) {
            SoundManager.playLineClear();
        }
        previousLinesRemoved = currentLinesRemoved;

        if (wasStarted && !engine.isStarted()) {
            SoundManager.stopBgm();
            SoundManager.playGameOver();
            scoreSaveStatus = "점수 저장 중";
            ScoreManager.saveScore(playerNickname, engine.getScore(), result -> {
                if (!isStopped) {
                    scoreSaveStatus = result.isSuccess() ? "서버 저장 완료"
                            : result.isPending() ? "재전송 대기" : "저장 실패";
                    statusbar.setText(scoreSaveStatus);
                    statusbar.setToolTipText(result.getMessage() + " · " + result.getDetail());
                }
            });
        }
        wasStarted = engine.isStarted();

        if (engine.isItemMode() && !engine.getLastItemName().isEmpty()) {
            statusbar.setText("아이템 발동: " + engine.getLastItemName());
        } else if (engine.isPaused()) {
            statusbar.setText("일시정지");
        } else if (!engine.isStarted()) {
            statusbar.setText(scoreSaveStatus.isEmpty() ? "게임 종료" : scoreSaveStatus);
        } else {
            statusbar.setText("제거한 줄: " + currentLinesRemoved);
        }

        sidePanel.setScore(engine.getScore());
        sidePanel.setLinesRemoved(currentLinesRemoved);
        updateDropSpeed();
        sidePanel.setLevel(currentLevel);
        sidePanel.setItemStatus(
                engine.getLinesUntilItemActivation(),
                engine.getLastItemName(),
                engine.getLastItemDescription());
        sidePanel.setItemMode(engine.isItemMode());
        sidePanel.setNextShape(engine.getNextShape());
        repaint();
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
            if (!battleMessage.isEmpty()) {
                drawBattleMessage(graphics, left, top, size);
            }
            if (engine.isPaused()) {
                drawOverlay(graphics, left, top, size, "PAUSED", "P 키를 눌러 계속하기");
            } else if (!engine.isStarted()) {
                drawOverlay(graphics, left, top, size, "GAME OVER", "메뉴로 돌아가서 다시 시작하세요");
            } else if (!levelMessage.isEmpty()) {
                drawLevelNotice(graphics, left, top, size);
            }
        } finally {
            graphics.dispose();
        }
    }

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

    private void drawPieces(Graphics2D g, int left, int top, int size) {
        for (int i = 0; i < BOARD_HEIGHT; ++i) {
            for (int j = 0; j < BOARD_WIDTH; ++j) {
                Tetrominoes shape = engine.shapeAt(j, BOARD_HEIGHT - i - 1);
                if (shape != Tetrominoes.NoShape) {
                    BlockPainter.drawSquare(g, left + j * size, top + i * size, size,
                            shape, engine.isItemCell(j, BOARD_HEIGHT - i - 1));
                }
            }
        }

        if (engine.getCurrentShape() != Tetrominoes.NoShape) {
            for (int i = 0; i < 4; ++i) {
                int x = engine.getCurrentX() + engine.getPieceX(i);
                int y = engine.getCurrentY() - engine.getPieceY(i);
                BlockPainter.drawSquare(g, left + x * size, top + (BOARD_HEIGHT - y - 1) * size,
                        size, engine.getCurrentShape(),
                        engine.isCurrentPieceItem() && i == engine.getCurrentItemCellIndex());
            }
        }
    }

    private void drawBattleMessage(Graphics2D g, int left, int top, int size) {
        int centerX = left + BOARD_WIDTH * size / 2;
        int centerY = top + BOARD_HEIGHT * size / 2;

        g.setFont(Style.DISPLAY_FONT.deriveFont((float) (size * 1.1)));
        g.setColor(Style.GOLD);

        int textWidth = g.getFontMetrics().stringWidth(battleMessage);
        g.drawString(battleMessage, centerX - textWidth / 2, centerY);
    }

    private void drawOverlay(Graphics2D g, int left, int top, int size, String title, String description) {
        g.setColor(Style.OVERLAY);
        g.fillRect(left, top, BOARD_WIDTH * size, BOARD_HEIGHT * size);
        int centerX = left + BOARD_WIDTH * size / 2;
        int centerY = top + BOARD_HEIGHT * size / 2;
        int panelX = left + size / 2;
        int panelY = centerY - size * 4;
        g.setColor(Style.PANEL);
        g.fillRect(panelX, panelY, size * 9, size * 7);
        g.setColor(Style.BORDER);
        g.drawRect(panelX, panelY, size * 9 - 1, size * 7 - 1);
        g.setColor(Style.ACCENT);
        int iconY = centerY - size * 3;
        if (engine.isPaused()) {
            g.fillRect(centerX - size / 2, iconY, size / 3, size);
            g.fillRect(centerX + size / 6, iconY, size / 3, size);
        } else {
            g.fillRect(centerX - size / 2, iconY, size, size);
        }

        g.setFont(Style.DISPLAY_FONT.deriveFont(size * 0.95f));
        g.setColor(Style.TEXT);
        drawCentered(g, title, centerX, centerY);
        g.setFont(Style.BODY_FONT.deriveFont(size * 0.45f));
        g.setColor(Style.MUTED_TEXT);
        drawCentered(g, description, centerX, centerY + (int) (size * 1.2));
        if (!engine.isStarted()) {
            g.setFont(Style.SCORE_FONT.deriveFont(size * 0.48f));
            g.setColor(Style.ACCENT);
            drawCentered(g, "최종 점수  " + java.text.NumberFormat.getIntegerInstance().format(engine.getScore()),
                    centerX, centerY + (int) (size * 2.2));
        }
    }

    private void drawLevelNotice(Graphics2D g, int left, int top, int size) {
        int centerX = left + BOARD_WIDTH * size / 2;
        g.setColor(Style.OVERLAY);
        g.fillRect(left + size / 2, top + size, size * 9, size * 3);
        g.setColor(Style.BORDER);
        g.drawRect(left + size / 2, top + size, size * 9, size * 3);
        g.setFont(Style.MONO_FONT.deriveFont(size * 0.62f));
        g.setColor(Style.ACCENT);
        drawCentered(g, "LEVEL " + currentLevel, centerX, top + (int) (2.15 * size));
        g.setFont(Style.BODY_FONT.deriveFont(size * 0.43f));
        g.setColor(Style.TEXT);
        String description = "블록이 더 빠르게 떨어집니다";
        drawCentered(g, description, centerX, top + (int) (3.15 * size));
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
    }

    public void showBattleMessage(String message) {
        battleMessage = message;
        battleMessageTimer.restart();
        repaint();
    }

    class TAdapter extends KeyAdapter {
        public void keyPressed(KeyEvent e) {
            if (!defaultKeysEnabled)
                return;

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
                    moveLeft();
                    return;
                case KeyEvent.VK_RIGHT:
                    moveRight();
                    return;
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
