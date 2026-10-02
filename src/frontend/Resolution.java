package frontend;

// 게임 화면 크기 프리셋
public enum Resolution {

    SMALL("작게", 24),
    MEDIUM("보통", 30),
    LARGE("크게", 36);

    private final String label;  // 사이즈 화면 표시
    private final int cellSize;

    Resolution(String label, int cellSize) {
        this.label = label;
        this.cellSize = cellSize;
    }

    public int getCellSize() {
        return cellSize;
    }

    // 콤보박스 등에 표시될 이름
    @Override
    public String toString() {
        return label;
    }
}
