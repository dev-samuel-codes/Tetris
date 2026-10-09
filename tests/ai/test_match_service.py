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
    assert len(parts) == 14
    assert parts[0] == "FRAME"
    for index in (8, 13):
        assert len(parts[index]) == 220
        assert set(parts[index]) <= set("01234567")
    return parts


def test_frame_has_active_overlay_and_one_shared_tick():
    match = make_match()
    initial = fields(match.frame())
    assert initial[1:4] == ["0", "0", "NONE"]
    assert initial[8] == initial[13]
    assert sum(char != "0" for char in initial[8]) == 4
    for tick in range(1, 6):
        frame = fields(match.step("LEFT"))
        assert frame[1] == str(tick)
        assert match.human.ticks == match.ai.ticks == tick
    assert match.policy.calls == 0
    match.step("WAIT")
    assert match.policy.calls == 0
    match.step("WAIT")
    assert match.policy.calls == 1


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
    assert match.ai.ticks == 34


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


def test_match_continues_beyond_3600_ticks():
    match = make_match()
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
