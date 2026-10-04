// 앱 진입 화면 파일
package frontend;

import frontend.style.Style;
import frontend.network.ScoreClient.RankingEntry;
import frontend.network.ScoreClient.RankingPage;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

// 앱 메인 화면
// 나중에 메뉴 화면이 더 늘어나면 화면별 JPanel로 나눠도 괜찮을 것 같음
public class Main extends JFrame {

    private static final int RANKING_PAGE_SIZE = 5;

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

        // 닉네임은 기기에 보관하고, 서버에서는 같은 닉네임의 최고 점수를 관리
        String playerName = ScoreManager.getNickname();
        JPanel identityPanel = new JPanel(new BorderLayout(10, 6));
        identityPanel.setOpaque(false);
        JPanel nicknameRow = new JPanel(new BorderLayout(12, 0));
        nicknameRow.setOpaque(false);
        JTextField nicknameField = new JTextField(playerName, 16);
        nicknameField.setFont(Style.BUTTON_FONT);
        nicknameField.setForeground(Style.TEXT);
        nicknameField.setBackground(Style.PANEL);
        nicknameField.setCaretColor(Style.TEXT);
        nicknameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Style.BOARD_BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        JLabel nicknameLabel = createLabel("닉네임", Style.TEXT);
        nicknameLabel.setLabelFor(nicknameField);
        JButton applyNickname = createButton("적용");
        applyNickname.setPreferredSize(new Dimension(90, 42));
        JLabel connectionStatus = createLabel(playerName.isEmpty()
                ? "문자·숫자·_·-로 1~16자 입력해 주세요." : "서버 기록을 불러오는 중입니다.", Style.MUTED_TEXT);
        Runnable saveNickname = () -> {
            try {
                ScoreManager.setNickname(nicknameField.getText());
                showMainMenu();
            } catch (IOException | IllegalArgumentException e) {
                connectionStatus.setText(e.getMessage());
                connectionStatus.setForeground(Style.PINK);
            }
        };
        applyNickname.addActionListener(e -> saveNickname.run());
        nicknameField.addActionListener(e -> saveNickname.run());
        nicknameRow.add(nicknameLabel, BorderLayout.WEST);
        nicknameRow.add(nicknameField, BorderLayout.CENTER);
        nicknameRow.add(applyNickname, BorderLayout.EAST);
        identityPanel.add(nicknameRow, BorderLayout.CENTER);
        identityPanel.add(connectionStatus, BorderLayout.SOUTH);
        content.add(identityPanel, BorderLayout.NORTH);

        // 세 가지 게임 모드는 큰 버튼으로, 랭킹과 설정은 아래에 배치
        JPanel modePanel = createButtonPanel(3);
        JButton classicButton = createMenuButton("클래식",
                playerName.isEmpty() ? "닉네임을 입력한 뒤 시작하세요"
                        : playerName + " · 내 최고 점수: " + ScoreManager.getCachedBestScore(playerName),
                Style.ACCENT, Style.CLASSIC_BUTTON);
        classicButton.setEnabled(!playerName.isEmpty());
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
        showPage(mainContainer, playerName.isEmpty() ? applyNickname : classicButton);

        if (!playerName.isEmpty()) {
            ScoreManager.loadBestScore(playerName, result -> {
                if (!isDisplayable() || getContentPane() != mainContainer)
                    return;
                setMenuButtonText(classicButton, "클래식", playerName + " · 내 최고 점수: " + result.getValue());
                connectionStatus.setText(result.getMessage());
                connectionStatus.setToolTipText(result.getDetail());
                connectionStatus.setForeground(result.isSuccess() ? Style.GREEN : Style.MUTED_TEXT);
            });
        }
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

    // 서버의 전체 랭킹을 한 페이지에 5명씩 표시
    private void showRankingMenu() {
        showRankingMenu(0);
    }

