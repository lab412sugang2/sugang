# 성능 기준점 문서

- 작성일: 2026-06-23
- 목적: 성능 개선 전 배포 상태를 기준점으로 고정한다.
- 기준 태그: `v1.0-baseline`
- 기준 커밋: `ce8d7a0`
- 기준 커밋 메시지: `Use cookie-only session tracking`
- 운영 URL: `https://sugang-5de3.onrender.com`
- 기준 브랜치: `origin/main`

## 원칙

이 단계에서는 코드를 개선하지 않는다.

이 문서는 현재 배포본의 동작, 실행 환경, DB 구조, 수강신청 처리 흐름을 기록하기 위한 문서다. 부하 테스트 결과, 병목 분석, 개선안은 이후 문서에 따로 기록한다.

## 저장소 상태

로컬 작업 트리에는 성능 측정, SEO, 동시성 실험 관련 변경이 섞여 있을 수 있다. 이 변경들은 기준 배포본에 포함되지 않는다.

이 기준점은 원격 `main` 배포본을 기준으로 한다.

확인 명령:

```bash
git show --no-patch --decorate --oneline v1.0-baseline
```

예상 결과:

```text
ce8d7a0 (tag: v1.0-baseline, origin/main, origin/HEAD) Use cookie-only session tracking
```

## 실행 스택

- Java: 17
- 빌드 도구: Gradle
- Spring Boot: 3.2.5
- 웹 프레임워크: Spring MVC
- 템플릿 엔진: Thymeleaf
- 영속성: Spring Data JPA
- ORM: Hibernate
- DB 드라이버: MySQL Connector/J
- 로컬 기본 DB: H2, MySQL 호환 모드
- 모니터링: Spring Boot Actuator, Micrometer Prometheus registry
- 런타임 이미지: `eclipse-temurin:17-jre`

## Render 실행 환경

- 서비스 형태: Render Web Service
- 운영 대상: Render Free 인스턴스
- 알려진 메모리 한계: 512MB
- 정확한 CPU 할당량: 저장소에 고정되어 있지 않으므로 Render 대시보드에서 확인 필요
- 애플리케이션 포트: `${PORT:8080}`
- 실행 산출물: Spring Boot fat jar
- 실행 명령:

```bash
java -jar app.jar
```

## Docker 빌드

Dockerfile은 2단계 빌드를 사용한다.

```text
gradle:8.7-jdk17 -> eclipse-temurin:17-jre
```

빌드 단계:

```bash
gradle clean bootJar -x test --no-daemon
```

노출 포트:

```text
8080
```

## 데이터베이스

배포 환경에서는 다음 환경 변수를 통해 DB에 연결한다.

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

`DB_URL`이 없을 때 기본값:

```properties
spring.datasource.url=jdbc:h2:mem:sugang;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
spring.datasource.username=sa
spring.datasource.password=
```

운영 DB의 제공자, 리전, 플랜은 저장소에 기록되어 있지 않다. 최종 성능 분석 전 Render 및 DB 대시보드에서 따로 기록해야 한다.

로컬 Docker DB 설정:

- 이미지: `mysql:8.0`
- 컨테이너 이름: `${MYSQL_CONTAINER_NAME:-sugang-db}`
- 포트: `${MYSQL_PORT:-3306}:3306`
- DB 이름: `${MYSQL_DATABASE:-sugang}`
- 문자셋: `utf8mb4`
- Collation: `utf8mb4_unicode_ci`

## JPA 및 스키마

JPA 설정:

```properties
spring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:update}
spring.jpa.show-sql=${JPA_SHOW_SQL:false}
spring.jpa.properties.hibernate.format_sql=true
```

주요 테이블:

- `courses`
- `course_applications`

주요 제약 조건:

- `courses`: `(code, division_number)` 유니크
- `course_applications`: `(student_id, course_id)` 유니크

## HikariCP

기준 배포본에는 명시적인 `spring.datasource.hikari.*` 설정이 없다.

따라서 실제 커넥션 풀 값은 코드에서 추측하지 말고 실행 중인 애플리케이션에서 확인해야 한다.

확인할 Actuator 지표:

```text
/actuator/metrics/hikaricp.connections.max
/actuator/metrics/hikaricp.connections.active
/actuator/metrics/hikaricp.connections.idle
/actuator/metrics/hikaricp.connections.pending
/actuator/metrics/hikaricp.connections.acquire
/actuator/metrics/hikaricp.connections.timeout
```

주의할 점:

`hikaricp.connections.pending`이 증가했다고 바로 “커넥션 풀 크기가 문제”라고 결론 내리면 안 된다. 느린 쿼리, 긴 트랜잭션, 락 대기, DB 네트워크 지연 때문에 커넥션이 오래 점유된 결과일 수도 있다.

## 관측 가능성

Actuator와 Prometheus가 활성화되어 있다.

```properties
management.endpoints.web.exposure.include=${MANAGEMENT_ENDPOINTS_EXPOSURE_INCLUDE:health,info,metrics,prometheus}
management.endpoint.health.show-details=${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:always}
management.prometheus.metrics.export.enabled=${MANAGEMENT_PROMETHEUS_ENABLED:true}
management.metrics.tags.application=${MANAGEMENT_METRICS_APP_TAG:sugang-local}
```

