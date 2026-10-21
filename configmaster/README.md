# ConfigMaster CLI

Intern capstone scaffold for the **ConfigMaster Intern Guide** (Year 1, 8 weeks).
Follow the guide week by week; files marked as deliverables are missing or stubbed.

![CI](https://github.com/YOUR_USERNAME/capstone/actions/workflows/configmaster.yml/badge.svg)

## What it does

ConfigMaster is a Java CLI that loads JSON configuration layers, validates the
effective document against a **client spec** (required fields, types, merge
strategies), and prints merged JSON. Product fields are **not** hardcoded —
swap `docs/client/BRIEF.md` and `config/client-spec.json` when the client
changes.

## Quick start

This project lives in the **capstone** monorepo. Fork `https://github.com/YOUR_ORG/capstone`.

```bash
git clone https://github.com/YOUR_USERNAME/capstone.git
cd capstone/configmaster
mvn test
mvn -q exec:java -Dexec.args="show fixtures/defaults.json fixtures/overlay.json"
```

Expect merged JSON on stdout: port `9090`, features `metrics` then `tracing`.

```bash
mvn -q exec:java -Dexec.args="merge fixtures/defaults.json fixtures/conflict-overlay.json"
```

Expect a non-zero exit: `app.region` uses `fail_on_conflict`.

## Docker (Week 7)

Build the image from the `configmaster/` directory:

```bash
docker build -t configmaster:1.0.0 .
```

Run the CLI with the fixtures directory mounted read-only (PowerShell):

```powershell
docker run --rm -v "${PWD}/fixtures:/app/fixtures:ro" configmaster:1.0.0 show fixtures/defaults.json fixtures/overlay.json
```

The `:ro` option prevents the container from modifying the mounted fixtures. This command was tested successfully and produced merged JSON containing port `9090` and the `metrics` and `tracing` features.

**Versioning note:** The Dockerfile currently copies `target/configmaster-0.1.0.jar`. If the version in `pom.xml` changes, update the JAR filename in the Dockerfile to match before rebuilding the image.

## Git / PR workflow

| Work | PR target |
|------|-----------|
| Weekly / day-to-day | Your **fork’s `main`** |
| Milestones (Month 1, Week 6, Week 8 handoff) | Upstream **`YOUR_ORG/capstone`** branch **`intern/YOUR_USERNAME`** |
| Never | Upstream **`main`** (mentors only) |

## CLI commands

- **validate** — merge layers, run schema checks, exit 0/1
- **merge** / **show** — print effective JSON if valid

`--spec path` or `CONFIGMASTER_SPEC` selects the client spec (default
`config/client-spec.json`).

## Client flexibility

| File | Role |
|------|------|
| [docs/client/BRIEF.md](docs/client/BRIEF.md) | Sample client letter (replaceable) |
| [docs/client/spec.example.json](docs/client/spec.example.json) | Canonical example spec |
| [config/client-spec.json](config/client-spec.json) | Runtime spec the engine loads |
| `docs/SPEC.md` | You write this in Week 1 from the brief |

## Internship path

| Week | You create / complete |
|------|------------------------|
| 1 | `docs/SPEC.md` from the client brief (create) |
| 2 | Fork → harden; hybrid PRs; complete `SECURITY.md` |
| 3 | Extend test stubs; ≥80% coverage (uncomment JaCoCo check) |
| 4 | `docs/schema.md`; Month 1 PR |
| 5 | `scripts/benchmark.sh` + `docs/BENCHMARKS.md` |
| 6 | `docs/ARCHITECTURE.md`; YAML loader or a new merge strategy |
| 7 | `Dockerfile`, compose; Trivy + coverage in CI |
| 8 | `CHANGELOG.md`, v1.0.0, handoff PR |

See **ConfigMaster_Intern_Guide.md** for the full checklist.

## Provided layout

```
configmaster/
├── src/main/java/com/genkey/configmaster/
├── src/test/java/...          # MergerTest starter; SchemaValidatorTest stub
├── fixtures/
├── config/client-spec.json
├── docs/client/
├── .github/workflows/ci.yml
└── pom.xml                    # Java 21, Jackson, JUnit 5, JaCoCo (gate commented)
```

No Spring Boot — this is a local CLI, not a Year 2 web service.
