package backend.ai;

// 난이도 이름과 사용할 모델의 체크포인트 점수를 함께 관리
public enum AiDifficulty {
    EASY("easy", "쉬움", 300),
    NORMAL("normal", "보통", 1000),
    HARD("hard", "어려움", 5000),
    VERY_HARD("very_hard", "매우 어려움", 50000),
    HELL("hell", "지옥", 100000);

    private final String id;
    private final String label;
    private final int checkpointScore;

    AiDifficulty(String id, String label, int checkpointScore) {
        this.id = id;
        this.label = label;
        this.checkpointScore = checkpointScore;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public int getCheckpointScore() { return checkpointScore; }
    @Override public String toString() { return label; }
}
