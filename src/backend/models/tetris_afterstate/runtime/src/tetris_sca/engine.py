"""Timed headless Tetris with the original Java shape coordinates.

Candidate paths use monotone horizontal movement, either before or after a
monotone series of left/right rotations, followed by hard drop. A ceiling-blocked
rotation may wait for gravity; wall/stack-blocked rotations and blocked lateral
moves are rejected. This deliberately excludes tucks, wall kicks and arbitrary
interleaved search. All simulated and real commands use the same tick executor.
"""
from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass
import random

import numpy as np

WIDTH, HEIGHT = 10, 22
SHAPES = (
    (),
    ((0, -1), (0, 0), (-1, 0), (-1, 1)),
    ((0, -1), (0, 0), (1, 0), (1, 1)),
    ((0, -1), (0, 0), (0, 1), (0, 2)),
    ((-1, 0), (0, 0), (1, 0), (0, 1)),
    ((0, 0), (1, 0), (0, 1), (1, 1)),
    ((-1, -1), (0, -1), (0, 0), (0, 1)),
    ((1, -1), (0, -1), (0, 0), (0, 1)),
)


def _rotations(points, square=False):
    result = []
    for _ in range(4):
        result.append(points)
        if not square:
            points = tuple((-y, x) for x, y in points)
    return tuple(result)


ORIENTATIONS = tuple(_rotations(points, i == 5) for i, points in enumerate(SHAPES))
MIN_X = tuple(tuple(min((x for x, _ in points), default=0) for points in rotations) for rotations in ORIENTATIONS)
MAX_X = tuple(tuple(max((x for x, _ in points), default=0) for points in rotations) for rotations in ORIENTATIONS)
_ROW_HEIGHTS = np.arange(1, HEIGHT + 1, dtype=np.int32)[:, None]


@dataclass(frozen=True)
class Rules:
    duration_ticks: int = 3600
    gravity_ticks: int = 8
    think_ticks: int = 4
    tick_ms: int = 50


@dataclass(frozen=True)
class Candidate:
    action: int
    path: tuple[str, ...]
    score_delta: int
    lines_cleared: int
    holes: int
    aggregate_height: int
    bumpiness: int
    ticks: int


