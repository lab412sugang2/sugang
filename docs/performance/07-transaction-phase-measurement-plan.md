# 수강신청 트랜잭션 단계별 측정 계획

- 작성일: 2026-08-23
- 상태: 측정 완료
- 원칙: 동작과 트랜잭션 범위는 변경하지 않는다.

## 문제

20 VU에서 동일 강의 집중 신청은 분산 신청보다 p95가 약 2.50배 증가하고 처리량이 약 43.5% 감소했다.

Hikari 지표에서는 connection 사용 평균이 약 2.49배, 획득 평균이 약 25.2배 증가했다. 그러나 이 결과만으로는 트랜잭션 내부의 어떤 SQL이 connection을 오래 점유했는지 직접 확인할 수 없다.

## 가설

동일 강의의 조건부 UPDATE가 row lock을 기다리면서 가장 많은 시간을 소비하고, 이후 `saveAndFlush()`와 commit이 끝날 때까지 lock을 보유할 것으로 예상한다.

예상 결과:

- C보다 D의 `conditional_update` 시간이 크게 증가한다.
- D의 트랜잭션 전체 시간도 함께 증가한다.
- `application_flush`는 증가하더라도 조건부 UPDATE보다 차이가 작다.
- 긴 트랜잭션 때문에 Hikari connection 사용 및 획득 대기가 증가한다.

대안 가설:

- `saveAndFlush()`의 INSERT 또는 unique index 처리가 더 오래 걸릴 수 있다.
- 조건부 UPDATE보다 사전 조회 SQL의 누적 비용이 클 수 있다.
- commit 또는 DB 네트워크 시간이 주요 비용일 수 있다.

## 측정 항목

Micrometer Timer를 다음 단계에 추가한다.

| Metric | Tag | 의미 |
| --- | --- | --- |
| `sugang.registration.transaction` | `outcome` | commit/rollback을 포함한 전체 트랜잭션 시간 |
| `sugang.registration.phase` | `course_lookup` | 강의 조회 |
| `sugang.registration.phase` | `duplicate_check` | 중복 신청 확인 |
| `sugang.registration.phase` | `applications_lookup` | 기존 신청 목록 조회 |
| `sugang.registration.phase` | `conditional_update` | 조건부 UPDATE와 row lock 대기 |
| `sugang.registration.phase` | `application_flush` | 신청 INSERT와 flush |

트랜잭션 Timer는 `applyCourse()` 진입 시 시작하고 Spring의 `afterCompletion`에서 종료해 commit 또는 rollback 완료까지 포함한다. Spring proxy가 트랜잭션을 시작한 뒤 서비스 메서드가 호출되므로 transaction 획득·시작 오버헤드는 포함하지 않는다.

## 검증 방법

1. 통합 테스트로 정상 신청 시 `committed` Timer가 증가하는지 확인한다.
2. 마감 강의 신청 실패 시 `rolled_back` Timer가 증가하는지 확인한다.
3. 조건부 UPDATE와 `saveAndFlush` Timer가 실제로 기록되는지 확인한다.
4. Render 배포 후 `/actuator/prometheus`에서 metric 노출을 확인한다.
5. C와 D를 동일한 20 VU 조건으로 다시 실행한다.
6. 트랜잭션, 조건부 UPDATE, `saveAndFlush`의 평균과 p95를 비교한다.

## 판단 기준

| 결과 | 해석 |
| --- | --- |
| D의 조건부 UPDATE만 급증 | 동일 row lock 대기가 직접 원인 |
| D의 `saveAndFlush`가 급증 | INSERT, index 또는 flush 비용 검토 |
| 모든 DB 단계가 비슷하게 증가 | DB 네트워크 또는 전반적인 DB 포화 검토 |
| 단계 합보다 전체 트랜잭션이 크게 김 | commit 또는 측정하지 않은 구간 검토 |

측정 결과를 확인하기 전에는 트랜잭션 범위 축소, `saveAndFlush` 변경, Hikari pool 증설을 적용하지 않는다.

## 측정 결과

2026-09-01에 C와 D를 20 VU로 각각 3회 실행했다. 순서 영향을 줄이기 위해 C→D, D→C, C→D 순으로 교차 실행했다.

- 전체 트랜잭션 p95 중앙값: C 709.97ms, D 1771.67ms, 약 2.50배
- 조건부 UPDATE p95 중앙값: C 140.13ms, D 1413.76ms, 약 10.09배
- `saveAndFlush()` p95 중앙값: C 107.70ms, D 108.18ms, 약 1.00배
- Hikari 획득 평균 중앙값: C 23.01ms, D 564.05ms, 약 24.5배
- C의 Hikari pending 중앙값은 0, D는 세 번 모두 4
- 6회 모두 HTTP 실패, Hikari timeout, DB 카운트 불일치 0건

가설과 같이 동일 강의 시나리오의 조건부 UPDATE 시간이 가장 크게 증가했다. `saveAndFlush()`는 C와 D가 거의 같아 hot-row 지연의 지배 구간이 아니었다. Hikari 대기는 조건부 UPDATE 대기로 connection 반환이 늦어진 후속 증상으로 해석했다.

자세한 결과와 표현상 한계는 `docs/performance/08-hot-row-phase-result.md`에 기록했다.
