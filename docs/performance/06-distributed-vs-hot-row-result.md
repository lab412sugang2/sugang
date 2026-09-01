# 분산 신청과 동일 강의 집중 신청 비교 결과

- 작성일: 2026-08-23
- 애플리케이션: Render
- 데이터베이스: Railway MySQL 9.4.0
- 격리 수준: `REPEATABLE-READ`
- HikariCP 최대 연결 수: 10
- 개선 적용 여부: 없음

## 실험 목적

단순 DB 조회 실험에서 커넥션 대기 병목을 확인했다. 이번 실험은 같은 수강신청 로직을 사용하면서 요청 대상만 바꿔 다음 질문을 검증한다.

```text
여러 강의에 요청이 분산될 때와 하나의 강의에 요청이 집중될 때 성능 차이가 발생하는가?
```

## 비교 조건

| 구분 | 요청 대상 | 나머지 처리 흐름 |
| --- | --- | --- |
| C: 분산 신청 | 20개 테스트 강의에 분산 | 동일 |
| D: 집중 신청 | 테스트 강의 1개에 집중 | 동일 |

두 시나리오는 모두 실제 `PlannerService.applyCourse()`를 호출한다.

공통 처리 흐름:

1. 강의 조회
2. 중복 신청 조회
3. 학생의 기존 신청 목록 조회
4. 학점 및 시간표 검증
5. 조건부 UPDATE로 정원 증가
6. 신청 내역 INSERT 및 flush
7. 트랜잭션 commit

조건부 UPDATE:

```sql
UPDATE courses
SET applied_count = applied_count + 1
WHERE id = ?
  AND applied_count < limit_count
  AND canceled = false;
```

## 공통 실행 조건

- Ramp: 30초
- Hold: 2분 30초
- 관찰 구간: 각 단계 마지막 1분
- VU: 10, 20
- 성공 응답만 p95와 p99에 포함
- HTTP 실패율과 애플리케이션 거부율 별도 측정
- 테스트 종료 후 `applied_count`와 실제 신청 행 수 비교
- 테스트 fixture 자동 삭제

## 10 VU 결과

- Run ID: `20260823-142751`

| 항목 | C: 분산 신청 | D: 동일 강의 집중 |
| --- | ---: | ---: |
| 성공 p95 | 1107.45ms | 1117.17ms |
| 성공 p99 | 1199.31ms | 1231.02ms |
| 처리량 | 4.40 req/s | 4.49 req/s |
| HTTP 실패율 | 0% | 0% |
| 애플리케이션 거부율 | 0% | 0% |
| Process CPU | 22.86% | 15.20% |
| Heap | 62.61% | 63.07% |
| Hikari active/max | 80% | 50% |
| Hikari pending | 0 | 0 |
| connection 획득 평균 | 77.08ms | 10.29ms |
| connection 사용 평균 | 736.37ms | 785.41ms |
| DB applied/actual | 830/830 | 823/823 |
| 카운트 불일치 | 0 | 0 |

10 VU에서는 두 시나리오의 p95와 처리량 차이가 작았다. 동일 강의에 요청이 집중돼도 이 부하에서는 명확한 hot row 경합이 나타나지 않았다.

## 20 VU 결과

- Run ID: `20260823-143504`

| 항목 | C: 분산 신청 | D: 동일 강의 집중 |
| --- | ---: | ---: |
| 성공 p95 | 1095.57ms | 2734.98ms |
| 성공 p99 | 1350.19ms | 2994.33ms |
| 처리량 | 9.02 req/s | 5.10 req/s |
| HTTP 실패율 | 0% | 0% |
| 애플리케이션 거부율 | 0% | 0% |
| Process CPU | 27.20% | 8.00% |
| Heap | 63.65% | 64.33% |
| Tomcat busy/max | 5.50% | 7.50% |
| Hikari active/max | 100% | 100% |
| Hikari pending | 0 | 4 |
| connection 획득 평균 | 26.07ms | 657.28ms |
| connection 획득 최대 | 593.14ms | 1233.74ms |
| connection 사용 평균 | 737.60ms | 1833.59ms |
| connection 사용 최대 | 5728ms | 3995ms |
| Hikari timeout | 0 | 0 |
| DB applied/actual | 1700/1700 | 943/943 |
| 카운트 불일치 | 0 | 0 |

