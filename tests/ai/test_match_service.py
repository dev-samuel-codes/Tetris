"""무제한 대전 규칙과 실제 체크포인트 호환성을 확인합니다."""
from copy import deepcopy
import importlib.util
from io import StringIO
from pathlib import Path
import sys

import numpy as np
import pytest

SERVICE_PATH = Path(__file__).resolve().parents[2] / "src" / "backend" / "ai" / "match_service.py"
spec = importlib.util.spec_from_file_location("match_service", SERVICE_PATH)
service = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = service
spec.loader.exec_module(service)


class FakePolicy:
    def __init__(self):
        self.calls = 0

    def predict(self, observation, *, deterministic, action_masks):
        self.calls += 1
        assert deterministic is True
        return np.flatnonzero(action_masks)[0], None


class NearPolicy(FakePolicy):
    def predict(self, observation, *, deterministic, action_masks):
        self.calls += 1
        assert deterministic is True and action_masks[6]
        return 6, None


def make_match(seed=123):
    return service.Match(FakePolicy(), {"observation": "board"}, seed)


def fields(frame):
    parts = frame.split("\t")
    assert len(parts) == 16
    assert parts[0] == "FRAME"
    for index in (8, 13):
        assert len(parts[index]) == 220
        assert set(parts[index]) <= set("012345678")
    for index in (14, 15):
        assert -1 <= int(parts[index]) < 22
    return parts


def test_frame_has_active_overlay_and_one_shared_tick():
    match = make_match()
    initial = fields(match.frame())
    assert initial[1:4] == ["0", "0", "NONE"]
    assert initial[8] == initial[13]
    assert sum(char in "1234567" for char in initial[8]) == 4
    assert initial[8].count("8") == 4
    assert initial[14:16] == ["-1", "-1"]
    for tick in range(1, 6):
        frame = fields(match.step("LEFT"))
        assert frame[1] == str(tick)
        assert match.human.ticks == match.ai.ticks == tick
    assert match.policy.calls == 0
    match.step("WAIT")
    assert match.policy.calls == 0
    match.step("WAIT")
    assert match.policy.calls == 1


def test_ghost_lands_above_stack_and_active_piece_overlays_it_without_mutation():
    engine = make_match().human
    engine.board.fill(0)
    engine.board[2, 4:6] = 7
    engine.piece, engine.rotation, engine.x, engine.y = 5, 0, 4, 10
    before = engine.board.copy()
    expected = before.copy()
    expected[3:5, 4:6] = 8
    expected[9:11, 4:6] = 5
    visible = np.array(list(service.board_string(engine)), dtype=np.uint8).reshape(22, 10)
    np.testing.assert_array_equal(visible, expected)
    np.testing.assert_array_equal(engine.board, before)
    assert engine.y == 10
    engine.y = 4
    landed = service.board_string(engine)
    assert "8" not in landed and landed.count("5") == 4
    engine._finish("top_out")
    np.testing.assert_array_equal(
        np.array(list(service.board_string(engine)), dtype=np.uint8).reshape(22, 10), before)


@pytest.mark.parametrize("engine_name, field_index", [("human", 14), ("ai", 15)])
def test_line_clear_row_changes_only_on_real_lock_and_resets(engine_name, field_index):
    match = make_match()
    engine = getattr(match, engine_name)
    engine.board.fill(0)
    engine.board[2, 4:6] = 7
    engine.board[3] = 7
    engine.board[3, 4:6] = 0
    engine.piece, engine.rotation, engine.x, engine.y = 5, 0, 4, 21
    engine.think_remaining = 0
    before_board = engine.board.copy()
    before_frame, before_rng = match.frame(), engine._rng.getstate()
    assert any(candidate.lines_cleared for candidate in engine.candidates().values())
    assert engine.last_cleared_row == -1 and engine.lines == engine.ticks == 0
    assert match.frame() == before_frame and engine._rng.getstate() == before_rng
    np.testing.assert_array_equal(engine.board, before_board)
    engine._tick("HARD_DROP")
    assert engine.lines == 1 and engine.score == 100
    assert fields(match.frame())[field_index] == "3"
    assert fields(match.frame())[15 if engine_name == "human" else 14] == "-1"
    # 줄을 지우지 않은 다음 고정에서는 마지막 삭제 위치를 유지합니다.
    engine.board.fill(0)
    engine.think_remaining = 0
    engine._tick("HARD_DROP")
    assert engine.lines == 1 and engine.last_cleared_row == 3
    engine.reset(seed=123)
    assert fields(match.frame())[field_index] == "-1"
    assert engine.lines == engine.score == 0


