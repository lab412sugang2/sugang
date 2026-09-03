# MySQL row-level lock wait 직접 검증 결과

- 측정일: 2026-09-03
- 상태: 직접 관찰 완료
- Run ID: `local-same-course-lock-wait-clean-20260903-184005`
- 원본: `docs/performance/raw/after/lock-wait/local-same-course-lock-wait-clean-20260903-184005/`

## 검증 목적

Render-Railway A/B/C/D 실험과 트랜잭션 단계별 Timer에서 확인한 동일 강의 hot-row 지연이 실제 MySQL row lock 대기인지 검증했다. 애플리케이션 응답시간만 보고 추론하지 않고, 신청 부하가 발생하는 동안 MySQL `performance_schema.data_lock_waits`와 `data_locks`를 0.2초 간격으로 함께 조회했다.

## 범위와 조건

이번 결과는 로컬 Spring Boot와 로컬 Docker MySQL의 직접 검증이다. Render 애플리케이션과 Railway MySQL의 운영 성능 수치로 해석하지 않으며, 운영 환경의 p95와 직접 비교하지 않는다.

| 항목 | 조건 |
| --- | --- |
| 애플리케이션 | 로컬 Spring Boot, `http://127.0.0.1:18080` |
| 데이터베이스 | Docker MySQL `8.0.45`, `127.0.0.1:3307` |
| 격리 수준 | `REPEATABLE-READ` |
| 시나리오 | 하나의 `courses` row에 신청 집중 |
| 정원 | `100000` (정원 마감이 아닌 lock 경합 관찰 목적) |
| 부하 | 20 VU |
| Ramp / Hold | 30초 / 2분 30초 |
| lock wait 수집 | 0.2초 간격, 210초 |
| fixture 정리 | lock wait 수집 종료 후 실행 |

정리 요청을 부하와 겹치지 않게 분리했다. 이전 잘못된 실행에서는 fixture 삭제 트랜잭션이 신청 트랜잭션과 동시에 실행되어 별도의 deadlock을 만들었으므로, 해당 결과는 이 검증에서 제외했다.

## 결과

### 애플리케이션과 정합성

| 지표 | 결과 |
| --- | ---: |
| k6 종료 코드 | `0` |
| 신청 요청 | 3,205건 |
| 신청 성공 | 3,205건 |
| 신청 거절 | 0건 |
| HTTP 실패율 | 0% |
| HTTP p95 | 41.92ms |
| HTTP p99 | 52.56ms |
| 처리량 | 17.70 req/s |
| 최종 `applied_count` | 3,205 |
| 실제 신청 행 | 3,205 |
| 카운트 불일치 | 0건 |

### MySQL lock wait

| 지표 | 결과 |
| --- | ---: |
| 전체 수집 샘플 | 820개 |
| 대기 존재 샘플 | 133개 |
| 단일 샘플의 최대 대기 수 | 45건 |
| 상세 조회가 실행된 샘플 | 4개 |
| `courses` 상세 lock 행 | 13개 |
| 대상 테이블 | `sugang.courses` |
| 대상 인덱스 | `PRIMARY` |
| 대기 lock | `X,REC_NOT_GAP` |
| blocking lock | `X,REC_NOT_GAP` |
| lock data | `40` |

상세 행의 공통 형태는 다음과 같다.

```text
sugang.courses / PRIMARY / waiting X,REC_NOT_GAP / blocking X,REC_NOT_GAP / lock_data=40
```

이는 테스트 fixture의 강의 ID 40 row를 여러 트랜잭션이 동시에 갱신하면서, 한 트랜잭션이 보유한 배타적 레코드 잠금이 해제될 때까지 다른 트랜잭션이 기다린 것을 의미한다.

## 분석

조건부 UPDATE는 `applied_count < limit_count`를 UPDATE의 조건으로 함께 평가해 정원 초과를 막는다. 하지만 정합성을 위해 같은 `courses` row의 값을 변경하는 작업 자체가 lock-free로 병렬 실행되는 것은 아니다. InnoDB는 해당 row의 배타적 잠금을 순서대로 처리하므로, 인기 강의 하나에 요청이 몰리면 UPDATE 구간의 대기가 누적될 수 있다.

이번 실행에서는 lock wait가 실제로 관찰됐지만, 부하와 fixture 정리를 분리했기 때문에 deadlock이나 HTTP 실패는 발생하지 않았다. `SHOW ENGINE INNODB STATUS`에 남아 있던 과거 deadlock 기록은 이전 오염된 실행의 기록이며, 현재 실행의 근거로 사용하지 않았다.

따라서 다음 두 가지를 구분한다.

- 조건부 UPDATE의 역할: 정원 조건과 증가를 원자화해 정원 초과를 막는다.
- hot-row의 비용: 같은 강의 row를 갱신하는 트랜잭션은 MySQL row lock으로 직렬화될 수 있다.

중복 확인 SQL 1개 제거는 요청 경로를 단순화했지만 Render-Railway 재측정에서 D의 p95를 개선하지 못했다. 이번 직접 관찰은 그 결과가 이상한 것이 아니라, 지배 구간이 중복 조회가 아닌 동일 `courses` row의 UPDATE 경합이었기 때문이라는 해석을 뒷받침한다.

## 한계

- 로컬 MySQL에서의 lock wait 직접 관찰이므로 Render와 Railway의 운영 지연을 증명하지 않는다.
- `data_lock_waits`는 순간 상태 조회 결과라 누적 대기 시간이나 모든 요청의 대기 횟수를 의미하지 않는다.
- `SHOW ENGINE INNODB STATUS`의 deadlock 기록은 과거 기록이 남을 수 있으므로 실행 ID와 애플리케이션 로그를 함께 확인해야 한다.
- 이번 단계에서는 추가적인 speculative optimization을 적용하지 않았다. 이미 수행한 중복 조회 제거 Before/After 결과로 한 차례 개선 효과를 검증했고, hot-row 원인을 먼저 확정하는 것이 우선이었다.

## 결론

동일 강의 집중 신청에서 관찰한 지연은 애플리케이션 Timer만의 추론이 아니라 MySQL `courses` 단일 row의 `PRIMARY` 레코드 lock wait로 직접 확인됐다. 현재 조건부 UPDATE 구현은 20 VU 실험에서 정원·신청 행을 일치시키면서 정합성을 유지했다.

이번 단계의 결론은 “조건부 UPDATE가 병목을 해결했다”가 아니라, “조건부 UPDATE로 정합성을 확보한 뒤에도 인기 강의의 hot-row 직렬화 비용은 남으며, 그 대상을 MySQL 내부에서 확인했다”이다.

## 원본 파일

- `metadata.txt`: DB 버전, 격리 수준, 수집 조건
- `lock-wait-samples.csv`: 시점별 대기 수
- `lock-wait-details.tsv`: 대기·blocking transaction과 테이블·인덱스·lock mode
- `k6-summary.json`: k6 원본 요약
- `final-state.json`: 최종 강의 카운터와 실제 신청 행
- 수집기: `scripts/perf/collect_mysql_lock_waits.sh`
