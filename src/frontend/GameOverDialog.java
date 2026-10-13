package frontend;

import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import javax.swing.SwingConstants;

import frontend.style.Style;

// 게임 결과와 다음 행동을 안내하는 키보드 접근 가능한 결과 창
public class GameOverDialog extends JDialog {
    private boolean actionTaken;

    // 점수,줄,콤보,시간 표시
    public GameOverDialog(JFrame owner, int score, int linesRemoved, int maxCombo, int elapsedSeconds,
                          Runnable onRestart, Runnable onMenu) {
        super(owner, "게임 결과", true);

        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(Style.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        setContentPane(root);

        // 제목
        JLabel title = label("GAME OVER", Style.ACCENT, Style.TITLE_FONT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        root.add(title, BorderLayout.NORTH);

        // 창 중간
        JPanel center = new JPanel(new BorderLayout(0, 18));
        center.setOpaque(false);

        JPanel stats = new JPanel(new GridLayout(2, 2, 10, 10));
        stats.setOpaque(false);
        stats.add(statTile("점수", NumberFormat.getIntegerInstance().format(score)));
        stats.add(statTile("게임 시간", formatTime(elapsedSeconds)));
        stats.add(statTile("제거한 줄", linesRemoved + "줄"));
        stats.add(statTile("최대 콤보", maxCombo >= 2 ? "x" + maxCombo : "없음"));
        center.add(stats, BorderLayout.NORTH);
        JLabel hint = label("한 번 더 도전해 보세요 · Enter 다시 하기 / ESC 메뉴", Style.MUTED_TEXT, Style.SMALL_FONT);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        center.add(hint, BorderLayout.SOUTH);

        root.add(center, BorderLayout.CENTER);

        // 창 하단
        JButton restartButton = new JButton("다시 하기");
        JButton menuButton = new JButton("메뉴로");
        restartButton.putClientProperty("primary", true);
        Style.applyButtonStyle(restartButton, Style.ACCENT, Style.PANEL);
        Style.applyButtonStyle(menuButton, Style.ACCENT, Style.PANEL);
        restartButton.addActionListener(e -> {
            takeAction(onRestart);
        });
        menuButton.addActionListener(e -> {
            takeAction(onMenu);
        });
        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setOpaque(false);
        buttons.add(restartButton);
        buttons.add(menuButton);
        root.add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(restartButton);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "result.menu");
        getRootPane().getActionMap().put("result.menu", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { takeAction(onMenu); }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { takeAction(onMenu); }
            @Override public void windowOpened(WindowEvent event) { restartButton.requestFocusInWindow(); }
        });
        setResizable(false);
        pack();
        setSize(Math.max(getWidth(), 360), getHeight());
        setLocationRelativeTo(owner);
    }

    private void takeAction(Runnable action) {
        if (actionTaken) return;
        actionTaken = true;
        dispose();
        action.run();
    }

    // 기록 : 위-제목 , 중간-기록값
    private static JPanel statTile(String name, String value) {
        JPanel tile = new JPanel(new BorderLayout(0, 4));
        tile.setBackground(Style.PANEL);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Style.BORDER),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        tile.add(label(name, Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.NORTH);
        tile.add(label(value, Style.TEXT, Style.SCORE_FONT), BorderLayout.CENTER);
        return tile;
    }

    // 초 형식 변경 (00분 00초)
    static String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static JLabel label(String text, java.awt.Color color, java.awt.Font font) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(font);
        return label;
    }



}