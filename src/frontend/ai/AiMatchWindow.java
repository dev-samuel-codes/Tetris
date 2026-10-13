package frontend.ai;

import backend.ai.AiDifficulty;
import backend.ai.AiMatchClient;
import backend.ai.MatchFrame;
import frontend.BlockPainter;
import frontend.LineClearFlash;
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
import java.awt.event.KeyEvent;
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
import frontend.GameSettings;

// 같은 블록 순서로 점수를 겨루는 AI 대전 화면
public final class AiMatchWindow extends JFrame {
    private static final int TICK_MILLIS = 50;
    private static final int BOARD_WIDTH = 10;
    private static final int BOARD_HEIGHT = 22;
    private static final AtomicLong THREAD_IDS = new AtomicLong();
    private final Resolution resolution;
    private final Set<String> pressedKeys = new HashSet<String>();
    private volatile Session session;
    private long generation;
    private boolean disposed;
    private AiDifficulty difficulty;
    private MatchFrame latestFrame;
    private JLabel clockLabel;
    private JLabel statusLabel;
    private JLabel resultLabel;
    private JLabel detailLabel;
    private JButton pauseButton;
    private JButton errorButton;
    private JButton retryButton;
    private Throwable lastFailure;
    private BoardView humanView;
    private BoardView aiView;

