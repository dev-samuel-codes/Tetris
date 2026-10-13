package frontend.engine;

import frontend.Shape;
import frontend.Tetrominoes;
import java.lang.reflect.Field;
import shared.ai.AiAssistPlan;
import shared.ai.AiAssistState;

// AI 경로의 원자적 적용과 수동 조작의 게임 규칙 일치를 확인합니다.
public final class AiAssistEngineTest {
    public static void main(String[] args) throws Exception {
        testAllPiecesAndRotations();
        testLowCurrentPosition();
        testWallKick();
        testStaleState();
        testInvalidPlansAreAtomic();
        testClassicPieceAndNextStream();
        testLineScoreAndNextPiece();
        testUnavailableModes();
        System.out.println("AiAssistEngineTest passed");
    }

    private static void testAllPiecesAndRotations() throws Exception {
        for (int piece = 1; piece <= 7; piece++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                GameEngine assisted = fixture(piece, rotation, 5, 16);
                GameEngine manual = fixture(piece, rotation, 5, 16);
                AiAssistState state = assisted.getAiAssistState();
                check(state.getPiece() == piece, "현재 블록 번호");
                check(state.getRotation() == (piece == 5 ? 0 : rotation), "원형 기준 실제 회전");
                check(state.getBoard().equals(repeat('0', 220)), "활성 블록을 보드에서 제외");
                String path = "LDRCAH";
                AiAssistPlan plan = manualPlan(manual, path);
                check(assisted.applyAiAssistPlan(state, plan), "7종 블록과 모든 회전 경로 적용");
                check(snapshot(assisted).equals(snapshot(manual)), "수동 이동·회전·착지 규칙 일치");
                check(occupied(assisted) == 4, "AI 보조는 한 블록만 고정");
            }
        }
        GameEngine engine = fixture(4, 0, 5, 16);
        board(engine)[2 * 10 + 3] = Tetrominoes.MirroredLShape;
        check(engine.getAiAssistState().getBoard().charAt(23) == '7', "아래쪽 행부터 직렬화");

