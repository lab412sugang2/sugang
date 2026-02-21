# sugang-local

단국대학교 수강신청 화면을 로컬에서 재현한 Spring Boot 데모 프로젝트입니다.

## 로컬 실행

1. `.env.example`을 참고해 `.env` 파일을 생성합니다.
2. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 실제 값으로 설정합니다.
3. DB 실행:

```bash
docker compose --env-file .env up -d
```

4. 앱 실행:

```bash
./gradlew bootRun
```

접속 URL: `http://localhost:8080/`

## 실배포(연습사이트) 빠른 가이드: Render 무료 + Native(Java)

이 프로젝트는 서버 렌더링(Spring Boot + Thymeleaf)이라 Web Service 1개만 배포하면 됩니다.

### 1) Render 서비스 생성

1. Render Dashboard -> `New` -> `Web Service`
2. GitHub 저장소 연결 후 이 저장소 선택
3. Region은 `Singapore` 권장
4. Instance Type은 반드시 `Free`

### 2) Build / Start 명령

- Build Command
```bash
./scripts/render-build.sh
```

- Start Command
```bash
java -jar build/libs/*.jar
```

`server.port=${PORT:8080}` 설정이 이미 적용되어 있어 Render가 주입하는 `PORT`를 자동 사용합니다.

### 3) 환경변수

- 연습용 무료 배포라면 필수 없음 (H2 메모리 DB fallback)
- 필요 시 선택적으로 사용:
  - `DB_URL`
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `JPA_DDL_AUTO`
  - `JPA_SHOW_SQL`

### 4) 배포 후 확인

1. 배포 완료 후 발급된 `https://...onrender.com` 접속
2. 무료 플랜은 idle 후 첫 접속 시 깨어나는 시간이 있을 수 있음

### 5) 도메인 연결(선택)

1. 도메인 DNS에 `CNAME` 또는 `A` 레코드 추가
2. Render의 Custom Domain 메뉴에서 도메인 등록
3. HTTPS 인증서 자동 발급 확인

## 현재 범위

- `/` 메인 화면 렌더링
- 기존 프론트엔드의 `*.do` 요청은 데모용 라우트로 수용
- 접속 브라우저(세션)마다 임시 학번을 자동 발급해 개인 연습 내역 분리
- 실제 학사 시스템 연동/영속화 로직은 미구현
