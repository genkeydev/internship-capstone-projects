# HealthLogger - Architecture

**Week 6**

**Project:** HealthLogger
**Client:** Harbor Logistics
**Version:** 1.0.0

---

## 1. Purpose

HealthLogger is a local health and event logging tool designed for use on developer laptops and small lab environments.

The application provides:

* A command-line interface (CLI).
* A small HTTP API.
* Local JSON Lines (JSONL) event storage.
* Validation based on a client-defined JSON specification.
* Separate storage for rejected HTTP submissions.
* Structured JSON logs written to standard output.

HealthLogger does not use a remote database in version 1.

The architecture is intentionally small so that it can run locally without requiring a central observability platform.

---

## 2. Architecture Overview

The system follows a simple processing pipeline:

```text
                    Client / User
                         |
              +----------+----------+
              |                     |
             CLI                   HTTP
              |                     |
              v                     v
     HealthLoggerCli        HealthHttpServer
              |                     |
              +----------+----------+
                         |
                         v
                   Parse JSON
                         |
                         v
                   HealthEvent
                         |
                         v
                    Validate
                         |
              +----------+----------+
              |                     |
            Valid                Invalid
              |                     |
              v                     v
         JsonlStore          RejectedEventStore
              |                     |
              v                     v
      data/events.jsonl    data/rejected-events.jsonl
              |
              v
      StructuredLogger
              |
              v
            stdout
```
**Note:** `RejectedEventStore` is used only for rejected HTTP submissions. Invalid CLI `record` commands report an error and exit unsuccessfully; they do not write to `data/rejected-events.jsonl`.

The active client requirements are loaded from:

```text
config/client-spec.json
```

This allows the validation rules to change without requiring a new Java field for every client-defined event field.

---

## 3. Main Components

### 3.1 HealthLoggerCli

`HealthLoggerCli` is the command-line entry point.

It supports:

* `record`
* `query`
* `serve`

The CLI loads the client specification, creates the event store, and starts the appropriate operation.

For the `serve` command, it creates the HTTP server and keeps the process running.

---

### 3.2 HealthHttpServer

`HealthHttpServer` provides the local HTTP API.

The server exposes:

```text
GET  /health
POST /events
```

The server listens on localhost using the port defined by the client specification.

The current default port is:

```text
8088
```

`GET /health` is a process-liveness endpoint. It does not provide a complete service monitoring dashboard.

`POST /events` accepts a JSON event, parses it, validates it, and either stores or rejects it.

---

### 3.3 HealthEvent

`HealthEvent` represents an event as an open JSON object:

```java
Map<String, Object>
```

It intentionally does not define fixed Java fields such as:

```text
service
status
timestamp
```

as individual Java properties.

This design allows the client to introduce additional fields without requiring a Java model change.

The current implementation provides helper methods for commonly used fields such as:

* `service`
* `status`

All other event fields remain available through the underlying map.

---

### 3.4 ClientSpec

`ClientSpec` represents the client-defined configuration loaded from:

```text
config/client-spec.json
```

The specification currently controls:

* schema version
* client name
* required fields
* allowed statuses
* status-to-severity mappings
* HTTP port

Example:

```json
{
  "requiredFields": [
    "service",
    "status",
    "timestamp"
  ],
  "allowedStatuses": [
    "ok",
    "degraded",
    "down"
  ]
}
```

The application therefore keeps client-specific validation rules outside the Java event model.

---

### 3.5 Json

The `Json` class provides JSON-related operations.

It is responsible for:

* Loading the client specification.
* Parsing incoming event JSON.
* Serializing Java objects to JSON.
* Validating events against the active `ClientSpec`.

Validation checks include:

* Missing required fields.
* Null required fields.
* Unsupported status values.

---

### 3.6 JsonlStore

`JsonlStore` provides persistent local storage for valid health events.

The default event store is:

```text
data/events.jsonl
```

Each event is stored as exactly one JSON object per line.

The store uses append-only writes for new events.

The CLI query operation can read the stored events and filter them by service.

---

### 3.7 RejectedEventStore

`RejectedEventStore` stores rejected HTTP submissions separately from valid events.

The current file is:

```text
data/rejected-events.jsonl
```

A rejected entry records information such as:

* rejection timestamp
* original input
* validation or parsing reason

This keeps invalid submissions separate from valid health events while preserving information useful for troubleshooting.

---

### 3.8 StructuredLogger

`StructuredLogger` writes structured event information to standard output as JSON Lines.

A valid event log contains:

```text
ts
severity
event
```

The severity is obtained from the status-to-severity mapping in the client specification.

For the current client:

```text
ok       -> info
degraded -> warn
down     -> error
```

The logs are written to stdout so they can potentially be collected by a future centralized logging system.

---

## 4. CLI Data Flow

### 4.1 `record`

The CLI record flow is:

```text
healthlogger record
        |
        v
Read JSON input
        |
        v
Parse JSON
        |
        v
Create HealthEvent
        |
        v
Validate against ClientSpec
        |
     +--+--+
     |     |
   valid invalid
     |     |
     v     v
 JsonlStore
     |
     v
StructuredLogger
     |
     v
   stdout
```

