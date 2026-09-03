# 동일 강의 Hot Row 트랜잭션 단계별 측정 결과

- 측정일: 2026-09-01
- 운영 환경: Render 애플리케이션 + Railway MySQL 9.4.0
- Batch ID: `20260901-202942`
- 상태: 개선 전 원인 구간 확인 완료

## 문제

20 VU에서 동일 강의 집중 신청은 여러 강의 분산 신청보다 응답시간이 길고 처리량이 낮았다. 이전 측정만으로는 조건부 UPDATE, 신청 내역 INSERT, commit 중 어느 구간이 지연을 지배하는지 구분할 수 없었다.

## 가설

동일 강의에 요청이 집중되면 모든 트랜잭션이 같은 `courses` row를 변경한다. 이때 조건부 UPDATE가 앞선 트랜잭션의 종료를 기다리면서 트랜잭션과 DB connection 점유 시간이 증가할 것으로 예상했다.

대안 가설은 다음과 같았다.

- 신청 내역 `saveAndFlush()`와 UNIQUE index 처리가 느릴 수 있다.
- Render CPU, JVM Heap, GC 또는 Tomcat thread가 먼저 포화될 수 있다.
- Hikari connection pool 크기 자체가 근본 원인일 수 있다.

## 실험 조건

| 항목 | 조건 |
| --- | --- |
| C | 20개 강의에 신청 분산 |
| D | 하나의 강의에 신청 집중 |
| 부하 | 20 VU |
| Ramp | 30초 |
| Hold | 2분 30초 |
| 반복 | C와 D 각각 3회 |
| 순서 | 1회 C→D, 2회 D→C, 3회 C→D |
| 실행 간격 | 60초 |
| 애플리케이션 코드 | C와 D 모두 동일한 `PlannerService.applyCourse()` 사용 |
| 통제 변수 | 요청 대상 강의 row 분포만 변경 |

실행 순서를 교차해 항상 나중에 실행한 시나리오가 불리해지는 편향을 줄였다. 결과 비교에는 이상치 영향을 줄이기 위해 3회 중앙값을 사용하고 범위를 함께 기록했다.

## 전체 결과

| 지표 | C: 분산 신청 중앙값 | D: 동일 강의 중앙값 | D/C 또는 변화 |
| --- | ---: | ---: | ---: |
| HTTP p95 | 1025.71ms | 2519.56ms | 2.46배 |
| HTTP p99 | 1117.61ms | 2581.04ms | 2.31배 |
| 처리량 | 9.29 req/s | 5.40 req/s | 41.9% 감소 |
| Hikari 획득 평균 | 23.01ms | 564.05ms | 24.5배 |
| Hikari 사용 평균 | 696.11ms | 1732.30ms | 2.49배 |
| Hikari pending 최대 | 0 | 4 | D는 3회 모두 4 |
| Hikari timeout | 0 | 0 | 동일 |
| HTTP 실패율 | 0% | 0% | 동일 |
| DB 카운트 불일치 | 0건 | 0건 | 동일 |

HTTP p95 범위는 C가 1014.12~1414.38ms, D가 2512.98~2635.60ms였다. D는 실행 순서와 관계없이 세 번 모두 C보다 느렸고 처리량도 낮았다.

## 트랜잭션 단계별 결과

각 Timer의 p95는 독립된 분포의 백분위 값이므로 서로 더하거나 빼지 않고 C와 D의 동일 단계만 비교했다.

전체 트랜잭션 Timer는 Spring proxy가 트랜잭션을 시작한 뒤 `applyCourse()`에 진입할 때 시작하고 `afterCompletion`에서 종료한다. 따라서 서비스 로직과 commit/rollback 완료까지는 포함하지만 transaction 획득·시작 오버헤드는 포함하지 않는다.

| 단계 p95 중앙값 | C: 분산 신청 | D: 동일 강의 | D/C |
| --- | ---: | ---: | ---: |
| 전체 트랜잭션 | 709.97ms | 1771.67ms | 2.50배 |
| 조건부 UPDATE | 140.13ms | 1413.76ms | 10.09배 |
| 신청 내역 flush | 107.70ms | 108.18ms | 1.00배 |

