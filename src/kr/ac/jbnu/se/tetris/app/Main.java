package kr.ac.jbnu.se.tetris.app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import kr.ac.jbnu.se.tetris.ui.Tetris;

public class Main extends JFrame {

    private static final Color BACKGROUND = new Color(15, 17, 27);
    private static final Color PANEL = new Color(27, 30, 46);
    private static final Color MUTED = new Color(151, 158, 190);

    public Main() {
        setTitle("Tetris");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(760, 680);
        setResizable(false);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 24));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(32, 42, 28, 42));

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        JLabel title = new JLabel("TETRIS");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 42));
        JLabel subtitle = new JLabel("CHOOSE YOUR GAME MODE");
        subtitle.setForeground(MUTED);
        subtitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);

        JButton multiplayerButton = createMenuButton(
                "MULTI PLAY", "Play with friends and foes",
                new Color(239, 83, 133), new Color(53, 31, 50));
        JButton soloButton = createMenuButton(
                "SOLO", "Challenge yourself and beat your best",
                new Color(151, 126, 255), new Color(34, 33, 54));
        JButton itemModeButton = createMenuButton(
                "ITEM MODE", "A fresh twist on classic Tetris",
                new Color(91, 213, 139), new Color(29, 52, 43));

        multiplayerButton.addActionListener(event -> showComingSoon("Multi play"));
        soloButton.addActionListener(event -> startSoloGame());
        itemModeButton.addActionListener(event -> showComingSoon("Item Mode"));

        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 0, 16));
        buttonPanel.setOpaque(false);
        buttonPanel.add(multiplayerButton);
        buttonPanel.add(soloButton);
        buttonPanel.add(itemModeButton);

        JLabel footer = new JLabel("MOVE  ←  →     ROTATE  ↑  ↓     DROP  SPACE", SwingConstants.CENTER);
        footer.setForeground(new Color(103, 110, 139));
        footer.setFont(new Font("SansSerif", Font.BOLD, 11));

        root.add(header, BorderLayout.NORTH);
        root.add(buttonPanel, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JButton createMenuButton(String title, String description, Color accent, Color background) {
        String label = "<html><div style='text-align:left;'>"
                + "<span style='font-size:22pt;'><b>" + title + "</b></span>"
                + "<br><span style='font-size:11pt;'>" + description + "</span></div></html>";
        JButton button = new JButton(label);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(new Color(233, 230, 247));
        button.setBackground(background);
        button.setFont(new Font("SansSerif", Font.BOLD, 18));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setMargin(new Insets(20, 24, 20, 24));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(
                                Math.min(background.getRed() + 14, 255),
                                Math.min(background.getGreen() + 14, 255),
                                Math.min(background.getBlue() + 14, 255))),
                        new EmptyBorder(10, 16, 10, 16))));

        Color hoverBackground = background.brighter();
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                button.setBackground(hoverBackground);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                button.setBackground(background);
            }
        });
        return button;
    }

    private void startSoloGame() {
        Tetris game = new Tetris();
        game.setLocationRelativeTo(this);
        game.setVisible(true);
        dispose();
    }

    private void showComingSoon(String mode) {
        JOptionPane.showMessageDialog(
                this,
                mode + " is coming soon.",
                "Tetris",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
