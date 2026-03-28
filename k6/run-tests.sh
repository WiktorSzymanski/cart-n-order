#!/usr/bin/env bash
# run-tests.sh — k6 load test orchestrator
#
# Usage:
#   ./k6/run-tests.sh [OPTIONS] [SYSTEM] [BASE_URL]
#
# Options:
#   --docker    Run k6 inside Docker (via docker compose run) instead of a
#               local k6 binary. The app and postgres services must already be
#               running via docker compose up -d.
#
# Arguments:
#   SYSTEM    Name tag for the system under test (default: crud).
#             When the ES implementation is ready, pass "es" here.
#   BASE_URL  Base URL of the running service.
#             Default (local):  http://localhost:8080
#             Default (docker): http://app:8080
#
# Examples:
#   # Local k6 binary
#   ./k6/run-tests.sh
#   ./k6/run-tests.sh crud http://localhost:8080
#
#   # Docker mode (no local k6 needed)
#   docker compose up -d postgres app   # start the SUT first
#   ./k6/run-tests.sh --docker
#   ./k6/run-tests.sh --docker es http://app:8081
#
# Prerequisites (local mode):
#   - k6 installed: https://k6.io/docs/getting-started/installation/
#   - Service running at BASE_URL
#   - Run from the repository root directory
#
# Prerequisites (Docker mode):
#   - Docker Compose available
#   - docker compose up -d (postgres + app) already done
#   - Run from the repository root directory
#
# Outputs (written to k6/results/):
#   ${SYSTEM}-D1-summary.json             — data-prep end-of-run summary
#   ${SYSTEM}-${SCENARIO}.json            — full time-series metrics
#   ${SYSTEM}-${SCENARIO}-summary.json    — end-of-run percentile summary
#
# TODO (ES comparison):
#   When the ES service is implemented, run both systems and compare results:
#     ./k6/run-tests.sh crud http://localhost:8080
#     ./k6/run-tests.sh es   http://localhost:8081
#   docker-compose-es.yml will be added alongside docker-compose.yml.
#
# Prometheus remote-write:
#   To enable live metric export, add to each k6 run:
#     --out experimental-prometheus-rw=http://localhost:9090/api/v1/write

set -euo pipefail

# ---- Helper functions ---------------------------------------------------

scenario_description() {
  case "$1" in
    B1) echo "Baseline Capacity (30 min, 10→200 VU)" ;;
    W1) echo "Conflict-Heavy / Inventory Contention (20 min, 50→500 VU)" ;;
    R1) echo "Read-Heavy / CQRS Projections (25 min, 100→1000 VU)" ;;
    S1) echo "Soak Test (130 min, 140 VU sustained)" ;;
    *)  echo "Unknown scenario" ;;
  esac
}

# ---- Parse arguments ----------------------------------------------------
DOCKER_MODE=false
POSITIONAL=()

for arg in "$@"; do
  case "$arg" in
    --docker) DOCKER_MODE=true ;;
    *)        POSITIONAL+=("$arg") ;;
  esac
done

SYSTEM="${POSITIONAL[0]:-crud}"

if $DOCKER_MODE; then
  BASE_URL="${POSITIONAL[1]:-http://app:8080}"
  K6_CMD="docker compose run --rm k6 run"
else
  BASE_URL="${POSITIONAL[1]:-http://localhost:8080}"
  K6_CMD="k6 run"
fi

RESULTS_DIR="k6/results"
SCRIPTS_DIR="k6/scripts"

echo "======================================================"
echo "  k6 Load Test Suite"
echo "  Mode    : $($DOCKER_MODE && echo docker || echo local)"
echo "  System  : ${SYSTEM}"
echo "  Base URL: ${BASE_URL}"
echo "  Results : ${RESULTS_DIR}/"
echo "======================================================"

mkdir -p "${RESULTS_DIR}"

# ---- Health check -------------------------------------------------------
echo ""
echo ">>> Waiting for service to be healthy…"
for i in $(seq 1 30); do
  if curl -sf "${BASE_URL}/api-docs" > /dev/null 2>&1; then
    echo "    Service is up."
    break
  fi
  if [ "${i}" -eq 30 ]; then
    echo "ERROR: Service not reachable at ${BASE_URL} after 30s. Aborting."
    exit 1
  fi
  echo "    Attempt ${i}/30 — retrying in 1s…"
  sleep 1
done

# ---- Data preparation (D1) ----------------------------------------------
echo ""
echo ">>> D1 — Data preparation (10k products, 1k customers)"
$K6_CMD "${SCRIPTS_DIR}/data-prep.js" \
  -e BASE_URL="${BASE_URL}" \
  --tag system:"${SYSTEM}" \
  --summary-export="${RESULTS_DIR}/${SYSTEM}-D1-summary.json"

echo "    test-data.json written to ${SCRIPTS_DIR}/test-data.json"

# ---- Load scenarios ------------------------------------------------------
SCENARIOS=("B1" "W1" "R1" "S1")

for SCENARIO in "${SCENARIOS[@]}"; do
  echo ""
  echo ">>> ${SCENARIO} — $(scenario_description "${SCENARIO}")"
  $K6_CMD "${SCRIPTS_DIR}/scenarios/${SCENARIO}.js" \
    -e BASE_URL="${BASE_URL}" \
    --tag system:"${SYSTEM}" \
    --out "json=${RESULTS_DIR}/${SYSTEM}-${SCENARIO}.json" \
    --summary-export="${RESULTS_DIR}/${SYSTEM}-${SCENARIO}-summary.json"

  echo "    Results: ${RESULTS_DIR}/${SYSTEM}-${SCENARIO}.json"
done

echo ""
echo "======================================================"
echo "  All scenarios complete for system: ${SYSTEM}"
echo "  Results saved to: ${RESULTS_DIR}/"
echo "======================================================"
