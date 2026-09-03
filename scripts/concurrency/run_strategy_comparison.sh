#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_ID="${RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
OUT_DIR="${CONCURRENCY_RESULT_DIR:-${ROOT_DIR}/tmp/concurrency-strategy-comparison/${RUN_ID}}"

export DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:3307/sugang?serverTimezone=Asia/Seoul&characterEncoding=UTF-8}"
export DB_USERNAME="${DB_USERNAME:-root}"
export JPA_DDL_AUTO="${JPA_DDL_AUTO:-update}"
export CONCURRENCY_RESULT_DIR="${OUT_DIR}"
JAVA_RUNTIME="$(java -version 2>&1 | head -n 1)"

if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "DB_PASSWORD 환경 변수가 필요합니다." >&2
  echo "예: DB_PASSWORD='<로컬 MySQL 비밀번호>' ./scripts/concurrency/run_strategy_comparison.sh" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"

cat > "${OUT_DIR}/experiment.txt" <<EOF
run_id=${RUN_ID}
java_source_compatibility=17
java_runtime=${JAVA_RUNTIME}
db_url=${DB_URL%%\?*}
requests=100
threads=100
capacity=10
repetitions=3
hikari_max=32
execution_order=PESSIMISTIC-OPTIMISTIC-CONDITIONAL / OPTIMISTIC-CONDITIONAL-PESSIMISTIC / CONDITIONAL-PESSIMISTIC-OPTIMISTIC
EOF

set +e
"${ROOT_DIR}/gradlew" test \
  --tests 'sugang.service.ConcurrencyStrategyComparisonTest' \
  --rerun-tasks \
  --no-daemon 2>&1 | tee "${OUT_DIR}/gradle-test.log"
test_exit=${PIPESTATUS[0]}
set -e

result_xml="${ROOT_DIR}/build/test-results/test/TEST-sugang.service.ConcurrencyStrategyComparisonTest.xml"
if [[ -f "${result_xml}" ]]; then
  cp "${result_xml}" "${OUT_DIR}/TEST-sugang.service.ConcurrencyStrategyComparisonTest.xml"
fi

echo
if ((test_exit == 0)); then
  echo "동시성 전략 반복 비교 완료:"
else
  echo "동시성 전략 반복 비교 실패(exit=${test_exit}):" >&2
fi
echo "  ${OUT_DIR}/summary.md"
echo "  ${OUT_DIR}/results.csv"
echo "  ${OUT_DIR}/experiment.txt"

exit "${test_exit}"
