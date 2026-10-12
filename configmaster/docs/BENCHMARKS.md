# ConfigMaster Benchmarks

This document records end-to-end CLI wall-clock benchmark results for ConfigMaster when merging large generated JSON configuration trees.

## Benchmark configuration

The benchmark generates two JSON configuration layers. Each layer contains 200 groups with 50 values per group, giving 10,000 values per layer.

Both layers include the required `app.name` and `app.port` fields so the merged configuration is valid against the current client specification.

The script generates the test files and compiles ConfigMaster before timing starts.

The recorded wall time measures the full `mvn exec:java` invocation, including Maven and JVM startup, configuration loading, merging, validation, and output generation. It is an end-to-end CLI measurement, not the time spent on merging alone.

Generated benchmark files are stored under `target/benchmark/` and are not committed.

## Results

| Date                | Groups | Keys per group | Values per layer | End-to-end CLI wall time |
|---------------------|-------:|---------------:|-----------------:|-------------------------:|
| 2026-10-06 16:17:37 |    200 |             50 |            10000 |                  3086 ms |
| 2026-10-12 12:20:52 |    200 |             50 |            10000 |                  2614 ms |
| 2026-10-12 12:27:56 |    200 |             50 |            10000 |                  2535 ms |
| 2026-10-12 12:58:41 |    200 |             50 |            10000 |                  2748 ms |
