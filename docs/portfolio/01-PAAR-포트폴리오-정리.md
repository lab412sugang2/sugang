# PAAR 포트폴리오 정리

- 갱신일: 2026-09-03
- 작성 원칙: 실제 실험으로 확인한 숫자만 사용하고 가설, 분석, 개선 결과를 구분한다.
- 설명 구조: `Problem -> Action -> Achievement -> Reflection`
- 기술 선택 구조: `Why -> Alternatives -> Trade-off -> Verification`

## 프로젝트 한눈에 보기

- 서비스: 비공식 단국대 수강신청 연습 서비스
- 운영 환경: Render + Railway MySQL 9.4.0
- 백엔드: Java 17, Spring Boot 3.2.5, Spring MVC, Spring Data JPA, Hibernate
- 화면: Thymeleaf
- 측정: k6, Actuator, Micrometer, Prometheus, Grafana
- 핵심 경험: 동시성 정합성 문제 재현과 해결, 경로 분리 기반 병목 분석

## PAAR 1. 동시 신청 Race Condition

### Problem

기존 신청 경로는 강의를 조회하고 애플리케이션에서 정원을 검사한 뒤 신청 내역을 저장했다. 각 요청이 `@Transactional`이어도 여러 트랜잭션이 같은 `applied_count`를 동시에 읽으면 정원 검사를 함께 통과할 수 있었다.

정원 10명에 서로 다른 사용자 100명의 동시 요청을 보낸 결과:

- 성공 13건
- 실제 신청 행 13건
- `applied_count` 12
- MySQL transaction rollback 계열 예외 60건

정원 초과와 카운터 불일치를 실제로 재현했다.

### Action

정원 조건 확인과 증가를 하나의 조건부 UPDATE로 묶었다.

```sql
UPDATE courses
SET applied_count = applied_count + 1
WHERE id = ?
  AND applied_count < limit_count
  AND canceled = false;
```

실제 신청 흐름:

```text
강의·폐강·중복·학점·시간표 검증
-> 조건부 UPDATE
-> 변경 행 0개: 마감 거절
-> 변경 행 1개: 신청 내역 INSERT
-> 모두 성공: COMMIT
-> 후속 실패: 전체 ROLLBACK
```

- JPQL 벌크 UPDATE 뒤 영속성 컨텍스트 불일치를 막기 위해 `clearAutomatically = true`를 적용했다.
- 중복 신청은 사전 조회로 빠르게 거절하되, 최종 방어선은 `(student_id, course_id)` UNIQUE 제약으로 유지했다.

### Achievement

같은 조건에서 다음 결과를 확인했다.

- 성공 10건
- 마감 거절 90건
- 기타 실패 0건
- 실제 신청 행 10건
- `applied_count` 10
- 정원 초과 0건

추가로 검증했다.

- 동일 사용자의 동시 신청 2건: 성공 1건, 중복 거절 1건
- UPDATE 후 강제 예외: `applied_count` 0, 신청 행 0

개선 전 185ms와 개선 후 174ms는 실패 결과의 종류가 다르므로 성능 개선률로 사용하지 않는다. 이 실험의 성과는 정합성이다.

좌석 선점 핵심만 분리한 로컬 MySQL 비교에서는 세 전략을 각각 3회 반복했다.

| 전략 | 전체 소요 중앙값 | p95 중앙값 | 처리량 중앙값 | 충돌·재시도 중앙값 |
| --- | ---: | ---: | ---: | ---: |
| 비관적 락 | 79.83ms | 72.58ms | 1252.58 req/s | 0회 |
| 낙관적 락 | 196.75ms | 185.97ms | 508.25 req/s | 309회 |
| 조건부 UPDATE | 63.06ms | 57.65ms | 1585.90 req/s | 0회 |

