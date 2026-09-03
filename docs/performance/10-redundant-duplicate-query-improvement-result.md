# 중복 확인 SQL 제거 개선 실험 결과

- 측정일: 2026-09-03
- 상태: 측정 완료, 유의미한 종단간 성능 개선은 확인하지 못함
- 개선 커밋: `7d890df`
- 기준선: `docs/performance/08-hot-row-phase-result.md`
- After 원본: `docs/performance/raw/after/redundant-duplicate-query/20260903-1350-duplicate-query-after-stable/`

## 문제와 변경

기존 신청 경로는 같은 학생의 중복 신청 여부를 `EXISTS` 쿼리로 확인한 뒤, 학점과 시간표 검증을 위해 같은 학생의 신청 목록을 다시 조회했다.

신청 목록에 대상 강의 정보가 이미 포함되므로, After 버전에서는 목록을 한 번 조회한 뒤 그 결과에서 중복 여부를 검사하도록 변경했다. 따라서 중복 신청 경로의 불필요한 조회 SQL을 1개 줄였다. `(student_id, course_id)` UNIQUE 제약은 동시 요청의 최종 방어선으로 그대로 유지했다.

## 가설

SQL 1개를 줄이면 중복 신청 경로의 DB 작업과 트랜잭션 시간이 감소하고, Hikari connection 사용 시간과 HTTP p95도 개선될 수 있다고 예상했다.

반면 D 시나리오에서는 모든 요청이 같은 `courses` row를 UPDATE하므로, 동일 row 직렬화가 계속 지연을 지배한다면 이 변경만으로는 종단간 성능이 크게 좋아지지 않을 수 있다고 함께 가정했다.

## 검증 조건

| 항목 | 조건 |
| --- | --- |
| 환경 | Render 애플리케이션 + Railway MySQL 9.4.0 |
| C | 20개 강의에 신청 분산 |
| D | 하나의 강의에 신청 집중 |
| 부하 | 20 VU |
| Ramp | 30초 |
| Hold | 2분 30초 |
| 관찰 | 종료 후 1분 |
| 반복 | C와 D 각각 3회 |
| 실행 순서 | C→D / D→C / C→D |
| 실행 간격 | 60초 cooldown |

코드, DB, 데이터 fixture, Hikari 설정, VU, 부하 시간은 기준선과 동일하게 유지했다. 각 실행에서 k6, Prometheus, DB 최종 카운트를 함께 수집했다.

## 기준선 대비 결과

아래 값은 각 시나리오 3회 중앙값이다.

| 지표 | C 기준선 | C 개선 후 | D 기준선 | D 개선 후 |
| --- | ---: | ---: | ---: | ---: |
| HTTP p95 | 1025.71ms | 952.90ms | 2519.56ms | 2673.16ms |
| HTTP p99 | 1117.61ms | 1056.71ms | 2581.04ms | 2832.05ms |
| 처리량 | 9.29 req/s | 9.64 req/s | 5.40 req/s | 5.44 req/s |
| 전체 트랜잭션 평균 | 610.56ms | 532.01ms | 1646.21ms | 1652.83ms |
| 전체 트랜잭션 p95 | 709.97ms | 623.11ms | 1771.67ms | 1786.26ms |
| 조건부 UPDATE 평균 | 93.36ms | 94.06ms | 1125.47ms | 1216.27ms |
| 조건부 UPDATE p95 | 140.13ms | 147.30ms | 1413.76ms | 1416.92ms |
| 신청 내역 flush 평균 | 86.15ms | 87.52ms | 86.73ms | 87.61ms |
| 신청 내역 flush p95 | 107.70ms | 110.37ms | 108.18ms | 110.69ms |

C의 HTTP p95 중앙값은 1025.71ms에서 952.90ms로 낮아졌지만 기준선 범위와 개선 후 범위가 겹친다. D의 HTTP p95는 2519.56ms에서 2673.16ms로 오히려 높아졌고, 처리량은 5.40 req/s와 5.44 req/s로 거의 같았다. 이 차이는 실행 변동 범위 안에 있으므로 성능 개선 또는 성능 악화로 단정하지 않는다.

