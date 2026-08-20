# Race Condition 개선 전 재현 결과

- 실행일: 2026-08-20
- 배포 기준 커밋: `76086c1`
- 대상 메서드: `PlannerService.applyCourse()`
- DB: Docker MySQL 8.0
- Java: 17

## 목적

실제 신청 경로의 `강의 조회 -> 정원 확인 -> 신청 저장 -> 신청 수 재계산`이 동시 요청에서 정원을 보장하는지 검증한다.

## 실험 조건

- 강의 정원: 10명
- 동시 요청: 100개
- 스레드 풀: 32개
- 사용자: 모두 서로 다른 학번
- 동시 시작: `CountDownLatch`
- 테스트: `EnrollmentRaceConditionReproductionTest`

## 결과

```text
requests=100
capacity=10
success=13
actual_applications=13
applied_count=12
duration_ms=185
failures={IllegalStateException=27, MySQLTransactionRollbackException=60}
```

## 확인된 문제

1. 정원은 10명이지만 실제 신청 행은 13개가 저장됐다.
2. `courses.applied_count` 12와 `course_applications` 13개가 일치하지 않았다.
3. 동시 트랜잭션 중 MySQL deadlock 계열 예외가 발생했다.
4. 각 요청에 `@Transactional`이 있어도 여러 트랜잭션이 같은 정원 값을 읽고 검사하는 Race Condition은 방지하지 못했다.

## 원인

정원 확인과 증가가 서로 다른 DB 연산이다.

```text
A: applied_count=9 조회 -> 신청 가능
B: applied_count=9 조회 -> 신청 가능
A: 신청 저장
B: 신청 저장
```

일반 `findById()`는 정원 확인 시점에 해당 강의 row를 동시 요청으로부터 보호하지 않는다. 나중에 `course`를 저장하며 row lock을 획득해도 여러 요청이 이미 정원 검사를 통과한 뒤라 늦다.

## 개선 가설

정원 확인과 증가를 다음 조건부 `UPDATE` 하나로 묶는다.

```sql
UPDATE courses
SET applied_count = applied_count + 1
WHERE id = ?
  AND applied_count < limit_count
  AND canceled = false;
```

- 변경된 행이 1개이면 신청을 저장한다.
- 변경된 행이 0개이면 마감된 강의로 판단한다.
- 정원 증가와 신청 저장을 같은 트랜잭션에 두어 후속 저장이 실패하면 정원 증가도 Rollback되게 한다.

## 제한

이 결과는 해당 환경에서 실행한 1회 실험이다. 정확한 재현 빈도와 성능 비교를 위해서는 반복 실험이 필요하다. 다만 이 실험에서 정원 초과와 카운터 불일치가 실제로 관찰됐으므로 현재 방식이 정원 정합성을 보장하지 못한다는 점은 확인했다.