9회 모두 성공 10건·마감 거절 90건으로 정합성을 지켰다. 따라서 현재 선택은 정합성만 보장하는 임의의 방식이 아니라, 고충돌·단순 정원 규칙에서 재시도와 DB 작업이 가장 적었던 방식이라는 근거를 갖는다.

### Reflection

`@Transactional`은 요청 한 건의 원자적 commit/rollback을 제공하지만, 애플리케이션의 `조회 -> 검사 -> 저장`을 동시 요청 사이에서 자동으로 직렬화하지 않는다. 이 문제에서는 DB가 조건 검사와 상태 변경을 하나의 원자적 SQL로 수행하게 만드는 것이 명확했다.

### Why / Alternatives / Trade-off / Verification

- Why: `실제 신청 수 <= 정원`이라는 불변식을 지키기 위해 변경했다.
- Alternatives: 비관적 락, 낙관적 락, 조건부 UPDATE를 같은 조건으로 각각 3회 비교했다.
- Choice: 현재 규칙은 단순한 숫자 조건으로 표현할 수 있고, 조건부 UPDATE가 전체 소요시간 중앙값 63.06ms와 재시도 0회를 기록해 선택했다.
- Trade-off: 복잡한 규칙에는 적용하기 어렵고 벌크 UPDATE 이후 영속성 컨텍스트를 관리해야 한다.
- Verification: 전략별 100개 동시 요청 3회, 중복 신청, 강제 Rollback 테스트로 검증했다.

## PAAR 2. 혼합 트래픽을 경로별로 분리

### Problem

과거 Render + H2 혼합 트래픽에서 500 VU부터 p95 약 9초와 CPU 100%를 관찰했다. 하지만 홈, 조회, 신청이 섞여 있어 CPU 상승의 직접 원인을 알 수 없었다. 현재 운영 DB도 Railway MySQL로 변경됐기 때문에 H2 수치를 운영 MySQL 성능으로 사용할 수 없었다.

### Action

환경을 Render + Railway MySQL로 다시 고정하고 경로를 분리했다.

| 시나리오 | 처리 경로 | 분리 대상 |
| --- | --- | --- |
| A | DB 없는 ping | Render, JVM, Tomcat, 기본 네트워크 |
| B | 강의 단건 조회 | DB 연결, SQL, DB 네트워크 |
| C | 여러 강의 분산 신청 | 쓰기와 트랜잭션 |
| D | 동일 강의 집중 신청 | 동일 row lock 경합 |

k6 응답시간·처리량과 CPU, Heap, GC, Tomcat, Hikari 지표를 같은 시간축에서 수집했다.

### Achievement

A는 100 VU까지 안정적이었다.

- p95 220.48ms
- 78.13 req/s
- 실패율 0%
- Hikari pending 0

B는 40 VU에서 포화 신호가 나타났다.

- p95 1273.15ms
- 17.04 req/s
- Hikari active/max 100%
- Hikari pending 10
- connection 획득 평균 472.18ms
- Process CPU 7.60%
- Heap 56.38%
- Tomcat busy/max 10.50%

DB가 없는 경로는 안정적이지만 DB 조회가 포함되면 커넥션 획득 대기가 증가했다. 최초 병목 범위를 CPU·메모리에서 DB 접근 이후로 좁혔다.

### Reflection

Hikari active 100%와 pending 증가는 근본 원인이 아니라 느린 SQL, 네트워크, JPA 처리, DB 자원으로 커넥션 반환이 늦어진 결과일 수 있다. 따라서 pool을 바로 늘리지 않고 약 544ms의 connection 사용 시간을 더 분리하기로 했다.

### Why / Alternatives / Trade-off / Verification

- Why: 혼합 트래픽 한 번으로는 어느 처리 경로가 느린지 구분할 수 없었다.
- Alternatives: pool 증설, 서버 증설, 캐시 도입을 바로 적용할 수 있었다.
- Choice: 개선 전에 A/B/C/D 경로와 세부 지표를 분리했다.
- Trade-off: 해결까지 시간이 더 걸리지만 추측성 최적화를 피할 수 있다.
- Verification: 동일 Render-Railway 환경에서 VU별 p95, 처리량, Hikari, JVM 지표를 함께 비교했다.