평균값 비교도 같은 방향이었다.

| 단계 평균 중앙값 | C: 분산 신청 | D: 동일 강의 | D/C |
| --- | ---: | ---: | ---: |
| 전체 트랜잭션 | 610.56ms | 1646.21ms | 2.70배 |
| 조건부 UPDATE | 93.36ms | 1125.47ms | 12.06배 |
| 신청 내역 flush | 86.15ms | 86.73ms | 1.01배 |

## 분석

가장 큰 차이가 발생한 단계는 조건부 UPDATE였다. 동일 강의 시나리오의 조건부 UPDATE p95는 분산 신청보다 약 10.1배 증가했지만 신청 내역 flush p95는 거의 같았다.

C와 D는 같은 코드와 SQL을 실행하고 요청 대상 row 분포만 다르다. 따라서 이 결과는 동일 `courses` row의 UPDATE 직렬화와 row lock 대기가 D의 지연을 지배한다는 해석을 강하게 지지한다.

Hikari pending 4와 connection 획득 평균 증가도 pool 크기 자체의 독립적인 문제라기보다, 긴 조건부 UPDATE 대기로 트랜잭션이 connection을 오래 점유한 결과로 해석했다. D가 C보다 CPU가 낮은 실행에서도 더 느렸으므로 CPU 포화도 이 차이의 근본 원인으로 보기 어렵다.

## 정합성

성능보다 먼저 데이터 정합성을 확인했다.

- 6회 모두 HTTP 실패율 0%
- 마감 거절 0%: fixture 정원을 충분히 크게 설정한 성능 실험
- Hikari timeout 0건
- 모든 실행에서 `applied_count`와 실제 신청 행 일치
- 카운트 불일치 0건

조건부 UPDATE는 동일 row 경합 비용이 있지만 실험 중 정합성은 유지했다.

## 결론

이번 실험에서 확인한 지배 구간은 `saveAndFlush()`나 CPU가 아니라 동일 강의 row에 대한 조건부 UPDATE다. Hikari pool 대기는 원인이 아니라 긴 트랜잭션과 connection 점유의 후속 증상으로 판단했다.

후속 로컬 검증에서는 MySQL `performance_schema.data_lock_waits`와 `data_locks`를 신청 부하와 동시에 수집했다. 동일 `courses` row의 `PRIMARY` 인덱스에서 `X,REC_NOT_GAP` 대기가 실제로 관찰됐으며, 상세 결과는 `docs/performance/11-mysql-lock-wait-verification.md`에 기록했다.

## 다음 단계

1. 현재 결과를 개선 전 기준선으로 고정한다.
2. 같은 row 쓰기는 정합성을 위해 직렬화될 수밖에 있다는 제약을 명시한다.
3. 중복 확인 SQL 1개 제거 통제 실험을 완료했으며, SQL 수는 줄었지만 종단간 성능 개선은 확인하지 못했다.
4. 따라서 현재 지배 구간은 중복 조회가 아니라 동일 row UPDATE 직렬화로 유지된다.
5. 다음 변경도 하나만 선택하고 같은 C/D 20 VU 조건으로 3회 재측정한다.
6. 로컬 MySQL에서 `performance_schema` lock wait를 직접 수집해 동일 row 경합 해석을 검증했다.

## 원본 근거

- 통합 요약: `docs/performance/raw/baseline/bottleneck-isolation/20260901-202942/combined-summary.csv`
- 단계별 요약: `docs/performance/raw/baseline/bottleneck-isolation/20260901-202942/combined-phases.csv`
- 실행 순서: `docs/performance/raw/baseline/bottleneck-isolation/20260901-202942/run-order.csv`
- 회차별 k6·DB 결과: `docs/performance/raw/baseline/bottleneck-isolation/20260901-202942/raw/`
- 반복 실행 스크립트: `scripts/perf/run_cd_repeated.sh`
- 중복 확인 SQL 제거 After 결과: `docs/performance/10-redundant-duplicate-query-improvement-result.md`
- MySQL lock wait 직접 검증: `docs/performance/11-mysql-lock-wait-verification.md`
