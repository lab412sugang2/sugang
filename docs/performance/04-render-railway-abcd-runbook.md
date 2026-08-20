# Render-Railway A/B/C/D 병목 분리 테스트

- 작성일: 2026-08-20
- 애플리케이션: Render
- 운영 DB: Railway MySQL 9.4.0
- 트랜잭션 격리 수준: `REPEATABLE-READ`
- HikariCP 최대 연결 수: 10
- 목표: 성능 개선 전에 CPU, DB 조회, DB 쓰기, 동일 행 경합을 분리해 측정한다.

Tomcat busy/max 스레드를 수집하기 위해 `server.tomcat.mbeanregistry.enabled=true`를 적용한다.

## 이전 결과와 분리하는 이유

이전에 Render에서 측정한 100/300/500/1000 VU 결과는 메모리 H2 환경에서 실행됐다. 현재 운영 DB는 Railway MySQL이므로 이전 수치를 MySQL 성능 결과로 사용하지 않는다.

이번 실험부터 새로운 운영 기준선으로 기록한다.

## 시나리오

| 구분 | 요청 | DB 사용 | 확인하려는 것 |
| --- | --- | --- | --- |
| A | `GET /performance/ping` | 없음 | Render, JVM, Tomcat, 네트워크 기본 비용 |
| B | `GET /performance/courses/{id}` | 단일 조회 1회 | DB 연결과 단순 조회 비용 |
| C | 여러 강의에 분산 신청 | 읽기·조건부 UPDATE·INSERT | hot row가 없는 쓰기 경로 |
| D | 하나의 강의에 집중 신청 | 읽기·조건부 UPDATE·INSERT | 동일 강의 행의 락 경합 |

C와 D는 같은 `PlannerService.applyCourse()`를 호출한다. 따라서 실제 운영 코드의 조건부 UPDATE와 트랜잭션 경로를 측정한다.

## 안전장치

- `APP_PERFORMANCE_TEST_ENABLED`의 기본값은 `false`다.
- DB 변경 API는 `X-Performance-Test-Token`이 없거나 틀리면 거부한다.
- 로그인 인증을 우회하는 필터는 배포하지 않는다.
- 성능 데이터는 `PERF-`로 시작하는 전용 강의만 사용한다.
- 테스트 전 기존 성능 데이터를 지우고 테스트 후 다시 자동 삭제한다.
- 테스트 학번은 DB 컬럼 제한인 30자를 넘지 않게 생성한다.
- C/D의 최종 `applied_count`와 실제 신청 행 수를 비교해 요약에 저장한다.
- 최초 기본 부하는 `10/50/100 VU`다. 포화 지점을 확인하기 전에는 500/1000 VU를 실행하지 않는다.

## 1. Render 환경 변수

Render 애플리케이션에 다음 값을 설정하고 재배포한다.

```text
APP_PERFORMANCE_TEST_ENABLED=true
APP_PERFORMANCE_TEST_TOKEN=<충분히 긴 임의의 비밀값>
```

토큰 값은 Git, 문서, 캡처, 채팅에 남기지 않는다.

배포 후 A 엔드포인트를 확인한다.

```bash
curl -i https://sugang-5de3.onrender.com/performance/ping
```

HTTP `200`과 `{"status":"ok", ...}`가 나오면 준비된 것이다.

## 2. Prometheus와 Grafana 실행

```bash
cd /Users/kangdaeun/Desktop/강대운/단국대/수강신청찐/untitled/monitoring
docker compose -f docker-compose.monitoring.yml up -d
```

확인 주소:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

재현성을 위해 Docker 이미지 버전은 다음으로 고정했다.

- Prometheus `3.12.0`
- Grafana `13.0.1-security-01`

Prometheus의 `sugang-app` target이 `UP`이어야 한다. 준비되지 않아도 k6 결과는 저장되지만 서버 메트릭은 `NaN`으로 기록된다.

## 3. 토큰을 로컬 셸에 입력

