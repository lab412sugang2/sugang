# 병목 분리 테스트 계획

- 작성일: 2026-06-23
- 기준 태그: `v1.0-baseline`
- 기준 커밋: `ce8d7a0`
- 대상 URL: `https://sugang-5de3.onrender.com`
- 목표: 개선 전에 먼저 측정한다.

## 테스트는 크게 3개다

이번 성능 분석은 테스트를 3개 축으로 나눈다.

1. 혼합 트래픽 테스트
2. 동일 강의 동시신청 테스트
3. 병목 분리 테스트

각 테스트가 답하는 질문이 다르다.

| 테스트 | 질문 | 핵심 관심사 |
| --- | --- | --- |
| 혼합 트래픽 | 실제 사용 패턴에서 서버가 버티는가 | 가용성 |
| 동일 강의 동시신청 | 동시에 신청해도 최종 데이터가 맞는가 | 정합성 |
| 병목 분리 | 느려지는 원인이 앱, DB, 쓰기, 락 중 어디인가 | 원인 분석 |

## 공통 준비

정식 테스트 전에 확인할 것:

1. Render가 의도한 배포본을 실행 중인지 확인한다.
2. Prometheus target이 `UP`인지 확인한다.
3. Grafana 대시보드에 데이터가 들어오는지 확인한다.
4. 콜드 스타트 테스트가 아니라면 서버를 먼저 깨워둔다.
5. 실행 명령어, 실행 시간, 대상 URL을 기록한다.
6. k6 원본 결과를 저장한다.
7. 테스트 시간대의 Grafana 화면을 캡처한다.

모니터링 실행:

```bash
cd /Users/kangdaeun/Desktop/강대운/단국대/수강신청찐/untitled/monitoring
docker compose -f docker-compose.monitoring.yml up -d
```

Prometheus:

```text
http://localhost:9090
```

Grafana:

```text
http://localhost:3000
```

공통 지표:

- k6 p95
- k6 p99
- k6 실패율
- k6 request rate
- CPU max
- Heap max
- Tomcat busy thread
- Hikari active/max
- Hikari pending
- Hikari connection timeout
- 최종 등록 인원
- 정원 초과 건수
- 중복 신청 건수

## 테스트 엔드포인트 활성화

병목 분리 엔드포인트는 기본적으로 비활성화되어 있다.

활성화 환경 변수:

```text
APP_PERFORMANCE_TEST_ENABLED=true
APP_PERFORMANCE_TEST_TOKEN=원하는_토큰값
```

읽기 엔드포인트인 A/B는 `APP_PERFORMANCE_TEST_ENABLED=true`만 있으면 열린다.

C/D처럼 DB를 변경하는 엔드포인트는 `X-Performance-Test-Token` 헤더가 필요하다.

이 안전장치가 필요한 이유:

- C/D 테스트는 성능 테스트용 강의를 생성한다.
- C/D 테스트는 해당 테스트 강의의 신청 데이터를 초기화한다.
- 운영 DB에서 실수로 임의 데이터를 지우지 않기 위해 토큰을 요구한다.

구현된 엔드포인트:

| 구분 | 엔드포인트 | 역할 |
| --- | --- | --- |
| A | `GET /performance/ping` | DB 없는 ping |
| B | `GET /performance/courses/{courseId}` | 단일 강의 DB 조회 |
| C 준비 | `POST /performance/fixtures/distributed-courses` | 분산 신청용 테스트 강의 생성/초기화 |
| D 준비 | `POST /performance/fixtures/same-course` | 동일 강의 테스트 강의 생성/초기화 |
| C/D 실행 | `POST /performance/apply` | 성능 테스트용 신청 요청 |

## A/B/C/D 자동 실행

추가된 실행 스크립트:

```text
scripts/perf/run_bottleneck_isolation.sh
```

전체 실행:

```bash
cd /Users/kangdaeun/Desktop/강대운/단국대/수강신청찐/untitled
PERFORMANCE_TEST_TOKEN='Render에_설정한_토큰' \
VUS_LIST="100 300 500 1000" \
BASE_URL="https://sugang-5de3.onrender.com" \
bash ./scripts/perf/run_bottleneck_isolation.sh
```

처음에는 100 VU만 작게 실행해서 엔드포인트와 토큰이 맞는지 확인한다.

```bash
PERFORMANCE_TEST_TOKEN='Render에_설정한_토큰' \
VUS_LIST="100" \
SCENARIOS="ping course-read distributed-apply same-course-apply" \
bash ./scripts/perf/run_bottleneck_isolation.sh
```

특정 테스트만 실행할 수도 있다.

