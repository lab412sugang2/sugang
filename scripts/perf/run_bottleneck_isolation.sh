#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE_URL="${BASE_URL:-https://sugang-5de3.onrender.com}"
PROM_URL="${PROM_URL:-http://127.0.0.1:9090}"
APP_TAG="${APP_TAG:-sugang-local}"
HIKARI_POOL="${HIKARI_POOL:-HikariPool-1}"
PERFORMANCE_TEST_TOKEN="${PERFORMANCE_TEST_TOKEN:-}"
PERFORMANCE_TEST_TOKEN="${PERFORMANCE_TEST_TOKEN//$'\r'/}"
PERFORMANCE_TEST_TOKEN="${PERFORMANCE_TEST_TOKEN//$'\n'/}"
RAMP_DURATION="${RAMP_DURATION:-30s}"
HOLD_DURATION="${HOLD_DURATION:-2m30s}"
OBS_WINDOW="${OBS_WINDOW:-1m}"
VUS_LIST="${VUS_LIST:-10 50 100}"
SCENARIOS="${SCENARIOS:-ping course-read distributed-apply same-course-apply}"
COURSE_ID="${COURSE_ID:-1}"
COURSE_COUNT="${COURSE_COUNT:-20}"
CAPACITY="${CAPACITY:-100000}"
STOP_ON_THRESHOLD_FAILURE="${STOP_ON_THRESHOLD_FAILURE:-true}"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${ROOT_DIR}/tmp/perf-bottleneck-isolation/${RUN_ID}"

for command in k6 jq curl awk; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "필수 명령어가 없습니다: ${command}" >&2
    exit 1
  fi
done

requires_mutation_token=false
for scenario in ${SCENARIOS}; do
  case "${scenario}" in
    distributed-apply|same-course-apply) requires_mutation_token=true ;;
  esac
done

if [[ "${requires_mutation_token}" == "true" && -z "${PERFORMANCE_TEST_TOKEN}" ]]; then
  echo "C/D 시나리오에는 PERFORMANCE_TEST_TOKEN이 필요합니다." >&2
  exit 1
fi

# k6는 시스템 환경 변수를 __ENV로 전달한다. 토큰을 -e 인자로 넘겨 프로세스 목록에 노출하지 않는다.
export PERFORMANCE_TEST_TOKEN

mkdir -p "${OUT_DIR}"

script_for() {
  case "$1" in
    ping) echo "${ROOT_DIR}/k6/scenarios/isolation-ping.js" ;;
    course-read) echo "${ROOT_DIR}/k6/scenarios/isolation-course-read.js" ;;
    distributed-apply) echo "${ROOT_DIR}/k6/scenarios/isolation-distributed-apply.js" ;;
    same-course-apply) echo "${ROOT_DIR}/k6/scenarios/isolation-same-course-apply.js" ;;
    *) echo "알 수 없는 시나리오: $1" >&2; exit 1 ;;
  esac
}

is_mutation_scenario() {
  [[ "$1" == "distributed-apply" || "$1" == "same-course-apply" ]]
}

curl_with_token() {
  # curl의 프로세스 인자에 토큰이 노출되지 않도록 설정을 표준 입력으로 전달한다.
  printf 'header = "X-Performance-Test-Token: %s"\n' "${PERFORMANCE_TEST_TOKEN}" |
    curl --config - "$@"
}

cleanup_fixtures() {
  if [[ -z "${PERFORMANCE_TEST_TOKEN}" ]]; then
    return 0
  fi
  curl_with_token -fsS -X POST \
    "${BASE_URL}/performance/fixtures/cleanup" >/dev/null 2>&1 || true
}

trap cleanup_fixtures EXIT

ping_status="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/performance/ping" || true)"
if [[ "${ping_status}" != "200" ]]; then
  echo "성능 테스트 API가 준비되지 않았습니다: GET /performance/ping -> ${ping_status}" >&2
  echo "Render에서 APP_PERFORMANCE_TEST_ENABLED=true 설정과 재배포를 확인하세요." >&2
  exit 1
