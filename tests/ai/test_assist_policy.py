"""클래식 도움 후보와 기존 대전 연결의 독립성을 확인합니다."""
import importlib.util
from pathlib import Path
import sys

import numpy as np
import pytest

SERVICE_PATH = Path(__file__).resolve().parents[2] / "src/backend/ai/match_service.py"
spec = importlib.util.spec_from_file_location("assist_test_service", SERVICE_PATH)
service = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = service
spec.loader.exec_module(service)
from assist_policy import build_observation, generate_candidates, parse_snapshot, select_placement
from tetris_sca.engine import MIN_X, ORIENTATIONS


def snapshot(board=None, piece=4, rotation=0, x=5, y=15):
    board = np.zeros((22, 10), dtype=np.uint8) if board is None else board
    digits = "".join(map(str, board.ravel()))
    return parse_snapshot(digits, str(piece), str(rotation), str(x), str(y))


def fits(board, piece, rotation, x, y):
    return all(0 <= x + dx < 10 and 0 <= y - dy < 22 and not board[y-dy, x+dx]
               for dx, dy in ORIENTATIONS[piece][rotation])


def replay(snap, placement):
    x, y, rotation = snap.x, snap.y, snap.rotation
    assert placement.path.endswith("H") and placement.path.count("H") == 1
    assert len(placement.path) <= 256
    for command in placement.path:
        if command in "LR":
            x += -1 if command == "L" else 1
        elif command in "AC":
            rotated = (rotation + (-1 if command == "A" else 1)) % 4 if snap.piece != 5 else 0
            for kick in (0, -1, 1, -2, 2):
                if fits(snap.board, snap.piece, rotated, x+kick, y):
                    x, rotation = x+kick, rotated
                    break
            else:
                pytest.fail("응답 경로의 회전이 막혀 있습니다.")
        elif command == "D":
            y -= 1
        elif command == "H":
            while fits(snap.board, snap.piece, rotation, x, y-1):
                y -= 1
        else:
            pytest.fail("알 수 없는 경로 입력입니다.")
        assert fits(snap.board, snap.piece, rotation, x, y)
    assert (rotation, x, y) == (placement.rotation, placement.x, placement.y)


@pytest.mark.parametrize("piece,rotation,x,y", [(4, 2, 5, 15), (3, 0, 0, 12), (5, 0, 4, 1), (6, 3, 8, 10)])
def test_current_rotation_wall_kick_and_low_piece_paths(piece, rotation, x, y):
    snap = snapshot(piece=piece, rotation=rotation, x=x, y=y)
    candidates = generate_candidates(snap)
    assert candidates
    for action, candidate in candidates.items():
        replay(snap, candidate)
        assert action == candidate.rotation*10 + candidate.x + MIN_X[piece][candidate.rotation]
    if piece == 3 and x == 0:
        # 왼쪽 벽의 세로 I 회전은 Java와 같은 kick으로 실행되어야 합니다.
        assert candidates[10].path == "CH" and candidates[10].x == 2


def test_obstacles_preserve_board_and_only_reachable_candidates():
    board = np.zeros((22, 10), dtype=np.uint8)
    board[:12, 3] = 7
    board[:8, 7] = 6
    snap = snapshot(board, piece=5, x=4, y=2)
    before = snap.board.copy()
    candidates = generate_candidates(snap)
    for candidate in candidates.values():
        replay(snap, candidate)
    assert all(candidate.x >= 4 for candidate in candidates.values())
    np.testing.assert_array_equal(snap.board, before)


def test_line_clear_metrics_use_training_score_and_bottom_first_rows():
    board = np.zeros((22, 10), dtype=np.uint8)
    board[0, :4] = 7
    board[0, 6:] = 7
    snap = snapshot(board, piece=5, x=4, y=1)
    candidates = generate_candidates(snap)
    chosen = candidates[4]
    assert chosen.path == "H"
    observation, mask = build_observation(snap, candidates)
    assert observation["state"].shape == (248,)
    assert observation["candidates"].shape == (40, 7)
    assert mask.shape == (40,) and mask[4]
    np.testing.assert_allclose(observation["candidates"][4], [1, 100/800, 1/4, 0, 2/220, 2/198, 1/3600])
    np.testing.assert_array_equal(observation["state"][245:], [1, 1, 0])
    assert observation["state"][220+4] == 1


class FakePolicy:
    def predict(self, observation, *, deterministic, action_masks):
        assert deterministic is True
        return np.flatnonzero(action_masks)[0], None


def test_selected_action_is_masked_and_contains_one_final_hard_drop():
    snap = snapshot()
    chosen = select_placement(FakePolicy(), snap)
    replay(snap, chosen)
    class InvalidPolicy:
        def predict(self, *_args, **_kwargs):
            return 39, None
    with pytest.raises(ValueError, match="masked|invalid"):
        select_placement(InvalidPolicy(), snapshot(piece=5))


@pytest.mark.parametrize("digits,piece,rotation,x,y", [
    ("0"*219, "4", "0", "5", "15"),
    ("8"+"0"*219, "4", "0", "5", "15"),
    ("0"*220, "8", "0", "5", "15"),
    ("0"*220, "4", "4", "5", "15"),
    ("0"*220, "4", "0", "10", "15"),
    ("0"*220, "4", "0", "5", "22"),
    ("0"*220, "4", "0", "-1", "15"),
    ("7"*220, "4", "0", "5", "15"),
    ("0"*220, "4", "0", "0", "0"),
])
def test_invalid_snapshot_rejected(digits, piece, rotation, x, y):
    with pytest.raises(ValueError):
        parse_snapshot(digits, piece, rotation, x, y)


def test_assist_needs_no_start_reuses_hell_and_does_not_mutate_match():
    calls = []
    def loader(difficulty):
        calls.append(difficulty)
        return FakePolicy(), {"observation": "afterstate"}
    controller = service.MatchService(loader)
    marker = object()
    controller.match = marker
    request = "ASSIST\t" + "0"*220 + "\t4\t0\t5\t15"
    response = controller.request(request)
    assert response.startswith("ASSIST\t100000\t")
    assert len(response.split("\t")) == 6
    assert controller.request(request) == response
    assert calls == ["hell"] and controller.match is marker
    assert controller.loaded["hell"][0].__class__ is FakePolicy


def test_bad_assist_does_not_load_checkpoint():
    controller = service.MatchService(lambda _: pytest.fail("불법 스냅샷에서 모델을 로드했습니다."))
    with pytest.raises(ValueError):
        controller.request("ASSIST\t" + "8"*220 + "\t4\t0\t5\t15")


def test_assist_reuses_hell_loaded_by_existing_match_and_preserves_frame():
    calls = []
    manifest = service.json.loads((service.MODEL_ROOT / "checkpoints/score_100000.manifest.json").read_text())
    def loader(difficulty):
        calls.append(difficulty)
        return FakePolicy(), manifest["config"]
    controller = service.MatchService(loader)
    original_frame = controller.request("START\thell\t17")
    original_match = controller.match
    controller.request("ASSIST\t" + "0"*220 + "\t4\t2\t5\t15")
    assert calls == ["hell"]
    assert controller.match is original_match and controller.match.frame() == original_frame


def test_real_hell_checkpoint_accepts_java_assist_observation():
    model, _ = service.load_policy("hell")
    snap = snapshot(piece=3, x=0, y=12)
    chosen = select_placement(model, snap)
    replay(snap, chosen)
    assert str(model.device) == "cpu"
