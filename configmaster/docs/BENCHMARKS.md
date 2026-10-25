# ConfigMaster Benchmarks

This document records end-to-end CLI wall-clock benchmark results for ConfigMaster when merging large generated JSON configuration trees.

## Benchmark configuration

The benchmark generates two JSON configuration layers. Each layer contains 200 groups with 50 values per group, giving 10,000 values per layer.

Both layers include the required `app.name` and `app.port` fields so the merged configuration is valid against the current client specification.

The script generates the test files and compiles ConfigMaster before timing starts.

The earlier benchmark results measured the full Maven command, including Maven and JVM startup.

The updated script runs Java directly after compiling the project. It uses Bash's built-in `time` command to measure execution time without including Maven startup. The new measurement still includes JVM startup, loading, merging, validation, and JSON output.

The earlier results are kept for reference, while new Java CLI results are recorded separately in this same document.

Generated benchmark files are stored under `target/benchmark/` and are not committed.

## Earlier benchmark results

| Date                | Groups | Keys per group | Values per layer | End-to-end CLI wall time |
|---------------------|-------:|---------------:|-----------------:|-------------------------:|
| 2026-10-06 16:17:37 |    200 |             50 |            10000 |                  3086 ms |
| 2026-10-12 12:20:52 |    200 |             50 |            10000 |                  2614 ms |
| 2026-10-12 12:27:56 |    200 |             50 |            10000 |                  2535 ms |
| 2026-10-12 12:58:41 |    200 |             50 |            10000 |                  2748 ms |

## Java CLI results

These measurements include JVM startup but exclude Maven startup.

| Date                | Groups | Keys per group | Values per layer | Java CLI wall time |
|---------------------|-------:|---------------:|-----------------:|-------------------:|
| 2026-10-25 18:51:14 |    200 |             50 |            10000 |            2881 ms |
| 2026-10-25 19:26:53 |    200 |             50 |            10000 |            1103 ms |