```bash
SCENARIOS="ping" bash ./scripts/perf/run_bottleneck_isolation.sh
SCENARIOS="course-read" COURSE_ID="1" bash ./scripts/perf/run_bottleneck_isolation.sh
SCENARIOS="distributed-apply" PERFORMANCE_TEST_TOKEN='토큰' bash ./scripts/perf/run_bottleneck_isolation.sh
SCENARIOS="same-course-apply" PERFORMANCE_TEST_TOKEN='토큰' bash ./scripts/perf/run_bottleneck_isolation.sh
```

결과 저장 위치:

```text
tmp/perf-bottleneck-isolation/{run-id}/summary.md
tmp/perf-bottleneck-isolation/{run-id}/summary.csv
```

## 테스트 1: 혼합 트래픽

목적:

실제 사용자가 로그인, 홈 조회, 시간표 팝업, 신청을 섞어서 사용하는 상황에서 서버가 어디까지 버티는지 본다.

현재 스크립트:

```text
k6/scenarios/fixed-stage-render.js
```

트래픽 비율:

- 홈 페이지: 70%
- 시간표 팝업: 15%
- 신청 요청: 15%

주의:

이 테스트는 동일 강의 정합성을 증명하지 않는다. VU가 많아도 모든 요청이 같은 순간 같은 강의에 들어가는 것이 아니라, 루프를 돌며 시간상 분산되기 때문이다.

실행 명령:

```bash
cd /Users/kangdaeun/Desktop/강대운/단국대/수강신청찐/untitled
VUS_LIST="100 300 500 1000" \
BASE_URL="https://sugang-5de3.onrender.com" \
LOADTEST_AUTH_BYPASS=true \
bash ./scripts/perf/run_stage_summary.sh
```

관찰할 것:

- p95, p99
- 실패율
- req/s
- CPU max
- Heap max
- Hikari active/max
- Hikari pending

해석:

| 신호 | 해석 |
| --- | --- |
| p95 급증, CPU 100% 근접, Hikari pending 0 | 앱 또는 Render CPU 병목 가능성 |
| p95 급증, CPU 낮음, Hikari pending 증가 | DB 커넥션 대기 또는 DB 지연 가능성 |
| Heap 한계 근접, GC 증가 | 메모리/GC 병목 가능성 |
| VU 증가에도 처리량 정체 | 포화 지점 도달 |
| 5xx 또는 timeout 발생 | 가용성 한계 초과 |

## 테스트 2: 동일 강의 동시신청

목적:

정원 10명인 강의에 여러 사용자가 동시에 신청했을 때 최종 데이터가 정확한지 확인한다.

질문:

100명이 거의 동시에 같은 강의를 신청하면 정확히 10명만 등록되는가?

이미 수행한 로컬 실험:

- 테스트 파일: `src/test/java/sugang/service/ConcurrencyComparisonTest.java`
- 동시 요청 수: 100
- Thread pool size: 32
- 강의 정원: 10
- 비교 전략:
  - 비관적 락
  - 낙관적 락
  - 조건부 UPDATE

실험에서 확인하는 값:

- 성공 수
- 정원 마감 실패 수
- 락 충돌 실패 수
- 기타 실패 수
- 전체 소요 시간
- p95 지연시간
- 실제 DB 신청 수
- `courses.applied_count`

JUnit 실행 명령:

```bash
cd /Users/kangdaeun/Desktop/강대운/단국대/수강신청찐/untitled
DB_URL='jdbc:mysql://127.0.0.1:3306/sugang?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
DB_USERNAME='root' \
DB_PASSWORD='1234' \
./gradlew test --tests 'sugang.service.ConcurrencyComparisonTest' --no-daemon
```

성공 조건:

- 최종 신청 수가 정원을 넘지 않는다.
- 성공 수와 실제 `course_applications` 행 수가 일치한다.
- `courses.applied_count`와 실제 신청 수가 일치한다.
- 동일 사용자 중복 신청이 없다.

이 테스트가 증명하는 것:

- 정합성
- 락 전략별 동작 차이
- 조건부 UPDATE 방식의 원자성
- 정원 초과 방지 여부

이 테스트가 증명하지 않는 것:

- Render 운영 환경에서 몇 명까지 버티는지
- 실제 사용자가 섞여 들어오는 상황의 전체 처리량
- 서버 CPU/메모리 포화 지점

## 테스트 3: 병목 분리

목적:

느려지는 원인이 어디서 시작되는지 분리한다.

이 테스트는 4개의 작은 테스트로 나눈다.

