package shared.ai;

// 고정 블록만 아래쪽 행부터 저장하며, 현재 움직이는 블록은 별도로 전달합니다.
public final class AiAssistState {
    private final String board;
    private final int piece;
    private final int rotation;
    private final int x;
    private final int y;

    public AiAssistState(String board, int piece, int rotation, int x, int y) {
        if (board == null || board.length() != 220) {
            throw new IllegalArgumentException("AI 도움 보드는 220칸이어야 합니다.");
        }
        for (int i = 0; i < board.length(); i++) {
            if (board.charAt(i) < '0' || board.charAt(i) > '7') {
                throw new IllegalArgumentException("AI 도움 보드에는 고정 블록 번호 0~7만 사용할 수 있습니다.");
            }
        }
        if (piece < 1 || piece > 7 || rotation < 0 || rotation > 3
                || (piece == 5 && rotation != 0) || x < 0 || x > 9 || y < 0 || y > 21) {
            throw new IllegalArgumentException("AI 도움 블록 상태가 올바르지 않습니다.");
        }
        this.board = board;
        this.piece = piece;
        this.rotation = rotation;
        this.x = x;
        this.y = y;
    }

    public String getBoard() { return board; }
    public int getPiece() { return piece; }
    public int getRotation() { return rotation; }
    public int getX() { return x; }
    public int getY() { return y; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AiAssistState)) return false;
        AiAssistState state = (AiAssistState) other;
        return piece == state.piece && rotation == state.rotation && x == state.x
                && y == state.y && board.equals(state.board);
    }

    @Override public int hashCode() {
        int result = board.hashCode();
        result = 31 * result + piece;
        result = 31 * result + rotation;
        result = 31 * result + x;
        return 31 * result + y;
    }
}
