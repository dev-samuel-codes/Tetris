"""시간제한 없는 AI 대전과 표준 입출력 통신 처리."""
from __future__ import annotations

from collections import deque
from contextlib import redirect_stdout
from dataclasses import dataclass
import hashlib
import json
import logging
from pathlib import Path
import sys
from typing import TextIO

MODEL_ROOT = Path(__file__).resolve().parents[1] / "models" / "tetris_afterstate"
RUNTIME_ROOT = MODEL_ROOT / "runtime" / "src"
sys.path.insert(0, str(RUNTIME_ROOT))

import numpy as np
from tetris_sca.config import validate_config
from tetris_sca.engine import TetrisEngine
from tetris_sca.env import TetrisEnv

PROTOCOL_VERSION = 2
RULES_VERSION = "java-iid-placement40-timed-v1"
ENGINE_SHA256 = "b37b7c9929f5ee99c43002747ee3e685469e13bced613be2d540ff8699b8120d"
RULE_SETTINGS = {"duration_ticks": 3600, "gravity_ticks": 8, "think_ticks": 4, "tick_ms": 50}
OBSERVATION_VERSIONS = {"board": "bottomfirst-248-v1", "afterstate": "afterstate-candidates40x7-v1"}
HUMAN_COMMANDS = frozenset(("WAIT", "LEFT", "RIGHT", "ROTATE_LEFT", "ROTATE_RIGHT", "HARD_DROP", "SOFT_DROP"))
AI_REACTION_TICKS = 6
DEFAULT_PACE_TICKS = 40
MIN_PACE_TICKS = 10
MAX_PACE_TICKS = 120


def ai_pace_ticks(human_ticks: int, difficulty_id: str = "normal") -> int:
    # 지옥은 75%, 나머지는 85% 배치 간격을 틱 단위로 올림
    percentage = 75 if difficulty_id == "hell" else 85
    return (human_ticks * percentage + 99) // 100


class UntimedTetrisEngine(TetrisEngine):
    """원본 엔진에서 시간 종료 조건을 제거한 대전용 엔진."""

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
        # 후보 배치 계산에도 같은 무제한 규칙 적용
        sim = object.__new__(type(self))
        sim.__dict__ = self.__dict__.copy()
        sim._simulation_mode = True
        sim._last_action = None
        return sim

    def observation(self) -> np.ndarray:
        observation = super().observation()
        # 학습 입력 크기는 유지하고 남은 시간은 항상 충분한 상태로 전달
        observation[245] = 1.0
        return observation


@dataclass(frozen=True)
class Difficulty:
    score: int
    checkpoint: str
    observation: str


DIFFICULTIES = {
    "easy": Difficulty(200, "checkpoints/score_00200.zip", "board"),
    "normal": Difficulty(500, "checkpoints/score_00500.zip", "board"),
    "hard": Difficulty(1000, "checkpoints/score_01000.zip", "afterstate"),
    "very_hard": Difficulty(5000, "checkpoints/score_05000.zip", "afterstate"),
    "hell": Difficulty(10000, "best.zip", "afterstate"),
}


def validate_manifest(manifest: dict, difficulty: Difficulty, engine_path: Path | None = None) -> dict:
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        raise ValueError("Unsupported checkpoint manifest schema")
    if manifest.get("rules_version") != RULES_VERSION:
        raise ValueError("Checkpoint rules version is incompatible")
    engine_path = engine_path or RUNTIME_ROOT / "tetris_sca" / "engine.py"
    if manifest.get("engine_sha256") != ENGINE_SHA256 or hashlib.sha256(engine_path.read_bytes()).hexdigest() != ENGINE_SHA256:
        raise ValueError("Checkpoint engine hash is incompatible")
    config = validate_config(manifest.get("config"))
    if config["env"] != RULE_SETTINGS or config["reward"] != "score":
        raise ValueError("Checkpoint must use the 180 second score-race rules")
    if config["observation"] != difficulty.observation or manifest.get("observation_version") != OBSERVATION_VERSIONS[difficulty.observation]:
        raise ValueError("Checkpoint observation version is incompatible")
    expected_policy = "CandidateActorCriticPolicy" if difficulty.observation == "afterstate" else "MaskableActorCriticPolicy"
    if manifest.get("policy_class", "MaskableActorCriticPolicy") != expected_policy:
        raise ValueError("Checkpoint policy class is incompatible")
    if difficulty.checkpoint == "best.zip":
        if manifest.get("target_score") != difficulty.score or manifest.get("target_reached") is not True:
            raise ValueError("Hell checkpoint must be the completed 10000 point target model")
    else:
        # 해당 파일을 저장한 점수 기준 확인
        identity = manifest.get("score_milestone", manifest.get("score_checkpoint"))
        if identity != difficulty.score:
            raise ValueError(f"Checkpoint score milestone must be {difficulty.score}")
    return config


def load_policy(difficulty_id: str, model_root: Path = MODEL_ROOT):
    if difficulty_id not in DIFFICULTIES:
        raise ValueError(f"Unknown difficulty: {difficulty_id}")
    difficulty = DIFFICULTIES[difficulty_id]
    checkpoint = model_root / difficulty.checkpoint
    manifest_path = checkpoint.with_suffix(".manifest.json")
    if not checkpoint.is_file() or not manifest_path.is_file():
        raise ValueError(f"Missing checkpoint or manifest for {difficulty_id}")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise ValueError(f"Cannot read checkpoint manifest for {difficulty_id}") from exc
    config = validate_manifest(manifest, difficulty)
    import torch
    from sb3_contrib import MaskablePPO
    torch.set_num_threads(1)
    with redirect_stdout(sys.stderr):
        model = MaskablePPO.load(checkpoint, device="cpu")
    expected_env = TetrisEnv(config)
    if model.observation_space != expected_env.observation_space or model.action_space != expected_env.action_space:
        raise ValueError("Checkpoint model observation/action shape is incompatible")
    if model.policy.__class__.__name__ != manifest.get("policy_class", "MaskableActorCriticPolicy"):
        raise ValueError("Checkpoint model policy differs from manifest")
    model.policy.set_training_mode(False)
    return model, config


def board_string(engine: TetrisEngine) -> str:
    visible = engine.board.copy()
    if not engine.terminated and engine.piece:
        for dx, dy in engine._offsets():
            visible[engine.y - dy, engine.x + dx] = engine.piece
    return "".join(str(int(value)) for value in visible.ravel())