## 핵심 비교

20 VU에서 D는 C보다 다음처럼 악화됐다.

- p95: 약 2.50배 증가
- 처리량: 약 43.5% 감소
- connection 사용 평균: 약 2.49배 증가
- connection 획득 평균: 약 25.2배 증가
- Hikari pending: 0에서 4로 증가

반면 D의 Process CPU는 8%, Heap은 64.33%, Tomcat busy/max는 7.5%였다. CPU, 메모리, Tomcat thread가 먼저 포화돼 느려진 결과가 아니다.

## 원인 분석

C는 조건부 UPDATE 대상이 20개 강의 row로 분산된다. 서로 다른 row를 갱신하므로 요청들이 병렬로 진행될 수 있다.

D는 모든 요청이 하나의 강의 row를 갱신한다. MySQL은 UPDATE 대상 row에 배타적 row lock을 획득하고 트랜잭션이 끝날 때까지 보유한다. 먼저 진입한 요청이 신청 INSERT와 flush를 끝내고 commit할 때까지 다음 요청은 같은 row의 UPDATE를 기다리게 된다.

```text
동일 강의 조건부 UPDATE 대기
-> 트랜잭션과 DB connection 점유 시간 증가
-> Hikari의 10개 connection이 모두 사용됨
-> 다음 요청의 connection 획득 대기 증가
-> 처리량 감소와 p95 증가
```

따라서 20 VU에서 추가로 확인된 병목은 **동일 강의 row의 lock 경합**이다. Hikari pending 증가는 이 경합으로 connection 반환이 늦어진 결과다.

## 정합성 결과

모든 단계에서 다음 조건을 만족했다.

- HTTP 실패율 0%
- 애플리케이션 거부율 0%
- `courses.applied_count`와 실제 `course_applications` 행 수 일치
- 카운트 불일치 강의 0개
- 테스트 fixture 삭제 완료
- 테스트 종료 후 Render와 MySQL 상태 `UP`

이번 테스트 강의의 정원을 충분히 크게 설정했기 때문에 마감 거절은 발생하지 않았다. 이 결과는 정원 초과 방지 실험이 아니라, 조건부 UPDATE를 사용하는 실제 신청 경로에서 분산 row와 hot row의 성능 차이를 비교한 결과다.

## 해석

조건부 UPDATE는 정원 확인과 증가를 하나의 원자적 SQL로 처리해 정합성을 보호한다. 하지만 하나의 강의에 요청이 집중되면 어떤 정합성 전략을 사용하더라도 동일 row의 변경은 완전히 병렬화할 수 없다.

현재 방식은 다음 트레이드오프를 가진다.

| 장점 | 비용 |
| --- | --- |
| 정원 증가의 원자성 보장 | 동일 row 갱신 직렬화 |
| 별도 재시도 로직 불필요 | hot row에서 lock 대기 발생 |
| DB 조건으로 마감 판정 | 부하 증가 시 connection 점유 시간 증가 |

따라서 “락을 제거해서 빠르게 만든다”는 개선은 정원 정합성을 훼손할 수 있어 적용하지 않는다. 먼저 트랜잭션 안에서 lock을 보유하는 시간을 줄일 수 있는지 확인해야 한다.

## 다음 검증

1. C/D 20 VU를 각각 3회 반복해 실행 순서와 관계없이 차이가 재현되는지 확인했다.
2. 조건부 UPDATE, `saveAndFlush()`, 트랜잭션 완료까지의 Timer를 분리했다.
3. 조건부 UPDATE p95에서 약 10.1배 차이가 발생하고 `saveAndFlush()`는 거의 같은 것을 확인했다.
4. 상세 결과는 `docs/performance/08-hot-row-phase-result.md`에 기록했다.
5. 다음에는 가장 작은 변경 하나를 적용한 뒤 동일한 20 VU 조건으로 재측정한다.

정합성 검증용 정원 10명·동시 요청 100개 실험은 이 지속 부하 실험과 별도로 유지한다.

## 원본 결과

```text
docs/performance/raw/baseline/bottleneck-isolation/20260823-142751/
docs/performance/raw/baseline/bottleneck-isolation/20260823-143504/
docs/performance/raw/baseline/bottleneck-isolation/20260901-202942/
```
