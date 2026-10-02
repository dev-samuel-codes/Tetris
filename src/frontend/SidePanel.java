package frontend;

import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import frontend.style.Style;

// 점수,다음 도형 보여주는 sidePanel
public class SidePanel extends JPanel {

    // 정보 패널의 가로 칸 수. 실제 너비는 칸 크기와 곱해서 정한다.
    public static final int COLS = 6;
    private JLabel scoreLabel;

    // sidePanel 색상 (임시)
    public SidePanel() {
        setBackground(new Color(40, 40, 40));

        scoreLabel = new JLabel("SCORE : 0");
        scoreLabel.setForeground(Color.WHITE);
        scoreLabel.setFont(Style.SCORE_FONT);

        add(scoreLabel);
    }

    public void setScore(int score) {
        scoreLabel.setText("SCORE : " + score);
    }
}