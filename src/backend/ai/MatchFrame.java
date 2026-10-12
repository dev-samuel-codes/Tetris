package backend.ai;

// 사람과 AI의 보드, 점수, 경기 진행 상태를 함께 보관
public final class MatchFrame {
    public static final int BOARD_WIDTH = 10;
    public static final int BOARD_HEIGHT = 22;
    private final long elapsedTicks;
    private final boolean ended;
    private final String winner;
    private final BoardState human;
    private final BoardState ai;

    MatchFrame(long elapsedTicks, boolean ended,
               String winner, BoardState human, BoardState ai) {
        this.elapsedTicks = elapsedTicks;
        this.ended = ended;
        this.winner = winner;
        this.human = human;
        this.ai = ai;
    }

    public long getElapsedTicks() { return elapsedTicks; }
    public boolean isEnded() { return ended; }
    public String getWinner() { return winner; }
    public BoardState getHuman() { return human; }
    public BoardState getAi() { return ai; }

    public static final class BoardState {

        private final int score;
        private final int lines;
        private final boolean ended;
        private final String reason;
        private final int[] board;
        private final int lastClearedRow;

        // 기존 생성자도 사용할 수 있도록 유지
        BoardState(int score, int lines, boolean ended,
                   String reason, int[] board) {
            this(score, lines, ended, reason, board, -1);
        }

        // 삭제한 줄 위치까지 전달받는 생성자
        BoardState(int score, int lines, boolean ended,
                   String reason, int[] board, int lastClearedRow) {
            this.score = score;
            this.lines = lines;
            this.ended = ended;
            this.reason = reason;
            this.board = board.clone();
            this.lastClearedRow = lastClearedRow;
        }

        public int getScore() {
            return score;
        }

        public int getLines() {
            return lines;
        }

        public boolean isEnded() {
            return ended;
        }

        public String getReason() {
            return reason;
        }

        public int[] getBoard() {
            return board.clone();
        }

        public int getLastClearedRow() {
            return lastClearedRow;
        }
    }
}