def test_candidate_path_matches_whole_engine_step_without_using_it(monkeypatch):
    match = make_match()
    for _ in range(service.AI_REACTION_TICKS):
        match.step("WAIT")
    reference = service.TetrisEngine(match.ai.rules, seed=123)
    reference.restore(match.ai.snapshot())
    action = int(np.flatnonzero(reference.action_masks())[0])
    reference.step(action)
    monkeypatch.setattr(match.ai, "step", lambda *_: pytest.fail("Whole placement step must never be used"))
    while match.ai.pieces_placed == 0:
        match.step("WAIT")
    np.testing.assert_array_equal(match.ai.board, reference.board)
    for attr in ("piece", "rotation", "x", "y", "score", "lines", "pieces_placed", "gravity_remaining", "think_remaining"):
        assert getattr(match.ai, attr) == getattr(reference, attr)
    assert match.ai._rng.getstate() == reference._rng.getstate()
    assert match.policy.calls == 1
    assert match.ai.ticks == 32


def test_same_iid_sequence_at_different_human_ai_speeds():
    match = make_match(9821)
    history = {"human": [match.human.piece], "ai": [match.ai.piece]}
    for _ in range(200):
        previous = match.human.pieces_placed, match.ai.pieces_placed
        match.step("HARD_DROP")
        for name, old in zip(("human", "ai"), previous):
            engine = getattr(match, name)
            if engine.pieces_placed != old and engine.piece:
                history[name].append(engine.piece)
            # 블록 속도 비교 중에는 쌓인 보드를 비웁니다.
            engine.board.fill(0)
    assert len(history["human"]) > len(history["ai"]) > 5
    assert history["human"][:len(history["ai"])] == history["ai"]


@pytest.mark.parametrize("difficulty_id", ["normal", "very_hard", "hell"])
def test_match_continues_beyond_3600_ticks(difficulty_id):
    difficulty = service.DIFFICULTIES[difficulty_id]
    match = service.Match(FakePolicy(), {"observation": difficulty.observation,
                          "env": {"duration_ticks": difficulty.duration_ticks}}, 123, difficulty_id)
    assert match.ai.rules.duration_ticks == match.human.rules.duration_ticks == 3600
    for _ in range(3700):
        match.step("HARD_DROP")
        match.human.board.fill(0)
        match.ai.board.fill(0)
    frame = fields(match.frame())
    assert frame[1:4] == ["3700", "0", "NONE"]
    assert frame[6:8] == ["0", "-"]
    assert frame[11:13] == ["0", "-"]
    assert match.human.ticks == match.ai.ticks == 3700
    assert match.ai.observation()[245] == match.human.observation()[245] == 1.0
    match.step("WAIT")
    assert match.elapsed_ticks == 3701


def test_one_topout_freezes_only_that_board_and_scores_decide_winner():
    match = make_match()
    match.human.score = 100
    match.human._finish("top_out")
    before = service.board_string(match.human)
    frame = fields(match.step("WAIT"))
    assert frame[1:4] == ["1", "0", "NONE"]
    assert match.human.ticks == 0 and match.ai.ticks == 1
    assert frame[6:9] == ["1", "top_out", before]
    match.ai._finish("top_out")
    assert fields(match.frame())[3] == "HUMAN"
    match.ai.score = 200
    assert fields(match.frame())[3] == "AI"
    match.human.score = 200
    assert fields(match.frame())[3] == "DRAW"
    assert match.step("WAIT") == match.frame()