명령 기록에 토큰 값을 직접 남기지 않도록 숨김 입력을 사용한다.

```bash
read -s PERFORMANCE_TEST_TOKEN
export PERFORMANCE_TEST_TOKEN
echo
```

Render에 설정한 토큰과 같은 값을 입력한다.

## 4. 1 VU 스모크 테스트

먼저 1 VU, 짧은 시간으로 API와 자동 정리를 확인한다.

```bash
cd /Users/kangdaeun/Desktop/강대운/단국대/수강신청찐/untitled

VUS_LIST="1" \
RAMP_DURATION="5s" \
HOLD_DURATION="10s" \
bash ./scripts/perf/run_bottleneck_isolation.sh
```

확인 항목:

- 네 시나리오가 모두 실행된다.
- C/D의 `rejected %`가 `0`이다.
- C/D의 `Count mismatch`가 `0`이다.
- 실행 후 Railway에서 `PERF-` 강의가 남지 않는다.

## 5. 개선 전 기준 성능 측정

스모크 테스트가 통과한 뒤 다음 명령을 실행한다.

```bash
VUS_LIST="10 50 100" \
RAMP_DURATION="30s" \
HOLD_DURATION="2m30s" \
bash ./scripts/perf/run_bottleneck_isolation.sh
```

전체 실행 시간은 약 36분이다. 시나리오 4개와 VU 단계 3개를 각각 3분씩 실행한다.

결과 저장 위치:

```text
tmp/perf-bottleneck-isolation/{run-id}/summary.md
tmp/perf-bottleneck-isolation/{run-id}/summary.csv
tmp/perf-bottleneck-isolation/{run-id}/*.json
tmp/perf-bottleneck-isolation/{run-id}/*.log
```

## 수집 항목

- k6 p95, p99, 실패율, 요청 처리량
- 애플리케이션 거부율
- Process CPU, System CPU
- JVM Heap 사용률
- Tomcat busy/max 비율
- GC pause 시간
- Hikari active/max 비율
- Hikari pending
- Hikari connection timeout
- C/D의 최종 `courses.applied_count`
- C/D의 실제 `course_applications` 수
- 두 값의 불일치 강의 수

## 결과 해석

| 관찰 결과 | 우선 검토할 원인 |
| --- | --- |
| A부터 CPU와 p95가 급증 | Render CPU, JVM, Tomcat, 네트워크 |
| A는 빠르고 B부터 지연 | DB 네트워크 또는 단순 조회 비용 |
| B는 빠르고 C/D부터 지연 | 쓰기 트랜잭션, SQL 횟수, 커넥션 점유 |
| C는 빠르고 D만 지연 | 동일 강의 행의 락 경합 |
| Hikari active 100%와 pending 증가 | 커넥션을 오래 점유하는 쿼리·트랜잭션·락 대기 |
| Heap 상승과 GC pause 증가 | 객체 생성 또는 메모리 압박 |
| Tomcat busy 100% | 요청 처리 스레드 포화 |
| VU를 높여도 req/s 정체, p95만 증가 | 최초 포화 지점 도달 |
| Count mismatch가 1 이상 | 성능보다 먼저 해결해야 할 정합성 오류 |

Hikari pending 증가는 원인이 아니라 결과일 수 있다. 느린 쿼리, 긴 트랜잭션, 네트워크 지연, 행 락 대기로 커넥션 반환이 늦어졌는지 함께 확인한다.

## 6. 테스트 종료

테스트가 끝나면 Render 환경 변수를 다음처럼 변경하고 재배포한다.

```text
APP_PERFORMANCE_TEST_ENABLED=false
```

`APP_PERFORMANCE_TEST_TOKEN`도 제거한다. 이후 `/performance/ping`이 HTTP `404`인지 확인한다.

```bash
curl -i https://sugang-5de3.onrender.com/performance/ping
```

마지막으로 로컬 셸의 토큰을 제거한다.

```bash
unset PERFORMANCE_TEST_TOKEN
```