실행별 범위도 함께 확인했다.

- C HTTP p95: 기준선 `1014.12~1414.38ms`, 개선 후 `937.67~1410.09ms`
- D HTTP p95: 기준선 `2512.98~2635.60ms`, 개선 후 `2460.92~2684.34ms`
- D 조건부 UPDATE p95: 기준선 `1411.76~1413.76ms`, 개선 후 `1413.76~1419.20ms`

## 정합성과 오류

유효한 6회 모두 기능 결과와 DB 결과가 일치했다.

- k6 종료 코드: 6회 모두 `0`
- HTTP 실패율: 6회 모두 `0%`
- 마감 거절과 기타 오류: `0건` (성능 fixture는 정원 초과가 발생하지 않도록 구성)
- Hikari timeout: `0건`
- `applied_count`와 실제 신청 행: 6회 모두 일치
- count mismatch: `0건`

첫 시도는 테스트 중 Render 재배포가 겹쳐 관찰 구간의 DB 카운트가 일치하지 않았으므로 유효 비교에서 제외했다. 이는 코드 성능 결과가 아니라 실험 환경 통제 실패로 분류했다.

## 분석

중복 신청 경로의 SQL 수가 줄어든 사실은 `PlannerServiceQueryOptimizationTest`에서 중복 경로의 prepared statement 수가 2개인지 검증해 확인했다. 그러나 Render-Railway 종단간 실험에서는 다음 결과가 나왔다.

1. C는 소폭 낮아졌지만 반복 범위가 겹쳐 유의미한 개선으로 보기 어렵다.
2. D의 조건부 UPDATE p95와 전체 트랜잭션 p95는 개선 전후가 사실상 동일하다.
3. D의 신청 내역 flush p95도 계속 약 110ms로 동일하다.
4. 따라서 SQL 1개 제거는 유효한 코드 정리이지만, 이번 부하에서 지배 구간을 제거하지 못했다.

D에서 지연을 지배하는 것은 중복 확인 SQL이 아니라 동일 `courses` row를 갱신할 때 발생하는 직렬화 비용으로 해석한다. Hikari pending 최대 4도 이 긴 트랜잭션과 connection 점유의 후속 증상으로 보는 것이 타당하다.

이번 실험은 단계 Timer와 통제 변수 비교를 통해 hot-row 해석을 지지하지만, MySQL `performance_schema`의 lock wait 행을 동시에 직접 수집한 것은 아니다. 따라서 “row lock을 직접 관찰했다”가 아니라 “동일 row UPDATE의 대기 구간이 지배적이라는 근거를 확보했다”고 표현한다.

## 결론

“중복 확인 SQL 1개를 제거하면 C와 D의 종단간 성능이 유의미하게 좋아질 것”이라는 가설은 기각한다. 변경 자체는 요청당 불필요한 조회를 줄이는 낮은 위험의 개선이므로 유지하지만, 처리 가능한 동시 사용자 수가 늘었다고 주장할 근거는 없다.

현재 다음 성능 실험의 대상은 중복 조회가 아니라 동일 강의 hot row다. 다음 변경도 한 번에 하나만 적용하고, 같은 C/D 20 VU 3회 조건으로 검증해야 한다. Redis, Kafka, Hikari 증설을 근거 없이 먼저 적용하지 않는다.

## 원본 근거

- 통합 결과: `docs/performance/raw/after/redundant-duplicate-query/20260903-1350-duplicate-query-after-stable/combined-summary.csv`
- 단계 결과: `docs/performance/raw/after/redundant-duplicate-query/20260903-1350-duplicate-query-after-stable/combined-phases.csv`
- 실행 순서: `docs/performance/raw/after/redundant-duplicate-query/20260903-1350-duplicate-query-after-stable/run-order.csv`
- 반복 측정 요약: `docs/performance/raw/after/redundant-duplicate-query/20260903-1350-duplicate-query-after-stable/반복-측정-요약.md`
- 개선 코드: `src/main/java/sugang/service/PlannerService.java`
- SQL 실행 수 검증: `src/test/java/sugang/service/PlannerServiceQueryOptimizationTest.java`