@pytest.mark.parametrize("frozen_name, survivor_name, winner", [("human", "ai", "AI"), ("ai", "human", "HUMAN")])
def test_survivor_wins_immediately_when_score_is_strictly_higher(frozen_name, survivor_name, winner):
    match = make_match()
    frozen = getattr(match, frozen_name)
    survivor = getattr(match, survivor_name)
    frozen.score = 100
    frozen._finish("top_out")
    survivor.score = 200
    before = match.frame()
    before_boards = match.human.board.copy(), match.ai.board.copy()
    before_ticks = match.human.ticks, match.ai.ticks
    before_rng = match.human._rng.getstate(), match.ai._rng.getstate()
    assert fields(before)[2:4] == ["1", winner]
    assert survivor.terminated is False and survivor.end_reason is None
    for command in service.HUMAN_COMMANDS:
        assert match.step(command) == before
    assert match.elapsed_ticks == 0 and match.policy.calls == 0
    assert (match.human.ticks, match.ai.ticks) == before_ticks
    assert (match.human.score, match.ai.score) == ((100, 200) if frozen_name == "human" else (200, 100))
    assert (match.human._rng.getstate(), match.ai._rng.getstate()) == before_rng
    np.testing.assert_array_equal(match.human.board, before_boards[0])
    np.testing.assert_array_equal(match.ai.board, before_boards[1])


@pytest.mark.parametrize("frozen_name", ["human", "ai"])
def test_one_topout_with_tied_scores_keeps_match_running(frozen_name):
    match = make_match()
    match.human.score = match.ai.score = 100
    getattr(match, frozen_name)._finish("top_out")
    assert match.ended is False and match.winner == "NONE"
    assert fields(match.step("WAIT"))[1:4] == ["1", "0", "NONE"]


@pytest.mark.parametrize("survivor_name, frozen_name, winner", [("ai", "human", "AI"), ("human", "ai", "HUMAN")])
def test_survivor_overtakes_on_actual_line_clear_tick(survivor_name, frozen_name, winner):
    match = make_match()
    survivor = getattr(match, survivor_name)
    frozen = getattr(match, frozen_name)
    frozen.score = 100
    frozen._finish("top_out")
    for expected_score in (100, 200):
        # 사각 블록으로 한 줄씩 지워 동점과 역전 시점 확인
        survivor.board.fill(0)
        survivor.board[0] = 7
        survivor.board[0, 4:6] = 0
        survivor.piece, survivor.rotation, survivor.x, survivor.y = 5, 0, 4, 1
        survivor.think_remaining = 0
        survivor.gravity_remaining = 8
        assert survivor._fits(survivor.x, survivor.y, survivor.rotation)
        if survivor_name == "ai":
            match.ai_path = service.deque(["HARD_DROP"])
            match.ai_spawn_tick = match.elapsed_ticks - service.AI_REACTION_TICKS
            match.ai_target_ticks = 1
        frame = fields(match.step("HARD_DROP"))
        assert survivor.score == expected_score and survivor.lines == expected_score // 100
        assert frame[14 if survivor_name == "human" else 15] == "0"
        assert frozen.score == 100 and frozen.ticks == 0
        assert survivor.terminated is False and survivor.end_reason is None
        if expected_score == 100:
            assert frame[1:4] == ["1", "0", "NONE"]
        else:
            assert frame[1:4] == ["2", "1", winner]
    final = match.frame()
    final_state = survivor.snapshot()
    assert match.step("HARD_DROP") == final
    assert survivor.ticks == final_state["ticks"]
    np.testing.assert_array_equal(survivor.board, final_state["board"])


def test_both_alive_keep_playing_despite_score_gap_and_both_topout_draw():
    match = make_match()
    match.human.score = 100000
    match.ai.score = 0
    assert fields(match.step("WAIT"))[2:4] == ["0", "NONE"]
    match.ai.score = match.human.score
    match.human._finish("top_out")
    match.ai._finish("top_out")
    assert fields(match.frame())[2:4] == ["1", "DRAW"]


def test_soft_drop_respects_think_window_and_advances_one_tick():
    match = make_match()
    before = match.human.y
    match.step("SOFT_DROP")
    assert match.human.y == before
    for _ in range(3):
        match.step("WAIT")
    before = match.human.y
    match.step("SOFT_DROP")
    assert match.human.y == before - 1
    assert match.human.ticks == match.ai.ticks == 5
    while match.human._fits(match.human.x, match.human.y - 1, match.human.rotation):
        match.human.y -= 1
    match.step("SOFT_DROP")
    assert match.human.pieces_placed == 1 and match.human.ticks == 6


