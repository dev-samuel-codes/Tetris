package frontend;

import java.awt.*;

import javax.swing.*;

public class Tetris extends JFrame {

	JLabel statusbar;

	public Tetris() {
		// 픽셀 크기
		int cellSize = 20;

		statusbar = new JLabel(" 0");
		add(statusbar, BorderLayout.SOUTH);

		// main board
		Board board = new Board(this);
		board.setPreferredSize(new Dimension(Board.BOARD_WIDTH * cellSize, Board.BOARD_HEIGHT * cellSize));

		// SidePanel
		SidePanel sidePanel = new SidePanel();
		sidePanel.setPreferredSize(new Dimension(SidePanel.COLS * cellSize, Board.BOARD_HEIGHT * cellSize));

		// main ,side 묶기
		JPanel gameArea = new JPanel(new BorderLayout());
		gameArea.add(board, BorderLayout.CENTER);
		gameArea.add(sidePanel, BorderLayout.EAST);

		// main center에 배치
		JPanel background = new JPanel(new GridBagLayout());
		background.add(gameArea);
		add(background, BorderLayout.CENTER);

		board.start();

		pack();
		setTitle("Tetris");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	public JLabel getStatusBar() {
		return statusbar;
	}
}