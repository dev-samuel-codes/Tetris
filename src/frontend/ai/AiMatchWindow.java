package frontend.ai;

import backend.ai.AiDifficulty;
import backend.ai.AiMatchClient;
import backend.ai.MatchFrame;
import frontend.BlockPainter;
import frontend.Main;
import frontend.Resolution;
import frontend.Tetrominoes;
import frontend.style.Style;
import frontend.ui.PixelLogo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

// 같은 블록 순서로 점수를 겨루는 AI 대전 화면
public final class AiMatchWindow extends JFrame {
    private static final int TICK_MILLIS = 50;
    private static final int BOARD_WIDTH = 10;
    private static final int BOARD_HEIGHT = 22;

    private static JPanel page() {
        JPanel root = Style.createBackground(new BorderLayout(0, 18));
        root.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
        return root;
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel header(String mode) {
        JPanel heading = transparent(new BorderLayout(0, 8));
        JLabel logo = new JLabel(new PixelLogo(3));
        logo.getAccessibleContext().setAccessibleName("Tetris");
        heading.add(logo, BorderLayout.NORTH);
        heading.add(label(mode, Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.SOUTH);
        return heading;
    }

    private static JLabel label(String text, Color color, java.awt.Font font) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(font);
        return label;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        Style.applyButtonStyle(button, Style.ACCENT, Style.PANEL);
        button.setFocusable(false);
        return button;
    }

    static String formatElapsedTime(long elapsedTicks) {
        long seconds = Math.max(0L, elapsedTicks) / (1000 / TICK_MILLIS);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static String score(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    private static final class BoardView extends JPanel {
        private final JLabel scoreLabel;
        private final JLabel linesLabel;
        private final BoardCanvas canvas;

        BoardView(String title) {
            super(new BorderLayout(0, 10));
            setOpaque(false);
            JPanel heading = transparent(new BorderLayout(0, 8));
            heading.add(label(title, Style.TEXT, Style.SCORE_FONT), BorderLayout.NORTH);
            JPanel metrics = transparent(new BorderLayout(10, 0));
            scoreLabel = label("0", Style.ACCENT, Style.MONO_FONT.deriveFont(26f));
            linesLabel = label("0줄", Style.MUTED_TEXT, Style.SMALL_FONT);
            metrics.add(scoreLabel, BorderLayout.WEST);
            metrics.add(linesLabel, BorderLayout.EAST);
            heading.add(metrics, BorderLayout.SOUTH);
            add(heading, BorderLayout.NORTH);
            canvas = new BoardCanvas();
            add(canvas, BorderLayout.CENTER);
        }

        void update(MatchFrame.BoardState state) {
            scoreLabel.setText(score(state.getScore()));
            linesLabel.setText(state.getLines() + "줄");
            canvas.cells = state.getBoard();
            canvas.ended = state.isEnded();
            canvas.repaint();
        }

        void setPaused(boolean paused) {
            canvas.paused = paused;
            canvas.repaint();
        }
    }

    private static final class BoardCanvas extends JPanel {
        private int[] cells = new int[BOARD_WIDTH * BOARD_HEIGHT];
        private boolean ended;
        private boolean paused;

        BoardCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(300, 660));
            setMinimumSize(new Dimension(100, 220));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                int size = Math.max(1, Math.min((getWidth() - 2) / BOARD_WIDTH, (getHeight() - 2) / BOARD_HEIGHT));
                int width = size * BOARD_WIDTH;
                int height = size * BOARD_HEIGHT;
                int left = (getWidth() - width) / 2;
                int top = (getHeight() - height) / 2;
                g.setColor(Style.BOARD_BACKGROUND);
                g.fillRect(left, top, width, height);
                g.setColor(Style.BOARD_GRID);
                for (int x = 1; x < BOARD_WIDTH; x++)
                    g.drawLine(left + x * size, top, left + x * size, top + height);
                for (int y = 1; y < BOARD_HEIGHT; y++)
                    g.drawLine(left, top + y * size, left + width, top + y * size);
                Tetrominoes[] shapes = Tetrominoes.values();
                for (int y = 0; y < BOARD_HEIGHT; y++) {
                    for (int x = 0; x < BOARD_WIDTH; x++) {
                        int value = cells[y * BOARD_WIDTH + x];
                        if (value > 0 && value < shapes.length)
                            BlockPainter.drawSquare(g, left + x * size, top + (BOARD_HEIGHT - y - 1) * size, size, shapes[value]);
                    }
                }
                g.setColor(Style.BOARD_BORDER);
                g.drawRect(left - 1, top - 1, width + 1, height + 1);
                if (ended || paused) {
                    g.setColor(Style.OVERLAY);
                    g.fillRect(left, top, width, height);
                    g.setColor(ended ? Style.PINK : Style.TEXT);
                    g.setFont(Style.SCORE_FONT);
                    String text = ended ? "종료" : "일시정지";
                    FontMetrics metrics = g.getFontMetrics();
                    g.drawString(text, left + (width - metrics.stringWidth(text)) / 2,
                            top + height / 2 + metrics.getAscent() / 2);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
