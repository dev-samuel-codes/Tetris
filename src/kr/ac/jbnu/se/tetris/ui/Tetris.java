package kr.ac.jbnu.se.tetris.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import kr.ac.jbnu.se.tetris.game.Board;

public class Tetris extends javax.swing.JFrame {

	private static final Color BACKGROUND = new Color(15, 17, 27);
	private static final Color PANEL = new Color(27, 30, 46);
	private static final Color MUTED = new Color(151, 158, 190);
	private static final Color ACCENT = new Color(151, 126, 255);

	private final JLabel statusbar;

	public Tetris() {
		setTitle("Tetris - Solo");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setBackground(BACKGROUND);

		statusbar = new JLabel("0", SwingConstants.CENTER);
		statusbar.setForeground(new Color(228, 225, 255));
		statusbar.setFont(new Font("SansSerif", Font.BOLD, 36));

		JPanel root = new JPanel(new BorderLayout(0, 22));
		root.setBackground(BACKGROUND);
		root.setBorder(new EmptyBorder(22, 28, 22, 28));
		root.add(createHeader(), BorderLayout.NORTH);

		Board board = new Board(this);
		board.setPreferredSize(new Dimension(360, 720));
		board.setBackground(BACKGROUND);

		JPanel gameArea = new JPanel(new FlowLayout(FlowLayout.CENTER, 28, 0));
		gameArea.setBackground(BACKGROUND);
		gameArea.add(board);
		gameArea.add(createSidePanel());
		root.add(gameArea, BorderLayout.CENTER);

		add(root);
		pack();
		setMinimumSize(new Dimension(760, 700));
		setLocationRelativeTo(null);
		board.start();
	}

	private JPanel createHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(PANEL);
		header.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 4, 0, 0, ACCENT),
				new EmptyBorder(15, 18, 15, 18)));

		JLabel title = new JLabel("TETRIS");
		title.setForeground(Color.WHITE);
		title.setFont(new Font("SansSerif", Font.BOLD, 27));

		JLabel mode = new JLabel("SOLO MODE");
		mode.setForeground(new Color(190, 177, 255));
		mode.setFont(new Font("SansSerif", Font.BOLD, 13));

		header.add(title, BorderLayout.WEST);
		header.add(mode, BorderLayout.EAST);
		return header;
	}

	private JPanel createSidePanel() {
		JPanel sidePanel = new JPanel();
		sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
		sidePanel.setBackground(BACKGROUND);
		sidePanel.setPreferredSize(new Dimension(220, 720));

		JPanel linesCard = createCard();
		JLabel linesTitle = createLabel("LINES CLEARED", MUTED, 13, Font.BOLD);
		linesTitle.setAlignmentX(CENTER_ALIGNMENT);
		statusbar.setAlignmentX(CENTER_ALIGNMENT);
		linesCard.add(linesTitle);
		linesCard.add(Box.createVerticalStrut(18));
		linesCard.add(statusbar);
		sidePanel.add(linesCard);
		sidePanel.add(Box.createVerticalStrut(18));

		JPanel controlsCard = createCard();
		JLabel controlsTitle = createLabel("CONTROLS", Color.WHITE, 16, Font.BOLD);
		controlsTitle.setAlignmentX(LEFT_ALIGNMENT);
		controlsCard.add(controlsTitle);
		controlsCard.add(Box.createVerticalStrut(20));
		addControl(controlsCard, "←  →", "Move");
		addControl(controlsCard, "↑  ↓", "Rotate");
		addControl(controlsCard, "D", "Soft drop");
		addControl(controlsCard, "SPACE", "Hard drop");
		addControl(controlsCard, "P", "Pause");
		sidePanel.add(controlsCard);

		return sidePanel;
	}

	private JPanel createCard() {
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(PANEL);
		card.setBorder(new EmptyBorder(22, 18, 22, 18));
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
		return card;
	}

	private void addControl(JPanel panel, String key, String action) {
		JPanel row = new JPanel(new BorderLayout(12, 0));
		row.setBackground(PANEL);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		JLabel keyLabel = createLabel(key, new Color(190, 177, 255), 13, Font.BOLD);
		JLabel actionLabel = createLabel(action, MUTED, 14, Font.PLAIN);
		row.add(keyLabel, BorderLayout.WEST);
		row.add(actionLabel, BorderLayout.EAST);
		panel.add(row);
		panel.add(Box.createVerticalStrut(9));
	}

	private JLabel createLabel(String text, Color color, int size, int style) {
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(new Font("SansSerif", style, size));
		return label;
	}

	public JLabel getStatusBar() {
		return statusbar;
	}
}
