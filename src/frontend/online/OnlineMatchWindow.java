package frontend.online;

import frontend.BlockPainter;
import frontend.Resolution;
import frontend.Shape;
import frontend.Tetrominoes;
import frontend.style.Style;
import shared.network.OnlineFrame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

// 서버 스냅숏만 표시합니다. 낙하·점수·승패의 계산은 서버가 담당합니다.
// 나중에 연결 폼과 관전 화면이 늘어나면 JPanel 단위로 분리할 수 있습니다.
public final class OnlineMatchWindow extends JFrame {
    static String statusText(OnlineFrame frame) {
        if (frame.isFinished()) {
            String result = frame.getWinner() < 0 ? "SCORE".equals(frame.getReason()) ? "무승부" : "방 종료"
                    : frame.getWinner() == frame.getSeat() ? "승리!" : "패배";
            String reason = "DISCONNECT".equals(frame.getReason()) ? " · 상대 연결 해제"
                    : "LEFT".equals(frame.getReason()) ? " · 상대가 방을 나갔습니다"
                    : "EXPIRED".equals(frame.getReason()) ? " · 대기 시간이 만료되었습니다" : "";
            return result + reason;
        }
        if ("COUNTDOWN".equals(frame.getPhase()))
            return "시작까지 " + Math.max(1, (frame.getCountdownMillis() + 999) / 1000) + "초";
        if ("WAITING".equals(frame.getPhase()))
            return frame.getPlayer(1 - frame.getSeat()).getNickname().isEmpty()
                    ? "상대 참가를 기다리고 있습니다 · 방 코드를 알려 주세요"
                    : "두 명 모두 준비하면 3초 뒤 시작합니다";
        return !frame.getPlayer(frame.getSeat()).isAlive() ? "게임오버 · 상대의 최종 점수를 기다립니다"
                : !frame.getPlayer(1 - frame.getSeat()).isAlive() ? "상대 게임오버 · 상대 점수를 넘으면 승리합니다"
                : "경기 중 · 같은 블록 순서 · 점수 대결";
    }

    static String formatElapsedTime(long elapsedMillis) {
        long seconds = Math.max(0, elapsedMillis) / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, Color color, Font font) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(font);
        return label;
    }

    static final class BoardView extends JPanel {
        final JLabel name;
        final JLabel metrics;
        final BoardCanvas canvas;
        final NextCanvas next = new NextCanvas();
        private final String title;
        BoardView(String title, Resolution resolution) {
            super(new BorderLayout(0, 8));
            this.title = title;
            setOpaque(false);
            JPanel heading = transparent(new BorderLayout(0, 6));
            name = label(title, Style.TEXT, Style.SCORE_FONT);
            metrics = label("0점 · 0줄", Style.ACCENT, Style.MONO_FONT);
            heading.add(name, BorderLayout.NORTH);
            heading.add(metrics, BorderLayout.CENTER);
            JPanel nextRow = transparent(new BorderLayout(10, 0));
            nextRow.add(label("다음 블록", Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.WEST);
            nextRow.add(next, BorderLayout.CENTER);
            heading.add(nextRow, BorderLayout.SOUTH);
            add(heading, BorderLayout.NORTH);
            canvas = new BoardCanvas();
            canvas.setPreferredSize(new Dimension(10 * resolution.getCellSize(), 22 * resolution.getCellSize()));
            add(canvas, BorderLayout.CENTER);
        }
        void update(OnlineFrame.Player player) { update(player, true); }
        void update(OnlineFrame.Player player, boolean started) {
            boolean empty = player.getNickname().isEmpty();
            name.setText(title + " · " + (empty ? "참가 대기" : player.getNickname()) + (player.isReady() ? " · 준비" : ""));
            metrics.setText(NumberFormat.getIntegerInstance().format(player.getScore()) + "점 · " + player.getLines() + "줄");
            canvas.cells = player.getCells();
            canvas.ended = started && !empty && !player.isAlive();
            next.shape = player.getNextShape();
            next.repaint();
            canvas.repaint();
        }
    }

    static final class BoardCanvas extends JPanel {
        int[] cells = new int[220];
        boolean ended;
        BoardCanvas() { setOpaque(false); setMinimumSize(new Dimension(100, 220)); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                int size = Math.max(1, Math.min((getWidth() - 2) / 10, (getHeight() - 2) / 22));
                int width = 10 * size, height = 22 * size;
                int left = (getWidth() - width) / 2, top = (getHeight() - height) / 2;
                g.setColor(Style.BOARD_BACKGROUND);
                g.fillRect(left, top, width, height);
                g.setColor(Style.BOARD_GRID);
                for (int x = 1; x < 10; x++) g.drawLine(left + x * size, top, left + x * size, top + height);
                for (int y = 1; y < 22; y++) g.drawLine(left, top + y * size, left + width, top + y * size);
                for (int y = 0; y < 22; y++) {
                    for (int x = 0; x < 10; x++) {
                        int value = cells[y * 10 + x];
                        if (value > 0 && value < 8)
                            BlockPainter.drawSquare(g, left + x * size, top + (21 - y) * size, size, Tetrominoes.values()[value]);
                    }
                }
                g.setColor(Style.BOARD_BORDER);
                g.drawRect(left - 1, top - 1, width + 1, height + 1);
                if (ended) {
                    g.setColor(Style.OVERLAY);
                    g.fillRect(left, top, width, height);
                    g.setColor(Style.PINK);
                    g.setFont(Style.SCORE_FONT);
                    FontMetrics metrics = g.getFontMetrics();
                    g.drawString("게임오버", left + (width - metrics.stringWidth("게임오버")) / 2, top + height / 2);
                }
            } finally { g.dispose(); }
        }
    }

    static final class NextCanvas extends JPanel {
        int shape;
        NextCanvas() { setOpaque(false); setPreferredSize(new Dimension(90, 44)); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (shape < 1 || shape > 7)
                return;
            Shape piece = new Shape();
            piece.setShape(Tetrominoes.values()[shape]);
            int minX = piece.minX(), minY = piece.minY();
            for (int i = 0; i < 4; i++)
                BlockPainter.drawSquare(graphics, 3 + (piece.x(i) - minX) * 10,
                        3 + (piece.y(i) - minY) * 10, 10, piece.getShape());
        }
    }
}
