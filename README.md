# sugang-local

단국대학교 수강신청 화면을 로컬에서 재현한 Spring Boot 데모 프로젝트입니다.

## 실행 전 준비

1. `.env.example`을 참고해 `.env` 파일을 생성합니다.
2. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`를 실제 값으로 설정합니다.

## 실행

```bash
docker compose --env-file .env up -d
./gradlew bootRun
```

접속 URL: `http://localhost:8080/`

## 현재 범위

- `/` 메인 화면 렌더링
- 기존 프론트엔드의 `*.do` 요청은 데모용 라우트로 수용
- 실제 학사 시스템 연동/영속화 로직은 미구현
