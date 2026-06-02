# sugang-local

단국대학교 수강신청 화면을 로컬에서 재현한 Spring Boot 연습용 사이트 프로젝트입니다.  
실제 학사 시스템 연동용이 아닌, UI/흐름 재현과 백엔드 구조 학습 목적의 연습 서비스입니다.

Made by. 컴퓨터공학과 강대운 · 김준수 · 서준영

## 라이브 연습용 사이트
- URL: `https://sugang-5de3.onrender.com`
- 배포 환경: `Render Free (Docker)`
- 참고: Free 플랜 특성상 일정 시간 미사용 후 첫 요청에서 기동 지연(sleep/wake-up)이 발생할 수 있습니다.

## 주요 기술
- `Spring Boot 3`
- `Thymeleaf` (SSR)
- `Spring Data JPA`
- `MySQL 8` (기본), `H2 In-Memory` (fallback)
- `Docker` / `Docker Compose`

## 핵심 기능
- 수강 과목 목록 조회
- 과목 신청/취소
- 19학점 초과 방지
- 시간표 충돌 방지
- 정원 초과/폐강/중복 신청 방지
- 세션 단위 임시 학번 발급(`PXXXXXXXX`)
- 레거시 `.do` 라우팅 호환

## 프로젝트 구조
```text
src/main/java/sugang
├─ controller
│  ├─ HomeController.java
│  └─ SugangMockController.java
├─ service
│  ├─ PlannerService.java
│  ├─ RegistrationService.java
│  ├─ SessionStudentService.java
│  └─ HomePageService.java
├─ entity
│  ├─ Course.java
│  └─ CourseApplication.java
└─ repository
   ├─ CourseRepository.java
   └─ CourseApplicationRepository.java
```

## 빠른 실행
### 1) 환경변수 준비
`.env.example`를 복사해 `.env` 생성:

```bash
cp .env.example .env
```

### 2) MySQL 실행(선택)
MySQL로 실행하려면:

```bash
docker compose --env-file .env up -d
```

### 3) 앱 실행
```bash
./gradlew bootRun
```

접속: `http://localhost:8080`

## DB 동작 방식
- `DB_URL` 미설정 시: H2 메모리 DB 사용
- `DB_URL` 설정 시: 지정한 MySQL 사용

예시:
```properties
DB_URL=jdbc:mysql://localhost:3306/sugang?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=your_password
```

## Docker 실행
이미지 빌드:
```bash
docker build -t sugang-local .
```

컨테이너 실행:
```bash
docker run --rm -p 8080:8080 \
  -e DB_URL='jdbc:mysql://host.docker.internal:3306/sugang?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
  -e DB_USERNAME='root' \
  -e DB_PASSWORD='your_password' \
  sugang-local
```

참고: 현재 Dockerfile은 빌드 시 테스트를 제외(`-x test`)합니다.

## 테스트 실행
```bash
./gradlew test
```

주요 테스트:
- `SugangRoutingTest`
- `PlannerServiceValidationTest`

## 모니터링(Micrometer)
- Actuator/Micrometer가 활성화되어 HTTP 요청 메트릭을 수집합니다.
- 기본 엔드포인트:
  - `GET /actuator/health`
  - `GET /actuator/metrics`
  - `GET /actuator/metrics/http.server.requests`
  - `GET /actuator/prometheus`
- `http.server.requests`에 p95/p99(분위수)와 히스토그램이 설정되어 있습니다.

### Prometheus + Grafana 실행
모니터링 스택 파일 위치:
- `monitoring/docker-compose.monitoring.yml`
- `monitoring/prometheus/prometheus.yml`
- `monitoring/grafana/dashboards/sugang-performance.json`

기본 scrape 대상:
- Render: `https://sugang-5de3.onrender.com/actuator/prometheus`
- 로컬 앱을 볼 때는 `monitoring/prometheus/prometheus.yml`을 `http://host.docker.internal:8080` 기준으로 다시 바꿉니다.

실행:
```bash
cd monitoring
docker compose -f docker-compose.monitoring.yml up -d
```

접속:
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (기본 계정 `admin` / `admin`)

### k6 부하 테스트 실행
시나리오 파일:
- `k6/scenarios/concurrency-ramp.js`
- `k6/scenarios/first-pass-render.js` (`100 -> 300 -> 500 -> 1000` 1차 실험용)

실행 예시:
```bash
k6 run -e BASE_URL=http://localhost:8080 k6/scenarios/concurrency-ramp.js
```

Render 1차 실험:
```bash
k6 run k6/scenarios/first-pass-render.js
```

1차 실험에서 Grafana에서 먼저 볼 패널:
- `HTTP Latency (p95/p99)`
- `Hikari Active/Max %`
- `Hikari Pending / Timeout`
- `CPU Usage`
- `Heap Usage %`

각 단계(`100`, `300`, `500`, `1000`) 유지 구간에서 위 5개 패널을 캡처하면 됩니다.

## 라우팅
- `GET /` : 메인 수강신청 화면
- `GET /main.do` : `/`로 리다이렉트
- `GET /findGsLctTmtbl.do` : 시간표 팝업 화면
- `POST /saveTkcrsApl.do` : 과목 신청
- `POST /deleteTkcrsApl.do` : 과목 취소
- `POST /findSubjInfo.do` : 연습용 라우트(`/`로 리다이렉트)

## Render 배포 기준 설정
1. GitHub 저장소 연결 후 Web Service 생성
2. `Dockerfile` 자동 인식 확인
3. 환경변수 설정
4. Deploy 후 서비스 URL 확인

필수 환경변수:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

선택 환경변수:
- `JPA_DDL_AUTO=update`
- `JPA_SHOW_SQL=false`

## 안내 및 주의
- 본 프로젝트는 비공식 연습용 사이트입니다.
- 실제 단국대학교/공식 학사시스템과 무관합니다.
- 실제 수강신청/학적 처리 기능을 제공하지 않습니다.
