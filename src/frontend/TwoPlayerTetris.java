package frontend;

import frontend.style.Style;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;


public class TwoPlayerTetris extends JFrame {

    private Board player1Board;
    private Board player2Board;
    private Timer gameCheckTimer;
    private boolean gameFinished = false;

    // 1PC 2인용 공격 효과 종류
    private enum BattleEffect {
        ROTATION_LOCK,
        SCORE_PENALTY,
        COLUMN_CLEAR
    }

    //방금 어떤 플레이어가 칸을 지웠는지
    private int previousPlayer1Lines = 0;
    private int previousPlayer2Lines = 0;

    // 1PC 2인용 게임 창 생성
    public TwoPlayerTetris() {
        setTitle("Tetris - 1PC 2인");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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
        menuButton.setFocusable(false);
        menuButton.addActionListener(e -> returnToMenu());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.add(menuButton);
        root.add(footer, BorderLayout.SOUTH);

        // 두 플레이어 게임 시작
        player1Board.start();
        player2Board.start();

        // 두 플레이어의 게임오버 여부 확인
        gameCheckTimer = new Timer(200, e -> {
            checkGameOver();
            checkAttack();
        });
        gameCheckTimer.start();

        // BGM 시작
        SoundManager.playBgm();

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyInput(e);
            }
        });

        setFocusable(true);
        requestFocusInWindow();

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

    // 두 플레이어가 누적 2줄을 제거할 때마다 공격
    private void checkAttack() {
        int player1Lines = player1Board.getEngine().getNumLinesRemoved();
        int player2Lines = player2Board.getEngine().getNumLinesRemoved();

        int player1Attacks = player1Lines / 2 - previousPlayer1Lines / 2;
        int player2Attacks = player2Lines / 2 - previousPlayer2Lines / 2;

        for (int i = 0; i < player1Attacks; i++) {
            applyBattleEffect(player2Board);
        }

        for (int i = 0; i < player2Attacks; i++) {
            applyBattleEffect(player1Board);
        }

        previousPlayer1Lines = player1Lines;
        previousPlayer2Lines = player2Lines;
    }

    // 세 가지 공격 효과 중 하나를 랜덤으로 선택
    private BattleEffect getRandomBattleEffect() {
        BattleEffect[] effects = BattleEffect.values();
        int index = (int) (Math.random() * effects.length);
        return effects[index];
    }

    // 선택된 공격 효과를 상대 플레이어에게 적용
    private void applyBattleEffect(Board targetBoard) {
        BattleEffect effect = getRandomBattleEffect();

        switch (effect) {
            case ROTATION_LOCK:
                targetBoard.getEngine().applyBattleRotationLock();
                targetBoard.showBattleMessage("다음 2블럭동안 회전 금지!");
                break;

            case SCORE_PENALTY:
                targetBoard.getEngine().applyBattleScorePenalty();
                targetBoard.showBattleMessage("점수 -20");
                break;

            case COLUMN_CLEAR:
                targetBoard.getEngine().applyBattleColumnClear();
                targetBoard.showBattleMessage("랜덤 열 제거!");
                break;
        }

        targetBoard.repaint();
    }

    // 두 플레이어의 게임오버 상태를 확인하고 승자를 결정
    private void checkGameOver() {
        if (gameFinished)
            return;

        boolean player1Alive = player1Board.getEngine().isStarted();
        boolean player2Alive = player2Board.getEngine().isStarted();

        if (!player1Alive || !player2Alive) {
            gameFinished = true;
            gameCheckTimer.stop();

            if (!player1Alive && !player2Alive) {
                JOptionPane.showMessageDialog(this, "무승부입니다.");
            } else if (!player1Alive) {
                JOptionPane.showMessageDialog(this, "PLAYER 2 승리!");
            } else {
                JOptionPane.showMessageDialog(this, "PLAYER 1 승리!");
            }
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
                    { "SPACE", "즉시 낙하" }
            };
        } else {
            controls = new String[][] {
                    { "← →", "이동" },
                    { "↑", "왼쪽 회전" },
                    { "↓", "오른쪽 회전" },
                    { "ENTER", "즉시 낙하" }
            };
        }

        SidePanel sidePanel = new SidePanel(controls);

        Board board = new Board(statusbar, sidePanel, false);

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
        int key = e.getKeyCode();

        switch (key) {
            // PLAYER 1
            case KeyEvent.VK_A:
                player1Board.moveLeft();
                return;
            case KeyEvent.VK_D:
                player1Board.moveRight();
                return;
            case KeyEvent.VK_W:
                player1Board.getEngine().rotateLeft();
                break;
            case KeyEvent.VK_S:
                player1Board.getEngine().rotateRight();
                break;
            case KeyEvent.VK_SPACE:
                player1Board.getEngine().dropDown();
                break;

            // PLAYER 2
            case KeyEvent.VK_LEFT:
                player2Board.moveLeft();
                return;
            case KeyEvent.VK_RIGHT:
                player2Board.moveRight();
                return;
            case KeyEvent.VK_UP:
                player2Board.getEngine().rotateLeft();
                break;
            case KeyEvent.VK_DOWN:
                player2Board.getEngine().rotateRight();
                break;
            case KeyEvent.VK_ENTER:
                player2Board.getEngine().dropDown();
                break;
        }

        player1Board.repaint();
        player2Board.repaint();
    }

    // 현재 2인용 게임 창을 닫고 메인 메뉴로 이동
    private void returnToMenu() {
        Main menu = new Main();
        menu.setLocationRelativeTo(this);
        dispose();
        menu.setVisible(true);
    }

    // 창을 닫을 때 두 게임판과 승패 확인 타이머 정지
    @Override
    public void dispose() {
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
