package frontend;

import frontend.style.Style;
import frontend.engine.GameEngine;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;
import java.util.Set;


public class TwoPlayerTetris extends JFrame {

    private Board player1Board;
    private Board player2Board;
    private Timer gameCheckTimer;
    private boolean gameFinished = false;
    private JButton pauseButton;
    private Set<Integer> pressedKeys = new HashSet<>();
    private JDialog resultDialog;

    // 1PC 2인용 게임 창 생성
    public TwoPlayerTetris() {
        setTitle("Tetris - 1PC 2인");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Style.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Style.PANEL);
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel title = new JLabel("TETRIS");
        title.setFont(Style.ROOM_TITLE_FONT);
        title.setForeground(Style.TEXT);

        JLabel mode = new JLabel("1PC 2 PLAYER");
        mode.setFont(Style.BODY_FONT);
        mode.setForeground(Style.ACCENT);

        header.add(title, BorderLayout.WEST);
        header.add(mode, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel gameArea = new JPanel(new GridLayout(1, 2, 30, 0));
        gameArea.setBackground(Style.BACKGROUND);

        gameArea.add(createPlayerPanel("PLAYER 1", true));
        gameArea.add(createPlayerPanel("PLAYER 2", false));

        // 2인용에서는 Board의 기본 키 입력 비활성화
        player1Board.disableDefaultKeys();
        player2Board.disableDefaultKeys();

        // 2인용에서는 두 플레이어의 낙하 속도를 고정
        player1Board.setBattleMode(true);
        player2Board.setBattleMode(true);

        root.add(gameArea, BorderLayout.CENTER);

        JButton menuButton = new JButton("메뉴로 돌아가기");
        Style.applyButtonStyle(menuButton, Style.ACCENT, Style.PANEL);
        menuButton.setFocusable(false); // 진행 중 Space/Enter 입력은 두 게임판에서 처리
        menuButton.addActionListener(e -> returnToMenu());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        pauseButton = new JButton("Ⅱ 일시정지");
        Style.applyButtonStyle(pauseButton, Style.ACCENT, Style.PANEL);
        pauseButton.setToolTipText("P / ESC · 두 플레이어 일시정지");
        pauseButton.setFocusable(false);
        pauseButton.addActionListener(e -> toggleMatchPause());
        footer.add(pauseButton);
        footer.add(menuButton);
        player1Board.addPropertyChangeListener("paused", e -> {
            boolean paused = player1Board.getEngine().isPaused();
            pauseButton.setText(paused ? "▶ 함께 계속하기" : "Ⅱ 일시정지");
            // 쉬는 동안 버튼 키보드 접근을 허용하고, 재개 뒤에는 하드드롭 키를 게임판으로 전달합니다.
            pauseButton.setFocusable(paused);
            menuButton.setFocusable(paused);
            if (!paused) player1Board.requestFocusInWindow();
        });
        root.add(footer, BorderLayout.SOUTH);

        // 두 플레이어 게임 시작
        player1Board.start();
        player2Board.start();

        // 두 플레이어의 게임오버 여부 확인
        gameCheckTimer = new Timer(200, e -> {
            checkGameOver();
            checkBattleItems();
        });
        gameCheckTimer.start();

        // BGM 시작
        SoundManager.playBgm();

        installKeyBindings();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { returnToMenu(); }
            @Override public void windowOpened(WindowEvent event) { player1Board.requestFocusInWindow(); }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent event) {
                pressedKeys.clear();
                player1Board.clearPressedKeys();
                player2Board.clearPressedKeys();
                setMatchPaused(true, "창을 벗어나 자동으로 멈췄습니다");
            }
        });

        setSize(1100, 750);
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        Rectangle screen = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        // 메뉴 막대와 Dock을 제외한 작업 영역에 두 게임판을 함께 맞춤
        int availableWidth = screen.width - insets.left - insets.right - 32;
        int availableHeight = screen.height - insets.top - insets.bottom - 32;
        double scale = Math.min(1, Math.min(availableWidth / (double) getWidth(),
                availableHeight / (double) getHeight()));
        if (scale < 1)
            setSize((int) (getWidth() * scale), (int) (getHeight() * scale));
        setLocationRelativeTo(null);
        setResizable(false);
    }

    // 각 플레이어가 발동한 방해 아이템을 상대에게 적용
    private void checkBattleItems() {
        if (gameFinished) return;
        applyPendingItems(player1Board, player2Board);
        applyPendingItems(player2Board, player1Board);
    }

    // 대기 중인 방해 아이템을 상대 보드에 적용
    private void applyPendingItems(Board source, Board target) {
        GameEngine.ItemType item;

        while ((item = source.getEngine().pollBattleItem()) != null) {
            if (item == GameEngine.ItemType.ROTATION_LOCK) {
                target.getEngine().applyBattleRotationLock();
                target.showBattleMessage("다음 2블록 회전 금지!");
            } else if (item == GameEngine.ItemType.FOG) {
                target.getEngine().applyBattleFog();
                target.showBattleMessage("안개 공격!");
            }

            target.repaint();
        }
    }

    // 두 플레이어의 게임오버 상태를 확인하고 승자를 결정
    private void checkGameOver() {
        if (gameFinished)
            return;

        boolean player1Alive = player1Board.getEngine().isStarted();
        boolean player2Alive = player2Board.getEngine().isStarted();

        if (!player1Alive || !player2Alive) {
            stopMatch();
            String result = !player1Alive && !player2Alive ? "DRAW · 무승부"
                    : !player1Alive ? "PLAYER 2 WIN" : "PLAYER 1 WIN";
            showMatchResult(result);
        }
    }

    private void stopMatch() {
        gameFinished = true;
        if (gameCheckTimer != null) gameCheckTimer.stop();
        player1Board.stop();
        player2Board.stop();
        if (pressedKeys != null) pressedKeys.clear();
        if (pauseButton != null) pauseButton.setEnabled(false);
        SoundManager.stopBgm();
    }

    private void showMatchResult(String result) {
        resultDialog = new JDialog(this, "대전 결과", true);
        JPanel root = Style.framedPanel(new BorderLayout(0, 18), Style.PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JLabel title = new JLabel(result, SwingConstants.CENTER);
        title.setFont(Style.ROOM_TITLE_FONT);
        title.setForeground(Style.ACCENT);
        root.add(title, BorderLayout.NORTH);
        JPanel scores = new JPanel(new GridLayout(1, 2, 24, 0));
        scores.setOpaque(false);
        for (int i = 0; i < 2; i++) {
            Board board = i == 0 ? player1Board : player2Board;
            JLabel score = new JLabel("PLAYER " + (i + 1) + " · "
                    + java.text.NumberFormat.getIntegerInstance().format(board.getEngine().getScore()) + "점", SwingConstants.CENTER);
            score.setFont(Style.SCORE_FONT);
            score.setForeground(Style.TEXT);
            scores.add(score);
        }
        root.add(scores, BorderLayout.CENTER);
        JButton again = new JButton("다시 대전");
        again.putClientProperty("primary", true);
        Style.applyButtonStyle(again, Style.ACCENT, Style.PANEL);
        again.addActionListener(e -> restartMatch());
        JButton menu = new JButton("메뉴로");
        Style.applyButtonStyle(menu, Style.ACCENT, Style.PANEL);
        menu.addActionListener(e -> returnToMenu());
        JPanel actions = new JPanel(new GridLayout(1, 2, 10, 0));
        actions.setOpaque(false);
        actions.add(again);
        actions.add(menu);
        root.add(actions, BorderLayout.SOUTH);
        resultDialog.setContentPane(root);
        resultDialog.getRootPane().setDefaultButton(again);
        resultDialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "result.menu");
        resultDialog.getRootPane().getActionMap().put("result.menu", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { returnToMenu(); }
        });
        resultDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        resultDialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { returnToMenu(); }
        });
        resultDialog.pack();
        resultDialog.setResizable(false);
        resultDialog.setLocationRelativeTo(this);
        resultDialog.setVisible(true);
    }

    private void restartMatch() {
        if (!isDisplayable()) return;
        Point location = getLocation();
        dispose();
        TwoPlayerTetris next = new TwoPlayerTetris();
        next.setLocation(location);
        next.setVisible(true);
    }

    private void toggleMatchPause() {
        setMatchPaused(!player1Board.getEngine().isPaused(), "두 플레이어가 함께 쉬고 있습니다");
    }

    private void setMatchPaused(boolean paused, String reason) {
        if (gameFinished) return;
        player1Board.setPaused(paused, reason);
        player2Board.setPaused(paused, reason);
    }

    private void installKeyBindings() {
        int[] keys = { KeyEvent.VK_A, KeyEvent.VK_D, KeyEvent.VK_W, KeyEvent.VK_S,
                KeyEvent.VK_SPACE, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP,
                KeyEvent.VK_DOWN, KeyEvent.VK_ENTER, KeyEvent.VK_P, KeyEvent.VK_ESCAPE };
        for (int key : keys) {
            String press = "match.press." + key;
            String release = "match.release." + key;
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, false), press);
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, true), release);
            getRootPane().getActionMap().put(press, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    handleKeyInput(new KeyEvent(player1Board, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
                }
            });
            getRootPane().getActionMap().put(release, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { pressedKeys.remove(key); }
            });
        }
    }

    // 각 플레이어의 게임판과 사이드패널 생성
    private JPanel createPlayerPanel(String playerName, boolean isPlayer1) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Style.BACKGROUND);

        JLabel label = new JLabel(playerName, SwingConstants.CENTER);
        label.setFont(Style.ROOM_TITLE_FONT);
        label.setForeground(Style.TEXT);
        panel.add(label, BorderLayout.NORTH);

        JLabel statusbar = new JLabel("제거한 줄: 0");
        statusbar.setFont(Style.BODY_FONT);
        statusbar.setForeground(Style.MUTED_TEXT);

        String[][] controls;

        if (isPlayer1) {
            controls = new String[][] {
                    { "A D", "이동" },
                    { "W", "왼쪽 회전" },
                    { "S", "오른쪽 회전" },
                    { "SPACE", "즉시 낙하" },
                    { "P / ESC", "함께 일시정지" }
            };
        } else {
            controls = new String[][] {
                    { "← →", "이동" },
                    { "↑", "왼쪽 회전" },
                    { "↓", "오른쪽 회전" },
                    { "ENTER", "즉시 낙하" },
                    { "P / ESC", "함께 일시정지" }
            };
        }

        SidePanel sidePanel = new SidePanel(controls);

        Board board = new Board(statusbar, sidePanel, true);
        // 1PC 대전에서는 방해 아이템을 상대에게 적용
        board.getEngine().setBattleItemMode(true);

        // 클래식과 같은 배치를 사용해 두 게임판과 정보 패널의 칸 비율 유지
        JPanel gamePanel = new JPanel(new Tetris.GameAreaLayout(board, sidePanel, 26));
        gamePanel.setOpaque(false);
        gamePanel.add(board);
        gamePanel.add(sidePanel);

        panel.add(gamePanel, BorderLayout.CENTER);
        panel.add(statusbar, BorderLayout.SOUTH);

        if (isPlayer1)
            player1Board = board;
        else
            player2Board = board;

        return panel;
    }

    // 1P와 2P의 키 입력을 나눠 처리
    private void handleKeyInput(KeyEvent e) {
        if (gameFinished) return;
        int key = e.getKeyCode();
        boolean movement = key == KeyEvent.VK_A || key == KeyEvent.VK_D
                || key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT;
        if (pressedKeys == null) pressedKeys = new HashSet<>();
        if (!movement && !pressedKeys.add(key)) return;
        if (key == KeyEvent.VK_P || key == KeyEvent.VK_ESCAPE) {
            toggleMatchPause();
            return;
        }

        switch (key) {
            // PLAYER 1
            case KeyEvent.VK_A:
                player1Board.moveLeft();
                return;
            case KeyEvent.VK_D:
                player1Board.moveRight();
                return;
            case KeyEvent.VK_W:
                player1Board.rotateLeft();
                break;
            case KeyEvent.VK_S:
                player1Board.rotateRight();
                break;
            case KeyEvent.VK_SPACE:
                player1Board.hardDrop();
                break;

            // PLAYER 2
            case KeyEvent.VK_LEFT:
                player2Board.moveLeft();
                return;
            case KeyEvent.VK_RIGHT:
                player2Board.moveRight();
                return;
            case KeyEvent.VK_UP:
                player2Board.rotateLeft();
                break;
            case KeyEvent.VK_DOWN:
                player2Board.rotateRight();
                break;
            case KeyEvent.VK_ENTER:
                player2Board.hardDrop();
                break;
        }

        player1Board.repaint();
        player2Board.repaint();
    }

    // 현재 2인용 게임 창을 닫고 메인 메뉴로 이동
    private void returnToMenu() {
        if (!isDisplayable()) return;
        Main menu = new Main();
        menu.setLocationRelativeTo(this);
        dispose();
        menu.setVisible(true);
    }

    // 창을 닫을 때 두 게임판과 승패 확인 타이머 정지
    @Override
    public void dispose() {
        if (resultDialog != null) resultDialog.dispose();
        gameFinished = true;
        if (gameCheckTimer != null)
            gameCheckTimer.stop();

        if (player1Board != null)
            player1Board.stop();

        if (player2Board != null)
            player2Board.stop();

        SoundManager.stopBgm();
        super.dispose();
    }
}
