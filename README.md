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

## 실배포(연습사이트) 빠른 가이드

이 프로젝트는 서버 렌더링(Spring Boot + Thymeleaf)이므로 `백엔드 1개`만 배포하면 됩니다.

### 1) 배포 플랫폼

- 추천: `Railway` 또는 `Render`
- 이유: Docker 기반으로 바로 배포 가능 + 관리형 MySQL 연결 쉬움

### 2) 필수 환경변수

- `DB_URL` (예: `jdbc:mysql://<host>:3306/sugang?serverTimezone=Asia/Seoul&characterEncoding=UTF-8`)
- `DB_USERNAME`
- `DB_PASSWORD`
- `PORT` (플랫폼이 자동 주입하면 생략 가능)
- 선택:
  - `JPA_DDL_AUTO=update`
  - `JPA_SHOW_SQL=false`

### 3) Docker 이미지 빌드/실행 확인

```bash
docker build -t sugang-local .
docker run --rm -p 8080:8080 \
  -e DB_URL='jdbc:mysql://host.docker.internal:3306/sugang?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
  -e DB_USERNAME='root' \
  -e DB_PASSWORD='your_password' \
  sugang-local
```

### 4) 플랫폼 배포 절차(공통)

1. GitHub에 코드 push
2. Railway/Render에서 `New Service` -> `Deploy from GitHub`
3. Dockerfile 자동 인식 확인
4. 환경변수(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) 입력
5. 배포 완료 후 발급 URL 접속

### 5) 도메인 연결(선택)

1. 구매한 도메인 DNS에서 `CNAME` 또는 `A` 레코드 추가
2. 플랫폼 Custom Domain 메뉴에 도메인 등록
3. SSL(HTTPS) 자동 발급 확인

## 현재 범위

- `/` 메인 화면 렌더링
- 기존 프론트엔드의 `*.do` 요청은 데모용 라우트로 수용
- 접속 브라우저(세션)마다 임시 학번을 자동 발급해 개인 연습 내역 분리
- 실제 학사 시스템 연동/영속화 로직은 미구현