## PAAR 3. 분산 쓰기와 동일 강의 hot row

### Problem

DB 쓰기가 느린 것과 모든 사용자가 같은 강의를 갱신해 느린 것은 서로 다른 문제다. 실제 수강신청처럼 특정 인기 강의에 요청이 집중될 때 추가 비용이 생기는지 확인해야 했다.

### Action

실제 `PlannerService.applyCourse()`를 그대로 사용하면서 요청 대상만 변경했다.

- C: 20개 강의 row에 신청 분산
- D: 하나의 강의 row에 신청 집중
- Ramp 30초, Hold 2분 30초
- 10 VU와 20 VU 실행
- 종료 후 `applied_count`와 실제 신청 행 비교

### Achievement

10 VU에서는 C와 D의 차이가 작았다. 20 VU에서는 C와 D를 각각 3회 실행하고 순서를 교차했다. 아래 값은 3회 중앙값이다.

| 항목 | C: 분산 신청 | D: 동일 강의 집중 | 변화 |
| --- | ---: | ---: | ---: |
| HTTP p95 | 1025.71ms | 2519.56ms | 2.46배 |
| HTTP p99 | 1117.61ms | 2581.04ms | 2.31배 |
| 처리량 | 9.29 req/s | 5.40 req/s | 41.9% 감소 |
| 전체 트랜잭션 p95 | 709.97ms | 1771.67ms | 2.50배 |
| 조건부 UPDATE p95 | 140.13ms | 1413.76ms | 10.09배 |
| 신청 내역 flush p95 | 107.70ms | 108.18ms | 1.00배 |
| connection 획득 평균 | 23.01ms | 564.05ms | 24.5배 |
| connection 사용 평균 | 696.11ms | 1732.30ms | 2.49배 |
| Hikari pending 최대 중앙값 | 0 | 4 | D는 3회 모두 4 |
| HTTP 실패율 | 0% | 0% | 동일 |
| 카운트 불일치 | 0 | 0 | 동일 |

D는 실행 순서와 관계없이 세 번 모두 C보다 느렸다. 조건부 UPDATE의 차이는 컸지만 `saveAndFlush()`는 거의 같았다.

### Reflection

조건부 UPDATE는 정합성을 보장하지만 동일 row 변경을 병렬화하지는 못한다. 요청 대상 row 분포만 변경한 통제 실험에서 조건부 UPDATE p95가 약 10.1배 증가했고 flush는 동일했다. 동일 강의 UPDATE의 row lock 대기로 트랜잭션과 connection 점유가 길어지고, Hikari pending은 그 결과로 나타난 것으로 분석했다.

Render-Railway 비교 실험 당시에는 MySQL lock wait 행을 직접 수집하지 않았으므로, 그 결과만으로 직접 관찰했다고 과장하지 않았다. 이후 동일 강의 로컬 부하와 MySQL `performance_schema`를 함께 실행해 `sugang.courses`의 `PRIMARY` row에서 `X,REC_NOT_GAP` lock wait를 직접 확인했다. 로컬 검증의 조건과 한계는 `docs/performance/11-mysql-lock-wait-verification.md`에 분리했다.

추가로 중복 확인 `EXISTS` SQL을 제거하고 학생 신청 목록을 재사용하는 최소 변경을 적용했다. 중복 경로의 prepared statement 수가 3개에서 2개로 줄어든 것은 테스트로 검증했지만, Render + Railway MySQL에서 C/D를 20 VU로 각각 3회 재측정한 결과 종단간 성능 개선은 확인하지 못했다.