    public AiMatchWindow(Resolution resolution) {
        this.resolution = resolution == null ? Resolution.MEDIUM : resolution;
        setTitle("Tetris · AI 대전");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (confirmLeaving()) dispose();
            }
        });
        setResizable(true);
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent event) {
                clearInput();
                Session current = session;
                if (current != null && current.playing && !current.paused)
                    setPaused(true);
            }
        });
        showDifficultySelection();
        pack();
        fitOnScreen();
        setMinimumSize(new Dimension(Math.min(620, getWidth()), Math.min(560, getHeight())));
        setLocationRelativeTo(null);
    }

    private void showDifficultySelection() {
        if (disposed)
            return;
        stopSession();
        difficulty = null;
        latestFrame = null;
        JPanel root = page();
        root.add(header("AI 대전"), BorderLayout.NORTH);
        JPanel content = transparent(new GridBagLayout());
        JPanel card = Style.framedPanel(new GridBagLayout(), Style.PANEL);
        card.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.insets = new Insets(0, 0, 6, 0);
        JPanel introduction = transparent(new BorderLayout(0, 6));
        introduction.add(label("상대의 난이도를 선택하세요", Style.TEXT, Style.ROOM_TITLE_FONT), BorderLayout.NORTH);
        introduction.add(label("같은 블록 순서 · 시간 제한 없이 점수 대결", Style.MUTED_TEXT, Style.BODY_FONT), BorderLayout.CENTER);
        introduction.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        row.gridy = 0;
        card.add(introduction, row);
        int index = 1;
        JButton defaultChoice = null;
        for (final AiDifficulty option : AiDifficulty.values()) {
            JButton choose = button(option.getLabel());
            choose.setFocusable(true);
            choose.setPreferredSize(new Dimension(430, 42));
            choose.addActionListener(event -> startMatch(option));
            if (option == AiDifficulty.NORMAL) {
                choose.putClientProperty("primary", true);
                defaultChoice = choose;
            }
            row.gridy = index++;
            card.add(choose, row);
        }
        GridBagConstraints placement = new GridBagConstraints();
        content.add(card, placement);
        root.add(content, BorderLayout.CENTER);
        JButton menu = button("← 메뉴로 돌아가기");
        menu.setFocusable(true);
        menu.putClientProperty("textButton", true);
        menu.addActionListener(event -> returnToMenu());
        JPanel footer = transparent(new BorderLayout());
        footer.add(menu, BorderLayout.EAST);
        footer.add(label("Enter 선택 · Tab 이동 · Esc 메뉴", Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.WEST);
        root.add(footer, BorderLayout.SOUTH);
        root.setPreferredSize(new Dimension(760, 650));
        setMinimumSize(new Dimension(620, 560));
        bindEscape(root, this::returnToMenu, pressedKeys);
        installContent(root);
        getRootPane().setDefaultButton(defaultChoice);
        final JButton focusChoice = defaultChoice;
        SwingUtilities.invokeLater(() -> focusChoice.requestFocusInWindow());
    }

    private void startMatch(AiDifficulty selected) {
        if (disposed || selected == null)
            return;
        stopSession();
        difficulty = selected;
        latestFrame = null;
        lastFailure = null;
        buildMatchPage();
        pack();
        fitOnScreen();
        final Session next = new Session(++generation);
        session = next;
        next.executor.execute(() -> {
            AiMatchClient client = null;
            try {
                client = new AiMatchClient();
                next.client = client;
                if (next.cancelled) {
                    client.close();
                    return;
                }
                final MatchFrame first = client.start(selected, System.nanoTime() & Long.MAX_VALUE);
                SwingUtilities.invokeLater(() -> {
                    if (!isCurrent(next))
                        return;
                    applyFrame(first);
                    next.playing = !first.isEnded();
                    // 게임판을 준비한 뒤 경기 시작
                    next.paused = !isFocused();
                    updateStatus();
                    if (next.playing) {
                        next.pacer = new FramePacer(next.executor, TICK_MILLIS,
                                () -> requestFrame(next), error -> {
                                    if (!next.cancelled)
                                        fail(next, error);
                                });
                        next.pacer.setPaused(next.paused);
                    } else {
                        closeSessionClient(next);
                        next.executor.shutdown();
                    }
                });
            } catch (IOException | RuntimeException error) {
                fail(next, error);
            } finally {
                if (next.cancelled && client != null)
                    client.close();
            }
        });
    }

    private void buildMatchPage() {
        final AiDifficulty matchDifficulty = difficulty;
        JPanel root = page();
        JPanel heading = transparent(new BorderLayout(18, 0));
        heading.add(header("AI 대전 · " + difficulty.getLabel()), BorderLayout.WEST);
        JPanel time = transparent(new BorderLayout(0, 2));
        clockLabel = label("00:00", Style.ACCENT, Style.DISPLAY_FONT.deriveFont(36f));
        clockLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel = label("준비 중", Style.MUTED_TEXT, Style.SMALL_FONT);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel elapsedLabel = label("경과 시간", Style.MUTED_TEXT, Style.SMALL_FONT);
        elapsedLabel.setHorizontalAlignment(SwingConstants.CENTER);
        time.add(elapsedLabel, BorderLayout.NORTH);
        time.add(clockLabel, BorderLayout.CENTER);
        time.add(statusLabel, BorderLayout.SOUTH);
        heading.add(time, BorderLayout.EAST);
        root.add(heading, BorderLayout.NORTH);

        JPanel boards = transparent(new GridLayout(1, 2, 24, 0));
        humanView = new BoardView("나");
        aiView = new BoardView("AI · " + difficulty.getLabel());
        boards.add(humanView);
        boards.add(aiView);
        JPanel center = transparent(new BorderLayout(0, 12));
        resultLabel = label("AI 상대를 준비하고 있습니다", Style.TEXT, Style.BUTTON_FONT);
        resultLabel.setHorizontalAlignment(SwingConstants.CENTER);
        center.add(resultLabel, BorderLayout.NORTH);
        center.add(boards, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JPanel footer = transparent(new BorderLayout(0, 10));
        detailLabel = label("한쪽이 게임오버된 뒤 남은 쪽이 점수를 앞서면 바로 승리합니다.", Style.MUTED_TEXT, Style.SMALL_FONT);
        detailLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel instructions = transparent(new BorderLayout(0, 5));
        instructions.add(detailLabel, BorderLayout.NORTH);
        JLabel keys = label(controlsText(), Style.MUTED_TEXT, Style.SMALL_FONT);
        keys.setHorizontalAlignment(SwingConstants.CENTER);
        instructions.add(keys, BorderLayout.CENTER);
        errorButton = button("설치 및 오류 안내");
        errorButton.setFocusable(true);
        errorButton.setVisible(false);
        errorButton.addActionListener(event -> showFailureDetails());
        instructions.add(errorButton, BorderLayout.SOUTH);
        footer.add(instructions, BorderLayout.NORTH);
        JPanel actions = transparent(new GridLayout(1, 4, 10, 0));
        pauseButton = button("일시정지");
        pauseButton.putClientProperty("primary", true);
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(event -> togglePause());
        retryButton = button("다시 시작");
        retryButton.addActionListener(event -> { if (confirmLeaving()) startMatch(matchDifficulty); });
        JButton choose = button("난이도 변경");
        choose.addActionListener(event -> { if (confirmLeaving()) showDifficultySelection(); });
        JButton menu = button("메뉴");
        menu.addActionListener(event -> returnToMenu());
        actions.add(pauseButton);
        actions.add(retryButton);
        actions.add(choose);
        actions.add(menu);
        footer.add(actions, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        root.setPreferredSize(new Dimension(2 * BOARD_WIDTH * resolution.getCellSize() + 120,
                BOARD_HEIGHT * resolution.getCellSize() + 240));
        installContent(root);
        // 경기 화면은 선택 화면보다 높이를 확보해 보드가 지나치게 작아지지 않도록 제한
        setMinimumSize(new Dimension(620, Math.min(600, Toolkit.getDefaultToolkit().getScreenSize().height - 96)));
        getRootPane().setDefaultButton(null);
        bindKeys(root);
        bindEscape(root, () -> {
            Session current = session;
            if (current != null && current.playing) togglePause();
            else if (confirmLeaving()) showDifficultySelection();
        }, pressedKeys);
    }

    private Runnable requestFrame(Session current) throws IOException {
        String command = current.commands.poll();
        final MatchFrame frame = current.client.step(command == null ? "WAIT" : command);
        if ("HARD_DROP".equals(command) || frame.getHuman().isEnded())
            current.commands.clear();
        if (frame.isEnded()) {
            current.playing = false;
            closeSessionClient(current);
            current.executor.shutdown();
        }
        return () -> {
            if (isCurrent(current)) {
                applyFrame(frame);
                updateStatus();
                if (frame.isEnded())
                    current.pacer.close();
            }
        };
    }

    private void applyFrame(MatchFrame frame) {
        latestFrame = frame;
        humanView.update(frame.getHuman());
        aiView.update(frame.getAi());
        clockLabel.setText(formatElapsedTime(frame.getElapsedTicks()));
        if (frame.isEnded()) {
            clearInput();
            String winner = frame.getWinner();
            resultLabel.setText("HUMAN".equals(winner) ? "승리!" : "AI".equals(winner) ? "AI 승리" : "무승부");
            resultLabel.setForeground("HUMAN".equals(winner) ? Style.GREEN : "AI".equals(winner) ? Style.PINK : Style.ACCENT);
            detailLabel.setText("최종 점수　나 " + score(frame.getHuman().getScore()) + " : AI " + score(frame.getAi().getScore()));
        } else {
            resultLabel.setText("같은 블록 · 시간 제한 없음 · 점수 대결");
            resultLabel.setForeground(Style.TEXT);
            detailLabel.setText(frame.getHuman().isEnded()
                    ? "AI가 내 점수를 넘거나 게임오버될 때까지 진행합니다."
                    : frame.getAi().isEnded()
                    ? "AI 점수를 넘으면 바로 승리합니다."
                    : "한쪽이 게임오버된 뒤 남은 쪽이 점수를 앞서면 바로 승리합니다.");
        }
    }

    private void updateStatus() {
        Session current = session;
        if (current == null || latestFrame == null)
            return;
        boolean ended = latestFrame.isEnded();
        statusLabel.setText(ended ? "경기 종료" : current.paused ? "일시정지" : "경기 중");
        pauseButton.setEnabled(!ended && current.playing);
        pauseButton.setText(current.paused ? "계속하기" : "일시정지");
        humanView.setPaused(!ended && current.paused);
        aiView.setPaused(!ended && current.paused);
    }

    private void fail(final Session failed, final Throwable error) {
        failed.playing = false;
        failed.commands.clear();
        if (failed.pacer != null)
            failed.pacer.close();
        closeSessionClient(failed);
        failed.executor.shutdown();
        SwingUtilities.invokeLater(() -> {
            if (!isCurrent(failed))
                return;
            lastFailure = error;
            System.err.println("AI 대전 오류: " + error.getMessage());
            error.printStackTrace(System.err);
            statusLabel.setText(latestFrame == null ? "준비 실패" : "경기 중단");
            resultLabel.setText(latestFrame == null ? "AI 대전을 시작하지 못했습니다" : "경기를 계속 진행할 수 없습니다");
            resultLabel.setForeground(Style.PINK);
            detailLabel.setText("다시 시도하거나 설치 및 오류 안내를 확인해 주세요.");
            retryButton.setText("다시 시도");
            retryButton.putClientProperty("primary", true);
            retryButton.setFocusable(true);
            getRootPane().setDefaultButton(retryButton);
            pauseButton.setEnabled(false);
            errorButton.setVisible(true);
            getContentPane().revalidate();
        });
    }

    private void showFailureDetails() {
        String reason = lastFailure == null ? "원인을 확인할 수 없습니다." : lastFailure.getMessage();
        String instructions = "AI 실행 환경을 설치하거나 복구한 뒤 다시 시작해 주세요.\n\n"
                + "macOS / Linux: 프로젝트 폴더에서 bash scripts/setup-ai.sh\n"
                + "Windows: 프로젝트 폴더에서 powershell -ExecutionPolicy Bypass -File scripts/setup-ai.ps1\n\n"
                + "이미 설치했다면 잠시 후 다시 시작해 주세요.\n\n상세 원인\n" + reason;
        JTextArea details = new JTextArea(instructions, 11, 48);
        details.setFont(Style.BODY_FONT);
        details.setEditable(false);
        details.setBackground(Style.PANEL);
        details.setForeground(Style.TEXT);
        details.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(details);
        JOptionPane.showMessageDialog(this, scroll, "AI 대전 오류 안내", JOptionPane.INFORMATION_MESSAGE);
    }

    private void togglePause() {
        Session current = session;
        if (current != null && current.playing)
            setPaused(!current.paused);
    }

    private void setPaused(boolean paused) {
        Session current = session;
        if (current == null || !current.playing)
            return;
        current.paused = paused;
        if (current.pacer != null)
            current.pacer.setPaused(paused);
        clearInput();
        updateStatus();
    }

    // 사용자 키 설정을 AI 대전에도 적용
    private void bindKeys(JComponent root) {
        bind(root, GameSettings.getLeftKey(), "LEFT", true);
        bind(root, GameSettings.getRightKey(), "RIGHT", true);
        bind(root, GameSettings.getRotateLeftKey(), "ROTATE_LEFT", false);
        bind(root, GameSettings.getRotateRightKey(), "ROTATE_RIGHT", false);
        bind(root, GameSettings.getHardDropKey(), "HARD_DROP", false);
        bind(root, GameSettings.getSoftDropKey(), "SOFT_DROP", true);
        bind(root, GameSettings.getPauseKey(), "PAUSE", false);
    }

    // 설정된 키를 AI 대전 명령과 연결
    private void bind(JComponent root, final int keyCode, final String command, final boolean repeat) {
        String pressed = "press-" + command;
        String released = "release-" + command;

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, 0, false), pressed);

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, 0, true), released);

        root.getActionMap().put(pressed, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!repeat && !pressedKeys.add(command))
                    return;

                if ("PAUSE".equals(command)) {
                    togglePause();
                    pressedKeys.add(command);
                    return;
                }

                Session current = session;
                if (current == null || !current.playing || current.paused
                        || latestFrame == null || latestFrame.getHuman().isEnded())
                    return;

                if ("HARD_DROP".equals(command))
                    current.commands.clear();

                current.commands.offer(command);
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
        Session current = session;
        if (current != null)
            current.commands.clear();
    }

    private boolean isCurrent(Session candidate) {
        return !disposed && session == candidate && generation == candidate.generation && !candidate.cancelled;
    }

    private void stopSession() {
        generation++;
        Session previous = session;
        session = null;
        pressedKeys.clear();
        if (previous == null)
            return;
        previous.cancelled = true;
        previous.playing = false;
        previous.commands.clear();
        if (previous.pacer != null)
            previous.pacer.close();
        previous.executor.shutdownNow();
        closeSessionClient(previous);
    }

    private static void closeSessionClient(final Session current) {
        final AiMatchClient client = current.client;
        if (client == null)
            return;
        // 연결을 닫는 동안 화면이 멈추지 않도록 따로 처리
        Thread cleanup = new Thread(() -> client.close(), "tetris-ai-close-" + THREAD_IDS.incrementAndGet());
        cleanup.setDaemon(true);
        cleanup.start();
    }

    private void returnToMenu() {
        if (disposed)
            return;
        if (!confirmLeaving()) return;
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

    private void installContent(JPanel root) {
        setContentPane(root);
        revalidate();
        repaint();
    }

    private void fitOnScreen() {
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        setSize(Math.min(getWidth(), bounds.width - insets.left - insets.right - 32),
                Math.min(getHeight(), bounds.height - insets.top - insets.bottom - 32));
    }

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
        button.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate");
        button.getActionMap().put("activate", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { button.doClick(); }
        });
        button.setFocusable(false);
        return button;
    }

    private boolean confirmLeaving() {
        Session current = session;
        if (current == null || !current.playing || latestFrame == null || latestFrame.isEnded())
            return true;
        boolean previouslyPaused = current.paused;
        setPaused(true);
        int answer = JOptionPane.showConfirmDialog(this, "진행 중인 경기를 끝내시겠습니까?",
                "진행 중인 경기", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION && !previouslyPaused)
            setPaused(false);
        return answer == JOptionPane.YES_OPTION;
    }

    private static void bindEscape(JComponent root, Runnable action, Set<String> pressedKeys) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), "release-escape");
        root.getActionMap().put("escape", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                if (!pressedKeys.add("ESCAPE"))
                    return;
                action.run();
                // 일시정지에서 clearInput()을 호출해도 키를 놓기 전에는 반복 입력을 막음
                pressedKeys.add("ESCAPE");
            }
        });
        root.getActionMap().put("release-escape", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { pressedKeys.remove("ESCAPE"); }
        });
    }

    static String controlsText() {
        return "<html><center>" + escapeHtml(GameSettings.keyName(GameSettings.getLeftKey())) + " / "
                + escapeHtml(GameSettings.keyName(GameSettings.getRightKey())) + " 이동　"
                + escapeHtml(GameSettings.keyName(GameSettings.getRotateLeftKey())) + " / "
                + escapeHtml(GameSettings.keyName(GameSettings.getRotateRightKey())) + " 회전<br>"
                + escapeHtml(GameSettings.keyName(GameSettings.getSoftDropKey())) + " 하강　"
                + escapeHtml(GameSettings.keyName(GameSettings.getHardDropKey())) + " 즉시 낙하　"
                + escapeHtml(GameSettings.keyName(GameSettings.getPauseKey())) + " 일시정지</center></html>";
    }

    private static String escapeHtml(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String formatElapsedTime(long elapsedTicks) {
        long seconds = Math.max(0L, elapsedTicks) / (1000 / TICK_MILLIS);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static String score(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    private static final class Session {
        final long generation;
        final ArrayBlockingQueue<String> commands = new ArrayBlockingQueue<String>(1);
        final ScheduledExecutorService executor;
        volatile AiMatchClient client;
        volatile boolean cancelled;
        volatile boolean paused;
        volatile boolean playing;
        volatile FramePacer pacer;

        Session(long generation) {
            this.generation = generation;
            executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "tetris-ai-match-" + THREAD_IDS.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            });
        }
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
            canvas.lineFlash.update(state.getLines(), state.getLastClearedRow());
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
        private final LineClearFlash lineFlash = new LineClearFlash(this);
        private int[] cells = new int[BOARD_WIDTH * BOARD_HEIGHT];
        private boolean ended;
        private boolean paused;

        BoardCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(300, 660));
            setMinimumSize(new Dimension(100, 220));
        }
        @Override
        public void removeNotify() {
            lineFlash.stop();
            super.removeNotify();
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

                        int blockX = left + x * size;
                        int blockY = top + (BOARD_HEIGHT - y - 1) * size;

                        if (value == 8) {
                            // 고스트 내부를 흐리게 표시
                            g.setColor(new Color(255, 255, 255, 30));
                            g.fillRect(blockX + 1, blockY + 1, size - 2, size - 2);

                            // 고스트 테두리 표시
                            g.setColor(new Color(255, 255, 255, 100));
                            g.drawRect(blockX + 1, blockY + 1, size - 3, size - 3);

                        } else if (value > 0 && value < shapes.length) {
                            BlockPainter.drawSquare(
                                    g, blockX, blockY, size, shapes[value]
                            );
                        }
                    }
                }
                lineFlash.draw(g, left, top, size);
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