fi

if [[ "${requires_mutation_token}" == "true" ]]; then
  token_status="$(curl_with_token -sS -o /dev/null -w '%{http_code}' -X POST \
    "${BASE_URL}/performance/fixtures/cleanup" || true)"
  if [[ "${token_status}" != "200" ]]; then
    echo "성능 테스트 토큰이 Render 설정과 일치하지 않습니다: ${token_status}" >&2
    exit 1
  fi
fi

if ! curl -fsS "${PROM_URL}/-/ready" >/dev/null 2>&1; then
  echo "경고: Prometheus가 준비되지 않아 서버 메트릭은 NaN으로 저장됩니다: ${PROM_URL}" >&2
fi

prom_query() {
  local query="$1"
  local response
  response="$(
    curl -fsSG "${PROM_URL}/api/v1/query" \
      --data-urlencode "query=${query}" 2>/dev/null || true
  )"
  if [[ -z "${response}" ]]; then
    echo "NaN"
    return 0
  fi
  jq -r '.data.result[0].value[1] // "NaN"' <<< "${response}"
}

fmt2() {
  local value="${1:-NaN}"
  case "${value}" in
    ""|NaN|null|-) printf '%s' "${value:-NaN}" ;;
    *) awk -v value="${value}" 'BEGIN { printf "%.2f", value }' ;;
  esac
}

to_percent() {
  local value="${1:-NaN}"
  case "${value}" in
    ""|NaN|null|-) printf '%s' "${value:-NaN}" ;;
    *) awk -v value="${value}" 'BEGIN { printf "%.2f", value * 100 }' ;;
  esac
}

RESULT_MD="${OUT_DIR}/summary.md"
RESULT_CSV="${OUT_DIR}/summary.csv"

cat > "${RESULT_MD}" <<MD
# A/B/C/D 병목 분리 테스트 요약

- Run ID: ${RUN_ID}
- Base URL: ${BASE_URL}
- Scenarios: ${SCENARIOS}
- VUs: ${VUS_LIST}
- Ramp: ${RAMP_DURATION}
- Hold: ${HOLD_DURATION}
- Observation window: ${OBS_WINDOW}

| Scenario | VUs | k6 exit | success p95 ms | success p99 ms | fail % | req/s | rejected % | Process CPU max % | System CPU max % | Heap max % | Tomcat busy/max % | GC pause sec | Hikari active/max % | Hikari pending max | Hikari acquire avg ms | Hikari acquire max ms | Hikari usage avg ms | Hikari usage max ms | Hikari timeouts | DB applied | DB actual | Count mismatch |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
MD

echo "scenario,vus,k6_exit,p95_ms,p99_ms,fail_rate_pct,req_per_sec,rejected_pct,process_cpu_max_pct,system_cpu_max_pct,heap_max_pct,tomcat_busy_pct,gc_pause_seconds,hikari_active_pct,hikari_pending_max,hikari_acquire_avg_ms,hikari_acquire_max_ms,hikari_usage_avg_ms,hikari_usage_max_ms,hikari_timeouts,db_applied,db_actual,count_mismatch" > "${RESULT_CSV}"

echo "run_id=${RUN_ID}"
echo "base_url=${BASE_URL}"
echo "prometheus=${PROM_URL}"
echo "app_tag=${APP_TAG}"
echo "scenarios=${SCENARIOS}"
echo "vus_list=${VUS_LIST}"
echo "ramp=${RAMP_DURATION}, hold=${HOLD_DURATION}, obs_window=${OBS_WINDOW}"

