// 앱 진입 화면 파일
package frontend;

import frontend.style.Style;
import frontend.ui.ModeCard;
import frontend.ui.PixelLogo;
import frontend.ai.AiMatchWindow;
import frontend.online.OnlineMatchWindow;
import frontend.network.ScoreClient.RankingEntry;
import frontend.network.ScoreClient.RankingPage;
import shared.score.ScoreMode;

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
import java.awt.event.KeyEvent;
import javax.swing.JScrollPane;

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
        setResizable(true);
        setMinimumSize(new Dimension(Math.min(800, getWidth()), Math.min(650, getHeight())));
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
        recordArea.setPreferredSize(new Dimension(222, 90));
        recordArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Style.BORDER),
                BorderFactory.createEmptyBorder(0, 24, 0, 0)));
        recordArea.add(label("최고 점수", Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.NORTH);
        // 로컬 캐시 점수를 먼저 표시해 서버 응답을 기다리는 동안 기록 영역을 비워 두지 않음
        JLabel bestScore = fittingLabel(playerName.isEmpty() ? "—" : score(ScoreManager.getCachedBestScore(ScoreMode.CLASSIC, playerName)),
                Style.ACCENT, Style.DISPLAY_FONT.deriveFont(24f));
        bestScore.setToolTipText("클래식 모드 최고 점수");
        bestScore.getAccessibleContext().setAccessibleName("클래식 최고 점수");
        JLabel itemBestScore = fittingLabel(playerName.isEmpty() ? "—" : score(ScoreManager.getCachedBestScore(ScoreMode.ITEM, playerName)),
                Style.GREEN, Style.DISPLAY_FONT.deriveFont(24f));
        itemBestScore.setToolTipText("아이템 모드 최고 점수");
        itemBestScore.getAccessibleContext().setAccessibleName("아이템 최고 점수");
        JPanel records = transparent(new GridLayout(2, 1, 0, 3));
        JLabel[] values = { bestScore, itemBestScore };
        ScoreMode[] scoreModes = { ScoreMode.CLASSIC, ScoreMode.ITEM };
        for (int i = 0; i < values.length; i++) {
            JPanel recordRow = transparent(new BorderLayout(10, 0));
            recordRow.add(label(scoreModes[i].getDisplayName(), Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.WEST);
            values[i].setHorizontalAlignment(SwingConstants.RIGHT);
            recordRow.add(values[i], BorderLayout.CENTER);
            records.add(recordRow);
        }
        recordArea.add(records, BorderLayout.CENTER);
        identityPanel.add(recordArea, BorderLayout.EAST);
        content.add(identityPanel, BorderLayout.NORTH);

        // 모드 카드는 제목·설명·행동을 한 버튼에 담아 전체 영역을 클릭할 수 있게 표시
        JPanel modeSection = transparent(new BorderLayout(0, 10));
        JPanel modeHeading = transparent(new BorderLayout());
        modeHeading.add(label("게임 모드", Style.TEXT, Style.BUTTON_FONT.deriveFont(16f)), BorderLayout.WEST);
        JPanel modes = transparent(new GridLayout(1, 3, 14, 0));
        ModeCard classicButton = new ModeCard("클래식", new String[] { "한 줄씩 쌓아 올리는 나만의 기록", "줄 제거 · 콤보 · 최고 점수" }, "게임 시작", "닉네임 적용 후 시작",
                Style.CLASSIC_BUTTON, Style.TEXT, Style.ACCENT, 0);
        // 닉네임이 저장되기 전에는 싱글 게임을 시작하지 않음
        classicButton.setEnabled(!playerName.isEmpty());
        classicButton.setToolTipText(playerName.isEmpty() ? "닉네임을 적용하면 게임을 시작할 수 있습니다." : "클래식 게임 시작");
        classicButton.addActionListener(e -> {
            Tetris game = new Tetris(GameSettings.getResolution());
            game.setLocationRelativeTo(Main.this);
            game.setVisible(true);
            dispose();
        });
        ModeCard itemButton = new ModeCard("아이템", new String[] { "블록 속 아이템으로 만드는 반전", "폭탄 · 줄 제거 · 점수 보너스" }, "게임 시작", "닉네임 적용 후 시작",
                Style.ITEM_BUTTON, Style.TEXT, Style.GREEN, 1);
        itemButton.setEnabled(!playerName.isEmpty());
        itemButton.setToolTipText(playerName.isEmpty() ? "닉네임을 적용하면 게임을 시작할 수 있습니다." : "아이템 게임 시작");
        itemButton.addActionListener(e -> {
            Tetris game = new Tetris(GameSettings.getResolution(), "아이템");
            game.setLocationRelativeTo(Main.this);
            game.setVisible(true);
            dispose();
        });
        ModeCard multiplayerButton = new ModeCard("멀티플레이", new String[] { "함께할 때 더 짜릿한 점수 대결", "1PC 2인 · AI · 온라인" }, "모드 보기", "준비 중",
                Style.MULTIPLAYER_BUTTON, Style.TEXT, Style.PINK, 2);
        multiplayerButton.addActionListener(e -> showMultiplayerMenu());
        modes.add(classicButton);
        modes.add(itemButton);
        modes.add(multiplayerButton);
        modeSection.add(modeHeading, BorderLayout.NORTH);
        modeSection.add(modes, BorderLayout.CENTER);
        content.add(modeSection, BorderLayout.CENTER);

        JPanel utilities = transparent(new GridLayout(1, 3, 14, 0));
        JButton rankingButton = createButton("전체 랭킹  →");
        rankingButton.getAccessibleContext().setAccessibleName("랭킹");
        JButton settingsButton = createButton("게임 설정  →");
        settingsButton.getAccessibleContext().setAccessibleName("설정");
        rankingButton.addActionListener(e -> showRankingMenu());
        settingsButton.addActionListener(e -> showSettingsMenu());
        utilities.add(rankingButton);
        utilities.add(settingsButton);
        JButton guideButton = createButton("플레이 가이드  →");
        guideButton.getAccessibleContext().setAccessibleName("플레이 가이드");
        guideButton.addActionListener(e -> showGuide());
        utilities.add(guideButton);
        content.add(utilities, BorderLayout.SOUTH);
        mainContainer.add(content, BorderLayout.CENTER);
        JLabel controls = label(GameSettings.controlsSummary(), Style.MUTED_TEXT, Style.SMALL_FONT);
        controls.setHorizontalAlignment(SwingConstants.CENTER);
        mainContainer.add(controls, BorderLayout.SOUTH);
        showPage(mainContainer, playerName.isEmpty() ? applyNickname : classicButton);

        // 서버 조회는 비동기로 진행하고 응답은 현재 화면에만 표시
        if (!playerName.isEmpty()) {
            int[] remaining = { scoreModes.length };
            boolean[] failed = { false };
            for (int i = 0; i < scoreModes.length; i++) {
                ScoreMode mode = scoreModes[i];
                JLabel valueLabel = values[i];
                ScoreManager.loadBestScore(mode, playerName, result -> {
                    // 닉네임 변경·화면 이동·게임 시작 후 도착한 이전 응답은 반영하지 않음
                    if (!isDisplayable() || getContentPane() != mainContainer)
                        return;
                    valueLabel.setText(score(result.getValue()));
                    remaining[0]--;
                    // 두 기록을 모두 불러오면 한 줄로 안내하고, 조회 실패는 성공 응답으로 덮지 않음
                    if (!result.isSuccess()) {
                        failed[0] = true;
                        connectionStatus.setText(result.getMessage());
                        connectionStatus.setToolTipText(mode.getDisplayName() + ": " + result.getDetail());
                        connectionStatus.setForeground(Style.PINK);
                    } else if (remaining[0] == 0 && !failed[0]) {
                        connectionStatus.setText("서버 기록을 불러왔습니다");
                        connectionStatus.setToolTipText(null);
                        connectionStatus.setForeground(Style.ACCENT);
                    }
                });
            }
        }
    }

    private void showMultiplayerMenu() {
        JPanel container = createPage("멀티플레이", "한 컴퓨터에서 함께 플레이하거나 AI·온라인 상대와 점수 대결");
        JPanel content = transparent(new BorderLayout(0, 14));
        JPanel modeSection = transparent(new BorderLayout(0, 10));
        modeSection.add(label("대전 모드", Style.TEXT, Style.BUTTON_FONT), BorderLayout.NORTH);
        JPanel modes = transparent(new GridLayout(1, 3, 14, 0));
        ModeCard twoPlayerButton = new ModeCard("1PC 2인 게임",
                new String[] { "한 컴퓨터에서 나란히 플레이", "두 사람이 함께하는 로컬 대전" },
                "2인 게임 시작", "준비 중", Style.PANEL, Style.TEXT, Style.ACCENT, 0);
        ModeCard aiButton = new ModeCard("AI 대전",
                new String[] { "나에게 맞는 상대와 실력 겨루기", "5단계 난이도 · 같은 블록 순서" },
                "난이도 선택", "준비 중", Style.PANEL, Style.TEXT, Style.GREEN, 1);
        ModeCard networkButton = new ModeCard("네트워크 대전",
                new String[] { "방 코드를 공유하고 친구 초대", "같은 블록 순서 · 1:1 점수 대결" },
                "대기방 열기", "준비 중", Style.PANEL, Style.TEXT, Style.PINK, 2);
        // 구현된 대전 모드는 각각의 게임 화면으로 연결
        twoPlayerButton.addActionListener(e -> {
            TwoPlayerTetris game = new TwoPlayerTetris();
            game.setLocationRelativeTo(Main.this);
            game.setVisible(true);
            dispose();
        });
        aiButton.addActionListener(e -> {
            AiMatchWindow game = new AiMatchWindow(GameSettings.getResolution());
            game.setLocationRelativeTo(Main.this);
            game.setVisible(true);
            dispose();
        });
        networkButton.addActionListener(e -> {
            OnlineMatchWindow game = new OnlineMatchWindow(GameSettings.getResolution());
            game.setLocationRelativeTo(Main.this);
            game.setVisible(true);
            dispose();
        });
        modes.add(twoPlayerButton);
        modes.add(aiButton);
        modes.add(networkButton);
        modeSection.add(modes, BorderLayout.CENTER);
        content.add(modeSection, BorderLayout.CENTER);
        content.add(label("함께할 상대와 플레이 방식을 선택하세요.", Style.MUTED_TEXT, Style.SMALL_FONT), BorderLayout.SOUTH);
        container.add(content, BorderLayout.CENTER);
        showSubmenu(container);
    }

    // 서버의 전체 랭킹을 한 페이지에 5명씩 표시
    private void showRankingMenu() {
        showRankingMenu(ScoreMode.CLASSIC, 0);
    }

    // 나중에 랭킹 검색이나 정렬 조건이 늘어나면 별도 화면 클래스로 나눠도 괜찮을 것 같음
    private void showRankingMenu(ScoreMode mode, int pageIndex) {
        JPanel container = createPage("전체 랭킹", "닉네임별 " + mode.getDisplayName() + " 최고 점수");
        JPanel content = transparent(new BorderLayout(0, 12));
        JLabel status = label("서버 랭킹을 불러오는 중입니다.", Style.MUTED_TEXT, Style.BODY_FONT);
        JPanel rankingHeader = transparent(new BorderLayout(0, 10));
        JPanel modeTabs = transparent(new GridLayout(1, 2, 10, 0));
        ButtonGroup modeGroup = new ButtonGroup();
        for (ScoreMode tabMode : ScoreMode.values()) {
            JToggleButton tab = new JToggleButton(tabMode.getDisplayName());
            Style.applyButtonStyle(tab, Style.ACCENT, Style.PANEL);
            tab.setSelected(tabMode == mode);
            tab.getAccessibleContext().setAccessibleName(tabMode.getDisplayName() + " 랭킹");
            tab.addActionListener(e -> {
                if (tabMode != mode)
                    showRankingMenu(tabMode, 0);
            });
            modeGroup.add(tab);
            modeTabs.add(tab);
        }
        rankingHeader.add(modeTabs, BorderLayout.NORTH);
        rankingHeader.add(status, BorderLayout.SOUTH);
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
        previous.addActionListener(e -> showRankingMenu(mode, pageIndex - 1));
        next.addActionListener(e -> showRankingMenu(mode, pageIndex + 1));
        refresh.addActionListener(e -> showRankingMenu(mode, pageIndex));
        paging.add(previous);
        paging.add(pageLabel);
        paging.add(next);
        navigation.add(paging, BorderLayout.CENTER);
        navigation.add(refresh, BorderLayout.EAST);
        content.add(rankingHeader, BorderLayout.NORTH);
        content.add(table, BorderLayout.CENTER);
        content.add(navigation, BorderLayout.SOUTH);
        container.add(content, BorderLayout.CENTER);
        showSubmenu(container);

        ScoreManager.loadRanking(mode, pageIndex * RANKING_PAGE_SIZE, RANKING_PAGE_SIZE, result -> {
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
                showRankingMenu(mode, Math.max(0, (page.getTotalPlayers() - 1) / RANKING_PAGE_SIZE));
                return;
            }
            status.setText("전체 " + page.getTotalPlayers() + "명 · " + mode.getDisplayName() + " 모드 최고 점수");
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
        JPanel container = createPage("게임 설정", "나에게 맞는 화면, 사운드, 조작키");
        ((BorderLayout) container.getLayout()).setVgap(16);
        container.add(new frontend.ui.SettingsPanel(), BorderLayout.CENTER);
        showSubmenu(container);
    }

    private void showGuide() {
        JPanel container = createPage("플레이 가이드", "쌓고, 채우고, 지우세요. 다음 블록을 보면 한 수 앞설 수 있습니다.");
        JPanel content = transparent(new GridLayout(0, 1, 0, 12));
        content.add(infoPanel("01  기본 플레이", "<html>가로 한 줄을 빈틈없이 채우면 줄이 사라집니다. 블록이 꼭대기까지 쌓이면 게임이 끝납니다.<br>착지 위치를 보여 주는 고스트와 다음 블록을 활용해 보세요.</html>"));
        content.add(infoPanel("02  점수와 콤보", "<html>한 번에 1 / 2 / 3 / 4줄을 지우면 10 / 25 / 40 / 60점입니다.<br>연속해서 줄을 지우면 콤보 보너스를 얻습니다. 점수가 오르면 블록이 더 빨리 내려옵니다.</html>"));
        content.add(infoPanel("03  아이템과 대전", "<html>아이템 칸을 지우면 줄·열 제거, 폭탄, 점수 업 등 다양한 효과가 발동합니다.<br>멀티플레이에서 한 컴퓨터의 2인 플레이, 5단계 AI, 방 코드로 만나는 온라인 대전을 선택하세요.</html>"));
        content.add(infoPanel("현재 조작키", "<html>" + GameSettings.controlsSummary() + "<br><br>싱글플레이는 창을 벗어나면 일시정지됩니다. 온라인 대전은 계속 진행됩니다.<br>조작키는 게임 설정에서 바꿀 수 있습니다.</html>"));
        JScrollPane scroll = new JScrollPane(content);
        Style.applyScrollStyle(scroll);
        container.add(scroll, BorderLayout.CENTER);
        showSubmenu(container);
    }

    // 메인은 큰 제목과 전체 폭 프로필을 사용하고 하위 화면의 이동 메뉴와 구분
    private JPanel createHomePage() {
        JPanel container = Style.createBackground(new BorderLayout(0, 18));
        container.setBorder(BorderFactory.createEmptyBorder(28, 34, 22, 34));
        JPanel header = transparent(new BorderLayout(24, 10));
        JLabel logo = new JLabel(new PixelLogo(7));
        logo.setHorizontalAlignment(SwingConstants.LEFT);
        logo.getAccessibleContext().setAccessibleName("TETRIS");
        logo.getAccessibleContext().setAccessibleDescription("테트리스 메인 메뉴");
        header.add(logo, BorderLayout.WEST);
        JPanel headline = transparent(new BorderLayout(0, 5));
        JLabel tagline = label("한 줄 더, 한 판 더.", Style.TEXT, Style.TITLE_FONT.deriveFont(28f));
        tagline.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel description = label("오늘의 플레이가 새로운 최고 기록으로", Style.MUTED_TEXT, Style.BODY_FONT);
        description.setHorizontalAlignment(SwingConstants.RIGHT);
        headline.add(tagline, BorderLayout.NORTH);
        headline.add(description, BorderLayout.SOUTH);
        header.add(headline, BorderLayout.CENTER);
        header.setBorder(BorderFactory.createEmptyBorder(8, 0, 12, 0));
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
        JLabel hint = label("ESC  메인 메뉴", Style.MUTED_TEXT, Style.SMALL_FONT);
        footer.add(hint, BorderLayout.EAST);
        container.add(footer, BorderLayout.SOUTH);
        container.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "menu-back");
        container.getActionMap().put("menu-back", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { showMainMenu(); }
        });
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