HTTP 지연시간 백분위 설정:

```properties
management.metrics.distribution.percentiles.http.server.requests=${MANAGEMENT_HTTP_PERCENTILES:0.95,0.99}
management.metrics.distribution.percentiles-histogram.http.server.requests=${MANAGEMENT_HTTP_HISTOGRAM:true}
```

Tomcat access log 기본 활성화:

```properties
server.tomcat.accesslog.enabled=${TOMCAT_ACCESSLOG_ENABLED:true}
server.tomcat.accesslog.pattern=%{yyyy-MM-dd'T'HH:mm:ss}t %m %U %s %D
```

Render 로그에서 요청을 집계하려면 다음 환경 변수를 켤 수 있다.

```properties
APP_REQUEST_LOG_STDOUT_ENABLED=true
```

## 초기 데이터 동작

빈 DB에서 `PlannerService.initSampleCourses()`는 샘플 강의 12개를 생성한다.

- 일반 연습 강의 10개: `limit_count = 10000`
- 캡스톤 강의 2개: `limit_count = 1`

초기 데이터가 생성될 때 캡스톤 강의에는 신청 데이터 2개가 함께 들어간다.

- `seed-closed-1`: `349901`
- `seed-closed-2`: `349902`

시작 후 다음 동작이 실행된다.

- `ensurePracticeLimitCounts()`
- `syncAllAppliedCounts()`

즉, 시작 시점에 `applied_count`는 `course_applications` 실제 개수를 기준으로 다시 맞춰진다.

## 운영 DB 데이터 개수

운영 DB의 실제 데이터 개수는 테스트 전 반드시 따로 기록해야 한다.

확인 SQL:

```sql
SELECT COUNT(*) AS courses FROM courses;
SELECT COUNT(*) AS course_applications FROM course_applications;

SELECT id, code, division_number, name, limit_count, applied_count, canceled
FROM courses
ORDER BY id;

SELECT course_id, COUNT(*) AS actual_applications
FROM course_applications
GROUP BY course_id
ORDER BY course_id;
```

기록란:

```text
courses_count=
course_applications_count=
captured_at=
db_source=
```

## 기준 배포본의 수강신청 흐름

기준 배포본의 `PlannerService.applyCourse(studentId, courseId)` 흐름:

1. `courseRepository.findById(courseId)`로 강의를 조회한다.
2. 폐강 여부를 확인한다.
3. `course.isFull()`로 정원 초과 여부를 확인한다.
4. `(studentId, courseId)` 중복 신청 여부를 확인한다.
5. 해당 학생의 기존 신청 목록을 조회한다.
6. 19학점 제한을 검증한다.
7. 시간표 중복을 검증한다.
8. `CourseApplication`을 저장한다.
9. `countByCourseId(courseId)`로 실제 신청 수를 센다.
10. `course.appliedCount`를 실제 신청 수와 동기화한다.
11. `course`를 저장한다.

## 기준 배포본의 동시성 특성

`v1.0-baseline` 기준 배포본에는 다음 전략이 아직 포함되어 있지 않다.

- 비관적 락
- `@Version` 기반 낙관적 락
- 조건부 원자적 `UPDATE`
- 명시적인 row-level lock 조회

중복 신청은 DB 유니크 제약 `(student_id, course_id)`으로 방어한다.

하지만 정원 확인은 단일 원자적 SQL이 아니라 `조회 -> 조건 확인 -> 신청 저장 -> 개수 재계산` 흐름이다. 따라서 동일 강의에 동시 신청이 몰릴 때 정원 초과 여부를 별도로 검증해야 한다.

## 로컬 동시성 실험 코드와의 구분

현재 로컬 작업 트리에는 비관적 락, 낙관적 락, 조건부 UPDATE 비교 코드와 `ConcurrencyComparisonTest`가 존재한다.

하지만 이 코드는 `v1.0-baseline` 태그 기준 배포본에는 포함되지 않는다. 따라서 문서와 실험 결과를 정리할 때 다음을 구분해야 한다.

- 기준 배포본: 실제 Render 배포 상태를 기준으로 한 성능/가용성 측정
- 로컬 동시성 실험: 비관적 락, 낙관적 락, 조건부 UPDATE 방식 비교

이 구분을 해두면 나중에 “운영 배포본의 병목 분석”과 “동시성 제어 방식 비교”가 섞이지 않는다.

## 기준점 확인 체크리스트

정식 테스트 전 확인할 것:

- Render 배포 커밋이 `ce8d7a0`인지 확인한다.
- `v1.0-baseline` 태그가 로컬과 원격에 존재하는지 확인한다.
- `https://sugang-5de3.onrender.com/actuator/health`가 정상인지 확인한다.
- `https://sugang-5de3.onrender.com/actuator/prometheus`가 수집 가능한지 확인한다.
- 운영 DB 테이블 개수를 기록한다.
- Hikari max connection 값을 Actuator 또는 Grafana에서 기록한다.
- Render 서비스 플랜, 리전, 메모리 한계를 기록한다.
- DB 제공자, 리전, 플랜을 기록한다.

## 다음 문서

다음 단계:

```text
docs/performance/01-hypothesis.md
```

이 문서는 다음 정식 부하 테스트를 실행하기 전에 작성되어 있어야 한다.
