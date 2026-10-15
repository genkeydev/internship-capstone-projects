# Changelog

This file records the main changes made to ConfigMaster.

## [1.0.0] - 2026-10-15

### Added

- Java 21 command-line tool for loading, merging, and validating JSON configuration files.
- `validate`, `merge`, and `show` commands.
- Support for the `override`, `append_list`, and `fail_on_conflict` merge strategies.
- Runtime client specification support through `config/client-spec.json`.
- Schema version validation for supported `1.x.x` specifications.
- JUnit tests covering CLI behavior, JSON loading, merging, validation, and schema versions.
- JaCoCo coverage checks with an 80% minimum requirement.
- Specification, JSON schema, and architecture documentation.
- Benchmark script and recorded end-to-end CLI performance results.
- Multi-stage Docker image using Java 21 and a non-root user.
- GitHub Actions workflow for Maven tests, Semgrep analysis, and Trivy container scanning.

### Changed

- Updated the Maven project version to `1.0.0`.
- Updated the Dockerfile to package the `1.0.0` application JAR.
- Updated CLI test fixtures to use the supported schema version format.

### Security

- Added security policy documentation.
- Configured the Docker container to run without root privileges.
- Added `.dockerignore` exclusions for unnecessary files and local sensitive data.
- Configured Trivy to report HIGH and CRITICAL vulnerabilities and fail CI when detected.