# 중복 확인 SQL 제거 개선 실험 계획

- 작성일: 2026-09-03
- 상태: 측정 완료
- 개선 커밋: `7d890df`
- 대상: `PlannerService.applyCourse()`
- 개선 전 기준선: `docs/performance/08-hot-row-phase-result.md`

## 문제

현재 신청 경로는 같은 학생의 중복 신청 여부를 `exists` 쿼리로 확인한 직후, 학점과 시간표 검증을 위해 그 학생의 전체 신청 목록을 다시 조회한다.

```text
강의 조회
-> 중복 여부 EXISTS 조회
-> 학생 신청 목록 조회
-> 조건부 UPDATE
-> 신청 INSERT와 flush
-> COMMIT
```

신청 목록에는 대상 강의 정보가 이미 포함되므로 중복 확인을 위해 별도의 SQL을 실행할 필요가 없다. 이 추가 조회는 조건부 UPDATE의 row lock을 획득하기 전이지만, 트랜잭션과 DB connection 점유시간 및 요청당 SQL 수를 늘린다.

## 개선 가설

학생 신청 목록에서 대상 강의 ID를 검사하고 별도 `EXISTS` 조회를 제거하면 요청당 SQL이 1개 줄어든다. 그 결과 다음 지표가 감소할 가능성이 있다.

- 전체 트랜잭션 시간
- Hikari connection 사용 시간
- Hikari connection 획득 대기
- HTTP p95

다만 이전 측정에서 D 시나리오의 지배 구간은 동일 강의 조건부 UPDATE였다. 따라서 이 변경으로 공통 DB 비용은 줄어도 동일 row 직렬화 자체는 사라지지 않으며, D의 p95와 처리량이 유의미하게 개선되지 않을 수도 있다.

## 변경 범위

개선 전:

```java
existsByStudentIdAndCourseId(studentId, courseId);
findByStudentIdOrderByCreatedAtAsc(studentId);
```

개선 후:

```java
List<CourseApplication> applications =
        findByStudentIdOrderByCreatedAtAsc(studentId);
applications에서 courseId 중복 확인;
```

DB의 `(student_id, course_id)` UNIQUE 제약은 동시에 들어온 두 요청이 목록 조회를 함께 통과하는 경우의 최종 방어선으로 유지한다.

## 정합성 통과 조건

- 정상 신청 성공
- 기존 신청 재요청 시 중복 거절
- 동일 사용자의 동시 신청 2건 중 1건만 성공
- UNIQUE 충돌로 실패한 트랜잭션의 정원 증가 Rollback
- 학점 제한과 시간표 충돌 검증 유지
- `applied_count`와 실제 신청 행 수 일치

## 성능 재측정 조건

개선 전 기준선과 동일하게 유지한다.

| 항목 | 조건 |
| --- | --- |
| 환경 | Render 애플리케이션 + Railway MySQL |
| C | 20개 강의에 신청 분산 |
| D | 하나의 강의에 신청 집중 |
| 부하 | 20 VU |
| Ramp | 30초 |
| Hold | 2분 30초 |
| 반복 | C와 D 각각 3회 |
| 실행 순서 | C→D / D→C / C→D |
| 실행 간격 | 60초 |

## 판단 기준

1. 성능보다 정합성과 오류율을 먼저 확인한다.
2. 개선 후 중앙값을 개선 전 중앙값과 비교한다.
3. HTTP p95, 처리량, 전체 트랜잭션, Hikari 사용·획득 시간을 함께 본다.
4. 조건부 UPDATE p95가 계속 지배하면 중복 조회는 근본 병목이 아니었다고 결론 낸다.
5. 차이가 작거나 실행별 변동 범위가 겹치면 성능 향상을 주장하지 않는다.

## 측정 결과

중복 확인 SQL을 1개 제거한 뒤 Render + Railway MySQL에서 C/D를 20 VU로 각각 3회 재측정했다. C의 HTTP p95는 기준선 1025.71ms에서 개선 후 952.90ms로 낮아졌지만 실행 범위가 겹쳤고, D의 HTTP p95는 2519.56ms에서 2673.16ms로 유의미하게 개선되지 않았다. D의 조건부 UPDATE p95도 1413.76ms와 1416.92ms로 사실상 같았다.

따라서 SQL 1개 제거는 코드와 요청 경로를 단순화했지만, 이번 부하의 종단간 병목은 해결하지 못했다. 동일 강의 `courses` row의 UPDATE 직렬화가 다음 분석 대상이다.

상세 결과와 원본 경로는 `docs/performance/10-redundant-duplicate-query-improvement-result.md`에 기록했다.
