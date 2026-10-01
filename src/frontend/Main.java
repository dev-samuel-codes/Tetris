// 앱 진입 화면 파일
package frontend;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

// 메인 화면
public class Main extends JFrame {
    public Main() {
        setTitle("게임 선택");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // 여백 설정
        JPanel MainContainer = new JPanel(new BorderLayout(0, 24)); 
        MainContainer.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40)); 
        
        // 제목과 시작 버튼 배치 
        // 글자 폰트, 크기 등 style 파일에서 관리하고 거기에서 필요한 곳에 가져다가 쓰는 게 깔끔할 것 같슴당
        JLabel title = new JLabel("Tetris", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        MainContainer.add(title, BorderLayout.CENTER);

        // 테트리스 시작 버튼
        // 나중에 components로 넣어서 재사용하도록 만들어도 좋을 듯
        JButton startButton = new JButton("테트리스 시작");
        
        startButton.addActionListener(event -> {
            startButton.setEnabled(false);
            Tetris game = new Tetris();
            game.setLocationRelativeTo(this);
            game.setVisible(true);
            dispose(); // 앱은 유지하면서 현재 선택 화면만 닫기
        });
        MainContainer.add(startButton, BorderLayout.SOUTH); // 버튼을 화면 아래(SOUTH)에 배치

        setContentPane(MainContainer);
        setSize(320, 220);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    // 시작 화면에서 테트리스 게임 화면으로 전환
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main menu = new Main();
            menu.setVisible(true);
        });
    }
}
