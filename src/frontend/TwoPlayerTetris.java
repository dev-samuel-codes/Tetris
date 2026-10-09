package frontend;

import frontend.style.Style;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;


public class TwoPlayerTetris extends JFrame {

    private Board player1Board;
    private Board player2Board;

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
        setLocationRelativeTo(null);
        setResizable(false);
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

        SidePanel sidePanel = new SidePanel();
        sidePanel.setPreferredSize(new Dimension(150, 0));

        Board board = new Board(statusbar, sidePanel, false);

        JPanel gamePanel = new JPanel(new BorderLayout(10, 0));
        gamePanel.setOpaque(false);
        gamePanel.add(board, BorderLayout.CENTER);
        gamePanel.add(sidePanel, BorderLayout.EAST);

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
                player1Board.getEngine().moveLeft();
                break;
            case KeyEvent.VK_D:
                player1Board.getEngine().moveRight();
                break;
            case KeyEvent.VK_W:
                player1Board.getEngine().rotateLeft();
                break;
            case KeyEvent.VK_S:
                player1Board.getEngine().oneLineDown();
                break;
            case KeyEvent.VK_SPACE:
                player1Board.getEngine().dropDown();
                break;

            // PLAYER 2
            case KeyEvent.VK_LEFT:
                player2Board.getEngine().moveLeft();
                break;
            case KeyEvent.VK_RIGHT:
                player2Board.getEngine().moveRight();
                break;
            case KeyEvent.VK_UP:
                player2Board.getEngine().rotateLeft();
                break;
            case KeyEvent.VK_DOWN:
                player2Board.getEngine().oneLineDown();
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

    // 창을 닫을 때 두 게임판의 타이머도 정지
    @Override
    public void dispose() {
        if (player1Board != null)
            player1Board.stop();

        if (player2Board != null)
            player2Board.stop();

        SoundManager.stopBgm();
        super.dispose();
    }
}