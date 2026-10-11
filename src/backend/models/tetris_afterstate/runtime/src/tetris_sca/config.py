"""Validated, versioned configuration shared by all command line tools."""
from __future__ import annotations

from copy import deepcopy
import json
import math
from pathlib import Path
from typing import Any

DEFAULT_CONFIG = {
    "schema_version": 1,
    "env": {"duration_ticks": 3600, "gravity_ticks": 8, "think_ticks": 4, "tick_ms": 50},
    "reward": "score",
    "observation": "board",
    "train": {
        "n_envs": 4, "n_steps": 512, "batch_size": 256, "n_epochs": 10,
        "learning_rate": 3e-4, "gamma": 1.0, "gae_lambda": 0.95,
        "clip_range": 0.2, "ent_coef": 0.01, "net_arch": [256, 256],
        "seed": 11, "device": "cuda", "torch_threads": 1,
        "eval_freq": 25000, "eval_episodes": 20, "checkpoint_freq": 25000,
        "score_checkpoint_interval": 0, "target_score": 0,
    },
    "evaluation": {"validation_seed_start": 1000000, "test_seed_start": 2000000,
                   "test_episodes": 500},
}


def _merge(base: dict, updates: dict, prefix: str = "") -> dict:
    if not isinstance(updates, dict):
        raise ValueError(f"{prefix or 'config'} must be an object")
    result = deepcopy(base)
    for key, value in updates.items():
        if key not in base:
            raise ValueError(f"Unknown configuration field: {prefix}{key}")
        if isinstance(base[key], dict):
            result[key] = _merge(base[key], value, f"{prefix}{key}.")
        else:
            result[key] = deepcopy(value)
    return result


def _integer(value: Any, name: str, minimum: int = 1) -> None:
    if type(value) is not int or value < minimum:
        raise ValueError(f"{name} must be an integer >= {minimum}")


def validate_config(config: dict | None = None) -> dict:
    """Return a deep merged copy; reject typos and inconsistent run settings."""
    c = _merge(DEFAULT_CONFIG, {} if config is None else config)
    if type(c['schema_version']) is not int or c['schema_version'] != 1:
        raise ValueError('Only schema_version 1 is supported')
    if c['reward'] not in ('score', 'potential'):
        raise ValueError('reward must be score or potential')
    if c['observation'] not in ('board', 'afterstate'):
        raise ValueError('observation must be board or afterstate')
    for key, value in c['env'].items():
        _integer(value, f'env.{key}', 0 if key == 'think_ticks' else 1)
    t = c['train']
    for key in ('n_envs', 'n_steps', 'batch_size', 'n_epochs', 'torch_threads', 'eval_episodes'):
        _integer(t[key], f'train.{key}', 2 if key in ('n_steps', 'batch_size') else 1)
    for key in ('seed', 'eval_freq', 'checkpoint_freq', 'score_checkpoint_interval', 'target_score'):
        _integer(t[key], f'train.{key}', 0)
    if (t['score_checkpoint_interval'] or t['target_score']) and t['eval_freq'] == 0:
        raise ValueError('Score milestones and target_score require eval_freq > 0')
    if t['n_steps'] * t['n_envs'] % t['batch_size']:
        raise ValueError('batch_size must divide n_steps * n_envs')
    if t['gamma'] != 1.0:
        raise ValueError('Finite score-race objective requires gamma=1.0')
    for key in ('learning_rate', 'gamma', 'gae_lambda', 'clip_range', 'ent_coef'):
        value = t[key]
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            raise ValueError(f'train.{key} must be finite numeric')
    if not 0 < t['learning_rate'] <= 1 or not 0 <= t['gae_lambda'] <= 1:
        raise ValueError('Invalid learning_rate or gae_lambda')
    if not 0 < t['clip_range'] < 1 or t['ent_coef'] < 0:
        raise ValueError('Invalid clip_range or ent_coef')
    if t['device'] not in ('cpu', 'cuda', 'auto'):
        raise ValueError('device must be cpu, cuda, or auto')
    if not isinstance(t['net_arch'], list) or not t['net_arch']:
        raise ValueError('net_arch must be a non-empty list')
    for width in t['net_arch']:
        _integer(width, 'net_arch width')
    e = c['evaluation']
    for key, value in e.items():
        _integer(value, f'evaluation.{key}', 0 if key.endswith('_start') else 1)
    if not 1000000 <= e['validation_seed_start'] < 2000000:
        raise ValueError('Validation seeds must be in [1000000, 2000000)')
    if e['validation_seed_start'] + t['eval_episodes'] > 2000000:
        raise ValueError('Validation sequence range overlaps test range')
    if e['test_seed_start'] < 2000000:
        raise ValueError('Test seed start must be >= 2000000')
    return c


def load_config(path: str | Path | None = None) -> dict:
    if path is None:
        return validate_config()
    with Path(path).open(encoding='utf-8') as handle:
        return validate_config(json.load(handle))


def rules_from_config(config: dict):
    from .engine import Rules
    return Rules(**validate_config(config)['env'])
