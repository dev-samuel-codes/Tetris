package shared.ai;

// 경로: L/R 이동, A/C 회전, D 한 칸 내리기, 마지막 H 하드 드롭.
public final class AiAssistPlan {
    private final int rotation;
    private final int x;
    private final int y;
    private final String path;

    public AiAssistPlan(int rotation, int x, int y, String path) {
        if (rotation < 0 || rotation > 3 || x < 0 || x > 9 || y < 0 || y > 21) {
            throw new IllegalArgumentException("AI 도움 목표 위치가 올바르지 않습니다.");
        }
        if (path == null || path.length() > 256 || !path.matches("[LRACD]*H")) {
            throw new IllegalArgumentException("AI 도움 이동 경로가 올바르지 않습니다.");
        }
        this.rotation = rotation;
        this.x = x;
        this.y = y;
        this.path = path;
    }

    public int getRotation() { return rotation; }
    public int getX() { return x; }
    public int getY() { return y; }
    public String getPath() { return path; }
}
