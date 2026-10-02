package frontend;

public class GameSettings {

    // 해상도 크기 기본값: 보통
    private static Resolution resolution = Resolution.MEDIUM;

    private GameSettings() {
    }

    public static Resolution getResolution() {
        return resolution;
    }

    public static void setResolution(Resolution newResolution) {
        resolution = newResolution;
    }
}