A valid event is appended to:

```text
data/events.jsonl
```

Invalid CLI events are rejected and are not written to the valid event store.

---

### 4.2 `query`

The query flow is:

```text
healthlogger query
        |
        v
JsonlStore
        |
        v
Read JSONL events
        |
        v
Filter by service
        |
        v
Print matching events
```

The query operation does not modify the event store.

---

### 4.3 `serve`

The serve flow is:

```text
healthlogger serve
        |
        v
Load ClientSpec
        |
        v
Create JsonlStore
        |
        v
Create RejectedEventStore
        |
        v
Start HealthHttpServer
        |
        v
Listen on localhost
```

The HTTP server then waits for incoming requests.

---

## 5. HTTP Data Flow

### 5.1 Valid `POST /events`

```text
POST /events
     |
     v
Read request body
     |
     v
Check request size
     |
     v
Parse JSON
     |
     v
Validate event
     |
     v
JsonlStore
     |
     v
events.jsonl
     |
     v
StructuredLogger
     |
     v
HTTP 201 Created
```

A valid event is stored and logged.

### 5.2 Invalid or Malformed `POST /events`

The HTTP API handles malformed JSON and events that fail validation separately from valid events.

```text
POST /events
     |
     v
Read request body
     |
     v
Parse JSON
     |
     +---- malformed JSON or JSON null
     |                 |
     |                 v
     |          Record rejection
     |                 |
     |                 v
     |          Return HTTP 400
     |
     v
Validate event
     |
     +---- invalid event
     |         |
     |         v
     |   Record rejection and reason
     |         |
     |         v
     |   Return HTTP 400
     |
     v
Store valid event
     |
     v
Return success response
```

If a request body exceeds the configured size limit, the server returns HTTP `413 Payload Too Large`.

**Scope clarification:** Rejected-event storage applies to the HTTP request handling path. An invalid CLI `record` command reports an error and exits unsuccessfully; it does not currently save the rejected event to the rejection store.

## 6. Client-Driven Configuration

HealthLogger uses the client specification to define event fields and validation requirements. The implementation should read these requirements from the configured specification rather than hardcoding client-specific fields in Java classes.

This allows the application to adapt when the client requirements change.

## 7. Storage Architecture

HealthLogger uses local JSON Lines (JSONL) files for persistence.

* **Event store:** Stores accepted events, with one JSON object per line.
* **Rejected-event store:** Stores rejected HTTP submissions and their rejection reasons.
* **Configuration:** Defines the client requirements and application settings.

The storage layer is responsible for writing and retrieving records. The application and HTTP layers handle request processing and validation before accepted events are stored.

## 8. Logging Architecture

Application logs provide information about server startup, request processing, and errors. Benchmark output and server logs are separate from the event data files.

Logs should help with troubleshooting without unnecessarily exposing sensitive information.

## 9. Error Handling

HealthLogger distinguishes between malformed input, validation failures, oversized requests, and internal application errors.

* Malformed JSON and validation failures in HTTP requests return HTTP `400`.
* Requests exceeding the body-size limit return HTTP `413`.
* Valid requests are processed and stored before a success response is returned.
* CLI commands report failures through their exit status and error output.

Error responses should communicate the problem without exposing unnecessary implementation details.

## 10. Testing Architecture

Tests verify the behavior of the application at different levels.

* **Unit tests:** Verify parsing, validation, and individual application components.
* **CLI tests:** Verify command behavior, exit codes, and output.
* **HTTP tests:** Verify endpoints, response codes, valid events, and rejected requests.
* **Storage tests:** Verify that accepted events and rejected submissions are persisted as expected.

Tests should avoid relying on fixed ports or timing assumptions when a dynamically assigned port or deterministic assertion can be used.

## 11. Security Considerations

HealthLogger should validate incoming data, enforce the configured request-body size limit, and avoid exposing sensitive information in logs or error responses.

Repository changes should follow the project's security guidance. Credentials and other secrets should not be committed to source control.

Authentication and authorization requirements should follow the client specification rather than being assumed to exist.

## 12. Deployment Considerations

HealthLogger is a Java 21 application built with Maven. It uses the JDK HTTP server and the project's configured dependencies.

Deployment requires the appropriate Java runtime, application configuration, and write access to the configured storage locations.

The application should be built and tested before deployment. Environment-specific paths should not be hardcoded into reusable scripts.

## 13. Architectural Principles

The design follows these principles:

* **Separation of concerns:** Request handling, validation, storage, and logging have distinct responsibilities.
* **Configuration-driven behavior:** Client-specific requirements are defined in configuration.
* **Explicit error handling:** Invalid input is handled deliberately.
* **Testability:** Components should be testable without depending unnecessarily on fixed ports or machine-specific settings.
* **Maintainability:** Documentation should reflect the current implementation and be updated when behavior changes.

## 14. Summary

HealthLogger provides a CLI and a small HTTP API for recording and querying health events. It validates incoming events, stores accepted events in JSONL format, and records rejected HTTP submissions with their rejection reasons.

The architecture is intended to remain simple, configuration-driven, testable, and maintainable while meeting the requirements in the client specification.
