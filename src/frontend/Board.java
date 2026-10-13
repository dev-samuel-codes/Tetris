package frontend;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
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
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.util.HashSet;
import java.util.Set;

import frontend.engine.GameEngine;
import frontend.style.Style;
import shared.score.ScoreMode;
import java.awt.AlphaComposite;

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
    private static final int COMBO_EFFECT_MS = 900;
    private final Timer comboTimer;
    private int previousCombo = 0;
    private int comboShown = 0;
    private long comboStartTime = 0;
    private boolean battleMode = false;
    private String pauseReason = "잠시 쉬어 가세요";
    private final Set<Integer> pressedKeys = new HashSet<>();

    private GameOverListener gameOverListener;

    // 게임 종료 인터페이스
    public interface GameOverListener {
        void onGameOver(int score, int linesRemoved, int maxCombo, int elapsedSeconds);
    }


    private final Timer lineFlashTimer;
    private int flashRow = -1;


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
            if (isStopped || !engine.isStarted() || engine.isPaused())
                return;
            elapsedSeconds++;
            sidePanel.setElapsedSeconds(elapsedSeconds);
        });
        levelTimer = new Timer(1500, e -> {
            levelMessage = "";
            repaint();
        });
        levelTimer.setRepeats(false);
        // 줄 삭제 플래시를 잠깐 보여주는 타이머
        lineFlashTimer = new Timer(120, e -> {
            flashRow = -1;
            repaint();
        });
        lineFlashTimer.setRepeats(false);
        battleMessageTimer = new Timer(3000, e -> {
            battleMessage = "";
            repaint();
        });
        battleMessageTimer.setRepeats(false);
        // 이펙트 16ms 간격으로 다시그림
        comboTimer = new Timer(16, e -> {
            if (System.currentTimeMillis() - comboStartTime >= COMBO_EFFECT_MS) {
                comboShown = 0;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        addKeyListener(new TAdapter());
        installKeyBindings();
        getAccessibleContext().setAccessibleName("테트리스 게임판");
        getAccessibleContext().setAccessibleDescription("이동과 회전 키로 블록을 배치하세요. ESC 키는 일시정지와 재개입니다.");
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
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).clear();
        clearPressedKeys();
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

    private boolean canPlay() {
        return !isStopped && engine.isStarted() && !engine.isPaused()
                && engine.getCurrentShape() != Tetrominoes.NoShape;
    }

    public boolean isRunning() {
        return !isStopped && engine.isStarted();
    }

    public void rotateLeft() { controlPiece(engine::rotateLeft); }
    public void rotateRight() { controlPiece(engine::rotateRight); }
    public void hardDrop() { controlPiece(engine::dropDown); }
    public void softDrop() { controlPiece(engine::oneLineDown); }

    private void controlPiece(Runnable action) {
        if (!canPlay()) return;
        action.run();
        updateView();
    }

    public void togglePause() {
        setPaused(!engine.isPaused(), "잠시 쉬어 가세요");
    }

    public void setPaused(boolean paused, String reason) {
        if (!isRunning()) return;
        boolean previous = engine.isPaused();
        if (paused) pauseReason = reason;
        if (previous != paused) engine.pause();
        updateView();
        firePropertyChange("paused", previous, engine.isPaused());
    }

    public void clearPressedKeys() {
        pressedKeys.clear();
    }

    private void installKeyBindings() {
        int[] keys = { GameSettings.getLeftKey(), GameSettings.getRightKey(),
                GameSettings.getRotateLeftKey(), GameSettings.getRotateRightKey(),
                GameSettings.getSoftDropKey(), GameSettings.getHardDropKey(),
                GameSettings.getPauseKey(), KeyEvent.VK_ESCAPE };
        for (int key : keys) {
            String press = "game.press." + key;
            String release = "game.release." + key;
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, false), press);
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, true), release);
            getActionMap().put(press, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { handleDefaultKey(key); }
            });
            getActionMap().put(release, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { pressedKeys.remove(key); }
            });
        }
    }

    private boolean handleDefaultKey(int key) {
        if (!defaultKeysEnabled || !isRunning()) return false;
        boolean pause = key == GameSettings.getPauseKey() || key == KeyEvent.VK_ESCAPE;
        boolean once = pause || key == GameSettings.getHardDropKey()
                || key == GameSettings.getRotateLeftKey() || key == GameSettings.getRotateRightKey();
        if (once && !pressedKeys.add(key)) return true;
        // 줄 삭제 대기 중에도 일시정지할 수 있습니다.
        if (pause) {
            togglePause();
            return true;
        }
        if (engine.isPaused()) return true;
        if (key == GameSettings.getLeftKey()) moveLeft();
        else if (key == GameSettings.getRightKey()) moveRight();
        else if (key == GameSettings.getRotateLeftKey()) rotateLeft();
        else if (key == GameSettings.getRotateRightKey()) rotateRight();
        else if (key == GameSettings.getHardDropKey()) hardDrop();
        else if (key == GameSettings.getSoftDropKey()) softDrop();
        else return false;
        return true;
    }

    public void start() {
        stop();
        clearPressedKeys();
        previousLinesRemoved = 0;
        wasStarted = false;
        currentLevel = 1;
        battleMessage = "";
        if (engine.isPaused()) engine.pause();
        elapsedSeconds = 0;
        previousCombo = 0;
        comboShown = 0;
        isStopped = false;
        engine.start();
        elapsedSeconds = 0;
        scoreSaveStatus = "";
        statusbar.setToolTipText(null);
        sidePanel.setElapsedSeconds(0);
        updateView();
    }

    public void stop() {
        comboTimer.stop();
        isStopped = true;
        clearPressedKeys();
        timer.stop();
        clockTimer.stop();
        levelTimer.stop();
        battleMessageTimer.stop();
        levelMessage = "";
        lineFlashTimer.stop();
        flashRow = -1;
    }

    public void setGameOverListener(GameOverListener listener) {
        this.gameOverListener = listener;
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
        if (!isStopped && engine.isStarted() && !engine.isPaused()) {
            timer.start();
            clockTimer.start();
        } else {
            timer.stop();
            clockTimer.stop();
        }

        int currentLinesRemoved = engine.getNumLinesRemoved();
        if (currentLinesRemoved > previousLinesRemoved) {
            SoundManager.playLineClear();

            // 방금 삭제된 줄을 짧게 번쩍이게 표시
            flashRow = engine.getLastClearedRow();
            lineFlashTimer.restart();
        }
        previousLinesRemoved = currentLinesRemoved;

        // 콤보 2 이상 이펙트
        int combo = engine.getCombo();
        if (combo >= 2 && combo > previousCombo) {
            comboShown = combo;
            comboStartTime = System.currentTimeMillis();
            comboTimer.restart();
        }
        previousCombo = combo;

        if (wasStarted && !engine.isStarted()) {
            SoundManager.stopBgm();
            SoundManager.playGameOver();
            // 1PC 대전의 두 보드는 같은 닉네임을 사용하므로 싱글 랭킹에 저장하지 않음
            if (!battleMode) {
                ScoreMode scoreMode = engine.isItemMode() ? ScoreMode.ITEM : ScoreMode.CLASSIC;
                scoreSaveStatus = "점수 저장 중";
                ScoreManager.saveScore(scoreMode, playerNickname, engine.getScore(), result -> {
                    if (!isStopped) {
                        scoreSaveStatus = result.isSuccess() ? "서버 저장 완료"
                                : result.isPending() ? "재전송 대기" : "저장 실패";
                        statusbar.setText(scoreSaveStatus);
                        statusbar.setToolTipText(result.getMessage() + " · " + result.getDetail());
                    }
                });
            }

            // 싱글 게임은 등록된 종료 리스너에 결과 전달
            if (gameOverListener != null && !battleMode) {
                gameOverListener.onGameOver(engine.getScore(), engine.getNumLinesRemoved(),
                        engine.getMaxCombo(), elapsedSeconds);
            }
        }
        wasStarted = engine.isStarted();

        if (!engine.isStarted()) {
            statusbar.setText(scoreSaveStatus.isEmpty() ? "게임 종료" : scoreSaveStatus);
        } else if (engine.isPaused()) {
            statusbar.setText("일시정지 · " + pauseReason);
        } else if (engine.isItemMode() && !engine.getLastItemName().isEmpty()) {
            statusbar.setText("최근 아이템: " + engine.getLastItemName());
        } else {
            statusbar.setText("제거한 줄: " + currentLinesRemoved);
        }

        sidePanel.setScore(engine.getScore());
        sidePanel.setLinesRemoved(currentLinesRemoved);
        updateDropSpeed();
        sidePanel.setLevel(currentLevel);
        sidePanel.setItemStatus(engine.getLastItemType());
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

            // 줄 삭제 순간 흰색 플래시 표시
            if (flashRow >= 0) {
                graphics.setColor(new java.awt.Color(255, 255, 255, 180));
                int screenY = top + (BOARD_HEIGHT - 1 - flashRow) * size;
                graphics.fillRect(left, screenY, BOARD_WIDTH * size, size);
            }
            if (engine.isFogActive()) {
                drawFog(graphics, left, top, size);
            }

            if (!battleMessage.isEmpty()) {
                drawBattleMessage(graphics, left, top, size);
            }
            if (comboShown >= 2) {
                drawComboEffect(graphics, left, top, size);
            }
            if (engine.isPaused()) {
                drawOverlay(graphics, left, top, size, "PAUSED", GameSettings.keyName(GameSettings.getPauseKey()) + " / ESC · 계속하기");
            } else if (!engine.isStarted()) {
                drawOverlay(graphics, left, top, size, "GAME OVER", "결과 창에서 다시 도전하세요");
            } else if (!levelMessage.isEmpty()) {
                drawLevelNotice(graphics, left, top, size);
            }
        } finally {
            graphics.dispose();
        }
    }

    // 콤보 이펙트
    private void drawComboEffect(Graphics2D g, int left, int top, int size) {
        long elapsed = System.currentTimeMillis() - comboStartTime;
        float t = Math.min(1f, elapsed / (float) COMBO_EFFECT_MS); // 콤보 이미지 지속시간
        float alpha = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;      // 서서히 사라지게

        // 콤보별 색상 주황,금,보라
        Color color = comboShown >= 4 ? Style.PINK : comboShown == 3 ? Style.GOLD : Style.ACCENT;

        Graphics2D effect = (Graphics2D) g.create();
        try {
            effect.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
            effect.setFont(Style.DISPLAY_FONT.deriveFont(size * 1.1f));
            String text = "COMBO x" + comboShown;
            int centerX = left + BOARD_WIDTH * size / 2;
            int baselineY = top + BOARD_HEIGHT * size / 2;

            effect.setColor(Style.BOARD_BACKGROUND);
            drawCentered(effect, text, centerX + 2, baselineY + 2);
            effect.setColor(color);
            drawCentered(effect, text, centerX, baselineY);
        } finally {
            effect.dispose();
        }
    }

    // 안개 아이템 : 보드 위쪽 가림, 경계는 1칸마다 흐리게 품
    private static final int FOG_ROWS = 8;
    private void drawFog(Graphics2D g, int left, int top, int size) {
        int width = BOARD_WIDTH * size;
        int fogHeight = FOG_ROWS * size;
        Color fog = new Color(0x5A, 0x60, 0x6E);
        int fogAlpha = 225;

        // 위쪽 : 거의 안보임
        g.setColor(new Color(fog.getRed(), fog.getGreen(), fog.getBlue(), fogAlpha));
        g.fillRect(left, top, width, fogHeight - size);

        // 마지막 : 그라데이션
        g.setPaint(new GradientPaint(left, top + fogHeight - size,
                new Color(fog.getRed(), fog.getGreen(), fog.getBlue(), fogAlpha),
                left, top + fogHeight,
                new Color(fog.getRed(), fog.getGreen(), fog.getBlue(), 0)));
        g.fillRect(left, top + fogHeight - size, width, size);

        // 먹구름 글자를 가운데 표시
        String text = "먹구름";
        g.setFont(Style.BUTTON_FONT.deriveFont((float) Math.max(10, size * .7)));
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(Style.MUTED_TEXT);
        g.drawString(text, left + (width - metrics.stringWidth(text)) / 2,
                top + fogHeight / 2 + metrics.getAscent() / 2);
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
        // 현재 블록이 착지할 위치를 반투명하게 표시 (안개아이템 시 제외)
        if (!engine.isFogActive() && engine.getCurrentShape() != Tetrominoes.NoShape) {
            drawGhostPiece(g, left, top, size);
        }

        for (int i = 0; i < BOARD_HEIGHT; ++i) {
            for (int j = 0; j < BOARD_WIDTH; ++j) {
                Tetrominoes shape = engine.shapeAt(j, BOARD_HEIGHT - i - 1);
                if (shape != Tetrominoes.NoShape) {
                    BlockPainter.drawSquare(g, left + j * size, top + i * size, size,
                            shape, engine.getItemTypeAt(j, BOARD_HEIGHT - i - 1));
                }
            }
        }

        if (engine.getCurrentShape() != Tetrominoes.NoShape) {
            for (int i = 0; i < 4; ++i) {
                int x = engine.getCurrentX() + engine.getPieceX(i);
                int y = engine.getCurrentY() - engine.getPieceY(i);
                BlockPainter.drawSquare(g, left + x * size, top + (BOARD_HEIGHT - y - 1) * size,
                        size, engine.getCurrentShape(),
                        engine.isCurrentPieceItem() && i == engine.getCurrentItemCellIndex()
                                ? engine.getCurrentItemType() : null);
            }
        }
    }

    // 현재 블록이 착지할 위치에 고스트 피스 표시
    private void drawGhostPiece(Graphics2D g, int left, int top, int size) {
        int ghostY = engine.getGhostY();
        Graphics2D ghostGraphics = (Graphics2D) g.create();
        try {
            ghostGraphics.setComposite(
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));

            for (int i = 0; i < 4; i++) {
                int x = engine.getCurrentX() + engine.getPieceX(i);
                int y = ghostY - engine.getPieceY(i);

                BlockPainter.drawSquare(
                        ghostGraphics,
                        left + x * size,
                        top + (BOARD_HEIGHT - y - 1) * size,
                        size,
                        engine.getCurrentShape());
            }
        } finally {
            ghostGraphics.dispose();
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
        @Override public void keyPressed(KeyEvent event) {
            // 포커스된 Board의 이벤트는 여기서 소비해 키 바인딩과 중복 실행하지 않습니다.
            if (handleDefaultKey(event.getKeyCode())) event.consume();
        }
        @Override public void keyReleased(KeyEvent event) {
            pressedKeys.remove(event.getKeyCode());
        }
    }
}
