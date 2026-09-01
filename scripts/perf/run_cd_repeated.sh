#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPETITIONS="${REPETITIONS:-3}"
START_REPETITION="${START_REPETITION:-1}"
VUS="${VUS:-20}"
COOLDOWN_SECONDS="${COOLDOWN_SECONDS:-60}"
BATCH_ID="${BATCH_ID:-$(date +%Y%m%d-%H%M%S)}"
RESUME="${RESUME:-false}"
BATCH_DIR="${ROOT_DIR}/tmp/perf-cd-repeat/${BATCH_ID}"
RAW_DIR="${BATCH_DIR}/raw"
TOKEN_FILE="${PERFORMANCE_TEST_TOKEN_FILE:-${ROOT_DIR}/tmp/.performance-test-token}"
RUN_INDEX="${BATCH_DIR}/run-order.csv"
SUMMARY_CSV="${BATCH_DIR}/combined-summary.csv"
PHASE_CSV="${BATCH_DIR}/combined-phases.csv"
SUMMARY_MD="${BATCH_DIR}/반복-측정-요약.md"

if [[ -z "${PERFORMANCE_TEST_TOKEN:-}" && -f "${TOKEN_FILE}" ]]; then
  PERFORMANCE_TEST_TOKEN="$(tr -d '\r\n' < "${TOKEN_FILE}")"
  export PERFORMANCE_TEST_TOKEN
fi

if [[ -z "${PERFORMANCE_TEST_TOKEN:-}" ]]; then
  echo "PERFORMANCE_TEST_TOKEN이 필요합니다." >&2
  echo "환경 변수로 전달하거나 다음 파일에 한 줄로 저장하세요:" >&2
  echo "  ${TOKEN_FILE}" >&2
  exit 1
fi

mkdir -p "${RAW_DIR}"
if [[ "${RESUME}" != "true" || ! -f "${RUN_INDEX}" ]]; then
  echo "repetition,order_position,scenario,run_id" > "${RUN_INDEX}"
  echo "repetition,order_position,scenario,vus,k6_exit,p95_ms,p99_ms,fail_rate_pct,req_per_sec,rejected_pct,process_cpu_max_pct,system_cpu_max_pct,heap_max_pct,tomcat_busy_pct,gc_pause_seconds,hikari_active_pct,hikari_pending_max,hikari_acquire_avg_ms,hikari_acquire_max_ms,hikari_usage_avg_ms,hikari_usage_max_ms,hikari_timeouts,db_applied,db_actual,count_mismatch" > "${SUMMARY_CSV}"
  echo "repetition,order_position,scenario,vus,transaction_avg_ms,transaction_p95_ms,conditional_update_avg_ms,conditional_update_p95_ms,save_flush_avg_ms,save_flush_p95_ms" > "${PHASE_CSV}"
fi

already_completed() {
  local repetition="$1"
  local scenario="$2"
  awk -F, -v rep="${repetition}" -v target="${scenario}" \
    'NR > 1 && $1 == rep && $3 == target { found=1 } END { exit !found }' "${SUMMARY_CSV}"
}

run_scenario() {
  local repetition="$1"
  local position="$2"
  local scenario="$3"
  local run_id="${BATCH_ID}-r${repetition}-p${position}-${scenario}"
  local attempt=1

  if already_completed "${repetition}" "${scenario}"; then
    echo "이미 완료되어 건너뜀: 반복 ${repetition}, ${scenario}"
    return 0
  fi

  echo
  echo "===== 반복 ${repetition}/${REPETITIONS}, 순서 ${position}: ${scenario} ====="

  while true; do
    if PERF_RUN_ID="${run_id}" \
      OUT_ROOT="${RAW_DIR}" \
      SCENARIOS="${scenario}" \
      VUS_LIST="${VUS}" \
      STOP_ON_THRESHOLD_FAILURE=false \
      "${ROOT_DIR}/scripts/perf/run_bottleneck_isolation.sh"; then
      break
    fi

    if ((attempt >= 3)); then
      echo "세 번 재시도 후 실패: 반복 ${repetition}, ${scenario}" >&2
      exit 1
    fi
    attempt=$((attempt + 1))
    echo "일시 오류로 10초 후 재시도(${attempt}/3)..." >&2
    sleep 10
  done

  echo "${repetition},${position},${scenario},${run_id}" >> "${RUN_INDEX}"

  tail -n +2 "${RAW_DIR}/${run_id}/summary.csv" |
    awk -F, -v rep="${repetition}" -v pos="${position}" \
      'BEGIN { OFS="," } { print rep, pos, $0 }' >> "${SUMMARY_CSV}"
  tail -n +2 "${RAW_DIR}/${run_id}/registration-phases.csv" |
    awk -F, -v rep="${repetition}" -v pos="${position}" \
      'BEGIN { OFS="," } { print rep, pos, $0 }' >> "${PHASE_CSV}"
}

for ((repetition = START_REPETITION; repetition <= REPETITIONS; repetition++)); do
  if ((repetition % 2 == 1)); then
    scenarios=(distributed-apply same-course-apply)
  else
    scenarios=(same-course-apply distributed-apply)
  fi

  position=1
  for scenario in "${scenarios[@]}"; do
    run_scenario "${repetition}" "${position}" "${scenario}"
    position=$((position + 1))

    if ! ((repetition == REPETITIONS && position == 3)); then
      echo "다음 실행 전 ${COOLDOWN_SECONDS}초 대기..."
      sleep "${COOLDOWN_SECONDS}"
    fi
  done
done

cat > "${SUMMARY_MD}" <<MD
# C/D 20 VU 반복 측정 요약

- Batch ID: ${BATCH_ID}
- 반복 횟수: ${REPETITIONS}회
- VU: ${VUS}
- 실행 순서: 홀수 회차 C -> D, 짝수 회차 D -> C
- 실행 사이 대기: ${COOLDOWN_SECONDS}초
- C: 여러 강의 분산 신청(distributed-apply)
- D: 동일 강의 집중 신청(same-course-apply)

## 결과 파일

- 전체 서버·HTTP 결과: combined-summary.csv
- 트랜잭션 단계별 결과: combined-phases.csv
- 실행 순서: run-order.csv
- 회차별 원본: raw/

## 해석 원칙

1. 세 번 모두 D의 지연 증가와 처리량 감소 방향이 반복되는지 확인한다.
2. transaction, conditional UPDATE, saveAndFlush의 p95를 서로 더하거나 빼지 않는다.
3. 각 단계의 C 대비 D 비율을 독립적으로 비교해 지배 구간을 찾는다.
4. Hikari pending 증가는 원인이 아니라 긴 트랜잭션·락 대기의 결과일 수 있다.
5. DB applied와 actual이 일치하고 count mismatch가 0인지 성능보다 먼저 확인한다.
MD

echo
echo "C/D 반복 측정 완료:"
echo "  ${SUMMARY_MD}"
echo "  ${SUMMARY_CSV}"
echo "  ${PHASE_CSV}"
echo "  ${RUN_INDEX}"
