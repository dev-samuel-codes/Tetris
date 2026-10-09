package kr.ac.jbnu.se.tetris.ui;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import kr.ac.jbnu.se.tetris.game.Board;

public class Tetris extends JFrame {
    private final JLabel statusBar;

    public Tetris() {
        setTitle("Tetris");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(360, 680);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        statusBar = new JLabel("0");
        root.add(statusBar, BorderLayout.SOUTH);

        Board board = new Board(this);
        root.add(board, BorderLayout.CENTER);

        setContentPane(root);
        board.start();
    }

    public JLabel getStatusBar() {
        return statusBar;
    }
}