def drive_human_intervals(match, intervals):
    ai_intervals = []
    last_ai_lock = 0
    for interval in intervals:
        next_lock = match.last_human_lock_tick + interval
        while match.elapsed_ticks < next_lock:
            previous_ai_placed = match.ai.pieces_placed
            match.step("HARD_DROP" if match.elapsed_ticks + 1 == next_lock else "WAIT")
            if match.ai.pieces_placed != previous_ai_placed:
                ai_intervals.append(match.elapsed_ticks - last_ai_lock)
                last_ai_lock = match.elapsed_ticks
            match.human.board.fill(0)
            match.ai.board.fill(0)
    return ai_intervals


@pytest.mark.parametrize("human_ticks, human_pace, ai_pace", [(5, 10, 8), (80, 80, 64), (150, 120, 96)])
def test_ai_tracks_fast_slow_and_clamped_human_pace(human_ticks, human_pace, ai_pace):
    match = service.Match(NearPolicy(), {"observation": "board"}, 9821)
    measured = drive_human_intervals(match, [human_ticks] * 20)
    assert match.human_intervals == service.deque([human_ticks] * 8, maxlen=8)
    assert match.human_pace_ticks == human_pace
    assert match.ai_target_ticks == ai_pace
    assert measured[-3:] == [ai_pace] * 3


@pytest.mark.parametrize("human_ticks, ai_ticks", [(40, 32), (20, 16), (60, 48), (10, 8), (120, 96), (11, 9)])
def test_ai_pace_is_80_percent_rounded_up(human_ticks, ai_ticks):
    assert service.ai_pace_ticks(human_ticks) == ai_ticks


@pytest.mark.parametrize("human_ticks, ai_ticks", [(40, 28), (20, 14), (60, 42), (10, 7), (120, 84), (11, 8)])
def test_hell_pace_is_70_percent_rounded_up(human_ticks, ai_ticks):
    assert service.ai_pace_ticks(human_ticks, "hell") == ai_ticks


@pytest.mark.parametrize("difficulty_id, expected_ticks", [("easy", 32), ("normal", 32), ("hard", 32), ("very_hard", 32), ("hell", 28)])
def test_start_passes_difficulty_pace_and_restart_resets_measurements(difficulty_id, expected_ticks):
    def loader(selected):
        return NearPolicy(), {"observation": service.DIFFICULTIES[selected].observation,
                              "env": {"duration_ticks": service.DIFFICULTIES[selected].duration_ticks}}
    controller = service.MatchService(loader)
    controller.request(f"START\t{difficulty_id}\t9821")
    match = controller.match
    assert match.difficulty_id == difficulty_id and match.ai_target_ticks == expected_ticks
    drive_human_intervals(match, [80] * 3)
    assert match.human_pace_ticks == 80
    assert match.ai_target_ticks == (56 if difficulty_id == "hell" else 64)
    controller.request(f"START\t{difficulty_id}\t9821")
    restarted = controller.match
    assert restarted is not match and restarted.difficulty_id == difficulty_id
    assert restarted.elapsed_ticks == restarted.last_human_lock_tick == restarted.ai_spawn_tick == 0
    assert not restarted.human_intervals and not restarted.ai_path
    assert restarted.human_pace_ticks == 40 and restarted.ai_target_ticks == expected_ticks
    other = "normal" if difficulty_id == "hell" else "hell"
    controller.request(f"START\t{other}\t9821")
    assert controller.match.ai_target_ticks == (32 if other == "normal" else 28)


def test_hell_minimum_pace_keeps_reaction_and_current_piece_target():
    match = service.Match(NearPolicy(), {"observation": "afterstate", "env": {"duration_ticks": 0}}, 9821, "hell")
    drive_human_intervals(match, [5])
    assert match.human_pace_ticks == 10 and match.ai_target_ticks == 28
    assert match.policy.calls == 0 and match.ai.pieces_placed == 0
    match.step("WAIT")
    assert match.policy.calls == 0
    match.step("WAIT")
    assert match.policy.calls == 1
    measured = drive_human_intervals(match, [5] * 30)
    assert match.human_pace_ticks == 10 and match.ai_target_ticks == 7
    assert measured[-3:] == [7] * 3


def test_recent_eight_median_updates_only_next_ai_piece_and_ignores_scores():
    match = service.Match(NearPolicy(), {"observation": "board"}, 79)
    drive_human_intervals(match, [5])
    assert match.human_pace_ticks == 10
    assert match.ai_target_ticks == 32 and match.ai.pieces_placed == 0
    match.human.score = 100000
    match.ai.score = 1
    drive_human_intervals(match, [80] * 4 + [20] * 5)
    assert len(match.human_intervals) == 8
    assert list(match.human_intervals) == [80] * 3 + [20] * 5
    assert match.human_pace_ticks == 20