- C HTTP p95 중앙값: 기준선 1025.71ms -> 개선 후 952.90ms
- D HTTP p95 중앙값: 기준선 2519.56ms -> 개선 후 2673.16ms
- D 조건부 UPDATE p95 중앙값: 기준선 1413.76ms -> 개선 후 1416.92ms
- 6회 모두 HTTP 실패율, Hikari timeout, DB count mismatch: 0

C의 수치 차이는 실행 범위가 겹치고 D의 지배 구간은 그대로였으므로, 이를 성능 개선으로 주장하지 않는다. SQL 정리는 유지하되 hot-row 직렬화가 다음 분석 대상이라는 결론을 기록했다. 상세 결과는 `docs/performance/10-redundant-duplicate-query-improvement-result.md`에 있다.

### Why / Alternatives / Trade-off / Verification

- Why: 일반 DB 쓰기 비용과 인기 강의 hot-row 경합을 분리하기 위해 실행했다.
- Alternatives: MySQL lock wait만 보거나 분산 신청만 측정할 수 있었다.
- Choice: 코드와 부하는 동일하게 두고 요청 대상 row만 통제 변수로 변경했다.
- Trade-off: 정합성을 위한 row 변경 직렬화 비용을 받아들여야 한다.
- Verification: C/D의 지연, 처리량, connection 사용·대기, 최종 DB 정합성을 비교하고, 로컬 동일 강의 부하에서 MySQL row lock wait를 직접 수집했다.

## PAAR 4. 배포 환경 검증과 기준선 재설정

### Problem

Render 배포가 성공했지만 `/actuator/health`의 DB는 MySQL이 아니라 H2였다. 이 상태에서는 재시작 시 데이터가 사라지고 H2 성능 결과를 운영 MySQL 결과로 오해할 수 있었다.

### Action

- Railway Hobby에 MySQL을 생성했다.
- Render에 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 설정했다.
- 재배포 뒤 health, 로그인, 홈, 강의 조회를 확인했다.
- DB 버전, 격리 수준, 데이터 수, Hikari 유휴 기준선을 기록했다.

### Achievement

- 애플리케이션과 MySQL 모두 `UP`
- Railway MySQL 9.4.0 연결
- 격리 수준 `REPEATABLE-READ`
- Hikari max 10, active 0, idle 10, pending 0 기준선 기록
- 신청, 중복 거절, 취소 스모크 테스트 뒤 데이터 원복

### Reflection

성능 실험에서는 코드뿐 아니라 DB 엔진, 배포 위치, 데이터와 설정을 검증해야 한다. 기존 H2 결과를 버리지 않되 운영 MySQL 결과와 분리해 예비 실험으로만 사용했다.

## 이력서용 압축 문장

> 정원 10명에 100개 동시 요청을 보내 기존 조회 후 저장 구조에서 13건이 등록되는 Race Condition과 카운터 불일치를 재현하고, 조건부 UPDATE로 정원 확인·증가를 원자화해 성공 10건, 마감 거절 90건, 정원 초과 0건으로 개선했습니다.

> 동일 사용자 중복 신청을 UNIQUE 제약으로 최종 방어하고, 조건부 UPDATE 이후 강제 예외 테스트에서 신청 카운터와 신청 행이 함께 Rollback되는 것을 검증했습니다.

> 비관적 락·낙관적 락·조건부 UPDATE를 정원 10명과 동시 요청 100개의 동일 조건에서 각각 3회 비교했습니다. 세 방식 모두 정합성을 지켰지만, 낙관적 락은 충돌 재시도가 중앙값 309회 발생했고 조건부 UPDATE는 전체 소요시간 중앙값 63.06ms로 가장 짧아 현재 규칙에 적합하다고 판단했습니다.

> Render-Railway 환경에서 DB 미사용·조회·분산 쓰기·동일 row 쓰기로 부하 경로를 분리해 단건 조회 40 VU에서 p95 1.27초와 Hikari pending 10을 관찰하고 최초 포화 범위를 DB 접근 이후로 좁혔습니다.

