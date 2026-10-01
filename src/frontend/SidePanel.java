package frontend;

import java.awt.Color;

import javax.swing.JPanel;

// 점수,다음 도형 보여주는 sidePanel
public class SidePanel extends JPanel {

    // 정보 패널의 가로 칸 수. 실제 너비는 칸 크기와 곱해서 정한다.
    public static final int COLS = 6;

    // sidePanel 색상 (임시)
    public SidePanel() {
        setBackground(new Color(40, 40, 40));
    }
}