def test_reaction_wait_has_same_gravity_without_inference():
    match = make_match()
    for _ in range(service.AI_REACTION_TICKS):
        match.step("WAIT")
        assert (match.ai.x, match.ai.y, match.ai.rotation, match.ai.gravity_remaining, match.ai.think_remaining) == (match.human.x, match.human.y, match.human.rotation, match.human.gravity_remaining, match.human.think_remaining)
    assert match.policy.calls == 0
    match.step("WAIT")
    assert match.policy.calls == 1


def test_last_hard_drop_waits_without_consuming_path_or_moving_target():
    match = service.Match(NearPolicy(), {"observation": "board"}, 123)
    while not match.ai_path or match.ai_path[0] != "HARD_DROP":
        match.step("WAIT")
    assert match.elapsed_ticks < match.ai_target_ticks
    position = match.ai.x, match.ai.rotation
    y = match.ai.y
    while match.elapsed_ticks + 1 < match.ai_target_ticks:
        match.step("WAIT")
        assert list(match.ai_path) == ["HARD_DROP"]
        assert (match.ai.x, match.ai.rotation) == position
        assert match.ai.pieces_placed == 0 and match.policy.calls == 1
    assert match.ai.y < y
    match.step("WAIT")
    assert match.elapsed_ticks == 32 and match.ai.pieces_placed == 1
    assert not match.ai_path


@pytest.mark.parametrize("during_reaction", [True, False])
def test_gravity_lock_discards_stale_path_and_restarts_reaction(during_reaction):
    match = service.Match(NearPolicy(), {"observation": "board"}, 123)
    if not during_reaction:
        while not match.ai_path or match.ai_path[0] != "HARD_DROP":
            match.step("WAIT")
    while match.ai._fits(match.ai.x, match.ai.y - 1, match.ai.rotation):
        match.ai.y -= 1
    match.ai.think_remaining = 0
    match.ai.gravity_remaining = 1
    previous_calls = match.policy.calls
    match.step("WAIT")
    assert match.ai.pieces_placed == 1
    assert not match.ai_path and match.ai_spawn_tick == match.elapsed_ticks
    assert match.human_pace_ticks == 40 and match.ai_target_ticks == 32
    for _ in range(service.AI_REACTION_TICKS):
        match.step("WAIT")
    assert match.policy.calls == previous_calls
    match.step("WAIT")
    assert match.policy.calls == previous_calls + 1


def test_ai_continues_at_last_human_pace_after_human_topout():
    match = service.Match(NearPolicy(), {"observation": "board"}, 9821)
    drive_human_intervals(match, [60] * 10)
    match.human._finish("top_out")
    frozen_ticks = match.human.ticks
    measured = []
    last_lock = match.ai_spawn_tick
    for _ in range(255):
        previous = match.ai.pieces_placed
        match.step("WAIT")
        if match.ai.pieces_placed != previous:
            measured.append(match.elapsed_ticks - last_lock)
            last_lock = match.elapsed_ticks
        match.ai.board.fill(0)
    assert measured == [48] * 5
    assert match.human.ticks == frozen_ticks and match.human_pace_ticks == 60
    assert match.ended is False


def test_restart_bad_command_and_protocol_recovery():
    policy = FakePolicy()
    loader_calls = []
    def loader(difficulty):
        loader_calls.append(difficulty)
        return policy, {"observation": "board"}
    controller = service.MatchService(loader)
    start = controller.request("START\teasy\t42")
    controller.request("STEP\tWAIT")
    before = controller.match.frame()
    with pytest.raises(ValueError, match="Unknown human command"):
        controller.request("STEP\tINVALID")
    assert controller.match.frame() == before
    assert controller.request("START\teasy\t42") == start
    assert controller.match.human_intervals == service.deque([], maxlen=8)
    assert controller.match.last_human_lock_tick == controller.match.ai_spawn_tick == 0
    assert controller.match.human_pace_ticks == 40 and controller.match.ai_target_ticks == 32
    assert loader_calls == ["easy"]
    output = StringIO()
    service.serve(StringIO("STEP\tWAIT\nSTART\teasy\t42\nSTEP\tINVALID\nSTEP\tWAIT\nQUIT\n"), output, service.MatchService(loader))
    lines = output.getvalue().splitlines()
    assert lines[0] == "READY\t2"
    assert lines[1].startswith("ERROR\t") and lines[3].startswith("ERROR\t")
    assert fields(lines[4])[1] == "1"
    assert len(lines) == 5


