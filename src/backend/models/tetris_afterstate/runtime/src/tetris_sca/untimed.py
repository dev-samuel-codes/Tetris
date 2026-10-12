"""시간 종료 없이 top_out까지 진행하는 학습용 엔진."""
from __future__ import annotations

from dataclasses import replace

import numpy as np

from .engine import Rules, TetrisEngine

NORMALIZATION_DURATION_TICKS = 3600


class UntimedTetrisEngine(TetrisEngine):
    """Rules.duration_ticks는 관측 정규화용이며 실제 종료 시한은 없습니다."""

    def __init__(self, rules: Rules | None = None, seed: int = 0):
        rules = rules or Rules()
        if type(rules.duration_ticks) is int and rules.duration_ticks == 0:
            rules = replace(rules, duration_ticks=NORMALIZATION_DURATION_TICKS)
        super().__init__(rules, seed=seed)

    def _tick(self, command: str, spawn: bool = True) -> bool:
        if self.terminated or not self.piece:
            return False
        self.ticks += 1
        if self.think_remaining:
            self.think_remaining -= 1
            command = 'WAIT'
        moved = True
        before_placed = self.pieces_placed
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
        assert self.pieces_placed - before_placed <= 1
        return moved

    def _simulation(self):
        # 후보 배치에도 같은 무제한 규칙을 적용합니다.
        sim = object.__new__(type(self))
        sim.__dict__ = self.__dict__.copy()
        sim._simulation_mode = True
        sim._last_action = None
        return sim

    def observation(self) -> np.ndarray:
        observation = super().observation()
        # 기존 248차원 입력을 유지하고 시간 여유를 항상 1로 전달합니다.
        observation[245] = 1.0
        return observation

    def render_ansi(self) -> str:
        rendered = super().render_ansi()
        return rendered.replace(f'ticks={self.ticks}/{self.rules.duration_ticks}',
                                f'ticks={self.ticks}/unlimited')
