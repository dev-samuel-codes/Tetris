package frontend;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.*;

import frontend.style.Style;

public class Tetris extends JFrame {

	private static final int MIN_CELL_SIZE = 10;

	JLabel statusbar;
	private final Board board; // 메뉴로 돌아갈 때 게임 정지에 사용

	public Tetris(Resolution resolution) {
		JPanel root = new JPanel(new BorderLayout(0, 12));
		root.setBackground(Style.BACKGROUND);
		root.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		setContentPane(root);

		// 게임 화면에서도 메뉴와 같은 제목과 색상 사용
		JPanel header = new JPanel(new BorderLayout(12, 0));
		header.setBackground(Style.PANEL);
		header.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 4, 0, 0, Style.ACCENT),
				BorderFactory.createEmptyBorder(8, 12, 8, 12)));
		JLabel title = new JLabel("TETRIS");
		title.setFont(Style.ROOM_TITLE_FONT);
		title.setForeground(Style.TEXT);
		JLabel mode = new JLabel("CLASSIC");
		mode.setFont(Style.BODY_FONT);
		mode.setForeground(Style.ACCENT);
		header.add(title, BorderLayout.CENTER);
		header.add(mode, BorderLayout.EAST);
		root.add(header, BorderLayout.NORTH);

		statusbar = new JLabel("제거한 줄: 0");
		statusbar.setFont(Style.BODY_FONT);
		statusbar.setForeground(Style.MUTED_TEXT);

		// 메뉴로 돌아가기 버튼
		JButton menuButton = new JButton("메뉴로 돌아가기");
		Style.applyButtonStyle(menuButton, Style.ACCENT, Style.PANEL);
		menuButton.setFocusable(false); // 버튼이 게임 키 입력 포커스를 가져가지 않도록 설정
		menuButton.addActionListener(e -> returnToMenu());

		// 하단에 제거한 줄 수와 메뉴 버튼 표시
		JPanel footer = new JPanel(new BorderLayout(12, 0));
		footer.setOpaque(false);
		footer.add(statusbar, BorderLayout.CENTER);
		footer.add(menuButton, BorderLayout.EAST);
		root.add(footer, BorderLayout.SOUTH);

		// 점수와 다음 블록, 조작 안내 패널
		SidePanel sidePanel = new SidePanel();
		sidePanel.setMinimumSize(new Dimension(SidePanel.COLS * MIN_CELL_SIZE, Board.BOARD_HEIGHT * MIN_CELL_SIZE));

		// 게임판
		board = new Board(this, sidePanel);
		board.setMinimumSize(new Dimension(Board.BOARD_WIDTH * MIN_CELL_SIZE, Board.BOARD_HEIGHT * MIN_CELL_SIZE));
		// 게임판과 정보 패널을 같은 배율로 확대
		JPanel background = new JPanel(new GameAreaLayout(board, sidePanel, resolution.getCellSize()));
		background.setBackground(Style.BACKGROUND);
		background.add(board);
		background.add(sidePanel);
		root.add(background, BorderLayout.CENTER);

		board.start();
		SoundManager.playBgm(); // BGM 시작

		pack();
		// 처음 표시되는 창의 가로·세로 비율을 이후 크기 변경에도 유지
		AspectRatioResizeHandler resizeHandler = new AspectRatioResizeHandler(getSize(), getMinimumSize());
		setMinimumSize(resizeHandler.minimumSize());
		addComponentListener(resizeHandler);
		setResizable(true);
		setTitle("Tetris");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent event) {
				board.requestFocusInWindow(); // 게임 화면을 열자마자 키보드 조작 가능
			}
		});
	}

	public JLabel getStatusBar() {
		return statusbar;
	}

	// 현재 게임 창을 닫고 메인 메뉴로 이동
	// 나중에 실수로 나가지 않도록 돌아가기 전에 확인창을 띄워도 괜찮을 것 같음
	private void returnToMenu() {
		// 이미 닫힌 창이면 메뉴를 또 만들지 않음
		if (!isDisplayable())
			return;

		// 메뉴를 새로 만들면서 최고 점수도 다시 불러옴
		Main menu = new Main();
		menu.setLocationRelativeTo(this);
		dispose();
		menu.setVisible(true);
	}

	// 창을 닫을 때 타이머와 BGM도 같이 정지
	@Override
	public void dispose() {
		board.stop();
		SoundManager.stopBgm();
		super.dispose();
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
		private static final int GAP_COLS = 1; // 두 패널 사이 여백도 칸 크기에 맞춤
		private final Component board;
		private final Component sidePanel;
		private final int initialCellSize; // 처음 창을 열 때의 칸 크기

		GameAreaLayout(Component board, Component sidePanel, int initialCellSize) {
			this.board = board;
			this.sidePanel = sidePanel;
			this.initialCellSize = initialCellSize;
		}

		@Override
		public void addLayoutComponent(String name, Component component) {
		}

		@Override
		public void removeLayoutComponent(Component component) {
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			return areaSize(parent, initialCellSize);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return areaSize(parent, MIN_CELL_SIZE);
		}

		private Dimension areaSize(Container parent, int cellSize) {
			Insets insets = parent.getInsets();
			return new Dimension((Board.BOARD_WIDTH + SidePanel.COLS + GAP_COLS) * cellSize + insets.left + insets.right,
					Board.BOARD_HEIGHT * cellSize + insets.top + insets.bottom);
		}

		@Override
		public void layoutContainer(Container parent) {
			Insets insets = parent.getInsets();
			int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
			int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
			int totalCols = Board.BOARD_WIDTH + SidePanel.COLS + GAP_COLS;
			int cellSize = Math.min(width / totalCols, height / Board.BOARD_HEIGHT);
			int boardWidth = Board.BOARD_WIDTH * cellSize;
			int sideWidth = SidePanel.COLS * cellSize;
			int gap = GAP_COLS * cellSize;
			int gameHeight = Board.BOARD_HEIGHT * cellSize;
			int left = insets.left + (width - boardWidth - sideWidth - gap) / 2;
			int top = insets.top + (height - gameHeight) / 2;

			board.setBounds(left, top, boardWidth, gameHeight);
			sidePanel.setBounds(left + boardWidth + gap, top, sideWidth, gameHeight);
		}
	}
}