@pytest.mark.parametrize("line", ["START\teasy\tx", "START\tunknown\t42", "START\teasy", "STEP", "QUIT\textra"])
def test_bad_protocol_request(line):
    with pytest.raises(ValueError):
        service.MatchService().request(line)


def test_manifest_rejects_incompatible_rules_and_identity():
    path = service.MODEL_ROOT / "best.manifest.json"
    manifest = service.json.loads(path.read_text())
    difficulty = service.Difficulty(10000, "best.zip", "afterstate", require_completed_target=True)
    assert service.validate_manifest(manifest, difficulty)["observation"] == "afterstate"
    for key, value, error in (("rules_version", "wrong", "rules"), ("engine_sha256", "wrong", "hash"), ("target_score", 500, "10000"), ("observation_version", "wrong", "observation")):
        changed = deepcopy(manifest)
        changed[key] = value
        with pytest.raises(ValueError, match=error):
            service.validate_manifest(changed, difficulty)
    changed = deepcopy(manifest)
    changed["config"]["env"]["gravity_ticks"] = 7
    with pytest.raises(ValueError, match="rules are incompatible"):
        service.validate_manifest(changed, difficulty)


@pytest.mark.parametrize("difficulty_id, score, checkpoint, observation, duration, completed", [
    ("easy", 300, "checkpoints/score_00300.zip", "board", 3600, False),
    ("normal", 1000, "checkpoints/score_01000.zip", "afterstate", 3600, False),
    ("hard", 5000, "checkpoints/score_05000.zip", "afterstate", 3600, False),
    ("very_hard", 50000, "checkpoints/score_50000.zip", "afterstate", 0, False),
    ("hell", 100000, "checkpoints/score_100000.zip", "afterstate", 0, True),
])
def test_difficulty_checkpoint_mapping(difficulty_id, score, checkpoint, observation, duration, completed):
    assert service.DIFFICULTIES[difficulty_id] == service.Difficulty(score, checkpoint, observation, duration, completed)


@pytest.mark.parametrize("difficulty_id", ["easy", "normal", "hard", "very_hard"])
def test_milestone_models_accept_unreached_target_but_reject_wrong_milestone(difficulty_id):
    difficulty = service.DIFFICULTIES[difficulty_id]
    manifest = checkpoint_manifest(difficulty_id)
    manifest["config"]["observation"] = difficulty.observation
    manifest["observation_version"] = service.OBSERVATION_VERSIONS[difficulty.observation]
    manifest["policy_class"] = "CandidateActorCriticPolicy" if difficulty.observation == "afterstate" else "MaskableActorCriticPolicy"
    manifest["target_reached"] = False
    manifest["score_milestone"] = difficulty.score
    assert manifest["target_score"] == (100000 if difficulty.duration_ticks == 0 else 10000)
    assert service.validate_manifest(manifest, difficulty)["observation"] == difficulty.observation
    manifest["score_milestone"] += 100
    with pytest.raises(ValueError, match=f"milestone must be {difficulty.score}"):
        service.validate_manifest(manifest, difficulty)


def test_missing_checkpoint_and_wrong_model_shape_fail_clearly(tmp_path, monkeypatch):
    with pytest.raises(ValueError, match="Missing checkpoint"):
        service.load_policy("easy", tmp_path)
    from sb3_contrib import MaskablePPO
    class WrongModel:
        observation_space = None
        action_space = None
    monkeypatch.setattr(MaskablePPO, "load", lambda *args, **kwargs: WrongModel())
    with pytest.raises(ValueError, match="shape is incompatible"):
        service.load_policy("hell")


def test_candidate_observation_does_not_consume_future_stream():
    match = service.Match(FakePolicy(), {"observation": "afterstate"}, 79)
    before = match.ai._rng.getstate()
    for _ in range(2):
        observation = match.ai_env._observation()
        assert observation["candidates"].shape == (40, 7)
        match.ai.action_masks()
    assert match.ai._rng.getstate() == before


