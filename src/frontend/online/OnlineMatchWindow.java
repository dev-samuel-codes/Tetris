package frontend.online;

import frontend.BlockPainter;
import frontend.Main;
import frontend.Resolution;
import frontend.ScoreManager;
import frontend.Shape;
import frontend.Tetrominoes;
import frontend.network.OnlineClient;
import frontend.network.ScoreClient;
import frontend.style.Style;
import frontend.ui.PixelLogo;
import shared.network.OnlineFrame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import frontend.GameSettings;
import frontend.engine.GameEngine.ItemType;
import java.awt.GradientPaint;

// 서버 스냅숏만 표시합니다. 낙하·점수·승패의 계산은 서버가 담당합니다.
// 나중에 연결 폼과 관전 화면이 늘어나면 JPanel 단위로 분리할 수 있습니다.
public final class OnlineMatchWindow extends JFrame {
    private final Resolution resolution;
    private final Set<String> pressedKeys = new HashSet<String>();
    private OnlineSession session;
    private long generation;
    private boolean disposed;
    private OnlineFrame latest;
    private JTextField hostField;
    private JTextField portField;
    private JTextField nicknameField;
    private JTextField codeField;
    private JLabel formStatus;
    private JButton createButton;
    private JButton joinButton;
    private JLabel status;
    private JLabel clock;
    private JLabel room;
    private JButton ready;
    private BoardView own;
    private BoardView other;
    private String savedHost = System.getProperty("tetris.server.host", "131.186.39.107");
    private String savedPort = System.getProperty("tetris.server.port", "5000");
    private String savedNickname = ScoreManager.getNickname();
    private String savedCode = "";

