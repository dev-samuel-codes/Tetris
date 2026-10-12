package frontend;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComponent;
import javax.swing.Timer;

public class LineClearFlash {

    private final Timer timer;
    private int previousLines = 0;
    private int flashRow = -1;

    public LineClearFlash(final JComponent component) {
        timer = new Timer(120, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                flashRow = -1;
                component.repaint();
            }
        });

        timer.setRepeats(false);
    }

    // 삭제한 줄 수가 증가했을 때만 이펙트 시작
    public void update(int lines, int clearedRow) {
        if (lines < previousLines) {
            stop();
        }

        if (lines > previousLines
                && clearedRow >= 0
                && clearedRow < 22) {
            flashRow = clearedRow;
            timer.restart();
        }

        previousLines = lines;
    }

    // 삭제한 줄 위치에 반투명 흰색 표시
    public void draw(Graphics g, int left, int top, int size) {
        if (flashRow < 0) {
            return;
        }

        g.setColor(new Color(255, 255, 255, 180));

        int screenY = top + (21 - flashRow) * size;
        g.fillRect(left, screenY, 10 * size, size);
    }

    // 화면을 닫을 때 타이머 정지
    public void stop() {
        timer.stop();
        flashRow = -1;
    }
}