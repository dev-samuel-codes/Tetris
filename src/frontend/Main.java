// 앱 진입 화면 파일
package frontend;

import frontend.style.Style;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

// 앱 메인 화면
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

    // =========================
    // 메인 메뉴 화면
    // =========================
    private void showMainMenu() {

        JPanel mainContainer = new JPanel(new BorderLayout());

        // 제목
        JLabel title = new JLabel(
                "TETRIS",
                SwingConstants.CENTER
        );
        title.setFont(Style.TITLE_FONT);

        // 세 가지 게임 모드 버튼을 세로로 배치
        JPanel buttonPanel = new JPanel(
                new GridLayout(4, 1, 20, 20)
        );

        // 버튼들이 화면 끝에 붙지 않도록 여백 설정
        buttonPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        80, 100, 100, 100
                )
        );

        // 게임 모드 버튼
        JButton classicButton = new JButton("클래식");
        JButton itemButton = new JButton("아이템");
        JButton multiplayerButton = new JButton("멀티플레이");
        JButton settingsButton = new JButton("설정");

        // 버튼 폰트 설정
        classicButton.setFont(Style.BUTTON_FONT);
        itemButton.setFont(Style.BUTTON_FONT);
        multiplayerButton.setFont(Style.BUTTON_FONT);
        settingsButton.setFont(Style.BUTTON_FONT);

        // 클래식 모드
        classicButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // 기존 테트리스 JFrame 실행
                Tetris game = new Tetris(GameSettings.getResolution()); // 기본값 '보통'
                game.setLocationRelativeTo(Main.this);
                game.setVisible(true);

                // 메인 화면 닫기
                dispose();
            }
        });

        // 아이템전
        itemButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // 나중에 아이템전 화면 연결
                System.out.println("아이템전");
            }
        });

        // 멀티플레이 선택 화면으로 이동
        multiplayerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showMultiplayerMenu();
            }
        });

        // 설정 화면으로 이동
        settingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSettingsMenu();
            }
            // 설정 화면
            private void showSettingsMenu() {

                JPanel settingsContainer = new JPanel(new BorderLayout());

                // 설정 제목
                JLabel title = new JLabel("설정", SwingConstants.CENTER);
                title.setFont(Style.TITLE_FONT);

                // 크기 설정 버튼 생성
                JPanel resolutionPanel = new JPanel(new GridLayout(3, 1, 20, 20));
                resolutionPanel.setBorder(BorderFactory.createEmptyBorder(150, 250, 200, 250));

                // 크기 선택
                ButtonGroup resolutionGroup = new ButtonGroup();

                // 크기 설정
                Resolution[] order = { Resolution.LARGE, Resolution.MEDIUM, Resolution.SMALL };

                for (Resolution resolution : order) {
                    JToggleButton button = new JToggleButton(resolution.toString());
                    button.setFont(Style.BUTTON_FONT);
                    button.setSelected(resolution == GameSettings.getResolution());

                    // 설정 저장
                    button.addActionListener(e -> GameSettings.setResolution(resolution));

                    resolutionGroup.add(button);
                    resolutionPanel.add(button);
                }

                // 메인 메뉴로 돌아가기
                JButton backButton = new JButton("뒤로가기");
                backButton.setFont(Style.BUTTON_FONT);
                backButton.addActionListener(e -> showMainMenu());

                // 설정 화면 구성
                settingsContainer.add(title, BorderLayout.NORTH);
                settingsContainer.add(resolutionPanel, BorderLayout.CENTER);
                settingsContainer.add(backButton, BorderLayout.SOUTH);

                // 현재 JFrame의 화면을 설정 화면으로 교체
                setContentPane(settingsContainer);
                revalidate();
                repaint();
            }
        });



        // 버튼 패널에 버튼 추가
        buttonPanel.add(classicButton);
        buttonPanel.add(itemButton);
        buttonPanel.add(multiplayerButton);
        buttonPanel.add(settingsButton);

        // 메인 화면 구성
        mainContainer.add(title, BorderLayout.NORTH);
        mainContainer.add(buttonPanel, BorderLayout.CENTER);

        // 현재 JFrame의 화면을 메인 메뉴로 교체
        setContentPane(mainContainer);

        // 화면 갱신
        revalidate();
        repaint();
    }

    // =========================
    // 멀티플레이 선택 화면
    // =========================
    private void showMultiplayerMenu() {

        JPanel multiplayerContainer = new JPanel(new BorderLayout());

        // 멀티플레이 제목
        JLabel title = new JLabel(
                "멀티플레이",
                SwingConstants.CENTER
        );
        title.setFont(Style.TITLE_FONT);

        // 1PC 2인 게임 / AI 대전 / 네트워크 대전 버튼
        JPanel buttonPanel = new JPanel(
                new GridLayout(3, 1, 20, 20)
        );

        buttonPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        120, 120, 120, 120
                )
        );

        JButton twoPlayerButton = new JButton("1PC 2인 게임");
        JButton aiButton = new JButton("AI 대전");
        JButton networkButton = new JButton("네트워크 대전");
        JButton backButton = new JButton("뒤로가기");

        // 버튼 폰트 설정
        twoPlayerButton.setFont(Style.BUTTON_FONT);
        aiButton.setFont(Style.BUTTON_FONT);
        networkButton.setFont(Style.BUTTON_FONT);
        backButton.setFont(Style.BUTTON_FONT);

        // 1PC 2인 게임
        twoPlayerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 나중에 1PC 2인 화면 연결
                System.out.println("1PC 2인 게임");
            }
        });

        // AI 대전
        aiButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // 나중에 AI 대전 화면 연결
                System.out.println("AI 대전");
            }
        });

        // 네트워크 대전
        networkButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // 나중에 네트워크 대전 화면 연결
                System.out.println("네트워크 대전");
            }
        });

        // 메인 메뉴로 돌아가기
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showMainMenu();
            }
        });

        // 버튼 추가
        buttonPanel.add(twoPlayerButton);
        buttonPanel.add(aiButton);
        buttonPanel.add(networkButton);

        // 멀티플레이 화면 구성
        multiplayerContainer.add(title, BorderLayout.NORTH);
        multiplayerContainer.add(buttonPanel, BorderLayout.CENTER);
        multiplayerContainer.add(backButton, BorderLayout.SOUTH);

        // 현재 JFrame의 화면을 멀티플레이 메뉴로 교체
        setContentPane(multiplayerContainer);

        // 화면 갱신
        revalidate();
        repaint();
    }

    // 시작 화면에서 테트리스 게임 화면으로 전환
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main menu = new Main();
            menu.setVisible(true);
        });
    }
}
