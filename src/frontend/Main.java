// 앱 진입 화면 파일
package frontend;

import frontend.style.Style;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

// 앱 메인 화면
// 나중에 메뉴 화면이 더 늘어나면 화면별 JPanel로 나눠도 괜찮을 것 같음
public class Main extends JFrame {

    public Main() {
        setTitle("Tetris");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 700);
        setResizable(false);
        setLocationRelativeTo(null);

        // 처음 실행 시 메인 메뉴 표시
        showMainMenu();
    }

    private void showMainMenu() {
        JPanel mainContainer = createPage("TETRIS", "플레이할 게임 모드를 선택하세요");
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);

        // 세 가지 게임 모드는 큰 버튼으로, 랭킹과 설정은 아래에 배치
        JPanel modePanel = createButtonPanel(3);
        JButton classicButton = createMenuButton("클래식",
                "Best Score: " + ScoreManager.getBestScore(), Style.ACCENT, Style.CLASSIC_BUTTON);
        JButton itemButton = createMenuButton("아이템", "아이템 모드 · 준비 중",
                Style.GREEN, Style.ITEM_BUTTON);
        JButton multiplayerButton = createMenuButton("멀티플레이", "함께 플레이할 모드 선택",
                Style.PINK, Style.MULTIPLAYER_BUTTON);

        classicButton.addActionListener(e -> {
            Tetris game = new Tetris(GameSettings.getResolution());
            game.setLocationRelativeTo(Main.this);
            game.setVisible(true);
            dispose();
        });
        // 나중에 아이템 게임이 구현되면 해당 화면으로 연결
        itemButton.addActionListener(e -> showComingSoon("아이템 모드"));
        multiplayerButton.addActionListener(e -> showMultiplayerMenu());
        modePanel.add(classicButton);
        modePanel.add(itemButton);
        modePanel.add(multiplayerButton);

        JPanel utilityPanel = new JPanel(new GridLayout(1, 2, 14, 0));
        utilityPanel.setOpaque(false);
        JButton rankingButton = createButton("랭킹");
        JButton settingsButton = createButton("설정");
        rankingButton.addActionListener(e -> showRankingMenu());
        settingsButton.addActionListener(e -> showSettingsMenu());
        utilityPanel.add(rankingButton);
        utilityPanel.add(settingsButton);
        content.add(modePanel, BorderLayout.CENTER);
        content.add(utilityPanel, BorderLayout.SOUTH);

        mainContainer.add(content, BorderLayout.CENTER);
        mainContainer.add(createLabel("← → 이동   ↑ ↓ 회전   D 한 칸 하강   SPACE 즉시 낙하   P 일시정지",
                Style.MUTED_TEXT), BorderLayout.SOUTH);
        showPage(mainContainer, classicButton);
    }

    private void showMultiplayerMenu() {
        JPanel container = createPage("멀티플레이", "대전 기능은 준비 중입니다");
        JPanel buttonPanel = createButtonPanel(3);
        JButton twoPlayerButton = createMenuButton("1PC 2인 게임", "한 컴퓨터에서 함께 플레이 · 준비 중",
                Style.ACCENT, Style.CLASSIC_BUTTON);
        JButton aiButton = createMenuButton("AI 대전", "AI 상대와 대결 · 준비 중",
                Style.GREEN, Style.ITEM_BUTTON);
        JButton networkButton = createMenuButton("네트워크 대전", "온라인 상대와 대결 · 준비 중",
                Style.PINK, Style.MULTIPLAYER_BUTTON);

        // 나중에 각 대전 기능이 구현되면 안내창 대신 게임 화면으로 연결
        twoPlayerButton.addActionListener(e -> showComingSoon("1PC 2인 게임"));
        aiButton.addActionListener(e -> showComingSoon("AI 대전"));
        networkButton.addActionListener(e -> showComingSoon("네트워크 대전"));
        buttonPanel.add(twoPlayerButton);
        buttonPanel.add(aiButton);
        buttonPanel.add(networkButton);
        container.add(buttonPanel, BorderLayout.CENTER);
        showSubmenu(container);
    }

    // 저장된 점수를 한 번 불러와서 상위 5개 표시
    private void showRankingMenu() {
        JPanel container = createPage("TOP 5", "클래식 모드 최고 기록");
        JPanel rankingPanel = createButtonPanel(5);
        List<Integer> scores = ScoreManager.loadScores();
        for (int i = 0; i < 5; i++) {
            String score = i < scores.size() ? String.valueOf(scores.get(i)) : "-";
            JLabel rankLabel = createLabel((i + 1) + "위    " + score, Style.TEXT);
            rankLabel.setFont(Style.SCORE_FONT);
            rankLabel.setOpaque(true);
            rankLabel.setBackground(Style.PANEL);
            rankLabel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, Style.ACCENT));
            rankingPanel.add(rankLabel);
        }
        container.add(rankingPanel, BorderLayout.CENTER);
        showSubmenu(container);
    }

    private void showSettingsMenu() {
        JPanel container = createPage("설정", "선택한 크기는 다음 게임부터 적용됩니다");
        JPanel resolutionPanel = createButtonPanel(3);
        ButtonGroup resolutionGroup = new ButtonGroup();
        Resolution[] order = { Resolution.LARGE, Resolution.MEDIUM, Resolution.SMALL };

        for (Resolution resolution : order) {
            JToggleButton button = new JToggleButton(resolution.toString());
            Style.applyButtonStyle(button, Style.ACCENT, Style.PANEL);
            resolutionGroup.add(button);
            button.setSelected(resolution == GameSettings.getResolution());
            button.addActionListener(e -> GameSettings.setResolution(resolution));
            resolutionPanel.add(button);
        }
        container.add(resolutionPanel, BorderLayout.CENTER);
        showSubmenu(container);
    }

    // 제목과 여백을 공유해 화면을 바꿔도 같은 메뉴 형태 유지
    private JPanel createPage(String heading, String description) {
        JPanel container = new JPanel(new BorderLayout(0, 24));
        container.setBackground(Style.BACKGROUND);
        container.setBorder(BorderFactory.createEmptyBorder(28, 42, 24, 42));
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        JLabel title = createLabel(heading, Style.TEXT);
        title.setFont(Style.TITLE_FONT);
        header.add(title, BorderLayout.CENTER);
        header.add(createLabel(description, Style.MUTED_TEXT), BorderLayout.SOUTH);
        container.add(header, BorderLayout.NORTH);
        return container;
    }

    private JPanel createButtonPanel(int rows) {
        JPanel panel = new JPanel(new GridLayout(rows, 1, 0, 14));
        panel.setOpaque(false);
        return panel;
    }

    private JLabel createLabel(String text, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(Style.BODY_FONT);
        label.setForeground(color);
        return label;
    }

    private JButton createMenuButton(String title, String description, Color accent, Color background) {
        JButton button = new JButton("<html>" + title
                + "<br><span style='font-size:10pt;font-weight:normal;'>" + description + "</span></html>");
        Style.applyButtonStyle(button, accent, background);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.getAccessibleContext().setAccessibleName(title);
        button.getAccessibleContext().setAccessibleDescription(description);
        return button;
    }

    private JButton createButton(String title) {
        JButton button = new JButton(title);
        Style.applyButtonStyle(button, Style.ACCENT, Style.PANEL);
        button.setPreferredSize(new Dimension(0, 46));
        return button;
    }

    private void showSubmenu(JPanel container) {
        JButton backButton = createButton("뒤로가기");
        backButton.addActionListener(e -> showMainMenu());
        container.add(backButton, BorderLayout.SOUTH);
        showPage(container, backButton);
    }

    private void showPage(JPanel container, JButton defaultButton) {
        setContentPane(container);
        getRootPane().setDefaultButton(defaultButton);
        revalidate();
        repaint();
    }

    private void showComingSoon(String mode) {
        JOptionPane.showMessageDialog(this, mode + "는 준비 중입니다.", "Tetris",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // Swing 화면을 UI 전용 스레드에서 생성
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main menu = new Main();
            menu.setVisible(true);
        });
    }
}
