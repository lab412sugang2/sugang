#!/usr/bin/env bash

set -euo pipefail

# MySQL performance_schema는 순간적인 대기만 보여주므로 짧은 간격으로 반복 수집한다.
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3307}"
DB_NAME="${DB_NAME:-sugang}"
DB_USERNAME="${DB_USERNAME:-root}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-0.2}"
DURATION_SECONDS="${DURATION_SECONDS:-180}"
OUT_DIR="${OUT_DIR:-$(pwd)/tmp/mysql-lock-waits/$(date +%Y%m%d-%H%M%S)}"

if ! command -v mysql >/dev/null 2>&1; then
  echo "필수 명령어가 없습니다: mysql" >&2
  exit 1
fi

if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "DB_PASSWORD 환경 변수가 필요합니다." >&2
  exit 1
fi

if ! [[ "${DURATION_SECONDS}" =~ ^[0-9]+$ ]] || ((DURATION_SECONDS < 1)); then
  echo "DURATION_SECONDS는 1 이상의 정수여야 합니다." >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"

# MYSQL_PWD는 mysql 프로세스 인자에 비밀번호가 노출되지 않도록 사용한다.
export MYSQL_PWD="${DB_PASSWORD}"
MYSQL_ARGS=(
  --protocol=TCP
  --host="${DB_HOST}"
  --port="${DB_PORT}"
  --user="${DB_USERNAME}"
  --database="${DB_NAME}"
  --batch
  --raw
  --skip-column-names
)

if ! mysql "${MYSQL_ARGS[@]}" -e 'SELECT 1 FROM performance_schema.data_lock_waits LIMIT 1;' >/dev/null 2>&1; then
  echo "performance_schema.data_lock_waits에 접근할 수 없습니다." >&2
  echo "DB 계정 권한과 MySQL 버전을 확인하세요." >&2
  exit 1
fi

cat > "${OUT_DIR}/metadata.txt" <<META
db_host=${DB_HOST}
db_port=${DB_PORT}
db_name=${DB_NAME}
db_username=${DB_USERNAME}
interval_seconds=${INTERVAL_SECONDS}
duration_seconds=${DURATION_SECONDS}
started_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
META

{
  mysql "${MYSQL_ARGS[@]}" -e 'SELECT VERSION(); SELECT @@transaction_isolation;'
} >> "${OUT_DIR}/metadata.txt"

cat > "${OUT_DIR}/lock-wait-samples.csv" <<'CSV'
sampled_at_utc,wait_count
CSV

cat > "${OUT_DIR}/lock-wait-details.tsv" <<'TSV'
sampled_at_utc	engine	requesting_transaction_id	blocking_transaction_id	requesting_thread_id	blocking_thread_id	object_schema	object_name	index_name	requesting_lock_type	requesting_lock_mode	blocking_lock_type	blocking_lock_mode	lock_data
TSV

wait_query='
SELECT
  UTC_TIMESTAMP(6),
  COUNT(*)
FROM performance_schema.data_lock_waits
;'

detail_query='
SELECT
  UTC_TIMESTAMP(6),
  wq.ENGINE,
  wq.REQUESTING_ENGINE_TRANSACTION_ID,
  wq.BLOCKING_ENGINE_TRANSACTION_ID,
  wq.REQUESTING_THREAD_ID,
  wq.BLOCKING_THREAD_ID,
  waiting.OBJECT_SCHEMA,
  waiting.OBJECT_NAME,
  waiting.INDEX_NAME,
  waiting.LOCK_TYPE,
  waiting.LOCK_MODE,
  blocking.LOCK_TYPE,
  blocking.LOCK_MODE,
  waiting.LOCK_DATA
FROM performance_schema.data_lock_waits AS wq
JOIN performance_schema.data_locks AS waiting
  ON waiting.ENGINE = wq.ENGINE
 AND waiting.ENGINE_LOCK_ID = wq.REQUESTING_ENGINE_LOCK_ID
JOIN performance_schema.data_locks AS blocking
  ON blocking.ENGINE = wq.ENGINE
 AND blocking.ENGINE_LOCK_ID = wq.BLOCKING_ENGINE_LOCK_ID
;'

end_at=$((SECONDS + DURATION_SECONDS))
sample_count=0
detail_count=0

while ((SECONDS < end_at)); do
  wait_row="$(mysql "${MYSQL_ARGS[@]}" -e "${wait_query}" 2>/dev/null || true)"
  if [[ -n "${wait_row}" ]]; then
    printf '%s\n' "${wait_row//$'\t'/,}" >> "${OUT_DIR}/lock-wait-samples.csv"
    wait_count="${wait_row##*$'\t'}"
    if [[ "${wait_count}" != "0" ]]; then
      details="$(mysql "${MYSQL_ARGS[@]}" -e "${detail_query}" 2>/dev/null || true)"
      if [[ -n "${details}" ]]; then
        printf '%s\n' "${details}" >> "${OUT_DIR}/lock-wait-details.tsv"
        detail_count=$((detail_count + 1))
      fi
    fi
  fi
  sample_count=$((sample_count + 1))
  sleep "${INTERVAL_SECONDS}"
done

cat >> "${OUT_DIR}/metadata.txt" <<META
finished_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
sample_count=${sample_count}
detail_query_hits=${detail_count}
META

echo "Lock Wait 수집 완료:"
echo "  ${OUT_DIR}/lock-wait-samples.csv"
echo "  ${OUT_DIR}/lock-wait-details.tsv"
echo "  ${OUT_DIR}/metadata.txt"
