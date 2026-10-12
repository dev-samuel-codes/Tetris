package shared.score;

// 화면 이름과 저장·통신에서 쓰는 게임 모드 구분을 한 곳에서 관리
public enum ScoreMode {
    CLASSIC("클래식"), ITEM("아이템");

    private final String displayName;

    ScoreMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