> Render-Railway 환경에서 C/D를 20 VU로 각각 3회 반복한 결과, 동일 강의 집중 신청은 분산 신청보다 p95 중앙값이 2.46배 높고 처리량이 41.9% 낮았습니다. 단계별 Timer에서 조건부 UPDATE p95가 10.09배 증가한 반면 신청 내역 flush는 동일해, 정합성을 유지하는 동일 row UPDATE 직렬화가 지배 구간임을 확인했습니다.

## 현재 진행 중인 검증

트랜잭션 단계별 Timer와 중복 확인 SQL 제거 통제 실험은 완료했다. SQL 1개를 줄였지만 동일 강의 hot-row 지연은 그대로였고, 후속 로컬 MySQL `performance_schema` 수집으로 `courses` 단일 row의 실제 lock wait를 확인했다. 현재는 원인 검증까지 완료한 상태이며, `saveAndFlush()`는 C와 D에서 거의 같았으므로 근거 없이 제거하지 않는다. Hikari pool 증설·캐시·Redis도 먼저 적용하지 않는다.

## 면접 질문과 답변 핵심

### `@Transactional`인데 왜 Race Condition이 발생했나요?

트랜잭션은 요청 한 건의 원자성을 보장하지만 여러 트랜잭션이 같은 값을 읽고 애플리케이션에서 검사하는 과정을 자동으로 직렬화하지 않는다. 조건 검사와 상태 변경을 하나의 조건부 UPDATE로 만들었다.

### 왜 비관적 락이나 낙관적 락 대신 조건부 UPDATE인가요?

현재 정원 규칙은 `applied_count < limit_count`라는 단순 조건으로 표현할 수 있다. UPDATE 결과 0/1로 마감과 성공을 판단할 수 있고 별도 충돌 재시도가 필요하지 않았다. 복잡한 규칙에는 적합하지 않고 영속성 컨텍스트 관리가 필요하다는 비용도 있다.

동일 조건을 3회 반복한 결과 세 전략 모두 정원 10명을 지켰지만, 전체 소요시간 중앙값은 조건부 UPDATE 63.06ms, 비관적 락 79.83ms, 낙관적 락 196.75ms였다. 낙관적 락은 충돌·재시도가 중앙값 309회였다. 다만 이는 고충돌·단순 정원 규칙을 로컬에서 격리한 결과이며 일반적인 우열로 주장하지 않는다.

### Hikari pending이 늘었는데 왜 pool을 바로 늘리지 않았나요?

pending은 커넥션 반환이 늦다는 증상이다. SQL, 네트워크, JPA, lock 대기가 원인이라면 pool 증설은 느린 작업을 DB에 더 많이 밀어 넣을 수 있다. 내부 시간을 먼저 측정하고 있다.

### 조건부 UPDATE가 병목 아닌가요?

분산 row에서는 Hikari pending 중앙값이 0이었지만 동일 row에서는 세 번 모두 4였다. 조건부 UPDATE p95는 140.13ms에서 1413.76ms로 약 10.1배 증가했고 `saveAndFlush()`는 거의 같았다. SQL 방식 자체보다 동일 row 경합이 지배 구간이라는 해석을 통제 실험으로 확인했다.

## 표현 주의

- 500 VU를 500명의 안정적 처리로 표현하지 않는다.
- 과거 H2 혼합 트래픽과 현재 Railway MySQL 결과를 합치지 않는다.
- Hikari pending을 pool 크기 자체의 문제로 단정하지 않는다.
- 조건부 UPDATE의 성능 우위를 일반화하지 않는다.
- 중복 확인 SQL 제거만으로 병목을 해결했다고 쓰지 않는다.
- 로컬 MySQL lock wait 직접 관찰 결과를 Render-Railway 운영 수치로 확장해 쓰지 않는다.
