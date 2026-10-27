#!/usr/bin/env bash

set -Eeuo pipefail

N="${1:-100}"
PORT=8088
STORE="data/benchmark-events.jsonl"
SERVER_LOG="data/benchmark-server.log"
URL="http://127.0.0.1:${PORT}"
SERVER_PID=""

# Validate the event count.
if [[ ! "$N" =~ ^[0-9]+$ ]] || (( N < 1 || N > 100000 )); then
    echo "Usage: $0 [N], where N is an integer from 1 to 100000." >&2
    exit 2
fi

# Confirm required tools are available.
MAVEN="${MAVEN:-mvn}"

for tool in curl awk date wc tr; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "Required tool not found: $tool" >&2
        exit 2
    fi
done

if ! command -v "$MAVEN" >/dev/null 2>&1; then
    echo "Maven not found. Install Maven or set MAVEN to its executable." >&2
    exit 2
fi

if [[ ! -f pom.xml || ! -f config/client-spec.json ]]; then
    echo "Run this script from the HealthLogger project root." >&2
    exit 2
fi

mkdir -p data
rm -f "$STORE" "$SERVER_LOG"

cleanup() {
    if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo "Starting HealthLogger server..."

"$MAVEN" -q exec:java \
    "-Dexec.mainClass=com.genkey.healthlogger.HealthLoggerCli" \
    "-Dexec.args=serve --store $STORE" \
    > "$SERVER_LOG" 2>&1 &

SERVER_PID=$!

echo "Waiting for server..."
SERVER_READY=false

for i in {1..30}; do
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Server exited during startup. Check $SERVER_LOG." >&2
        exit 1
    fi

    if curl -fsS --max-time 2 "$URL/health" >/dev/null 2>&1; then
        SERVER_READY=true
        break
    fi

    sleep 0.5
done

if [[ "$SERVER_READY" != true ]]; then
    echo "Server did not become ready. Check $SERVER_LOG." >&2
    exit 1
fi

echo "Server ready. Sending $N events..."

START=$(date +%s%N)

for ((i = 1; i <= N; i++)); do
    HTTP_STATUS=$(
        curl -sS -o /dev/null -w "%{http_code}" \
            --max-time 10 \
            -X POST \
            -H "Content-Type: application/json" \
            -d '{"service":"benchmark-api","status":"ok","timestamp":"2026-10-22T00:00:00Z"}' \
            "$URL/events"
    ) || {
        echo "Request $i failed: curl could not complete the request." >&2
        exit 1
    }

    if [[ "$HTTP_STATUS" != "201" ]]; then
        echo "Request $i failed: expected HTTP 201, received HTTP $HTTP_STATUS." >&2
        exit 1
    fi
done

END=$(date +%s%N)
ELAPSED_NS=$((END - START))

if (( ELAPSED_NS <= 0 )); then
    echo "Invalid elapsed time; cannot calculate throughput." >&2
    exit 1
fi

ELAPSED_SECONDS=$(awk "BEGIN {printf \"%.3f\", $ELAPSED_NS / 1000000000}")
EVENTS_PER_SECOND=$(awk "BEGIN {printf \"%.2f\", $N / ($ELAPSED_NS / 1000000000)}")

if [[ ! -f "$STORE" ]]; then
    echo "Benchmark store was not created." >&2
    exit 1
fi

ACCEPTED=$(wc -l < "$STORE")
ACCEPTED=$(tr -d '[:space:]' <<< "$ACCEPTED")

echo
echo "Benchmark complete."
echo "Requested events: $N"
echo "Accepted events:  $ACCEPTED"
echo "Elapsed time:     ${ELAPSED_SECONDS} seconds"
echo "Events/second:    $EVENTS_PER_SECOND"

if (( ACCEPTED != N )); then
    echo "FAIL: expected $N stored events but found $ACCEPTED." >&2
    exit 1
fi

echo "PASS: all events received HTTP 201 and were stored."
