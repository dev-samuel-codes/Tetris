// 앱 진입 화면 파일
package frontend;

import frontend.style.Style;
import frontend.ui.ModeCard;
import frontend.ui.PixelLogo;
import frontend.network.ScoreClient.RankingEntry;
import frontend.network.ScoreClient.RankingPage;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.io.IOException;
import java.text.NumberFormat;
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
        // 기본 메뉴 크기는 유지하고 작은 화면에서는 화면 바깥으로 벗어나지 않게 제한
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(960, screen.width - 48), Math.min(740, screen.height - 64));
        setResizable(false);
        setLocationRelativeTo(null);
        // 처음 실행 시 메인 메뉴 표시
        showMainMenu();
    }

    private void showMainMenu() {
        JPanel mainContainer = createHomePage();
        JPanel content = transparent(new BorderLayout(0, 18));

        // 닉네임은 기기에 보관하고, 서버에서는 같은 닉네임의 최고 점수를 관리
        String playerName = ScoreManager.getNickname();
        JPanel identityPanel = Style.framedPanel(new BorderLayout(24, 0), Style.PANEL);
        identityPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        JPanel nicknameArea = transparent(new BorderLayout(0, 7));
        JTextField nicknameField = new JTextField(playerName, 16);
        Style.applyTextFieldStyle(nicknameField);
        nicknameField.setPreferredSize(new Dimension(280, 40));
        nicknameField.getAccessibleContext().setAccessibleDescription("문자, 숫자, 밑줄, 하이픈으로 1~16자 닉네임을 입력하세요.");
        JLabel nicknameLabel = label("닉네임", Style.MUTED_TEXT, Style.SMALL_FONT);
        nicknameLabel.setLabelFor(nicknameField);
        JButton applyNickname = createButton("적용");
        applyNickname.setPreferredSize(new Dimension(78, 40));
        JPanel nicknameRow = transparent(new BorderLayout(10, 0));
        nicknameRow.add(nicknameField, BorderLayout.CENTER);
        nicknameRow.add(applyNickname, BorderLayout.EAST);
        JLabel connectionStatus = label(playerName.isEmpty()
                ? "문자·숫자·_·-로 1~16자 입력해 주세요." : "서버 기록을 불러오는 중입니다.",
                Style.MUTED_TEXT, Style.SMALL_FONT);
        // 적용 버튼과 입력창 Enter는 같은 검증·저장 동작 사용
        // 나중에 닉네임 입력과 연결 상태를 별도 프로필 패널로 나눠도 괜찮을 것 같음
        Runnable saveNickname = () -> {
            try {
                ScoreManager.setNickname(nicknameField.getText());
                showMainMenu();
            } catch (IOException | IllegalArgumentException e) {
                connectionStatus.setText(e.getMessage());
                connectionStatus.setToolTipText(e.getMessage());
                connectionStatus.setForeground(Style.PINK);
            }
        };
        applyNickname.addActionListener(e -> saveNickname.run());
        nicknameField.addActionListener(e -> saveNickname.run());
        JPanel inputArea = transparent(new BorderLayout(0, 7));
        inputArea.add(nicknameLabel, BorderLayout.NORTH);
        inputArea.add(nicknameRow, BorderLayout.CENTER);
        nicknameArea.add(inputArea, BorderLayout.CENTER);
        nicknameArea.add(connectionStatus, BorderLayout.SOUTH);
        identityPanel.add(nicknameArea, BorderLayout.CENTER);

        JPanel recordArea = transparent(new BorderLayout(0, 3));
        recordArea.setPreferredSize(new Dimension(198, 84));
        recordArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Style.BORDER),
                BorderFactory.createEmptyBorder(0, 24, 0, 0)));
        recordArea.add(label("최고 점수", Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.NORTH);
        // 로컬 캐시 점수를 먼저 표시해 서버 응답을 기다리는 동안 기록 영역을 비워 두지 않음
        JLabel bestScore = fittingLabel(playerName.isEmpty() ? "—" : score(ScoreManager.getCachedBestScore(playerName)),
                Style.ACCENT, Style.DISPLAY_FONT.deriveFont(30f));
        bestScore.setToolTipText("클래식 모드 최고 점수");
        recordArea.add(bestScore, BorderLayout.CENTER);
        recordArea.add(label("클래식 최고 점수", Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.SOUTH);
        identityPanel.add(recordArea, BorderLayout.EAST);
        content.add(identityPanel, BorderLayout.NORTH);

        // 모드 카드는 제목·설명·행동을 한 버튼에 담아 전체 영역을 클릭할 수 있게 표시
        JPanel modeSection = transparent(new BorderLayout(0, 10));
        JPanel modeHeading = transparent(new BorderLayout());
        modeHeading.add(label("게임 모드", Style.TEXT, Style.BUTTON_FONT.deriveFont(16f)), BorderLayout.WEST);
        JPanel modes = transparent(new GridLayout(1, 3, 14, 0));
        ModeCard classicButton = new ModeCard("클래식", new String[] { "혼자 즐기는 기본 테트리스" }, "게임 시작", "닉네임 적용 후 시작",
                Style.CLASSIC_BUTTON, Style.TEXT, Style.ACCENT, 0);
        // 닉네임이 저장되기 전에는 클래식 게임을 시작하지 않음
        classicButton.setEnabled(!playerName.isEmpty());
        classicButton.setToolTipText(playerName.isEmpty() ? "닉네임을 적용하면 게임을 시작할 수 있습니다." : "클래식 게임 시작");
        classicButton.addActionListener(e -> {
            Tetris game = new Tetris(GameSettings.getResolution());
            game.setLocationRelativeTo(Main.this);
            game.setVisible(true);
            dispose();
        });
        ModeCard itemButton = new ModeCard("아이템", new String[] { "아이템을 사용하는 게임 모드" }, "준비 중", "준비 중",
                Style.ITEM_BUTTON, Style.TEXT, Style.GREEN, 1);
        ModeCard multiplayerButton = new ModeCard("멀티플레이", new String[] { "1PC 2인 · AI · 네트워크 대전" }, "모드 보기", "준비 중",
                Style.MULTIPLAYER_BUTTON, Style.TEXT, Style.PINK, 2);
        itemButton.addActionListener(e -> showComingSoon("아이템 모드"));
        multiplayerButton.addActionListener(e -> showMultiplayerMenu());
        modes.add(classicButton);
        modes.add(itemButton);
        modes.add(multiplayerButton);
        modeSection.add(modeHeading, BorderLayout.NORTH);
        modeSection.add(modes, BorderLayout.CENTER);
        content.add(modeSection, BorderLayout.CENTER);

        JPanel utilities = transparent(new GridLayout(1, 2, 14, 0));
        JButton rankingButton = createButton("전체 랭킹  →");
        rankingButton.getAccessibleContext().setAccessibleName("랭킹");
        JButton settingsButton = createButton("게임 설정  →");
        settingsButton.getAccessibleContext().setAccessibleName("설정");
        rankingButton.addActionListener(e -> showRankingMenu());
        settingsButton.addActionListener(e -> showSettingsMenu());
        utilities.add(rankingButton);
        utilities.add(settingsButton);
        content.add(utilities, BorderLayout.SOUTH);
        mainContainer.add(content, BorderLayout.CENTER);
        JLabel controls = label("← → 이동　 ↑ ↓ 회전　 D 하강　 SPACE 즉시 낙하　 P 일시정지", Style.MUTED_TEXT, Style.SMALL_FONT);
        controls.setHorizontalAlignment(SwingConstants.CENTER);
        mainContainer.add(controls, BorderLayout.SOUTH);
        showPage(mainContainer, playerName.isEmpty() ? applyNickname : classicButton);

        // 서버 조회는 비동기로 진행하고 응답은 현재 화면에만 표시
        if (!playerName.isEmpty()) {
            ScoreManager.loadBestScore(playerName, result -> {
                // 닉네임 변경·화면 이동·게임 시작 후 도착한 이전 응답은 반영하지 않음
                if (!isDisplayable() || getContentPane() != mainContainer)
                    return;
                bestScore.setText(score(result.getValue()));
                connectionStatus.setText(result.getMessage());
                connectionStatus.setToolTipText(result.getDetail());
                connectionStatus.setForeground(result.isSuccess() ? Style.ACCENT : Style.MUTED_TEXT);
            });
        }
    }

    private void showMultiplayerMenu() {
        JPanel container = createPage("멀티플레이", "대전 기능은 준비 중입니다");
        JPanel content = transparent(new GridBagLayout());
        JButton twoPlayerButton = createModeButton("1PC 2인 게임", "한 컴퓨터에서 두 명이 플레이 · 준비 중", Style.ACCENT);
        JButton aiButton = createModeButton("AI 대전", "AI 상대와 대결하는 게임 · 준비 중", Style.ACCENT);
        JButton networkButton = createModeButton("네트워크 대전", "온라인 상대와 대결하는 게임 · 준비 중", Style.ACCENT);
        // 나중에 각 대전 기능이 구현되면 안내창 대신 게임 화면으로 연결
        // 1PC 2인용 모드로 이동
        twoPlayerButton.addActionListener(e -> {
            TwoPlayerTetris game = new TwoPlayerTetris();
            game.setLocationRelativeTo(Main.this);
            game.setVisible(true);
            dispose();
        });
        aiButton.addActionListener(e -> showComingSoon("AI 대전"));
        networkButton.addActionListener(e -> showComingSoon("네트워크 대전"));
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.insets = new java.awt.Insets(0, 0, 10, 0);
        JButton[] buttons = { twoPlayerButton, aiButton, networkButton };
        for (int i = 0; i < buttons.length; i++) {
            row.gridy = i;
            content.add(buttons[i], row);
        }
        row.gridy = 3;
        row.insets = new java.awt.Insets(10, 0, 0, 0);
        content.add(label("현재 클래식 모드를 플레이할 수 있습니다.", Style.MUTED_TEXT, Style.BODY_FONT), row);
        row.gridy = 4;
        row.weighty = 1;
        row.fill = GridBagConstraints.BOTH;
        content.add(transparent(new BorderLayout()), row);
        container.add(content, BorderLayout.CENTER);
        showSubmenu(container);
    }

    // 서버의 전체 랭킹을 한 페이지에 5명씩 표시
    private void showRankingMenu() {
        showRankingMenu(0);
    }

    // 나중에 랭킹 검색이나 정렬 조건이 늘어나면 별도 화면 클래스로 나눠도 괜찮을 것 같음
    private void showRankingMenu(int pageIndex) {
        JPanel container = createPage("전체 랭킹", "닉네임별 클래식 최고 점수");
        JPanel content = transparent(new BorderLayout(0, 12));
        JLabel status = label("서버 랭킹을 불러오는 중입니다.", Style.MUTED_TEXT, Style.BODY_FONT);
        JPanel table = Style.framedPanel(new BorderLayout(0, 0), Style.PANEL);
        table.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
        JPanel headings = rankingColumns("순위", "닉네임", "최고 점수");
        headings.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Style.BORDER),
                BorderFactory.createEmptyBorder(0, 8, 12, 8)));
        table.add(headings, BorderLayout.NORTH);
        JPanel rankingPanel = transparent(new GridLayout(RANKING_PAGE_SIZE, 1, 0, 0));
        JPanel[] rows = new JPanel[RANKING_PAGE_SIZE];
        JLabel[][] cells = new JLabel[RANKING_PAGE_SIZE][3];
        for (int i = 0; i < rows.length; i++) {
            JPanel row = transparent(new GridBagLayout());
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Style.BORDER),
                    BorderFactory.createEmptyBorder(10, 8, 10, 8)));
            cells[i][0] = label("—", Style.MUTED_TEXT, Style.MONO_FONT);
            cells[i][1] = label(i == 0 ? "기록을 불러오는 중입니다…" : "", Style.MUTED_TEXT, Style.BODY_FONT);
            cells[i][2] = fittingLabel("—", Style.MUTED_TEXT, Style.MONO_FONT);
            addRankingCells(row, cells[i]);
            rankingPanel.add(row);
            rows[i] = row;
        }
        table.add(rankingPanel, BorderLayout.CENTER);
        JPanel navigation = transparent(new BorderLayout(12, 0));
        JPanel paging = transparent(new GridLayout(1, 3, 10, 0));
        JButton previous = createButton("← 이전");
        JButton next = createButton("다음 →");
        JButton refresh = createButton("새로고침");
        refresh.setPreferredSize(new Dimension(116, 44));
        JLabel pageLabel = label((pageIndex + 1) + " 페이지", Style.MUTED_TEXT, Style.BODY_FONT);
        pageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        // 첫 페이지의 이전 이동은 막고 다음 이동은 서버의 전체 인원 확인 후 허용
        previous.setEnabled(pageIndex > 0);
        next.setEnabled(false);
        refresh.setEnabled(false);
        previous.addActionListener(e -> showRankingMenu(pageIndex - 1));
        next.addActionListener(e -> showRankingMenu(pageIndex + 1));
        refresh.addActionListener(e -> showRankingMenu(pageIndex));
        paging.add(previous);
        paging.add(pageLabel);
        paging.add(next);
        navigation.add(paging, BorderLayout.CENTER);
        navigation.add(refresh, BorderLayout.EAST);
        content.add(status, BorderLayout.NORTH);
        content.add(table, BorderLayout.CENTER);
        content.add(navigation, BorderLayout.SOUTH);
        container.add(content, BorderLayout.CENTER);
        showSubmenu(container);

        ScoreManager.loadRanking(pageIndex * RANKING_PAGE_SIZE, RANKING_PAGE_SIZE, result -> {
            // 다른 메뉴로 이동한 뒤 받은 응답은 이전 화면에만 해당하므로 무시
            if (!isDisplayable() || getContentPane() != container)
                return;
            refresh.setEnabled(true);
            status.setToolTipText(result.getDetail());
            // 실패 원인은 상태 문구와 툴팁으로 표시하고 새로고침으로 재조회 가능
            if (!result.isSuccess()) {
                status.setText(result.getMessage());
                status.setForeground(Style.PINK);
                cells[0][1].setText("랭킹을 가져오지 못했습니다.");
                cells[1][1].setText("새로고침으로 다시 시도해 주세요.");
                return;
            }
            RankingPage page = result.getValue();
            // 조회 사이에 인원이 줄어 현재 페이지가 비면 마지막 유효 페이지로 이동
            if (pageIndex > 0 && page.getEntries().isEmpty()) {
                showRankingMenu(Math.max(0, (page.getTotalPlayers() - 1) / RANKING_PAGE_SIZE));
                return;
            }
            status.setText("전체 " + page.getTotalPlayers() + "명 · 클래식 모드 최고 점수");
            pageLabel.setText((pageIndex + 1) + " / " + Math.max(1,
                    (page.getTotalPlayers() + RANKING_PAGE_SIZE - 1) / RANKING_PAGE_SIZE));
            next.setEnabled((pageIndex + 1) * RANKING_PAGE_SIZE < page.getTotalPlayers());
            // 마지막 페이지의 남는 행은 빈칸으로 유지하고 기록이 전혀 없으면 안내 표시
            for (int i = 0; i < rows.length; i++) {
                if (i >= page.getEntries().size()) {
                    cells[i][0].setText("—");
                    cells[i][1].setText(i == 0 ? "아직 등록된 기록이 없습니다." : "");
                    cells[i][2].setText("—");
                    continue;
                }
                RankingEntry entry = page.getEntries().get(i);
                // 내 기록은 이름 옆 표시와 행 색을 함께 사용해 쉽게 찾을 수 있게 표시
                boolean own = entry.getNickname().equals(ScoreManager.getNickname());
                cells[i][0].setText(String.format("%02d", entry.getRank()));
                cells[i][0].setForeground(entry.getRank() <= 3 ? Style.GOLD : Style.MUTED_TEXT);
                cells[i][1].setText(entry.getNickname() + (own ? "  ·  나" : ""));
                cells[i][1].setToolTipText(entry.getNickname() + (own ? " · 내 기록" : ""));
                cells[i][1].setForeground(own ? Style.ACCENT : Style.TEXT);
                cells[i][2].setText(score(entry.getScore()));
                cells[i][2].setForeground(own ? Style.ACCENT : Style.TEXT);
                if (own) {
                    rows[i].setOpaque(true);
                    rows[i].setBackground(new Color(43, 40, 51));
                    rows[i].setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 2, 1, 0, Style.ACCENT),
                            BorderFactory.createEmptyBorder(10, 6, 10, 8)));
                }
            }
        });
    }

    private void showSettingsMenu() {
        JPanel container = createPage("게임 설정", "게임 화면 크기를 선택하세요");
        JPanel content = transparent(new GridBagLayout());
        JPanel resolutionPanel = transparent(new GridLayout(1, 3, 10, 0));
        resolutionPanel.setPreferredSize(new Dimension(0, 64));
        // 한 번에 한 크기만 선택하고 선택 문구를 함께 표시
        ButtonGroup resolutionGroup = new ButtonGroup();
        Resolution[] order = { Resolution.SMALL, Resolution.MEDIUM, Resolution.LARGE };
        JToggleButton[] buttons = new JToggleButton[order.length];
        JLabel selectedDescription = label("", Style.ACCENT, Style.BODY_FONT);
        Runnable updateSelection = () -> {
            Resolution current = GameSettings.getResolution();
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i] != null) {
                    boolean selected = order[i] == current;
                    buttons[i].setText(resolutionText(order[i], selected));
                    buttons[i].getAccessibleContext().setAccessibleDescription(
                            "블록 " + order[i].getCellSize() + "픽셀. " + (selected ? "현재 선택됨" : "선택하려면 누르세요"));
                }
            }
            selectedDescription.setText("현재 선택: " + current + " · 다음 게임부터 적용됩니다.");
        };
        for (int i = 0; i < order.length; i++) {
            Resolution resolution = order[i];
            JToggleButton button = new JToggleButton();
            Style.applyButtonStyle(button, Style.ACCENT, Style.PANEL);
            button.setHorizontalAlignment(SwingConstants.CENTER);
            button.getAccessibleContext().setAccessibleName(resolution.toString());
            resolutionGroup.add(button);
            button.setSelected(resolution == GameSettings.getResolution());
            button.addActionListener(e -> {
                // 선택값만 보관하며 이미 실행 중인 게임의 크기는 변경하지 않음
                // 다음 클래식 시작 시 GameSettings의 선택값을 사용
                GameSettings.setResolution(resolution);
                updateSelection.run();
            });
            buttons[i] = button;
            resolutionPanel.add(button);
        }
        updateSelection.run();
        JPanel resolutionSection = transparent(new BorderLayout(0, 12));
        resolutionSection.add(label("화면 크기", Style.TEXT, Style.BUTTON_FONT), BorderLayout.NORTH);
        resolutionSection.add(resolutionPanel, BorderLayout.CENTER);
        resolutionSection.add(selectedDescription, BorderLayout.SOUTH);
        // 설정과 조작 안내는 필요한 높이만 사용하고 남은 공간은 아래 여백으로 유지
        GridBagConstraints section = new GridBagConstraints();
        section.gridx = 0;
        section.gridy = 0;
        section.weightx = 1;
        section.fill = GridBagConstraints.HORIZONTAL;
        content.add(resolutionSection, section);
        section.gridy = 1;
        section.insets = new java.awt.Insets(20, 0, 0, 0);
        content.add(infoPanel("조작 방법", "<html>← → 이동　　↑ ↓ 회전<br><br>D 한 칸 하강　　SPACE 즉시 낙하<br><br>P 일시정지 / 재개</html>"), section);
        section.gridy = 2;
        section.weighty = 1;
        section.fill = GridBagConstraints.BOTH;
        content.add(transparent(new BorderLayout()), section);
        container.add(content, BorderLayout.CENTER);
        showSubmenu(container);
    }

    private String resolutionText(Resolution resolution, boolean selected) {
        return "<html><div style='text-align:center'><b>" + resolution + "</b>"
                + "<br><span style='font-size:10pt;font-weight:normal'>" + resolution.getCellSize() + " px"
                + (selected ? " · 선택됨" : "") + "</span></div></html>";
    }

    // 메인은 큰 제목과 전체 폭 프로필을 사용하고 하위 화면의 이동 메뉴와 구분
    private JPanel createHomePage() {
        JPanel container = Style.createBackground(new BorderLayout(0, 18));
        container.setBorder(BorderFactory.createEmptyBorder(28, 34, 22, 34));
        JPanel header = transparent(new BorderLayout(0, 14));
        JLabel logo = new JLabel(new PixelLogo(8));
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        logo.getAccessibleContext().setAccessibleName("TETRIS");
        logo.getAccessibleContext().setAccessibleDescription("테트리스 메인 메뉴");
        header.add(logo, BorderLayout.CENTER);
        JLabel description = label("클래식 게임 · 최고 점수 기록", Style.MUTED_TEXT, Style.BODY_FONT);
        description.setHorizontalAlignment(SwingConstants.CENTER);
        header.add(description, BorderLayout.SOUTH);
        container.add(header, BorderLayout.NORTH);
        return container;
    }

    // 제목과 여백을 공유해 화면을 바꿔도 같은 메뉴 형태 유지
    private JPanel createPage(String heading, String description) {
        JPanel container = Style.createBackground(new BorderLayout(0, 26));
        container.setBorder(BorderFactory.createEmptyBorder(22, 32, 22, 32));
        JPanel header = transparent(new BorderLayout(0, 24));
        JPanel topbar = transparent(new BorderLayout());
        JLabel logo = new JLabel(new PixelLogo(3));
        logo.getAccessibleContext().setAccessibleName("TETRIS");
        topbar.add(logo, BorderLayout.WEST);
        JPanel navigation = transparent(new GridLayout(1, 2, 14, 0));
        JButton ranking = createTextButton("랭킹", "랭킹");
        JButton settings = createTextButton("설정", "설정");
        ranking.addActionListener(e -> showRankingMenu());
        settings.addActionListener(e -> showSettingsMenu());
        navigation.add(ranking);
        navigation.add(settings);
        topbar.add(navigation, BorderLayout.EAST);
        header.add(topbar, BorderLayout.NORTH);
        if (!heading.isEmpty()) {
            JPanel section = transparent(new BorderLayout(0, 7));
            section.add(label(heading, Style.TEXT, Style.TITLE_FONT.deriveFont(28f)), BorderLayout.NORTH);
            section.add(label(description, Style.MUTED_TEXT, Style.BODY_FONT), BorderLayout.SOUTH);
            header.add(section, BorderLayout.CENTER);
        }
        container.add(header, BorderLayout.NORTH);
        return container;
    }

    private JPanel rankingColumns(String rank, String nickname, String value) {
        JPanel panel = transparent(new GridBagLayout());
        addRankingCells(panel, new JLabel[] {
                label(rank, Style.MUTED_TEXT, Style.SMALL_FONT),
                label(nickname, Style.MUTED_TEXT, Style.SMALL_FONT),
                label(value, Style.MUTED_TEXT, Style.SMALL_FONT) });
        return panel;
    }

    // 순위·점수 열은 고정하고 닉네임은 남은 폭을 사용해 점수와 겹치지 않게 표시
    private void addRankingCells(JPanel panel, JLabel[] labels) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.weightx = 0;
        labels[0].setPreferredSize(new Dimension(60, 26));
        panel.add(labels[0], c);
        c.gridx = 1;
        c.weightx = 1;
        labels[1].setMinimumSize(new Dimension(0, 26));
        panel.add(labels[1], c);
        c.gridx = 2;
        c.weightx = 0;
        labels[2].setHorizontalAlignment(SwingConstants.RIGHT);
        labels[2].setPreferredSize(new Dimension(136, 26));
        panel.add(labels[2], c);
    }

    // 준비 상태와 조작 안내에 같은 여백·표면 사용
    // 나중에 안내 종류가 늘어나면 공통 안내 컴포넌트로 나눠도 괜찮을 것 같음
    private JPanel infoPanel(String heading, String description) {
        JPanel panel = Style.framedPanel(new BorderLayout(0, 8), Style.PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        panel.add(label(heading, Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.NORTH);
        panel.add(label(description, Style.TEXT, Style.BODY_FONT), BorderLayout.CENTER);
        return panel;
    }

    private JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private JLabel label(String text, Color color, Font font) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private JLabel fittingLabel(String text, Color color, Font font) {
        JLabel label = new FittingLabel(text, font);
        label.setForeground(color);
        return label;
    }

    // 서버 점수가 길어져도 숫자를 생략하지 않고 현재 영역에 맞춰 폰트만 줄임
    // 나중에 점수 표시 위치가 늘어나면 공통 숫자 표시 컴포넌트로 나눠도 괜찮을 것 같음
    private static final class FittingLabel extends JLabel {
        private final Font baseFont;

        FittingLabel(String text, Font font) {
            super(text);
            baseFont = font;
            setFont(font);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            int available = getWidth() - getInsets().left - getInsets().right;
            String text = getText();
            Font fitted = baseFont;
            if (available > 0 && text != null && !text.isEmpty()) {
                float size = baseFont.getSize2D();
                // 한 단계씩 실측해 폰트별 숫자 폭과 쉼표 폭도 함께 반영함
                while (size > 10f && getFontMetrics(fitted).stringWidth(text) > available) {
                    size = Math.max(10f, size - 0.5f);
                    fitted = baseFont.deriveFont(size);
                }
            }
            if (!fitted.equals(getFont()))
                setFont(fitted);
            super.paintComponent(graphics);
        }
    }

    private String score(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    // 모드 이름과 상태 한 줄만 표시하고 키보드·마우스 처리는 공통 버튼 스타일 사용
    private JButton createModeButton(String title, String description, Color accent) {
        JButton button = new JButton(modeButtonText(title, description, Style.TEXT));
        Style.applyButtonStyle(button, accent, Style.PANEL);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setPreferredSize(new Dimension(0, 74));
        button.getAccessibleContext().setAccessibleName(title);
        button.getAccessibleContext().setAccessibleDescription(description);
        return button;
    }

    private String modeButtonText(String title, String description, Color color) {
        String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return "<html><font color='" + hex + "'><b style='font-size:14pt'>" + title + "</b>"
                + "<br><span style='font-size:10pt;font-weight:normal'>" + description + "</span></font></html>";
    }

    // 보조 이동은 배경 없는 텍스트 버튼으로 표시하고 동작 이름은 접근성 정보에 별도 보관
    private JButton createTextButton(String text, String accessibleName) {
        JButton button = createButton(text);
        button.putClientProperty("textButton", true);
        button.setForeground(Style.MUTED_TEXT);
        button.setPreferredSize(new Dimension(86, 34));
        button.getAccessibleContext().setAccessibleName(accessibleName);
        return button;
    }

    private JButton createButton(String title) {
        JButton button = new JButton(title);
        Style.applyButtonStyle(button, Style.ACCENT, Style.PANEL);
        button.setFont(Style.BUTTON_FONT.deriveFont(14f));
        button.setPreferredSize(new Dimension(0, 44));
        return button;
    }

    private void showSubmenu(JPanel container) {
        JPanel footer = transparent(new BorderLayout());
        JButton backButton = createButton("← 뒤로가기");
        backButton.setPreferredSize(new Dimension(146, 44));
        backButton.addActionListener(e -> showMainMenu());
        footer.add(backButton, BorderLayout.WEST);
        container.add(footer, BorderLayout.SOUTH);
        showPage(container, backButton);
    }

    // 화면 교체 시 Enter로 실행할 기본 버튼도 함께 변경
    // 메인에서는 적용 또는 클래식, 하위 메뉴에서는 뒤로가기 사용
    private void showPage(JPanel container, JButton defaultButton) {
        setContentPane(container);
        getRootPane().setDefaultButton(defaultButton);
        revalidate();
        repaint();
    }

    private void showComingSoon(String mode) {
        JPanel content = transparent(new BorderLayout(0, 10));
        content.add(label(mode, Style.TEXT, Style.BUTTON_FONT.deriveFont(20f)), BorderLayout.NORTH);
        content.add(label("이 기능은 준비 중입니다.", Style.MUTED_TEXT, Style.BODY_FONT), BorderLayout.CENTER);
        JButton confirm = createButton("확인");
        confirm.setPreferredSize(new Dimension(96, 40));
        JOptionPane pane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, new Object[] { confirm });
        pane.setBackground(Style.BACKGROUND);
        pane.setBorder(BorderFactory.createEmptyBorder(20, 20, 18, 20));
        // 준비 안내도 메뉴와 같은 표면을 사용하고 확인·창 닫기는 기존 모달 동작 유지
        themeDialogPanels(pane);
        javax.swing.JDialog dialog = pane.createDialog(this, "Tetris");
        confirm.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(confirm);
        dialog.setVisible(true);
        dialog.dispose();
    }

    private void themeDialogPanels(java.awt.Component component) {
        if (component instanceof JPanel)
            component.setBackground(Style.BACKGROUND);
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents())
                themeDialogPanels(child);
        }
    }

    // Swing 화면을 UI 전용 스레드에서 생성
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main menu = new Main();
            menu.setVisible(true);
        });
    }
}
