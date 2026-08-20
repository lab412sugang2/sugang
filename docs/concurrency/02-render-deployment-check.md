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

## Railway MySQL 연결 전 상태

최초 배포 직후 Render의 `/actuator/health`가 보고한 DB는 MySQL이 아니라 `H2`였다.

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

애플리케이션 설정은 `DB_URL`이 없으면 메모리 H2를 사용한다. 따라서 당시 결과는 다음처럼 구분했다.

- Race Condition과 조건부 UPDATE 검증: 로컬 Docker MySQL 8.0
- 당시 Render 배포본: 메모리 H2

이 시점의 부하 테스트 결과를 Render 운영 MySQL의 실측 결과라고 표현하면 안 된다.

메모리 H2는 프로세스가 재시작되면 데이터가 초기화되므로 실제 운영 데이터의 영속 저장소로 사용하기에도 적합하지 않다.

## Railway MySQL 연결

Railway Hobby 환경에 MySQL을 생성하고 Public Access를 활성화했다. Render에는 값 자체를 문서에 남기지 않고 다음 환경 변수만 등록했다.

- `DB_URL`: Railway MySQL JDBC URL
- `DB_USERNAME`: Railway MySQL 사용자명
- `DB_PASSWORD`: Railway MySQL 비밀번호

JPA 설정은 기본값인 `spring.jpa.hibernate.ddl-auto=update`를 사용한다.

환경 변수를 저장하고 재배포한 뒤 프로세스 시작 시각이 다음 값으로 변경됐다.

```text
2026-08-20 18:46:40 KST
```

재배포 후 `/actuator/health`에서 애플리케이션과 DB 상태를 확인했다.

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    }
  }
}
```

`/login`도 HTTP `200`을 반환했다. 이후 별도의 스모크 테스트 계정으로 로그인하고 `/`를 요청해 강의 목록에 `데이터베이스` 과목이 표시되는 것도 확인했다. 신청과 취소 요청은 실행하지 않았다.

```text
login_final_status=200
home_status=200
course_query=OK
```

이 결과로 다음 항목을 확인했다.

- Render 애플리케이션이 Railway MySQL에 연결됨
- HikariCP의 DB 연결 유효성 검사가 성공함
- 애플리케이션이 MySQL의 강의 테이블을 실제 조회하고 정상 응답함

Railway Data 화면에서도 JPA가 생성한 테이블과 초기 데이터 수를 확인했다.

```text
courses = 12
course_applications = 2
```

같은 SQL 화면에서 DB 엔진 버전과 기본 트랜잭션 격리 수준을 조회했다.

```sql
SELECT VERSION() AS mysql_version,
       @@transaction_isolation AS isolation_level;
```

```text
mysql_version = 9.4.0
isolation_level = REPEATABLE-READ
```

따라서 이후 운영 환경 실험은 `MySQL 9.4.0 / REPEATABLE-READ`를 기준으로 기록한다. 로컬 Docker MySQL 8.0 동시성 실험과는 DB 버전이 다르므로 결과를 분리해 해석한다.

## 운영 MySQL 스모크 테스트

다른 사용자 데이터와 충돌하지 않는 고유 테스트 학번으로 다음 순서의 스모크 테스트를 실행했다.

1. `데이터베이스` 과목 정상 신청
2. 같은 과목 중복 신청
3. 신청 과목 취소

결과는 다음과 같다.

```text
신청 전 테스트 계정 과목 수 = 0
정상 신청 후 과목 수 = 1
중복 신청 결과 = "이미 신청된 과목입니다."
취소 후 과목 수 = 0
취소 결과 = "신청 과목이 삭제되었습니다."
```

취소 후 테스트 계정의 신청 내역이 다시 `0`이 되어 테스트 데이터를 원상 복구했다.

## HikariCP 운영 기준선

스모크 테스트가 끝난 유휴 상태에서 Actuator의 `/actuator/metrics/hikaricp.connections.*`를 조회했다.

| 항목 | 실측값 |
| --- | ---: |
| 최대 연결 수 | 10 |
| 사용 중 연결 수 | 0 |
| 유휴 연결 수 | 10 |
| 연결 대기 스레드 수 | 0 |
| 연결 획득 타임아웃 누적 수 | 0 |

현재 최대 연결 수 `10`을 개선 전 기준선으로 유지한다. 이후 부하 테스트에서 `active`, `pending`, `timeout`이 어떻게 변하는지 함께 측정한다. 유휴 상태에서 `pending=0`이라는 사실만으로 부하 상황의 DB 병목 여부를 판단하지 않는다.

## 결과 해석 시 주의사항

- 이전 Render 부하 테스트는 H2 환경의 결과이므로 Railway MySQL 전환 후 결과와 직접 합치지 않는다.
- 로컬 Docker MySQL 동시성 실험과 Render-Railway 운영 실험은 실행 환경이 다르므로 별도로 기록한다.
- Railway Public Access를 통한 외부 연결이므로 Render와 Railway 사이 네트워크 지연도 응답시간에 포함된다.
- 다음 성능 측정 전 `JPA_DDL_AUTO`, HikariCP 풀 크기, 테스트 데이터 수를 명시적으로 고정한다.
