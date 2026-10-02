package frontend;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.*;

public class Tetris extends JFrame {

	private static final int INITIAL_CELL_SIZE = 20;
	private static final int MIN_CELL_SIZE = 10;

	JLabel statusbar;

	public Tetris() {
		statusbar = new JLabel(" 0");
		add(statusbar, BorderLayout.SOUTH);

		// main board
		Board board = new Board(this);
		board.setMinimumSize(new Dimension(Board.BOARD_WIDTH * MIN_CELL_SIZE, Board.BOARD_HEIGHT * MIN_CELL_SIZE));

		// SidePanel
		SidePanel sidePanel = new SidePanel();
		sidePanel.setMinimumSize(new Dimension(SidePanel.COLS * MIN_CELL_SIZE, Board.BOARD_HEIGHT * MIN_CELL_SIZE));

		// 게임판과 정보 패널을 같은 배율로 확대
		JPanel background = new JPanel(new GameAreaLayout(board, sidePanel));
		background.add(board);
		background.add(sidePanel);
		add(background, BorderLayout.CENTER);

		board.start();

		pack();
		// 처음 표시되는 창의 가로·세로 비율을 이후 크기 변경에도 유지
		AspectRatioResizeHandler resizeHandler = new AspectRatioResizeHandler(getSize(), getMinimumSize());
		setMinimumSize(resizeHandler.minimumSize());
		addComponentListener(resizeHandler);
		setResizable(true);
		setTitle("Tetris");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	public JLabel getStatusBar() {
		return statusbar;
	}

	// 한쪽 크기를 변경하면 다른 쪽도 같은 배율로 보정
	static class AspectRatioResizeHandler extends ComponentAdapter {
		private final Dimension baseSize;
		private final double minimumScale;
		private Dimension lastSize;

		AspectRatioResizeHandler(Dimension baseSize, Dimension minimumSize) {
			this.baseSize = new Dimension(baseSize);
			this.lastSize = new Dimension(baseSize);
			this.minimumScale = Math.max(
					(double) minimumSize.width / baseSize.width,
					(double) minimumSize.height / baseSize.height);
		}

		Dimension minimumSize() {
			return new Dimension((int) Math.ceil(baseSize.width * minimumScale),
					(int) Math.ceil(baseSize.height * minimumScale));
		}

		Dimension constrainSize(Dimension requestedSize) {
			double widthChange = Math.abs(requestedSize.width - lastSize.width) / (double) baseSize.width;
			double heightChange = Math.abs(requestedSize.height - lastSize.height) / (double) baseSize.height;
			double scale = widthChange >= heightChange
					? (double) requestedSize.width / baseSize.width
					: (double) requestedSize.height / baseSize.height;
			scale = Math.max(minimumScale, scale);
			lastSize = new Dimension((int) Math.ceil(baseSize.width * scale),
					(int) Math.ceil(baseSize.height * scale));
			return new Dimension(lastSize);
		}

		@Override
		public void componentResized(ComponentEvent event) {
			Component window = event.getComponent();
			Dimension requestedSize = window.getSize();
			if (requestedSize.equals(lastSize))
				return;

			Dimension adjustedSize = constrainSize(requestedSize);
			if (!requestedSize.equals(adjustedSize))
				window.setSize(adjustedSize);
		}
	}

	// 게임판과 정보 패널은 같은 정사각형 칸 크기로 배치
	static class GameAreaLayout implements LayoutManager {
		private final Component board;
		private final Component sidePanel;

		GameAreaLayout(Component board, Component sidePanel) {
			this.board = board;
			this.sidePanel = sidePanel;
		}

		@Override
		public void addLayoutComponent(String name, Component component) {
		}

		@Override
		public void removeLayoutComponent(Component component) {
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			return areaSize(parent, INITIAL_CELL_SIZE);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return areaSize(parent, MIN_CELL_SIZE);
		}

		private Dimension areaSize(Container parent, int cellSize) {
			Insets insets = parent.getInsets();
			return new Dimension((Board.BOARD_WIDTH + SidePanel.COLS) * cellSize + insets.left + insets.right,
					Board.BOARD_HEIGHT * cellSize + insets.top + insets.bottom);
		}

		@Override
		public void layoutContainer(Container parent) {
			Insets insets = parent.getInsets();
			int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
			int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
			int totalCols = Board.BOARD_WIDTH + SidePanel.COLS;
			int cellSize = Math.min(width / totalCols, height / Board.BOARD_HEIGHT);
			int boardWidth = Board.BOARD_WIDTH * cellSize;
			int sideWidth = SidePanel.COLS * cellSize;
			int gameHeight = Board.BOARD_HEIGHT * cellSize;
			int left = insets.left + (width - boardWidth - sideWidth) / 2;
			int top = insets.top + (height - gameHeight) / 2;

			board.setBounds(left, top, boardWidth, gameHeight);
			sidePanel.setBounds(left + boardWidth, top, sideWidth, gameHeight);
		}
	}
}
