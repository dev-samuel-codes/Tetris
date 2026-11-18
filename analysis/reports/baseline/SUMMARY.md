# 개선 전 분석 결과

- 기준 소스 커밋: `55d4893d3cc318c8ebe5369a1ceb210d064afcd7`
- 대상: `src` 아래 Java 파일 40개. 테스트와 Python 코드는 제외.
- 소스 수정 없이 JDK 26.0.1에서 `--release 17`로 분석용 빌드 성공.
- CK 0.7.0: 내부·익명 클래스를 포함한 클래스 행 121개 측정.
- SpotBugs 4.10.5: 전체 경고 129건, High priority 3건.
- SpotBugs 분석 오류 0건, 누락 클래스 0건.

## 우선 검토할 High priority 경고

| 파일 | 줄 | 규칙 | 내용 |
| --- | --- | --- | --- |
| src/frontend/Shape.java | 77 | DMI_RANDOM_USED_ONLY_ONCE | Random 객체를 생성한 뒤 한 번만 사용 |
| src/frontend/Shape.java | 77 | RV_ABSOLUTE_VALUE_OF_RANDOM_INT | 난수의 절댓값 계산에서 음수 최솟값 처리 문제 가능성 |
| src/frontend/network/ConnectionTestClient.java | 27 | DM_DEFAULT_ENCODING | 출력 문자 인코딩을 기본 환경에 의존 |

High priority와 bug rank는 다른 기준이다. 과제의 '심각 수준'에 대한
구체적 매핑은 명시되어 있지 않으므로 전체 결과의 rank도 함께 검토한다.
아직 경고를 수정하거나 억제하지 않았다. 실행 화면을 통한 기능 확인은 별도다.

지표 정의와 분석 범위는 `../../README.md`를 참고한다.
보고서용 지표는 WMC(복잡도), CBO, RFC, LCOM, LOC, DIT 6개를 선정했다.
CK는 Metrics Reloaded의 대체 도구이며, 수치가 동일하다고 가정하지 않는다.
