"""Java 클래식 스냅샷의 도달 가능한 배치를 100000 정책에 전달합니다."""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass

import numpy as np
from tetris_sca.engine import HEIGHT, MIN_X, ORIENTATIONS, WIDTH

MAX_PATH_LENGTH = 256
# TODO: Java/Python 벽차기 설정을 공용 규칙 파일로 통합해 변경 누락을 줄입니다.
WALL_KICKS = (0, -1, 1, -2, 2)
TRAINING_LINE_SCORES = (0, 100, 300, 500, 800)


@dataclass(frozen=True)
class AssistSnapshot:
    board: np.ndarray
    piece: int
    rotation: int
    x: int
    y: int


@dataclass(frozen=True)
class AssistPlacement:
    rotation: int
    x: int
    y: int
    path: str
    lines_cleared: int
    holes: int
    aggregate_height: int
    bumpiness: int


def _number(token: str, minimum: int, maximum: int, name: str) -> int:
    if not token or len(token) > 2 or any(char < "0" or char > "9" for char in token):
        raise ValueError(f"Invalid assist {name}")
    value = int(token)
    if not minimum <= value <= maximum:
        raise ValueError(f"Invalid assist {name}")
    return value


def _fits(snapshot: AssistSnapshot, rotation: int, x: int, y: int) -> bool:
    for dx, dy in ORIENTATIONS[snapshot.piece][rotation]:
        column, row = x + dx, y - dy
        if not (0 <= column < WIDTH and 0 <= row < HEIGHT) or snapshot.board[row, column]:
            return False
    return True


def parse_snapshot(board: str, piece: str, rotation: str, x: str, y: str) -> AssistSnapshot:
    if len(board) != WIDTH * HEIGHT or any(char < "0" or char > "7" for char in board):
        raise ValueError("Invalid assist board: expected 220 fixed cells from 0 to 7")
    piece_id = _number(piece, 1, 7, "piece")
    orientation = _number(rotation, 0, 3, "rotation")
    cells = np.fromiter((ord(char) - ord("0") for char in board), dtype=np.uint8).reshape(HEIGHT, WIDTH)
    cells.setflags(write=False)
    snapshot = AssistSnapshot(cells, piece_id, 0 if piece_id == 5 else orientation,
                              _number(x, 0, 9, "x"), _number(y, 0, 21, "y"))
    if not _fits(snapshot, snapshot.rotation, snapshot.x, snapshot.y):
        raise ValueError("Invalid assist snapshot: active piece overlaps board or boundary")
    return snapshot


def _metrics(board: np.ndarray) -> tuple[np.ndarray, int, int]:
    occupied = board != 0
    heights = np.max(np.where(occupied, np.arange(1, HEIGHT + 1)[:, None], 0), axis=0)
    holes = int(heights.sum() - occupied.sum())
    bumpiness = int(np.abs(np.diff(heights)).sum())
    return heights, holes, bumpiness


def _placement(snapshot: AssistSnapshot, rotation: int, x: int, y: int, path: str) -> AssistPlacement:
    board = snapshot.board.copy()
    for dx, dy in ORIENTATIONS[snapshot.piece][rotation]:
        board[y - dy, x + dx] = snapshot.piece
    full = np.all(board != 0, axis=1)
    cleared = int(full.sum())
    if cleared:
        remaining = board[~full].copy()
        board.fill(0)
        board[:len(remaining)] = remaining
    heights, holes, bumpiness = _metrics(board)
    return AssistPlacement(rotation, x, y, path + "H", cleared, holes, int(heights.sum()), bumpiness)


def generate_candidates(snapshot: AssistSnapshot) -> dict[int, AssistPlacement]:
    """타이머를 멈춘 Java 입력을 탐색하며, 중간 하강에서는 고정하지 않습니다."""
    initial = (snapshot.rotation, snapshot.x, snapshot.y)
    queue = deque([(initial, "")])
    visited = {initial}
    candidates: dict[int, AssistPlacement] = {}
    while queue:
        (rotation, x, y), path = queue.popleft()
        landing_y = y
        while _fits(snapshot, rotation, x, landing_y - 1):
            landing_y -= 1
        action = rotation * WIDTH + x + MIN_X[snapshot.piece][rotation]
        # 동일 action의 여러 착지는 BFS에서 먼저 찾은 최단 경로로 대표합니다.
        if action not in candidates:
            candidates[action] = _placement(snapshot, rotation, x, landing_y, path)
        if len(path) + 1 >= MAX_PATH_LENGTH:
            continue
        transitions = [("L", rotation, x - 1, y), ("R", rotation, x + 1, y)]
        for command, direction in (("A", -1), ("C", 1)):
            next_rotation = (rotation + direction) % 4 if snapshot.piece != 5 else 0
            for kick in WALL_KICKS:
                if _fits(snapshot, next_rotation, x + kick, y):
                    transitions.append((command, next_rotation, x + kick, y))
                    break
        transitions.append(("D", rotation, x, y - 1))
        for command, next_rotation, next_x, next_y in transitions:
            state = (next_rotation, next_x, next_y)
            if state not in visited and _fits(snapshot, *state):
                visited.add(state)
                queue.append((state, path + command))
    return candidates


def build_observation(snapshot: AssistSnapshot, candidates: dict[int, AssistPlacement]) -> tuple[dict, np.ndarray]:
    state = np.zeros(248, dtype=np.float32)
    state[:220] = (snapshot.board != 0).ravel()
    state[220 + snapshot.piece - 1] = 1
    state[227:229] = (snapshot.x / 9, snapshot.y / 21)
    state[229 + snapshot.rotation] = 1
    heights, holes, bumpiness = _metrics(snapshot.board)
    state[233:243] = heights / HEIGHT
    state[243:245] = (holes / 220, bumpiness / 198)
    state[245:248] = (1, 1, 0)
    rows = np.zeros((40, 7), dtype=np.float32)
    mask = np.zeros(40, dtype=np.bool_)
    for action, candidate in candidates.items():
        mask[action] = True
        # 게임 점수와 별개로, 모델 학습 당시의 줄 점수·정규화를 유지합니다.
        rows[action] = (1, TRAINING_LINE_SCORES[min(candidate.lines_cleared, 4)] / 800,
                        candidate.lines_cleared / 4, candidate.holes / 220,
                        candidate.aggregate_height / 220, candidate.bumpiness / 198,
                        len(candidate.path) / 3600)
    return {"state": state, "candidates": rows}, mask


def select_placement(policy, snapshot: AssistSnapshot) -> AssistPlacement:
    candidates = generate_candidates(snapshot)
    observation, mask = build_observation(snapshot, candidates)
    action, _ = policy.predict(observation, deterministic=True, action_masks=mask)
    raw_action = np.asarray(action).item()
    if not isinstance(raw_action, (int, np.integer)) or isinstance(raw_action, bool) or raw_action not in candidates:
        raise ValueError("Assist AI predicted a masked or invalid action")
    return candidates[int(raw_action)]
