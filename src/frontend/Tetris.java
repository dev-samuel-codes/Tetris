package frontend;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.*;

import frontend.style.Style;
import frontend.ui.PixelLogo;

public class Tetris extends JFrame {

	private static final int MIN_CELL_SIZE = 20; // 일반 화면에서는 점수와 키 안내가 읽히는 최소 칸 크기로 사용

	JLabel statusbar;
	private final Board board; // 메뉴로 돌아갈 때 게임 정지에 사용

	public Tetris(Resolution resolution) {
		JPanel root = Style.createBackground(new BorderLayout(0, 16));
		root.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
		setContentPane(root);

		// 게임 화면에서도 메뉴와 같은 제목과 색상 사용
		JPanel header = new JPanel(new BorderLayout(12, 0));
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));
		JLabel title = new JLabel(new PixelLogo(3));
		title.setHorizontalAlignment(SwingConstants.LEFT);
		title.getAccessibleContext().setAccessibleName("Tetris");
		JLabel mode = new JLabel("클래식");
		mode.setFont(Style.SMALL_FONT);
		mode.setForeground(Style.MUTED_TEXT);
		header.add(title, BorderLayout.CENTER);
		header.add(mode, BorderLayout.EAST);
		root.add(header, BorderLayout.NORTH);

		statusbar = new JLabel("제거한 줄: 0");
		statusbar.setFont(Style.SMALL_FONT);
		statusbar.setForeground(Style.MUTED_TEXT);

		// 메뉴로 돌아가기 버튼
		JButton menuButton = new JButton("← 메뉴로 돌아가기");
		menuButton.getAccessibleContext().setAccessibleName("메뉴로 돌아가기");
		Style.applyButtonStyle(menuButton, Style.ACCENT, Style.PANEL);
		menuButton.setFont(Style.BODY_FONT);
		menuButton.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
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
		// 크게를 골라도 제목과 메뉴 버튼이 화면 밖으로 나가지 않도록 작업 영역 안에 배치
		fitOnScreen();
		// 처음 표시되는 창의 가로·세로 비율을 이후 크기 변경에도 유지
		// 작업 영역이 작으면 20픽셀 기준 최소 크기도 낮춰 화면에 맞춘 창을 다시 키우지 않음
		Dimension layoutMinimum = getMinimumSize();
		Dimension fittedMinimum = new Dimension(Math.min(layoutMinimum.width, getWidth()),
				Math.min(layoutMinimum.height, getHeight()));
		AspectRatioResizeHandler resizeHandler = new AspectRatioResizeHandler(getSize(), fittedMinimum);
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

	private void fitOnScreen() {
		GraphicsConfiguration configuration = getGraphicsConfiguration();
		Rectangle screen = configuration.getBounds();
		// 메뉴 막대와 Dock 영역을 제외하고 추가 여백을 둔 작업 영역 사용
		// 나중에 다른 모니터로 이동하면 해당 모니터의 작업 영역으로 다시 맞춰도 괜찮을 것 같음
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
		int availableWidth = screen.width - insets.left - insets.right - 32;
		int availableHeight = screen.height - insets.top - insets.bottom - 32;
		double scale = Math.min(1, Math.min(availableWidth / (double) getWidth(),
				availableHeight / (double) getHeight()));
		if (scale < 1)
			setSize((int) (getWidth() * scale), (int) (getHeight() * scale));
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
			// 더 크게 바뀐 축을 기준으로 다른 축도 보정해 처음 창 비율 유지
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
			// 비율 보정으로 다시 발생한 크기 변경은 반복 처리하지 않음
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
			// 남은 가로·세로 공간에 들어가는 정수 칸 크기를 사용해 블록을 정사각형으로 표시
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