| 구분 | 형태 | 의미 |
| --- | --- | --- |
| A | DB 없는 ping | 앱/Render/Tomcat 기본 비용 |
| B | 단일 강의 DB 조회 | DB 연결과 조회 지연 |
| C | 여러 강의 분산 신청 | hot row 없는 쓰기 경로 |
| D | 동일 강의 집중 신청 | 락 경합과 정원 정합성 |

### A. DB 없는 ping

목적:

DB를 전혀 쓰지 않을 때 애플리케이션 자체가 얼마나 빠른지 본다.

예상 엔드포인트:

```text
GET /performance/ping
```

예상 동작:

```text
200 OK
```

DB 접근 없음.

이 단계부터 느리면 DB가 아니라 다음을 의심한다.

- Render CPU
- JVM
- Tomcat thread
- 네트워크
- 과도한 로그

### B. 단일 강의 DB 조회

목적:

간단한 DB read 비용을 본다.

예상 엔드포인트:

```text
GET /performance/courses/{id}
```

예상 동작:

- 강의 1개 조회
- 최소 JSON 응답
- Thymeleaf 렌더링 없음
- 쓰기 트랜잭션 없음

A는 빠른데 B부터 느리면 다음을 의심한다.

- DB 커넥션 획득
- DB 네트워크 지연
- 단순 조회 쿼리 지연
- Hikari pool 대기

### C. 여러 강의 분산 신청

목적:

하나의 강의 row에 요청이 몰리지 않을 때 쓰기 경로가 버티는지 본다.

상황:

- 여러 사용자가 신청한다.
- 여러 강의에 분산해서 신청한다.
- 각 강의는 충분한 정원을 가진다.

B는 빠른데 C부터 느리면 다음을 의심한다.

- 트랜잭션 길이
- insert 비용
- 요청당 SQL 수
- JPA flush 비용
- unique constraint 처리 비용

### D. 동일 강의 집중 신청

목적:

하나의 강의 row에 요청이 몰릴 때 락 경합과 정합성을 본다.

상황:

- 여러 사용자가 같은 강의에 신청한다.
- 강의 정원은 고정한다.
- 테스트 후 최종 데이터를 반드시 확인한다.

C는 빠른데 D만 느리면 다음을 의심한다.

- row-level lock 경합
- 정원 증가 방식
- 트랜잭션 직렬화
- 마감 처리 경로

## 결과 저장 위치

원본 결과는 다음 위치에 저장한다.

```text
docs/performance/raw/baseline/
```

구조:

```text
docs/performance/raw/baseline/
  mixed-traffic/
  same-course-concurrent-apply/
  bottleneck-isolation/
```

각 실행마다 저장할 것:

- k6 JSON summary
- k6 콘솔 출력
- stage summary markdown 또는 CSV
- Grafana 스크린샷
- DB 검증 SQL 결과
- Render 상태 메모

## 판단 순서

그래프 하나 보고 바로 개선하지 않는다.

순서:

1. 혼합 트래픽으로 대략적인 가용성 한계를 찾는다.
2. 동일 강의 동시신청으로 정합성을 검증한다.
3. 병목 분리 테스트로 왜 느려지는지 확인한다.
4. 그 다음에 개선안을 선택한다.

## 좋은 결과 해석 예시

좋은 해석:

- 300 VUs까지는 p95가 안정적이었지만 500 VUs부터 p95가 9초 이상으로 증가했고 CPU가 100%에 근접했다.
- 동일 강의 100개 동시 요청에서도 최종 신청 수는 정확히 10명이었다.
- ping은 빠른데 DB 조회부터 느려져서 병목 시작점이 DB 접근 이후로 좁혀졌다.
- 분산 신청은 안정적이지만 동일 강의 신청만 느려져 hot row 경합 가능성이 커졌다.

나쁜 해석:

- 그냥 느렸다.
- DB가 문제인 것 같다.
- Redis를 넣으면 될 것 같다.
- CPU가 한 번 높았으니 CPU만 문제다.

## 현재 상태

- 혼합 트래픽 스크립트는 존재한다.
- Prometheus/Grafana 설정은 존재한다.
- 동일 강의 동시신청 전략 비교 JUnit 테스트는 존재한다.
- 병목 분리용 A/B/C/D 엔드포인트가 추가됐다.
- A/B/C/D k6 스크립트가 추가됐다.
- A/B/C/D 자동 실행 스크립트가 추가됐다.
- Render에서 사용하려면 `APP_PERFORMANCE_TEST_ENABLED=true`와 `APP_PERFORMANCE_TEST_TOKEN` 설정 후 재배포가 필요하다.

## 다음 단계

다음 문서:

```text
docs/performance/04-test-execution.md
```

여기에는 실제 실행 명령, 결과 파일 위치, Grafana 캡처, 해석을 기록한다.
