# 테트리스 후보 배치 결과 입력 모델

AI 대전에서는 `checkpoints/score_00200.zip`, `score_00500.zip`, `score_01000.zip`,
`score_05000.zip`을 각각 쉬움·보통·어려움·매우 어려움에 사용하고 `best.zip`을 지옥에 사용합니다.
쉬움과 보통은 기존 보드 관측 형식이며, 어려움부터 지옥까지는 아래의 후보 결과 관측을 사용합니다.
각 모델의 manifest에서 설정을 읽습니다.
체크포인트 숫자는 최초 도달 기준이며 점수 상한이 아닙니다. 학습 당시 180초 검증 평균은 각각 205·515·1,850·12,690·12,690점입니다.
5,000점과 10,000점 파일은 모두 75,000스텝에 저장되었으며, 파일의 SHA-256이 같아 실제 가중치도 같습니다.
지옥은 매우 어려움보다 배치 속도를 높여 구분합니다.
현재 Java AI 대전은 시간제한 없이 진행합니다. 한쪽이 게임오버된 뒤 남은 쪽이 점수를 앞서면
즉시 승리하며, 동점이거나 뒤지고 있으면 계속 진행합니다. 양쪽 모두 게임오버되면 최종 점수를 비교합니다.

쉬움과 보통 모델의 원본은 `AMPM_2:/home/workspace/samuel/sca/runs/ppo-target10000-seed11-20261004-181911/`입니다.
어려움과 매우 어려움 모델은 아래 지옥 모델과 같은 학습 실행의 `score_01000.zip`, `score_05000.zip`입니다.
모든 가중치는 전송 후 서버 원본과 SHA-256이 일치함을 확인했습니다.

- 가중치: `best.zip` (Stable-Baselines3 MaskablePPO 형식)
- 모델: `CandidateActorCriticPolicy`, 순수 RL, 75,000스텝
- 검증: 고정 시드 1,000,000–1,000,019의 20판 평균 12,690점
- 학습 당시 게임: 180초 제한, 같은 블록 순서의 점수 대결
- 원본 실행: `AMPM_2:/home/workspace/samuel/sca/runs/ppo-afterstate-2m-seed11-20261007-182618`
- 서버의 `best.zip`, `final.zip`, `score_10000.zip`은 같은 파일 해시입니다.

`best.manifest.json`에는 모델 버전·학습 설정·검증 결과가 있습니다.
학습·벤치마크·리플레이 코드와 중복 기록은 제외하고 추론에 필요한 패키지만 보관합니다.

## Python에서 사용

모델이 사용하는 사용자 정의 정책과 관측 코드는 `runtime/src/tetris_sca`에 함께 보관합니다.
필요한 의존성은 `runtime/pyproject.toml`에 정의되어 있습니다.

이 폴더를 기준으로 실행하는 예시입니다.

```sh
python -m pip install ./runtime
python - <<'PY'
from sb3_contrib import MaskablePPO
from tetris_sca.config import load_config
from tetris_sca.env import TetrisEnv

env = TetrisEnv(load_config('score_10000_afterstate.json'))
model = MaskablePPO.load('best.zip', env=env, device='cpu')
observation, _ = env.reset(seed=1000003)
action, _ = model.predict(observation, deterministic=True,
                          action_masks=env.action_masks())
print('AI가 선택한 배치:', int(action))
env.close()
PY
```

별도 환경에서 소스를 설치하지 않고 사용할 때는 `runtime/src`를 `PYTHONPATH`에 지정합니다.
입력은 현재 상태 `state=(248,)`와 후보 결과 `candidates=(40,7)`의 Dict이며,
추론 시 `action_masks`를 함께 전달해야 합니다. Java AI 대전은 `backend.ai.AiMatchClient`가
로컬 `backend/ai/match_service.py`를 실행하여 이 모델과 통신합니다.

대전에서는 `UntimedTetrisEngine`이 실제 블록 이동과 후보 계산의 시간 종료 조건을 제거합니다.
기존 관측 형식과 후보 이동 시간의 정규화는 유지하며 남은 시간 입력은 `1.0`으로 고정합니다.
원본 학습 엔진과 가중치는 변경하지 않았습니다. 위 검증 점수는 무제한 대전의 평가 결과가 아닙니다.

AI의 블록 배치 간격은 사용자의 최근 8개 배치 간격 중앙값을 기준으로 설정합니다.
사용자 간격을 0.5~6초 범위로 맞춘 뒤 일반 난이도는 85%, 지옥은 75%를 적용하고 50ms 단위로 올림합니다.
일반 난이도는 기본 1.7초, 목표 0.45~5.1초이며 지옥은 기본 1.5초, 목표 0.4~4.5초입니다.
새 블록의 반응 시간은 최소 300ms이며, 목표 배치 간격이 될 때까지 즉시 낙하를 보류합니다.
대기 중에도 자동 낙하는 진행하고, 블록이 고정되면 새 블록에서 다시 경로를 계산합니다.
