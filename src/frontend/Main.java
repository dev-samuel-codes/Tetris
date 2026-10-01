// 앱 진입 화면 파일
package frontend;

import javax.swing.SwingUtilities;

// 메인 화면
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Tetris game = new Tetris();
            game.setLocationRelativeTo(null);
            game.setVisible(true);
        });
    }
}