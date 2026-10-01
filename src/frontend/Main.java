// 앱 진입 화면 파일
package frontend;

import frontend.style.Style;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
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

        // 4개의 게임 모드 버튼을 2 x 2로 배치
        JPanel buttonPanel = new JPanel(
                new GridLayout(2, 2, 20, 20)
        );

        // 버튼들이 화면 끝에 붙지 않도록 여백 설정
        buttonPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        80, 100, 100, 100
                )
        );

        // 게임 모드 버튼
        JButton normalButton = new JButton("기본 모드");
        JButton itemButton = new JButton("아이템전");
        JButton twoPlayerButton = new JButton("1PC 2인 게임");
        JButton pvpButton = new JButton("PVP");

        // 버튼 폰트 설정
        normalButton.setFont(Style.BUTTON_FONT);
        itemButton.setFont(Style.BUTTON_FONT);
        twoPlayerButton.setFont(Style.BUTTON_FONT);
        pvpButton.setFont(Style.BUTTON_FONT);

        // 기본 모드
        normalButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // 기존 테트리스 JFrame 실행
                Tetris game = new Tetris();
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

        // 1PC 2인 게임
        twoPlayerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // 나중에 1PC 2인 화면 연결
                System.out.println("1PC 2인 게임");
            }
        });

        // PVP 선택 화면으로 이동
        pvpButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showPvpMenu();
            }
        });

        // 버튼 패널에 버튼 추가
        buttonPanel.add(normalButton);
        buttonPanel.add(itemButton);
        buttonPanel.add(twoPlayerButton);
        buttonPanel.add(pvpButton);

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
    // PVP 선택 화면
    // =========================
    private void showPvpMenu() {

        JPanel pvpContainer = new JPanel(new BorderLayout());

        // PVP 제목
        JLabel title = new JLabel(
                "PVP",
                SwingConstants.CENTER
        );
        title.setFont(Style.TITLE_FONT);

        // AI 대전 / 네트워크 대전 버튼
        JPanel buttonPanel = new JPanel(
                new GridLayout(1, 2, 20, 20)
        );

        buttonPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        120, 120, 120, 120
                )
        );

        JButton aiButton = new JButton("AI 대전");
        JButton networkButton = new JButton("네트워크 대전");
        JButton backButton = new JButton("뒤로가기");

        // 버튼 폰트 설정
        aiButton.setFont(Style.BUTTON_FONT);
        networkButton.setFont(Style.BUTTON_FONT);
        backButton.setFont(Style.BUTTON_FONT);

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
        buttonPanel.add(aiButton);
        buttonPanel.add(networkButton);

        // PVP 화면 구성
        pvpContainer.add(title, BorderLayout.NORTH);
        pvpContainer.add(buttonPanel, BorderLayout.CENTER);
        pvpContainer.add(backButton, BorderLayout.SOUTH);

        // 현재 JFrame의 화면을 PVP 메뉴로 교체
        setContentPane(pvpContainer);

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
