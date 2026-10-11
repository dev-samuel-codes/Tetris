package frontend;

import java.awt.event.KeyEvent;


public class GameSettings {

    // 해상도 크기 기본값: 보통
    private static Resolution resolution = Resolution.MEDIUM;

    // 클래식/아이템 공통 기본 조작키
    private static int leftKey = KeyEvent.VK_LEFT;
    private static int rightKey = KeyEvent.VK_RIGHT;
    private static int rotateLeftKey = KeyEvent.VK_UP;
    private static int rotateRightKey = KeyEvent.VK_DOWN;
    private static int softDropKey = KeyEvent.VK_D;
    private static int hardDropKey = KeyEvent.VK_SPACE;
    private static int pauseKey = KeyEvent.VK_P;

    // 왼쪽 이동 키 반환
    public static int getLeftKey() {
        return leftKey;
    }
    // 왼쪽 이동 키 변경
    public static void setLeftKey(int key) {
        leftKey = key;
    }
    // 오른쪽 이동 키 반환
    public static int getRightKey() {
        return rightKey;
    }
    // 오른쪽 이동 키 변경
    public static void setRightKey(int key) {
        rightKey = key;
    }
    // 왼쪽 회전 키 반환
    public static int getRotateLeftKey() {
        return rotateLeftKey;
    }
    // 왼쪽 회전 키 변경
    public static void setRotateLeftKey(int key) {
        rotateLeftKey = key;
    }
    // 오른쪽 회전 키 반환
    public static int getRotateRightKey() {
        return rotateRightKey;
    }
    // 오른쪽 회전 키 변경
    public static void setRotateRightKey(int key) {
        rotateRightKey = key;
    }
    // 한 칸 하강 키 반환
    public static int getSoftDropKey() {
        return softDropKey;
    }
    // 한 칸 하강 키 변경
    public static void setSoftDropKey(int key) {
        softDropKey = key;
    }
    // 즉시 낙하 키 반환
    public static int getHardDropKey() {
        return hardDropKey;
    }
    // 즉시 낙하 키 변경
    public static void setHardDropKey(int key) {
        hardDropKey = key;
    }
    // 일시정지 키 반환
    public static int getPauseKey() {
        return pauseKey;
    }
    // 일시정지 키 변경
    public static void setPauseKey(int key) {
        pauseKey = key;
    }

    private GameSettings() {
    }
    public static Resolution getResolution() {
        return resolution;
    }

    public static void setResolution(Resolution newResolution) {
        resolution = newResolution;
    }
}