#!/usr/bin/env bash

set -euo pipefail

# Benchmark size: 200 groups x 50 keys = 10,000 values per layer.
GROUP_COUNT=200
KEYS_PER_GROUP=50
VALUES_PER_LAYER=$((GROUP_COUNT * KEYS_PER_GROUP))

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BENCHMARK_DIR="$ROOT_DIR/target/benchmark"

BASE_FILE="$BENCHMARK_DIR/base.json"
OVERLAY_FILE="$BENCHMARK_DIR/overlay.json"
OUTPUT_FILE="$BENCHMARK_DIR/merged.json"
RESULT_FILE="$ROOT_DIR/docs/BENCHMARKS.md"

mkdir -p "$BENCHMARK_DIR"

generate_layer() {
    local output_file="$1"
    local port="$2"
    local suffix="$3"

    awk \
        -v groups="$GROUP_COUNT" \
        -v keys="$KEYS_PER_GROUP" \
        -v port="$port" \
        -v suffix="$suffix" '
        BEGIN {
            print "{"
            print "  \"app\": {"
            print "    \"name\": \"configmaster-benchmark\","
            print "    \"port\": " port
            print "  },"
            print "  \"benchmark\": {"

            for (g = 1; g <= groups; g++) {
                printf "    \"group%d\": {\n", g

                for (k = 1; k <= keys; k++) {
                    printf "      \"key%d\": \"value-%d-%d-%s\"", \
                        k, g, k, suffix

                    if (k < keys) {
                        printf ","
                    }

                    printf "\n"
                }

                printf "    }"

                if (g < groups) {
                    printf ","
                }

                printf "\n"
            }

            print "  }"
            print "}"
        }
    ' > "$output_file"
}

echo "Generating benchmark JSON..."
generate_layer "$BASE_FILE" 8080 "base"
generate_layer "$OVERLAY_FILE" 9090 "overlay"

echo "Generated $VALUES_PER_LAYER benchmark values per layer."

echo "Compiling ConfigMaster..."
(
    cd "$ROOT_DIR"
    mvn -q -DskipTests compile dependency:copy-dependencies \
        -DoutputDirectory=target/dependency
)

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) CP_SEPARATOR=";" ;;
    *) CP_SEPARATOR=":" ;;
esac

echo "Running ConfigMaster Java CLI benchmark..."

TIMEFORMAT='%3R'

(
    cd "$ROOT_DIR"
    { time java -cp "target/classes${CP_SEPARATOR}target/dependency/*" \
        com.genkey.configmaster.ConfigMasterCli \
        merge target/benchmark/base.json target/benchmark/overlay.json \
        > "$OUTPUT_FILE"; } 2> "$BENCHMARK_DIR/time.txt"
)

if [[ ! -s "$OUTPUT_FILE" ]]; then
    echo "Benchmark failed: merged output was not created." >&2
    exit 1
fi

elapsed_ms=$(awk 'END {printf "%.0f", $1 * 1000}' "$BENCHMARK_DIR/time.txt")

echo "Java CLI wall time (including JVM startup): ${elapsed_ms} ms."

if ! grep -q '^## Java CLI results$' "$RESULT_FILE"; then
    cat >> "$RESULT_FILE" <<'EOF'

## Java CLI results

These measurements include JVM startup but exclude Maven startup.

| Date                | Groups | Keys per group | Values per layer | Java CLI wall time |
|---------------------|-------:|---------------:|-----------------:|-------------------:|
EOF
fi

printf '| %-19s | %6s | %14s | %16s | %18s |\n' \
    "$(date '+%Y-%m-%d %H:%M:%S')" \
    "$GROUP_COUNT" \
    "$KEYS_PER_GROUP" \
    "$VALUES_PER_LAYER" \
    "${elapsed_ms} ms" >> "$RESULT_FILE"

echo "Benchmark result recorded in docs/BENCHMARKS.md."
