# 테트리스 후보 배치 결과 입력 모델

AI 대전의 난이도별 모델은 다음과 같습니다. 각 모델의 manifest에서 설정을 읽습니다.

| 난이도 | 체크포인트 | 가중치 | 관측 형식 | 학습 당시 검증 평균 | 학습·검증 시간 제한 |
| --- | ---: | --- | --- | ---: | --- |
| 쉬움 | 300점 | `checkpoints/score_00300.zip` | 보드 | 305점 | 180초 |
| 보통 | 1,000점 | `checkpoints/score_01000.zip` | 후보 결과 | 1,850점 | 180초 |
| 어려움 | 5,000점 | `checkpoints/score_05000.zip` | 후보 결과 | 12,690점 | 180초 |
| 매우 어려움 | 50,000점 | `checkpoints/score_50000.zip` | 후보 결과 | 59,615점 | 없음 |
| 지옥 | 100,000점 | `checkpoints/score_100000.zip` | 후보 결과 | 104,295점 | 없음 |

체크포인트 숫자는 최초 도달 기준이며 점수 상한이 아닙니다. 검증 시간 조건이 달라 평균 점수를 직접 비교할 수는 없습니다.
매우 어려움과 지옥은 무제한 환경에서 각각 200,000스텝과 2,450,000스텝까지 학습한 서로 다른 가중치입니다.
지옥은 배치 속도도 더 빠릅니다.
현재 Java AI 대전은 시간제한 없이 진행합니다. 한쪽이 게임오버된 뒤 남은 쪽이 점수를 앞서면
즉시 승리하며, 동점이거나 뒤지고 있으면 계속 진행합니다. 양쪽 모두 게임오버되면 최종 점수를 비교합니다.

쉬움 모델의 원본은 `AMPM_2:/home/workspace/samuel/sca/runs/ppo-target10000-seed11-20261004-181911/`입니다.
보통·어려움은 `AMPM_2:/home/workspace/samuel/sca/runs/ppo-afterstate-2m-seed11-20261007-182618/`의 모델입니다.
매우 어려움과 지옥은 `AMPM_2:/home/workspace/samuel/sca/runs/ppo-afterstate-unlimited-target100000-20m-seed11-20261009-160707/`의 `score_50000.zip`, `score_100000.zip`입니다.
모든 가중치는 전송 후 서버 원본과 SHA-256이 일치함을 확인했습니다.

- 지옥 가중치: `checkpoints/score_100000.zip` (Stable-Baselines3 MaskablePPO 형식)
- 모델: `CandidateActorCriticPolicy`, 순수 RL, 2,450,000스텝
- 검증: 고정 시드 1,000,000–1,000,019의 20판 평균 104,295점
- 학습 당시 게임: 시간제한 없이 게임오버까지 진행 (`duration_ticks=0`)
- 무제한 엔진: `runtime/src/tetris_sca/untimed.py`, manifest의 해시와 대조
- `best.zip`은 보관 중인 기존 10,000점 모델이며 현재 난이도에 연결하지 않습니다.

각 `.manifest.json`에는 모델 버전·학습 설정·검증 결과가 있습니다.
학습·벤치마크·리플레이 코드와 중복 기록은 제외하고 추론에 필요한 패키지만 보관합니다.

## Python에서 사용

모델이 사용하는 사용자 정의 정책과 관측 코드는 `runtime/src/tetris_sca`에 함께 보관합니다.
필요한 의존성은 `runtime/pyproject.toml`에 정의되어 있습니다.

이 폴더를 기준으로 실행하는 예시입니다.

```sh
python -m pip install ./runtime
python - <<'PY'
from sb3_contrib import MaskablePPO
from tetris_sca.env import TetrisEnv
import json
from pathlib import Path

checkpoint = Path('checkpoints/score_100000.zip')
manifest = json.loads(checkpoint.with_suffix('.manifest.json').read_text())
env = TetrisEnv(manifest['config'])
model = MaskablePPO.load(checkpoint, env=env, device='cpu')
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
원본 학습 엔진과 가중치는 변경하지 않았습니다. 위 검증 점수는 사용자 속도에 맞춘 대전의 평가 결과가 아닙니다.

AI의 블록 배치 간격은 사용자의 최근 8개 배치 간격 중앙값을 기준으로 설정합니다.
사용자 간격을 0.5~6초 범위로 맞춘 뒤 일반 난이도는 80%, 지옥은 70%를 적용하고 50ms 단위로 올림합니다.
일반 난이도는 기본 1.6초, 목표 0.4~4.8초이며 지옥은 기본 1.4초, 목표 0.35~4.2초입니다.
새 블록의 반응 시간은 최소 300ms이며, 목표 배치 간격이 될 때까지 즉시 낙하를 보류합니다.
대기 중에도 자동 낙하는 진행하고, 블록이 고정되면 새 블록에서 다시 경로를 계산합니다.