    // 나중에 랭킹 검색이나 정렬 조건이 늘어나면 별도 화면 클래스로 나눠도 괜찮을 것 같음
    private void showRankingMenu(int pageIndex) {
        JPanel container = createPage("전체 랭킹", "닉네임별 클래식 최고 점수");
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        JLabel status = createLabel("서버 랭킹을 불러오는 중입니다.", Style.MUTED_TEXT);
        JPanel rankingPanel = createButtonPanel(RANKING_PAGE_SIZE);
        JLabel[] rows = new JLabel[RANKING_PAGE_SIZE];
        for (int i = 0; i < rows.length; i++) {
            JLabel rankLabel = createLabel("-", Style.TEXT);
            rankLabel.setFont(Style.SCORE_FONT);
            rankLabel.setOpaque(true);
            rankLabel.setBackground(Style.PANEL);
            rankLabel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, Style.ACCENT));
            rankingPanel.add(rankLabel);
            rows[i] = rankLabel;
        }
        JPanel navigation = new JPanel(new GridLayout(1, 4, 12, 0));
        navigation.setOpaque(false);
        JButton previous = createButton("이전");
        JButton next = createButton("다음");
        JButton refresh = createButton("새로고침");
        JLabel pageLabel = createLabel((pageIndex + 1) + " 페이지", Style.TEXT);
        previous.setEnabled(pageIndex > 0);
        next.setEnabled(false);
        refresh.setEnabled(false);
        previous.addActionListener(e -> showRankingMenu(pageIndex - 1));
        next.addActionListener(e -> showRankingMenu(pageIndex + 1));
        refresh.addActionListener(e -> showRankingMenu(pageIndex));
        navigation.add(previous);
        navigation.add(pageLabel);
        navigation.add(next);
        navigation.add(refresh);
        content.add(status, BorderLayout.NORTH);
        content.add(rankingPanel, BorderLayout.CENTER);
        content.add(navigation, BorderLayout.SOUTH);
        container.add(content, BorderLayout.CENTER);
        showSubmenu(container);

        ScoreManager.loadRanking(pageIndex * RANKING_PAGE_SIZE, RANKING_PAGE_SIZE, result -> {
            // 다른 메뉴로 이동한 뒤 받은 응답은 이전 화면에만 해당하므로 무시
            if (!isDisplayable() || getContentPane() != container)
                return;
            refresh.setEnabled(true);
            status.setToolTipText(result.getDetail());
            if (!result.isSuccess()) {
                status.setText(result.getMessage());
                status.setForeground(Style.PINK);
                return;
            }
            RankingPage page = result.getValue();
            if (pageIndex > 0 && page.getEntries().isEmpty()) {
                showRankingMenu(Math.max(0, (page.getTotalPlayers() - 1) / RANKING_PAGE_SIZE));
                return;
            }
            status.setText("전체 " + page.getTotalPlayers() + "명 · 서버에 저장된 최고 점수");
            pageLabel.setText((pageIndex + 1) + " / " + Math.max(1,
                    (page.getTotalPlayers() + RANKING_PAGE_SIZE - 1) / RANKING_PAGE_SIZE));
            next.setEnabled((pageIndex + 1) * RANKING_PAGE_SIZE < page.getTotalPlayers());
            for (int i = 0; i < page.getEntries().size(); i++) {
                RankingEntry entry = page.getEntries().get(i);
                rows[i].setText(entry.getRank() + "위    " + entry.getNickname() + "    " + entry.getScore());
                if (entry.getNickname().equals(ScoreManager.getNickname()))
                    rows[i].setForeground(Style.ACCENT);
            }
            if (page.getEntries().isEmpty())
                rows[0].setText("아직 등록된 기록이 없습니다.");
        });
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
        JButton button = new JButton();
        setMenuButtonText(button, title, description);
        Style.applyButtonStyle(button, accent, background);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        return button;
    }

    private void setMenuButtonText(JButton button, String title, String description) {
        button.setText("<html>" + title
                + "<br><span style='font-size:10pt;font-weight:normal;'>" + description + "</span></html>");
        button.getAccessibleContext().setAccessibleName(title);
        button.getAccessibleContext().setAccessibleDescription(description);
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