def test_candidate_path_crosses_training_deadline_without_spawning_future_piece():
    engine = service.UntimedTetrisEngine(seed=123)
    engine.ticks = 3599
    rng_before = engine._rng.getstate()
    candidates = engine.candidates()
    candidate = candidates[min(candidates)]
    assert candidate.ticks > 1
    assert candidate.path[-1] == "HARD_DROP"
    simulated = engine._simulation()
    assert isinstance(simulated, service.UntimedTetrisEngine)
    for command in candidate.path:
        simulated._tick(command, spawn=False)
    assert simulated.ticks > 3600
    assert simulated.pieces_placed == 1
    assert simulated.piece == 0 and simulated.terminated is False
    assert simulated._rng.getstate() == engine._rng.getstate() == rng_before
    for command in candidate.path:
        engine._tick(command)
    assert engine.ticks == simulated.ticks and engine.pieces_placed == 1
    assert engine.piece != 0 and engine.terminated is False
    assert engine.observation()[245] == 1.0


def test_afterstate_tick_normalization_keeps_training_scale():
    match = service.Match(FakePolicy(), {"observation": "afterstate"}, 79)
    match.ai.ticks = 3599
    observation = match.ai_env._observation()
    assert observation["state"].shape == (248,)
    assert observation["state"][245] == 1.0
    assert observation["candidates"].shape == (40, 7)
    for action, candidate in match.ai.candidates().items():
        assert observation["candidates"][action, 6] == pytest.approx(candidate.ticks / 3600)


@pytest.mark.parametrize("difficulty_id", list(service.DIFFICULTIES))
def test_real_checkpoint_smoke(difficulty_id):
    difficulty = service.DIFFICULTIES[difficulty_id]
    assert (service.MODEL_ROOT / difficulty.checkpoint).is_file()
    model, config = service.load_policy(difficulty_id)
    match = service.Match(model, config, 1000003, difficulty_id)
    for _ in range(50):
        fields(match.step("WAIT"))
    assert config["env"]["duration_ticks"] == difficulty.duration_ticks
    assert match.ai.rules.duration_ticks == match.human.rules.duration_ticks == 3600
    assert match.ai.ticks == match.human.ticks == 50
    assert str(model.device) == "cpu"
    assert match.ai.pieces_placed >= 1
    # 같은 실제 모델로 학습 기한 경계를 넘어 새 블록도 실행합니다.
    match = service.Match(model, config, 1000003, difficulty_id)
    match.ai.ticks = match.human.ticks = match.elapsed_ticks = 3599
    match.ai_spawn_tick = 3599
    for _ in range(50):
        fields(match.step("WAIT"))
    assert match.elapsed_ticks == 3649 and match.ended is False
    assert match.ai.pieces_placed >= 1
    # 한쪽 블록 생성 실패 후에도 다른 보드가 계속 진행합니다.
    match.human.board.fill(7)
    match.human._spawn()
    human_ticks = match.human.ticks
    match.step("WAIT")
    assert match.human.end_reason == "top_out" and match.human.ticks == human_ticks
    assert match.ended is False
    match.ai.board.fill(7)
    match.ai._spawn()
    final = fields(match.frame())
    assert final[2:4] == ["1", "DRAW"]
    assert final[7] == final[12] == "top_out"


def test_real_service_very_hard_and_hell_first_lock_pace():
    controller = service.MatchService()
    measured = {}
    for difficulty_id in ("very_hard", "hell"):
        fields(controller.request(f"START\t{difficulty_id}\t1000003"))
        for _ in range(40):
            frame = fields(controller.request("STEP\tWAIT"))
            if controller.match.ai.pieces_placed:
                measured[difficulty_id] = int(frame[1])
                break
    assert measured == {"very_hard": 32, "hell": 28}


def checkpoint_manifest(difficulty_id):
    difficulty = service.DIFFICULTIES[difficulty_id]
    return service.json.loads((service.MODEL_ROOT / difficulty.checkpoint).with_suffix(".manifest.json").read_text())


@pytest.mark.parametrize("difficulty_id", list(service.DIFFICULTIES))
def test_difficulty_rejects_training_duration_from_the_other_mode(difficulty_id):
    difficulty = service.DIFFICULTIES[difficulty_id]
    manifest = checkpoint_manifest(difficulty_id)
    manifest["config"]["env"]["duration_ticks"] = 3600 if difficulty.duration_ticks == 0 else 0
    with pytest.raises(ValueError, match="rules are incompatible"):
        service.validate_manifest(manifest, difficulty)
    with pytest.raises(ValueError, match="rules are incompatible"):
        service.Match(FakePolicy(), manifest["config"], 123, difficulty_id)