stop_reason=""
for scenario in ${SCENARIOS}; do
  script="$(script_for "${scenario}")"

  for vus in ${VUS_LIST}; do
    json_file="${OUT_DIR}/${scenario}-${vus}.json"
    log_file="${OUT_DIR}/${scenario}-${vus}.log"

    echo
    echo "=== ${scenario} / ${vus} VUs ==="
    if is_mutation_scenario "${scenario}"; then
      cleanup_fixtures
    fi

    set +e
    k6 run \
      --summary-export "${json_file}" \
      -e BASE_URL="${BASE_URL}" \
      -e AUTO_CLEANUP=false \
      -e VUS_TARGET="${vus}" \
      -e RAMP_DURATION="${RAMP_DURATION}" \
      -e HOLD_DURATION="${HOLD_DURATION}" \
      -e COURSE_ID="${COURSE_ID}" \
      -e COURSE_COUNT="${COURSE_COUNT}" \
      -e CAPACITY="${CAPACITY}" \
      -e RUN_ID="${RUN_ID}-${scenario}-${vus}" \
      "${script}" 2>&1 | tee "${log_file}"
    k6_exit="${PIPESTATUS[0]}"
    set -e

    if [[ -f "${json_file}" ]]; then
      k6_p95="$(jq -r '.metrics.scenario_request_duration["p(95)"] // .metrics.scenario_request_duration.values["p(95)"] // "NaN"' "${json_file}")"
      k6_p99="$(jq -r '.metrics.scenario_request_duration["p(99)"] // .metrics.scenario_request_duration.values["p(99)"] // "NaN"' "${json_file}")"
      fail_rate="$(jq -r '.metrics.scenario_request_failed.value // .metrics.scenario_request_failed.values.rate // "NaN"' "${json_file}")"
      req_rate="$(jq -r '.metrics.scenario_requests.rate // .metrics.scenario_requests.values.rate // "NaN"' "${json_file}")"
      rejected_rate="$(jq -r '.metrics.application_rejected.value // .metrics.application_rejected.values.rate // "-"' "${json_file}")"
    else
      k6_p95="NaN"
      k6_p99="NaN"
      fail_rate="NaN"
      req_rate="NaN"
      rejected_rate="-"
    fi

    process_cpu="$(prom_query "100 * max_over_time(process_cpu_usage{application=\"${APP_TAG}\"}[${OBS_WINDOW}])")"
    system_cpu="$(prom_query "100 * max_over_time(system_cpu_usage{application=\"${APP_TAG}\"}[${OBS_WINDOW}])")"
    heap_max="$(prom_query "100 * max_over_time(((sum(jvm_memory_used_bytes{application=\"${APP_TAG}\",area=\"heap\"}) / sum(jvm_memory_max_bytes{application=\"${APP_TAG}\",area=\"heap\"})))[${OBS_WINDOW}:5s])")"
    tomcat_busy="$(prom_query "100 * max_over_time(((sum(tomcat_threads_busy_threads{application=\"${APP_TAG}\"}) / clamp_min(sum(tomcat_threads_config_max_threads{application=\"${APP_TAG}\"}), 1)))[${OBS_WINDOW}:5s])")"
    gc_pause="$(prom_query "sum(increase(jvm_gc_pause_seconds_sum{application=\"${APP_TAG}\"}[${OBS_WINDOW}]))")"
    hikari_active="$(prom_query "100 * max_over_time(((max(hikaricp_connections_active{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"}) / max(hikaricp_connections_max{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"})))[${OBS_WINDOW}:5s])")"
    hikari_pending="$(prom_query "max_over_time(hikaricp_connections_pending{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"}[${OBS_WINDOW}])")"
    hikari_acquire_avg="$(prom_query "1000 * sum(increase(hikaricp_connections_acquire_seconds_sum{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"}[${OBS_WINDOW}])) / clamp_min(sum(increase(hikaricp_connections_acquire_seconds_count{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"}[${OBS_WINDOW}])), 1)")"
    hikari_acquire_max="$(prom_query "1000 * max_over_time(hikaricp_connections_acquire_seconds_max{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"}[${OBS_WINDOW}])")"
    hikari_usage_avg="$(prom_query "1000 * sum(increase(hikaricp_connections_usage_seconds_sum{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"}[${OBS_WINDOW}])) / clamp_min(sum(increase(hikaricp_connections_usage_seconds_count{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"}[${OBS_WINDOW}])), 1)")"
    hikari_usage_max="$(prom_query "1000 * max_over_time(hikaricp_connections_usage_seconds_max{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"}[${OBS_WINDOW}])")"
    hikari_timeouts="$(prom_query "increase(hikaricp_connections_timeout_total{application=\"${APP_TAG}\",pool=\"${HIKARI_POOL}\"}[${OBS_WINDOW}])")"

    db_applied="-"
    db_actual="-"
    count_mismatch="-"
    if is_mutation_scenario "${scenario}"; then
      status_file="${OUT_DIR}/${scenario}-${vus}-final-state.json"
      if curl_with_token -fsS \
        "${BASE_URL}/performance/fixtures/status" > "${status_file}"; then
        db_applied="$(jq -r '[.courses[].appliedCount] | add // 0' "${status_file}")"
        db_actual="$(jq -r '[.courses[].actualApplications] | add // 0' "${status_file}")"
        count_mismatch="$(jq -r '[.courses[] | select(.countMatches == false)] | length' "${status_file}")"
      else
        db_applied="NaN"
        db_actual="NaN"
        count_mismatch="NaN"
      fi
      cleanup_fixtures
    fi

    printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
      "${scenario}" "${vus}" "${k6_exit}" \
      "$(fmt2 "${k6_p95}")" "$(fmt2 "${k6_p99}")" "$(to_percent "${fail_rate}")" \
      "$(fmt2 "${req_rate}")" "$(to_percent "${rejected_rate}")" \
      "$(fmt2 "${process_cpu}")" "$(fmt2 "${system_cpu}")" "$(fmt2 "${heap_max}")" \
      "$(fmt2 "${tomcat_busy}")" "$(fmt2 "${gc_pause}")" "$(fmt2 "${hikari_active}")" \
      "$(fmt2 "${hikari_pending}")" "$(fmt2 "${hikari_acquire_avg}")" \
      "$(fmt2 "${hikari_acquire_max}")" "$(fmt2 "${hikari_usage_avg}")" \
      "$(fmt2 "${hikari_usage_max}")" "$(fmt2 "${hikari_timeouts}")" \
      "${db_applied}" "${db_actual}" "${count_mismatch}" >> "${RESULT_MD}"

    printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
      "${scenario}" "${vus}" "${k6_exit}" \
      "$(fmt2 "${k6_p95}")" "$(fmt2 "${k6_p99}")" "$(to_percent "${fail_rate}")" \
      "$(fmt2 "${req_rate}")" "$(to_percent "${rejected_rate}")" \
      "$(fmt2 "${process_cpu}")" "$(fmt2 "${system_cpu}")" "$(fmt2 "${heap_max}")" \
      "$(fmt2 "${tomcat_busy}")" "$(fmt2 "${gc_pause}")" "$(fmt2 "${hikari_active}")" \
      "$(fmt2 "${hikari_pending}")" "$(fmt2 "${hikari_acquire_avg}")" \
      "$(fmt2 "${hikari_acquire_max}")" "$(fmt2 "${hikari_usage_avg}")" \
      "$(fmt2 "${hikari_usage_max}")" "$(fmt2 "${hikari_timeouts}")" \
      "${db_applied}" "${db_actual}" "${count_mismatch}" >> "${RESULT_CSV}"

    if [[ "${STOP_ON_THRESHOLD_FAILURE}" == "true" && "${k6_exit}" -ne 0 ]]; then
      stop_reason="${scenario}/${vus} VU에서 k6 exit ${k6_exit} 발생"
      break 2
    fi
  done
done

cleanup_fixtures
trap - EXIT

echo
echo "저장 완료:"
echo "  ${RESULT_MD}"
echo "  ${RESULT_CSV}"

if [[ -n "${stop_reason}" ]]; then
  echo
  echo "안전 중단: ${stop_reason}"
  exit 3
fi
