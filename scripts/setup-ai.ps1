# Windows PowerShell: 프로젝트 전용 Python 환경 준비
param([string]$PythonCommand = "python")
$ErrorActionPreference = "Stop"
$TetrisRoot = Split-Path -Parent $PSScriptRoot
$TetrisEnv = Join-Path $TetrisRoot ".venv-ai"
$TetrisPython = Join-Path $TetrisEnv "Scripts/python.exe"
$TetrisPackage = Join-Path $TetrisRoot "src/backend/models/tetris_afterstate/runtime"
& $PythonCommand -c "import sys; assert sys.version_info >= (3, 11), 'Python 3.11+ required'"
if ($LASTEXITCODE -ne 0) { throw "Python 3.11 이상을 설치해 주세요." }
if (!(Test-Path $TetrisPython)) {
    & $PythonCommand -m venv $TetrisEnv
    if ($LASTEXITCODE -ne 0) { throw "Python 환경 생성에 실패했습니다." }
}
& $TetrisPython -m pip install $TetrisPackage
if ($LASTEXITCODE -ne 0) { throw "AI 실행 패키지 설치에 실패했습니다." }
& $TetrisPython -c "import torch, sb3_contrib, tetris_sca.policy; print('AI runtime ready (CPU)')"
if ($LASTEXITCODE -ne 0) { throw "AI 실행 패키지를 확인하지 못했습니다." }
