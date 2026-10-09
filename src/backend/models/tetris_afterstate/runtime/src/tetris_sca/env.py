"""Gymnasium adapter for time-aware placement actions."""
from __future__ import annotations

import gymnasium as gym
import numpy as np

from .config import rules_from_config, validate_config
from .engine import TetrisEngine


BOARD_OBSERVATION_VERSION = "bottomfirst-248-v1"
AFTERSTATE_OBSERVATION_VERSION = "afterstate-candidates40x7-v1"
CANDIDATE_FEATURE_NAMES = (
    "legal", "score_delta", "lines_cleared", "holes",
    "aggregate_height", "bumpiness", "elapsed_ticks",
)


class TetrisEnv(gym.Env):
    metadata = {"render_modes": ["ansi"], "render_fps": 20}

    def __init__(self, config: dict | None = None, render_mode: str | None = None):
        super().__init__()
        self.config = validate_config(config)
        if render_mode not in (None, "ansi"):
            raise ValueError("render_mode must be None or ansi")
        self.render_mode = render_mode
        self.engine = TetrisEngine(rules_from_config(self.config))
        self.action_space = gym.spaces.Discrete(40)
        state_space = gym.spaces.Box(0.0, 1.0, (248,), np.float32)
        if self.config["observation"] == "afterstate":
            self.observation_space = gym.spaces.Dict({
                "state": state_space,
                "candidates": gym.spaces.Box(0.0, 1.0, (40, 7), np.float32),
            })
        else:
            self.observation_space = state_space
        self._phi = self._potential()

    def _observation(self):
        state = self.engine.observation()
        if self.config["observation"] == "board":
            return state
        # Reuse the engine's current-piece simulations and cache. Their paths
        # stop before drawing another piece, including at the intrinsic deadline.
        candidates = np.zeros((40, len(CANDIDATE_FEATURE_NAMES)), dtype=np.float32)
        for action, candidate in self.engine.candidates().items():
            candidates[action] = (
                1.0, candidate.score_delta / 800, candidate.lines_cleared / 4,
                candidate.holes / 220, candidate.aggregate_height / 220,
                candidate.bumpiness / 198,
                candidate.ticks / self.engine.rules.duration_ticks,
            )
        return {"state": state, "candidates": candidates}

    def _potential(self) -> float:
        if self.engine.terminated:
            return 0.0
        occupied = self.engine.board != 0
        heights = np.array([np.flatnonzero(col)[-1] + 1 if col.any() else 0
                            for col in occupied.T])
        holes = sum(int((~occupied[:height, col]).sum())
                    for col, height in enumerate(heights))
        return -float(holes / 220 + heights.sum() / 220 + np.abs(np.diff(heights)).sum() / 198)

    def reset(self, *, seed: int | None = None, options: dict | None = None):
        super().reset(seed=seed)
        engine_seed = int(seed) if seed is not None else int(self.np_random.integers(0, 1000000))
        self.engine.reset(seed=engine_seed, sequence=(options or {}).get("sequence"))
        self._phi = self._potential()
        return self._observation(), {"episode_seed": engine_seed}

    def step(self, action):
        info = self.engine.step(int(action))
        new_phi = self._potential()
        reward = info["score_delta"] / 100.0
        if self.config["reward"] == "potential":
            reward += 0.1 * (new_phi - self._phi)
        self._phi = new_phi
        return self._observation(), float(reward), self.engine.terminated, False, info

    def action_masks(self):
        return self.engine.action_masks()

    def render(self):
        return self.engine.render_ansi() if self.render_mode == "ansi" else None