@pytest.mark.parametrize("difficulty_id", ["easy", "very_hard", "hell"])
@pytest.mark.parametrize("key, value", [("gravity_ticks", 7), ("think_ticks", 5), ("tick_ms", 40)])
def test_match_and_manifest_reject_other_rule_changes(difficulty_id, key, value):
    manifest = checkpoint_manifest(difficulty_id)
    manifest["config"]["env"][key] = value
    with pytest.raises(ValueError, match="rules are incompatible"):
        service.validate_manifest(manifest, service.DIFFICULTIES[difficulty_id])
    with pytest.raises(ValueError, match="rules are incompatible"):
        service.Match(FakePolicy(), manifest["config"], 123, difficulty_id)


@pytest.mark.parametrize("key, value, error", [
    ("episode_limit_mode", None, "episode_limit_mode"),
    ("episode_limit_mode", "timed", "episode_limit_mode"),
    ("untimed_engine_sha256", None, "untimed engine hash"),
    ("untimed_engine_sha256", "wrong", "untimed engine hash"),
    ("score_milestone", 10000, "milestone must be 100000"),
    ("target_score", 10000, "100000 point target"),
    ("target_reached", False, "100000 point target"),
    ("policy_class", "MaskableActorCriticPolicy", "policy class"),
    ("observation_version", "wrong", "observation version"),
    ("schema_version", 2, "manifest schema"),
])
def test_unlimited_manifest_requires_mode_engine_identity_and_completed_target(key, value, error):
    manifest = checkpoint_manifest("hell")
    manifest[key] = value
    with pytest.raises(ValueError, match=error):
        service.validate_manifest(manifest, service.DIFFICULTIES["hell"])


@pytest.mark.parametrize("difficulty_id", ["very_hard", "hell"])
def test_unlimited_manifest_hash_normalizes_crlf_and_rejects_changed_runtime(tmp_path, difficulty_id):
    manifest = checkpoint_manifest(difficulty_id)
    engine = tmp_path / "engine.py"
    untimed = tmp_path / "untimed.py"
    runtime = service.RUNTIME_ROOT / "tetris_sca"
    for name, target in (("engine.py", engine), ("untimed.py", untimed)):
        target.write_bytes((runtime / name).read_bytes().replace(b"\r\n", b"\n").replace(b"\n", b"\r\n"))
    assert service.validate_manifest(manifest, service.DIFFICULTIES[difficulty_id], engine, untimed)["env"]["duration_ticks"] == 0
    untimed.write_bytes(untimed.read_bytes() + b"# altered runtime\r\n")
    with pytest.raises(ValueError, match="untimed engine hash"):
        service.validate_manifest(manifest, service.DIFFICULTIES[difficulty_id], engine, untimed)
    engine.write_bytes(engine.read_bytes() + b"# altered engine\r\n")
    with pytest.raises(ValueError, match="engine hash"):
        service.validate_manifest(manifest, service.DIFFICULTIES[difficulty_id], engine, untimed)


def test_unlimited_training_env_uses_shared_engine_and_normalization():
    from tetris_sca.engine import Rules
    from tetris_sca.untimed import UntimedTetrisEngine
    assert service.UntimedTetrisEngine is UntimedTetrisEngine
    env = service.TetrisEnv({"observation": "afterstate", "env": {"duration_ticks": 0}})
    assert isinstance(env.engine, UntimedTetrisEngine)
    assert env.config["env"]["duration_ticks"] == 0
    assert env.engine.rules.duration_ticks == 3600
    env.reset(seed=123)
    env.engine.ticks = 3599
    action = min(env.engine.candidates())
    observation, _, terminated, truncated, _ = env.step(action)
    assert env.engine.ticks > 3600
    assert terminated is truncated is False
    assert observation["state"][245] == 1.0
    zero_rules_engine = UntimedTetrisEngine(Rules(duration_ticks=0), seed=123)
    assert zero_rules_engine.rules.duration_ticks == 3600
    for action, candidate in env.engine.candidates().items():
        assert observation["candidates"][action, 6] == pytest.approx(candidate.ticks / 3600)
