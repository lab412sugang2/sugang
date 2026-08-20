# Render 배포 검증

- 확인일: 2026-08-20
- 애플리케이션 변경 커밋: `1943b88`
- 서비스: `https://sugang-5de3.onrender.com`

## 배포 확인

`main` 푸시 전 공개 Prometheus 메트릭의 프로세스 시작 시각은 다음과 같았다.

```text
2026-08-09 21:02:24 KST
```

`1943b88` 푸시 후 시작 시각이 다음 값으로 변경됐다.

```text
2026-08-20 18:16:37 KST
```

`/actuator/health`도 `UP`을 반환해 새 애플리케이션 인스턴스가 정상 기동된 것을 확인했다.

## 확인된 환경 차이

Render의 `/actuator/health`가 보고한 현재 DB는 MySQL이 아니라 `H2`다.

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "H2"
      }
    }
  }
}
```

애플리케이션 설정은 `DB_URL`이 없으면 메모리 H2를 사용한다. 따라서 다음을 구분해야 한다.

- Race Condition과 조건부 UPDATE 검증: 로컬 Docker MySQL 8.0
- 현재 Render 배포본: 메모리 H2

이번 MySQL 동시성 실험 결과를 Render 운영 MySQL의 실측 결과라고 표현하면 안 된다. Render에서도 MySQL을 사용하려면 외부 MySQL 인스턴스를 준비한 뒤 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 환경 변수로 설정하고 재배포해야 한다.

메모리 H2는 프로세스가 재시작되면 데이터가 초기화되므로 실제 운영 데이터의 영속 저장소로 사용하기에도 적합하지 않다.