class TetrisEngine:
    def __init__(self, rules: Rules | None = None, seed: int = 0):
        self.rules = rules or Rules()
        settings = (self.rules.duration_ticks, self.rules.gravity_ticks, self.rules.think_ticks, self.rules.tick_ms)
        if any(not isinstance(value, int) or isinstance(value, bool) for value in settings) or any(value <= 0 for value in (settings[0], settings[1], settings[3])) or settings[2] < 0:
            raise ValueError('Time settings must be positive integers; think_ticks may be zero')
        self.reset(seed)

    def reset(self, seed: int = 0, sequence: list[int] | None = None):
        sequence = [] if sequence is None else list(sequence)
        if any(not isinstance(piece, (int, np.integer)) or isinstance(piece, (bool, np.bool_)) or not 1 <= piece <= 7 for piece in sequence):
            raise ValueError('Piece sequence IDs must be integers from 1 to 7')
        self._rng = random.Random(seed)
        self._sequence = sequence
        self._sequence_index = 0
        self.board = np.zeros((HEIGHT, WIDTH), dtype=np.uint8)
        self.score = self.lines = self.pieces_placed = self.ticks = 0
        self.terminated = False
        self.end_reason = None
        self._simulation_mode = False
        self._candidate_key = None
        self._candidate_cache = {}
        self._last_action = None
        self._spawn()

    def _spawn(self):
        if self._sequence_index < len(self._sequence):
            self.piece = int(self._sequence[self._sequence_index])
            self._sequence_index += 1
        else:
            self.piece = self._rng.randrange(1, 8)
        self.rotation = 0
        self.x = 6
        self.y = 21 + min(y for _, y in SHAPES[self.piece])
        self.gravity_remaining = self.rules.gravity_ticks
        self.think_remaining = self.rules.think_ticks
        if not self._fits(self.x, self.y, self.rotation):
            self._finish('top_out')

    def _offsets(self, rotation=None):
        return ORIENTATIONS[self.piece][self.rotation if rotation is None else rotation]

    def _fits(self, x, y, rotation):
        for dx, dy in ORIENTATIONS[self.piece][rotation]:
            col, row = x + dx, y - dy
            if not (0 <= col < WIDTH and 0 <= row < HEIGHT) or self.board[row, col]:
                return False
        return True

    def _finish(self, reason):
        self.terminated = True
        self.end_reason = reason
        self.piece = 0

    def _lock(self, spawn=True):
        self._last_action = self.rotation * WIDTH + self.x + MIN_X[self.piece][self.rotation]
        if self._simulation_mode:
            self.board = self.board.copy()
        for dx, dy in self._offsets():
            self.board[self.y - dy, self.x + dx] = self.piece
        self.pieces_placed += 1
        full = np.all(self.board != 0, axis=1)
        removed = int(full.sum())
        if removed:
            remaining = self.board[~full].copy()
            self.board.fill(0)
            self.board[:len(remaining)] = remaining
            self.lines += removed
            self.score += (0, 100, 300, 500, 800)[min(removed, 4)]
        if spawn:
            self._spawn()
        else:
            self.piece = 0

    def _tick(self, command: str, spawn: bool = True) -> bool:
        """Execute a command, then gravity, within one tick. Return move success.

        HARD_DROP locks in its one command tick. At the final available tick a
        command may complete, but no next piece is spawned after that deadline.
        Candidate simulation calls this with spawn=False and never draws pieces.
        """
        if self.terminated or not self.piece:
            return False
        self.ticks += 1
        if self.think_remaining:
            self.think_remaining -= 1
            command = 'WAIT'
        moved = True
        before_placed = self.pieces_placed
        spawn = spawn and self.ticks < self.rules.duration_ticks
        if command == 'HARD_DROP':
            while self._fits(self.x, self.y - 1, self.rotation):
                self.y -= 1
            self._lock(spawn)
        elif command in ('LEFT', 'RIGHT', 'ROTATE_LEFT', 'ROTATE_RIGHT', 'WAIT'):
            new_x, new_rotation = self.x, self.rotation
            if command == 'LEFT':
                new_x -= 1
            elif command == 'RIGHT':
                new_x += 1
            elif self.piece != 5 and command in ('ROTATE_LEFT', 'ROTATE_RIGHT'):
                new_rotation = (self.rotation + (1 if command == 'ROTATE_RIGHT' else -1)) % 4
            if command != 'WAIT':
                moved = self._fits(new_x, self.y, new_rotation)
                if moved:
                    self.x, self.rotation = new_x, new_rotation
            self.gravity_remaining -= 1
            if self.gravity_remaining == 0:
                self.gravity_remaining = self.rules.gravity_ticks
                if self._fits(self.x, self.y - 1, self.rotation):
                    self.y -= 1
                else:
                    self._lock(spawn)
        else:
            raise ValueError(f'Unknown command: {command}')
        if self.ticks >= self.rules.duration_ticks:
            self._finish('timeout')
        # A freshly spawned piece's timer was set by _spawn and was not advanced.
        assert self.pieces_placed - before_placed <= 1
        return moved

    def _simulation(self):
        sim = object.__new__(TetrisEngine)
        sim.__dict__ = self.__dict__.copy()
        sim._simulation_mode = True
        sim._last_action = None
        return sim

    @staticmethod
    def _metrics(board):
        occupied = board != 0
        heights = np.max(np.where(occupied, _ROW_HEIGHTS, 0), axis=0)
        holes = int(heights.sum() - occupied.sum())
        bumpiness = int(np.abs(np.diff(heights)).sum())
        return heights, holes, bumpiness

    def _route(self, target_rotation, target_column, rotation_first, rotate_right):
        sim = self._simulation()
        path = []

        def execute(command):
            path.append(command)
            return sim._tick(command, spawn=False)

        while sim.think_remaining and sim.piece and not sim.terminated:
            execute('WAIT')
        target_x = target_column - MIN_X[self.piece][target_rotation]
        for phase in (('rotate', 'shift') if rotation_first else ('shift', 'rotate')):
            if not sim.piece or sim.terminated:
                break
            if phase == 'shift':
                while sim.x != target_x and sim.piece and not sim.terminated:
                    if not execute('LEFT' if sim.x > target_x else 'RIGHT'):
                        return None
            else:
                direction = 1 if rotate_right else -1
                count = ((target_rotation - sim.rotation) * direction) % 4
                for _ in range(count):
                    if not sim.piece or sim.terminated:
                        break
                    next_rotation = (sim.rotation + direction) % 4
                    if not sim._fits(sim.x, sim.y, next_rotation):
                        # Wait only when the ceiling is responsible, never kick.
                        if not any(sim.y - dy >= HEIGHT for _, dy in sim._offsets(next_rotation)):
                            return None
                        while sim.piece and not sim.terminated and not sim._fits(sim.x, sim.y, next_rotation):
                            execute('WAIT')
                    if sim.piece and not sim.terminated:
                        execute('ROTATE_RIGHT' if rotate_right else 'ROTATE_LEFT')
        if sim.piece and not sim.terminated:
            execute('HARD_DROP')
        # If gravity locked early, advertise the actual resulting placement.
        action = sim._last_action if sim._last_action is not None else target_rotation * WIDTH + target_column
        heights, holes, bumpiness = self._metrics(sim.board)
        return Candidate(action, tuple(path), sim.score - self.score, sim.lines - self.lines, holes, int(heights.sum()), bumpiness, sim.ticks - self.ticks)

    def candidates(self) -> dict[int, Candidate]:
        """Reachable placements without consulting or consuming the future stream.

        Paths interrupted by the intrinsic deadline remain valid actions and end
        at timeout. This ensures a live engine never has an all-false mask.
        Cache keys include board contents, so direct board fixture edits are safe.
        """
        if self.terminated or not self.piece:
            return {}
        key = (self.board.tobytes(), self.piece, self.rotation, self.x, self.y, self.ticks, self.gravity_remaining, self.think_remaining, self.score, self.lines, self.pieces_placed, self.rules)
        if key == self._candidate_key:
            return dict(self._candidate_cache)
        result = {}
        rotations = (0,) if self.piece == 5 else range(4)
        for rotation in rotations:
            width = MAX_X[self.piece][rotation] - MIN_X[self.piece][rotation] + 1
            for column in range(WIDTH - width + 1):
                # Order favors minimal direct paths; duplicates select fewer ticks.
                for rotation_first in (True, False):
                    for rotate_right in (True, False):
                        candidate = self._route(rotation, column, rotation_first, rotate_right)
                        if candidate is not None:
                            old = result.get(candidate.action)
                            if old is None or candidate.ticks < old.ticks:
                                result[candidate.action] = candidate
        # A legal current piece always has a WAIT/straight hard-drop route.
        assert result, 'A live engine must have at least one executable action'
        self._candidate_key, self._candidate_cache = key, result
        return dict(result)

    def action_masks(self) -> np.ndarray:
        mask = np.zeros(40, dtype=np.bool_)
        for action in self.candidates():
            mask[action] = True
        return mask

    def step(self, action: int) -> dict:
        before = self.score, self.lines, self.pieces_placed, self.ticks
        candidate = self.candidates().get(action)
        invalid = candidate is None
        for command in (('WAIT',) if invalid else candidate.path):
            self._tick(command)
            if self.terminated or self.pieces_placed != before[2]:
                break
        return dict(score_delta=self.score - before[0], lines_cleared=self.lines - before[1], elapsed_ticks=self.ticks - before[3], invalid_action=invalid, score=self.score, lines=self.lines, pieces_placed=self.pieces_placed, ticks=self.ticks, end_reason=self.end_reason)

    def observation(self) -> np.ndarray:
        observation = np.zeros(248, dtype=np.float32)
        observation[:220] = (self.board != 0).ravel()
        if self.piece and not self.terminated:
            observation[220 + self.piece - 1] = 1
            observation[227:229] = (self.x / 9, self.y / 21)
            observation[229 + self.rotation] = 1
            observation[246] = self.gravity_remaining / self.rules.gravity_ticks
            observation[247] = self.think_remaining / max(1, self.rules.think_ticks)
        heights, holes, bumpiness = self._metrics(self.board)
        observation[233:243] = heights / HEIGHT
        observation[243:245] = (holes / 220, bumpiness / 198)
        observation[245] = max(0, self.rules.duration_ticks - self.ticks) / self.rules.duration_ticks
        return observation

    def snapshot(self) -> dict:
        state = {k: v for k, v in self.__dict__.items() if k not in ('_rng', '_candidate_cache', '_candidate_key')}
        state['_rng_state'] = self._rng.getstate()
        return deepcopy(state)

    def restore(self, snapshot: dict) -> None:
        state = deepcopy(snapshot)
        rng_state = state.pop('_rng_state')
        self.__dict__ = state
        self._rng = random.Random()
        self._rng.setstate(rng_state)
        self._candidate_key = None
        self._candidate_cache = {}

    def render_ansi(self) -> str:
        visible = self.board.copy()
        if self.piece and not self.terminated:
            for dx, dy in self._offsets():
                visible[self.y - dy, self.x + dx] = self.piece
        rows = ['|' + ''.join(str(int(value)) if value else '.' for value in row) + '|' for row in visible[::-1]]
        return '\n'.join(['+' + '-' * WIDTH + '+', *rows, '+' + '-' * WIDTH + '+', f'score={self.score} lines={self.lines} pieces={self.pieces_placed} ticks={self.ticks}/{self.rules.duration_ticks} end={self.end_reason or "active"}'])