        GameEngine assisted = fixture(4, 0, 5, 16);
        GameEngine manual = fixture(4, 0, 5, 16);
        AiAssistPlan longest = manualPlan(manual, repeat('C', 255) + "H");
        check(assisted.applyAiAssistPlan(assisted.getAiAssistState(), longest), "최대 256개 입력 경로 허용");
        check(snapshot(assisted).equals(snapshot(manual)), "최대 길이 경로도 수동 조작과 일치");
    }

    private static void testLowCurrentPosition() throws Exception {
        GameEngine assisted = fixture(5, 0, 2, 2);
        GameEngine manual = fixture(5, 0, 2, 2);
        board(assisted)[4 * 10 + 4] = Tetrominoes.LineShape;
        board(manual)[4 * 10 + 4] = Tetrominoes.LineShape;
        AiAssistPlan plan = manualPlan(manual, "RRH");
        check(assisted.applyAiAssistPlan(assisted.getAiAssistState(), plan), "현재 낮은 위치에서 이동");
        check(snapshot(assisted).equals(snapshot(manual)), "스폰 위치에서 경로를 다시 시작하지 않음");
    }

    private static void testWallKick() throws Exception {
        GameEngine assisted = fixture(3, 0, 0, 12);
        GameEngine manual = fixture(3, 0, 0, 12);
        AiAssistState before = assisted.getAiAssistState();
        AiAssistPlan plan = manualPlan(manual, "CH");
        check(plan.getX() == 2, "벽 회전은 0,-1,+1,-2,+2 중 첫 성공인 +2 사용");
        check(assisted.applyAiAssistPlan(before, plan), "벽 차기 경로 적용");
        check(snapshot(assisted).equals(snapshot(manual)), "기존 벽 차기와 일치");
    }

    private static void testStaleState() throws Exception {
        GameEngine engine = fixture(4, 0, 5, 12);
        AiAssistState old = engine.getAiAssistState();
        AiAssistPlan plan = new AiAssistPlan(0, 5, engine.getGhostY(), "H");
        engine.moveLeft();
        rejectUnchanged(engine, old, plan, "이동 이후 오래된 상태 거부");
        engine.moveRight();
        board(engine)[0] = Tetrominoes.ZShape;
        rejectUnchanged(engine, old, plan, "보드 변경 이후 오래된 상태 거부");
        engine = fixture(4, 1, 5, 12);
        rejectUnchanged(engine, old, plan, "회전 변경 이후 오래된 상태 거부");
        engine = fixture(6, 0, 5, 12);
        rejectUnchanged(engine, old, plan, "블록 변경 이후 오래된 상태 거부");
    }

    private static void testInvalidPlansAreAtomic() throws Exception {
        GameEngine engine = fixture(4, 0, 5, 12);
        AiAssistState state = engine.getAiAssistState();
        int landing = engine.getGhostY();
        rejectUnchanged(engine, state, null, "빈 계획 거부");
        rejectUnchanged(engine, null, new AiAssistPlan(0, 5, landing, "H"), "빈 요청 상태 거부");
        rejectUnchanged(engine, state, new AiAssistPlan(0, 6, landing, "H"), "목표 X 불일치");
        rejectUnchanged(engine, state, new AiAssistPlan(0, 5, landing + 1, "H"), "목표 Y 불일치");
        rejectUnchanged(engine, state, new AiAssistPlan(1, 5, landing, "H"), "목표 회전 불일치");
        rejectUnchanged(engine, state, new AiAssistPlan(0, 5, landing, "LLLLLLLLH"), "중간 이동 실패시 무변경");
        for (String path : new String[] {"", "L", "HH", "HLH", "XH", "h", repeat('D', 256) + "H"}) {
            rejectUnchanged(engine, state, uncheckedPlan(0, 5, landing, path), "비정상 경로 거부: " + path);
        }
        rejectUnchanged(engine, state, uncheckedPlan(0, 5, landing, null), "null 경로 거부");
        engine = fixture(5, 0, 5, 1);
        rejectUnchanged(engine, engine.getAiAssistState(), new AiAssistPlan(0, 5, 1, "DH"),
                "D가 막히면 조기 고정하지 않고 거부");
        engine = fixture(3, 1, 5, 0);
        rejectUnchanged(engine, engine.getAiAssistState(), new AiAssistPlan(2, 4, 0, "LCH"),
                "앞선 이동이 성공해도 바닥 회전 실패시 무변경");
    }

    private static void testClassicPieceAndNextStream() throws Exception {
        GameEngine assisted = new GameEngine(910L);
        GameEngine manual = new GameEngine(910L);
        assisted.start();
        manual.start();
        for (int i = 0; i < 35; i++) {
            if (!manual.isStarted())
                break;
            AiAssistState state = assisted.getAiAssistState();
            check(state != null, "클래식 진행 중 상태 제공");
            int piece = manual.getCurrentShape().ordinal();
            AiAssistPlan plan = manualPlan(manual, "H");
            check(assisted.applyAiAssistPlan(state, plan), "연속 블록 보조 적용: " + piece);
            check(snapshot(assisted).equals(snapshot(manual)), "시드별 다음 블록과 점수 보존");
            if (manual.getCurrentShape() == Tetrominoes.NoShape && manual.isStarted()) {
                assisted.tick();
                manual.tick();
            }
        }
    }

    private static void testLineScoreAndNextPiece() throws Exception {
        GameEngine assisted = fixture(5, 0, 4, 12);
        GameEngine manual = fixture(5, 0, 4, 12);
        for (GameEngine engine : new GameEngine[] {assisted, manual}) {
            for (int x = 0; x < 10; x++)
                if (x != 4 && x != 5) board(engine)[x] = Tetrominoes.LineShape;
        }
        Tetrominoes expectedNext = assisted.getNextShape();
        AiAssistPlan plan = manualPlan(manual, "H");
        check(assisted.applyAiAssistPlan(assisted.getAiAssistState(), plan), "줄 삭제를 동반한 AI 착지");
        check(assisted.getScore() == 10 && assisted.getNumLinesRemoved() == 1, "기존 클래식 한 줄 점수");
        check(occupied(assisted) == 2, "첫 블록만 고정하고 줄 삭제");
        check(assisted.getCurrentShape() == Tetrominoes.NoShape, "줄 삭제의 대기 상태 유지");
        check(assisted.getAiAssistState() == null, "새 블록 대기 중 요청 거부");
        check(snapshot(assisted).equals(snapshot(manual)), "수동 줄 삭제 상태 일치");
        assisted.tick();
        manual.tick();
        check(assisted.getCurrentShape() == expectedNext, "기존 다음 블록 한 번 생성");
        check(snapshot(assisted).equals(snapshot(manual)), "줄 삭제 이후 다음 블록 순서 일치");
    }

    private static void testUnavailableModes() throws Exception {
        GameEngine engine = new GameEngine(1L);
        check(engine.getAiAssistState() == null, "시작 전 거부");
        engine.start();
        AiAssistState state = engine.getAiAssistState();
        AiAssistPlan plan = new AiAssistPlan(state.getRotation(), state.getX(), engine.getGhostY(), "H");
        engine.pause();
        check(engine.getAiAssistState() == null, "일시정지 상태 거부");
        rejectUnchanged(engine, state, plan, "일시정지 중 적용 거부");
        engine.pause();
        engine.setItemMode(true);
        check(engine.getAiAssistState() == null, "아이템 모드 거부");
        rejectUnchanged(engine, state, plan, "아이템 모드 중 적용 거부");
        engine.setItemMode(false);
        engine.setBattleItemMode(true);
        check(engine.getAiAssistState() == null, "대전 아이템 모드 거부");
        rejectUnchanged(engine, state, plan, "대전 아이템 모드 중 적용 거부");
        engine.setBattleItemMode(false);
        currentPiece(engine).setItemCell(0);
        check(engine.getAiAssistState() == null, "남아 있는 아이템 블록 거부");
        currentPiece(engine).setShape(Tetrominoes.NoShape);
        check(engine.getAiAssistState() == null, "활성 블록 없음 거부");
    }

    private static GameEngine fixture(int piece, int rotation, int x, int y) throws Exception {
        GameEngine engine = new GameEngine(910L);
        engine.start();
        Shape shape = new Shape();
        shape.setShape(Tetrominoes.values()[piece]);
        for (int i = 0; i < rotation; i++) shape = shape.rotateRight();
        set(engine, "curPiece", shape);
        set(engine, "curX", x);
        set(engine, "curY", y);
        return engine;
    }

    private static AiAssistPlan manualPlan(GameEngine engine, String path) {
        for (int i = 0; i < path.length() - 1; i++) {
            switch (path.charAt(i)) {
                case 'L': engine.moveLeft(); break;
                case 'R': engine.moveRight(); break;
                case 'A': engine.rotateLeft(); break;
                case 'C': engine.rotateRight(); break;
                case 'D': engine.oneLineDown(); break;
                default: throw new AssertionError("수동 비교 경로 오류");
            }
        }
        AiAssistState result = engine.getAiAssistState();
        check(result != null, "수동 비교 경로가 블록을 조기 고정하면 안 됨");
        AiAssistPlan plan = new AiAssistPlan(result.getRotation(), result.getX(), engine.getGhostY(), path);
        engine.dropDown();
        return plan;
    }

    private static AiAssistPlan uncheckedPlan(int rotation, int x, int y, String path) throws Exception {
        AiAssistPlan plan = new AiAssistPlan(rotation, x, y, "H");
        set(plan, "path", path); // DTO 방어 외에도 엔진의 방어를 독립적으로 검증합니다.
        return plan;
    }

    private static void rejectUnchanged(GameEngine engine, AiAssistState state, AiAssistPlan plan, String message) {
        String before = snapshot(engine);
        check(!engine.applyAiAssistPlan(state, plan), message);
        check(snapshot(engine).equals(before), message + " 후 엔진 무변경");
    }

    private static String snapshot(GameEngine engine) {
        StringBuilder result = new StringBuilder();
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 10; x++) result.append(engine.shapeAt(x, y).ordinal());
        result.append('/').append(engine.getCurrentShape()).append('/').append(engine.getNextShape())
                .append('/').append(engine.getCurrentX()).append('/').append(engine.getCurrentY())
                .append('/').append(engine.getScore()).append('/').append(engine.getNumLinesRemoved())
                .append('/').append(engine.getCombo()).append('/').append(engine.getMaxCombo())
                .append('/').append(engine.isStarted()).append('/').append(engine.isPaused());
        for (int i = 0; i < 4; i++) result.append('/').append(engine.getPieceX(i)).append(',').append(engine.getPieceY(i));
        return result.toString();
    }

    private static int occupied(GameEngine engine) {
        int count = 0;
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 10; x++) if (engine.shapeAt(x, y) != Tetrominoes.NoShape) count++;
        return count;
    }

    private static Tetrominoes[] board(GameEngine engine) throws Exception {
        return (Tetrominoes[]) field(GameEngine.class, "board").get(engine);
    }

    private static Shape currentPiece(GameEngine engine) throws Exception {
        return (Shape) field(GameEngine.class, "curPiece").get(engine);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
