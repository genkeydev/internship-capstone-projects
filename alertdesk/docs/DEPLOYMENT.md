# Local container deployment

From the `alertdesk/` directory, start the API with:

```bash
docker compose up --build
```

The API is available at <http://localhost:8000/docs>. Compose stores SQLite's
`data/alertdesk.sqlite` in the named `alertdesk-data` volume, so recreating the
container does not remove ticket data. Back up that volume before upgrading or
removing it. `docker compose down -v` deletes the volume and its data.

The image runs Uvicorn as the unprivileged `app` user. The application image
contains the runtime client specification and creates the SQLite database on
startup.

## Schema changes

Week 7 does not add or change database columns, so no migration is needed for
this release. Before a future column change, add a versioned migration that
records the current schema version, applies the new schema in a transaction,
and is safe to rerun. Back up the SQLite volume before applying it; test both a
fresh database and a copy of the prior release's database.

## Request logging

The API writes one structured JSON log event for each HTTP request. Each event
includes the request method, URL path, response status code, and duration in
milliseconds. The middleware does not log credentials or request bodies. These
logs are emitted to the container's standard output and can be collected by the
container runtime's logging system.

The CI `container-scan` job builds this Dockerfile and fails on Trivy HIGH or
CRITICAL findings.
