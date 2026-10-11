#!/usr/bin/env bash
# 로컬에서 AI 모델을 실행할 Python 환경과 필요한 패키지를 준비
set -euo pipefail
TETRIS_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TETRIS_ENV="$TETRIS_ROOT/.venv-ai"
TETRIS_PACKAGE="$TETRIS_ROOT/src/backend/models/tetris_afterstate/runtime"
if command -v uv >/dev/null 2>&1; then
    if [[ ! -x "$TETRIS_ENV/bin/python" ]]; then
        uv venv "$TETRIS_ENV" --python 3.12
    fi
    uv pip install --python "$TETRIS_ENV/bin/python" "$TETRIS_PACKAGE"
else
    TETRIS_PYTHON="${TETRIS_AI_PYTHON:-python3}"
    "$TETRIS_PYTHON" -c 'import sys; assert sys.version_info >= (3, 11), "Python 3.11 이상이 필요합니다."'
    if [[ ! -x "$TETRIS_ENV/bin/python" ]]; then
        "$TETRIS_PYTHON" -m venv "$TETRIS_ENV"
    fi
    "$TETRIS_ENV/bin/python" -m ensurepip --upgrade
    "$TETRIS_ENV/bin/python" -m pip install "$TETRIS_PACKAGE"
fi
"$TETRIS_ENV/bin/python" -c 'import torch, sb3_contrib, tetris_sca.policy; print("AI 대전 실행 환경 준비 완료 (CPU)")'