    public OnlineMatchWindow(Resolution resolution) {
        this.resolution = resolution;
        setTitle("Tetris · 온라인 점수 대전");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(2 * 10 * resolution.getCellSize() + 220, screen.width - 48),
                Math.min(22 * resolution.getCellSize() + 275, screen.height - 64));
        setMinimumSize(new Dimension(660, 600));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent event) {
                clearInput();
            }
        });
        showForm();
    }

    private void showForm() {
        stopSession();
        JPanel root = page();
        root.add(header("온라인 1:1 · 같은 블록 순서 · 점수 대결"), BorderLayout.NORTH);
        JPanel fields = Style.framedPanel(new GridLayout(0, 1, 0, 8), Style.PANEL);
        fields.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        hostField = field(savedHost, "서버 주소");
        portField = field(savedPort, "포트");
        nicknameField = field(savedNickname, "닉네임");
        codeField = field(savedCode, "방 코드 (영문 대문자·숫자 6자리)");
        addField(fields, "서버 주소", hostField);
        addField(fields, "포트", portField);
        addField(fields, "닉네임 (문자·숫자·_·-, 1~16자)", nicknameField);
        addField(fields, "방 코드 (참가할 때 영문 대문자·숫자 6자리 입력)", codeField);
        JPanel center = transparent(new BorderLayout(0, 16));
        center.add(fields, BorderLayout.NORTH);
        formStatus = label("방을 만들고 코드를 상대에게 알려 주세요. 두 명이 준비하면 시작합니다.", Style.MUTED_TEXT, Style.BODY_FONT);
        center.add(formStatus, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        JPanel actions = transparent(new GridLayout(1, 3, 12, 0));
        createButton = button("방 만들기");
        joinButton = button("방 참가");
        createButton.addActionListener(event -> connect(false));
        joinButton.addActionListener(event -> connect(true));
        JButton menu = button("메뉴 복귀");
        menu.addActionListener(event -> returnToMenu());
        actions.add(createButton);
        actions.add(joinButton);
        actions.add(menu);
        root.add(actions, BorderLayout.SOUTH);
        install(root);
    }

    private void connect(boolean joining) {
        final OnlineClient client;
        try {
            savedHost = hostField.getText().trim();
            savedPort = portField.getText().trim();
            savedNickname = ScoreClient.normalizeNickname(nicknameField.getText());
            savedCode = codeField.getText().trim().toUpperCase(Locale.ROOT);
            if (joining && !savedCode.matches("[A-Z0-9]{6}"))
                throw new IllegalArgumentException("방 코드는 영문 대문자·숫자 6자리로 입력해 주세요.");
            client = new OnlineClient(savedHost, Integer.parseInt(savedPort));
        } catch (IllegalArgumentException error) {
            formStatus.setText(error instanceof NumberFormatException ? "포트는 1~65535의 숫자로 입력해 주세요." : error.getMessage());
            formStatus.setForeground(Style.PINK);
            return;
        }
        stopSession();
        createButton.setEnabled(false);
        joinButton.setEnabled(false);
        formStatus.setText("서버에 연결하는 중입니다. 메뉴 복귀로 취소할 수 있습니다.");
        formStatus.setForeground(Style.MUTED_TEXT);
        final long currentGeneration = generation;
        session = new OnlineSession(client, frame -> {
            if (disposed || currentGeneration != generation)
                return;
            if (own == null)
                showMatch();
            applyFrame(frame);
        }, error -> {
            if (disposed || currentGeneration != generation)
                return;
            String message = error.getMessage() == null ? "연결을 계속할 수 없습니다." : error.getMessage();
            if (own == null) {
                showForm();
                formStatus.setText(message);
                formStatus.setToolTipText(message);
                formStatus.setForeground(Style.PINK);
            } else {
                clearInput();
                ready.setEnabled(false);
                status.setText("연결 해제 · " + message);
                status.setToolTipText(message);
                status.setForeground(Style.PINK);
            }
        });
        session.start(savedNickname, joining ? savedCode : null);
    }

    private void showMatch() {
        JPanel root = page();
        JPanel heading = header("온라인 1:1 점수 대전");
        JPanel info = transparent(new BorderLayout(16, 0));
        room = label("방 코드", Style.ACCENT, Style.MONO_FONT);
        clock = label("00:00", Style.TEXT, Style.MONO_FONT);
        info.add(room, BorderLayout.WEST);
        info.add(clock, BorderLayout.EAST);
        heading.add(info, BorderLayout.SOUTH);
        root.add(heading, BorderLayout.NORTH);
        JPanel center = transparent(new BorderLayout(0, 12));
        status = label("상대 참가를 기다리고 있습니다", Style.TEXT, Style.BUTTON_FONT);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        center.add(status, BorderLayout.NORTH);
        JPanel boards = transparent(new GridLayout(1, 2, 24, 0));
        own = new BoardView("나", resolution);
        other = new BoardView("상대", resolution);
        boards.add(own);
        boards.add(other);
        center.add(boards, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        JPanel footer = transparent(new BorderLayout(0, 10));
        JLabel keys = label("← → 이동　 ↑ ↓ 회전　 D 하강　 SPACE 즉시 낙하", Style.MUTED_TEXT, Style.SMALL_FONT);
        keys.setHorizontalAlignment(SwingConstants.CENTER);
        footer.add(keys, BorderLayout.NORTH);
        JPanel actions = transparent(new GridLayout(1, 3, 12, 0));
        ready = button("준비하기");
        ready.addActionListener(event -> {
            if (session != null) {
                ready.setEnabled(false);
                session.ready();
            }
        });
        JButton leave = button("방 나가기");
        leave.addActionListener(event -> showForm());
        JButton menu = button("메뉴 복귀");
        menu.addActionListener(event -> returnToMenu());
        actions.add(ready);
        actions.add(leave);
        actions.add(menu);
        footer.add(actions, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        bindKeys(root);
        install(root);
        // 연결 폼보다 경기 화면에 높이를 더 배정해 두 보드의 칸을 읽을 수 있게 표시
        setMinimumSize(new Dimension(660, Math.min(720, Toolkit.getDefaultToolkit().getScreenSize().height - 96)));
    }

    private void applyFrame(OnlineFrame frame) {
        latest = frame;
        room.setText("방 " + frame.getRoomCode());
        clock.setText(formatElapsedTime(frame.getElapsedMillis()));
        OnlineFrame.Player player = frame.getPlayer(frame.getSeat());
        OnlineFrame.Player opponent = frame.getPlayer(1 - frame.getSeat());
        boolean started = "PLAYING".equals(frame.getPhase()) || (frame.isFinished() && "SCORE".equals(frame.getReason()));
        own.update(player, started);
        other.update(opponent, started);
        ready.setEnabled("WAITING".equals(frame.getPhase()) && !player.isReady());
        ready.setText(player.isReady() ? "준비 완료" : "준비하기");
        status.setText(statusText(frame));
        status.setForeground(frame.isFinished() ? frame.getWinner() == frame.getSeat() ? Style.GREEN : Style.ACCENT : Style.TEXT);
        if (!"PLAYING".equals(frame.getPhase()) || !player.isAlive())
            clearInput();
    }

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

    // 사용자 키 설정을 네트워크 대전에도 적용
    private void bindKeys(JComponent root) {
        bind(root, GameSettings.getLeftKey(), "LEFT", true);
        bind(root, GameSettings.getRightKey(), "RIGHT", true);
        bind(root, GameSettings.getRotateLeftKey(), "ROTATE_LEFT", false);
        bind(root, GameSettings.getRotateRightKey(), "ROTATE_RIGHT", false);
        bind(root, GameSettings.getSoftDropKey(), "SOFT_DROP", true);
        bind(root, GameSettings.getHardDropKey(), "HARD_DROP", false);
    }

    // 설정된 키를 네트워크 대전 명령과 연결
    private void bind(JComponent root, int keyCode, String command, boolean repeat) {
        String pressed = "press-" + command;
        String released = "release-" + command;

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, 0, false), pressed);

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, 0, true), released);

        root.getActionMap().put(pressed, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (session == null || latest == null || !"PLAYING".equals(latest.getPhase())
                        || !latest.getPlayer(latest.getSeat()).isAlive())
                    return;

                if (!repeat && !pressedKeys.add(command))
                    return;

                session.input(command);
            }
        });

        root.getActionMap().put(released, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pressedKeys.remove(command);
            }
        });
    }

    private void clearInput() {
        pressedKeys.clear();
        if (session != null)
            session.commands.clear();
    }

    private void stopSession() {
        generation++;
        clearInput();
        if (session != null)
            session.close();
        session = null;
        latest = null;
        own = null;
        other = null;
    }

    private void returnToMenu() {
        if (disposed)
            return;
        Main menu = new Main();
        menu.setLocationRelativeTo(this);
        dispose();
        menu.setVisible(true);
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            stopSession();
        }
        super.dispose();
    }

    static String formatElapsedTime(long elapsedMillis) {
        long seconds = Math.max(0, elapsedMillis) / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void install(JPanel root) {
        setContentPane(root);
        revalidate();
        repaint();
    }

    private static JPanel page() {
        JPanel root = Style.createBackground(new BorderLayout(0, 16));
        root.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
        return root;
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel header(String title) {
        JPanel panel = transparent(new BorderLayout(0, 10));
        JLabel logo = new JLabel(new PixelLogo(3));
        logo.getAccessibleContext().setAccessibleName("Tetris");
        panel.add(logo, BorderLayout.NORTH);
        panel.add(label(title, Style.TEXT, Style.BUTTON_FONT), BorderLayout.CENTER);
        return panel;
    }

    private static JTextField field(String value, String accessibleName) {
        JTextField field = new JTextField(value);
        Style.applyTextFieldStyle(field);
        field.getAccessibleContext().setAccessibleName(accessibleName);
        return field;
    }

    private static void addField(JPanel root, String text, JTextField field) {
        JLabel label = label(text, Style.MUTED_TEXT, Style.SMALL_FONT);
        label.setLabelFor(field);
        // 라벨은 필요한 높이만 사용해 작은 창에서도 입력칸과 상태 문구 표시
        JPanel row = transparent(new BorderLayout(0, 5));
        row.add(label, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        root.add(row);
    }

    private static JLabel label(String text, Color color, Font font) {
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
            name = label(title, Style.TEXT, Style.BUTTON_FONT);
            metrics = label("0점 · 0줄", Style.ACCENT, Style.MONO_FONT.deriveFont(16f));
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

        void update(OnlineFrame.Player player) {
            update(player, true);
        }

        void update(OnlineFrame.Player player, boolean started) {
            boolean empty = player.getNickname().isEmpty();
            name.setText(title + " · " + (empty ? "참가 대기" : player.getNickname()) + (player.isReady() ? " · 준비" : ""));
            name.setToolTipText(name.getText());
            // 긴 닉네임도 최소 창의 게임판 폭 안에 들어가도록 필요한 만큼만 줄임
            Font nameFont = Style.BUTTON_FONT;
            while (name.getWidth() > 0 && nameFont.getSize2D() > 12f
                    && name.getFontMetrics(nameFont).stringWidth(name.getText()) > name.getWidth())
                nameFont = nameFont.deriveFont(nameFont.getSize2D() - .5f);
            name.setFont(nameFont);
            metrics.setText(NumberFormat.getIntegerInstance().format(player.getScore()) + "점 · " + player.getLines() + "줄");
            canvas.cells = player.getCells();
            canvas.itemTypes = player.getItemTypes();

            // 서버에서 받은 안개 상태 저장
            canvas.fogActive = player.isFogActive();
            canvas.fogRemaining = player.getFogRemaining();

            canvas.ended = started && !empty && !player.isAlive();
            next.shape = player.getNextShape();
            next.repaint();
            canvas.repaint();
        }
    }

    static final class BoardCanvas extends JPanel {
        int[] cells = new int[220];
        int[] itemTypes = new int[220];
        boolean fogActive;
        int fogRemaining;
        boolean ended;

        BoardCanvas() {
            setOpaque(false);
            setMinimumSize(new Dimension(100, 220));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
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
                        int index = y * 10 + x;
                        int value = cells[index];
                        int itemValue = itemTypes[index];

                        if (value > 0 && value < 8) {
                            ItemType itemType = null;

                            // 0은 아이템 없음, 1부터 실제 ItemType
                            if (itemValue > 0 && itemValue <= ItemType.values().length)
                                itemType = ItemType.values()[itemValue - 1];

                            BlockPainter.drawSquare(
                                    g,
                                    left + x * size,
                                    top + (21 - y) * size,
                                    size,
                                    Tetrominoes.values()[value],
                                    itemType
                            );
                        }
                    }
                }

                // 안개 효과 표시
                if (fogActive)
                    drawFog(g, left, top, size);
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
            } finally {
                g.dispose();
            }
        }

        // 안개 아이템 효과를 보드 위쪽 절반에 표시
        private void drawFog(Graphics2D g, int left, int top, int size) {
            int width = 10 * size;
            int fogHeight = 11 * size;

            Color fog = new Color(0x2B, 0x30, 0x3A);

            g.setColor(new Color(
                    fog.getRed(),
                    fog.getGreen(),
                    fog.getBlue(),
                    245
            ));
            g.fillRect(left, top, width, fogHeight - size);

            GradientPaint gradient = new GradientPaint(
                    left,
                    top + fogHeight - size,
                    new Color(fog.getRed(), fog.getGreen(), fog.getBlue(), 245),
                    left,
                    top + fogHeight,
                    new Color(fog.getRed(), fog.getGreen(), fog.getBlue(), 0)
            );

            g.setPaint(gradient);
            g.fillRect(left, top + fogHeight - size, width, size);

            String text = "안개 " + (fogRemaining + 1);

            g.setFont(Style.BUTTON_FONT.deriveFont(
                    (float) Math.max(10, size * 0.7)
            ));

            FontMetrics metrics = g.getFontMetrics();

            g.setColor(Style.MUTED_TEXT);
            g.drawString(
                    text,
                    left + (width - metrics.stringWidth(text)) / 2,
                    top + fogHeight / 2
            );
        }
    }

    static final class NextCanvas extends JPanel {
        int shape;

        NextCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(90, 44));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
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
