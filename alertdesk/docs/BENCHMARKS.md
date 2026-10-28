# Week 5 performance baseline and observability

## Benchmark

`scripts/benchmark.py` measures end-to-end HTTP latency for ticket creation and
listing against a running AlertDesk instance. It performs five warm-up pairs by
default, then records 100 measured create/list pairs. Results report mean,
median, p95, minimum, and maximum latency in milliseconds as JSON. The script
uses Python's standard library and the analyst bearer token configured by the
sample client spec.

Start the service in one terminal with `python -m alertdesk.cli`, then run
`python scripts/benchmark.py` in another. Options include `--base-url`, `--token`,
`--iterations`, and `--warmup`. Benchmark records use unique synthetic titles
and are persisted by the target service; use a disposable database for repeatable
runs. Listing time naturally rises as the table grows because the endpoint returns
all tickets. Results depend on machine load, database size, and whether the API
is local, so compare runs only under similar conditions.

## Baseline

No latency target is specified in the client brief. The following local
development run is a recorded baseline; it is not a production service-level
objective.

### Recorded run

- Date: 2026-10-28
- Command: `python scripts/benchmark.py --base-url http://127.0.0.1:8765 --token analyst-token --iterations 100 --warmup 5`
- Database: disposable SQLite database; 40,960 bytes after the run (105 tickets
  and 105 audit events, including five warm-up pairs)
- Measured iterations: 100 create/list pairs (plus five warm-up pairs)
- Emitted summary:

```json
{
  "base_url": "http://127.0.0.1:8765",
  "iterations": 100,
  "warmup": 5,
  "units": "ms",
  "create_ticket": {
    "count": 100,
    "mean_ms": 28.758,
    "median_ms": 29.668,
    "p95_ms": 40.342,
    "min_ms": 14.379,
    "max_ms": 53.993
  },
  "list_tickets": {
    "count": 100,
    "mean_ms": 22.908,
    "median_ms": 25.399,
    "p95_ms": 35.28,
    "min_ms": 10.062,
    "max_ms": 37.285
  }
}
```

The API was started locally on port 8765 with `ALERTDESK_DB` pointed at the
disposable database. The tracked development database was left unchanged.

## Request logging

The API emits one JSON log event per HTTP request with timestamp, method, URL
path, status code, and elapsed milliseconds. The logger intentionally excludes
headers, bearer tokens, query strings, request bodies, and ticket data. These
fields are enough to identify slow routes and error rates without copying alert
contents into general application logs. Logs go to stderr through Python's
standard logging handler and can be collected by the process supervisor or
container runtime.

## Operational notes

- The benchmark includes HTTP client, API, SQLite, and response serialization time.
- The list endpoint has no pagination, so its latency and response size grow with
  the number of stored tickets.
- The benchmark creates records and does not clean them up; point it at a
  disposable database when measuring repeatedly.
- This is a local development baseline, not a capacity or concurrency test.
