package com.genkey.healthlogger;

import com.genkey.healthlogger.model.HealthEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthHttpServerTest {

    @Test
    void healthEndpointReturns200() throws Exception {
        Path tempDir = Files.createTempDirectory("healthlogger-http-test");
        Path eventsPath = tempDir.resolve("events.jsonl");
        Path rejectedPath = tempDir.resolve("rejected-events.jsonl");

        int port = 0;

        ClientSpec spec = testSpec(port);
        JsonlStore store = new JsonlStore(eventsPath);
        RejectedEventStore rejectedStore =
                new RejectedEventStore(rejectedPath);

        HealthHttpServer server =
                new HealthHttpServer(spec, store, rejectedStore);

        server.start();

        port = server.port();

        try {
            HttpResponse<String> response = send(
                    "GET",
                    port,
                    "/health",
                    null
            );

            assertEquals(200, response.statusCode());
            assertEquals(
                    "{\"status\":\"ok\"}",
                    response.body()
            );
        } finally {
            server.stop();
        }
    }

    @Test
    void postEventsCreatesRecord() throws Exception {
        Path tempDir = Files.createTempDirectory("healthlogger-http-test");
        Path eventsPath = tempDir.resolve("events.jsonl");
        Path rejectedPath = tempDir.resolve("rejected-events.jsonl");

        int port = 0;

        ClientSpec spec = testSpec(port);
        JsonlStore store = new JsonlStore(eventsPath);
        RejectedEventStore rejectedStore =
                new RejectedEventStore(rejectedPath);

        HealthHttpServer server =
                new HealthHttpServer(spec, store, rejectedStore);

        server.start();

        port = server.port();

        try {
            String body = """
                    {
                      "service": "payments-api",
                      "status": "ok",
                      "timestamp": "2026-09-29T10:00:00Z"
                    }
                    """;

            HttpResponse<String> response = send(
                    "POST",
                    port,
                    "/events",
                    body
            );

            assertEquals(201, response.statusCode());

            List<HealthEvent> events = store.query(null);

            assertEquals(1, events.size());
            assertEquals(
                    "payments-api",
                    events.get(0).service()
            );
            assertEquals(
                    "ok",
                    events.get(0).status()
            );

            assertFalse(
                    Files.exists(rejectedPath)
            );
        } finally {
            server.stop();
        }
    }

    @Test
    void postEventsRejectsIncompleteBodyAndSavesRejection()
            throws Exception {

        Path tempDir = Files.createTempDirectory("healthlogger-http-test");
        Path eventsPath = tempDir.resolve("events.jsonl");
        Path rejectedPath = tempDir.resolve("rejected-events.jsonl");

        int port = 0;

        ClientSpec spec = testSpec(port);
        JsonlStore store = new JsonlStore(eventsPath);
        RejectedEventStore rejectedStore =
                new RejectedEventStore(rejectedPath);

        HealthHttpServer server =
                new HealthHttpServer(spec, store, rejectedStore);

        server.start();

        port = server.port();

        try {
            String body = """
                    {
                      "service": "payments-api",
                      "status": "ok"
                    }
                    """;

            HttpResponse<String> response = send(
                    "POST",
                    port,
                    "/events",
                    body
            );

            assertEquals(400, response.statusCode());

            assertTrue(
                    store.query(null).isEmpty()
            );

            assertTrue(
                    Files.exists(rejectedPath)
            );

            String rejection =
                    Files.readString(rejectedPath);

            assertTrue(
                    rejection.contains("payments-api")
            );

            assertTrue(
                    rejection.contains("timestamp")
            );

            assertTrue(
                    rejection.contains(
                            "missing required field: timestamp"
                    )
            );
        } finally {
            server.stop();
        }
    }

    @Test
    void postEventsRejectsMalformedJsonAndSavesRejection()
            throws Exception {

        Path tempDir = Files.createTempDirectory("healthlogger-http-test");
        Path eventsPath = tempDir.resolve("events.jsonl");
        Path rejectedPath = tempDir.resolve("rejected-events.jsonl");

        int port = 0;

        ClientSpec spec = testSpec(port);
        JsonlStore store = new JsonlStore(eventsPath);
        RejectedEventStore rejectedStore =
                new RejectedEventStore(rejectedPath);

        HealthHttpServer server =
                new HealthHttpServer(spec, store, rejectedStore);

        server.start();

        port = server.port();

        try {
            String body =
                    "{\"service\":\"payments-api\",\"status\":\"ok\"";

            HttpResponse<String> response = send(
                    "POST",
                    port,
                    "/events",
                    body
            );

            assertEquals(400, response.statusCode());

            assertTrue(
                    store.query(null).isEmpty()
            );

            assertTrue(
                    Files.exists(rejectedPath)
            );

            String rejection =
                    Files.readString(rejectedPath);

            assertTrue(
                    rejection.contains("payments-api")
            );

            assertTrue(
                    rejection.contains("malformed JSON")
            );
        } finally {
            server.stop();
        }
    }

    @Test
    void postEventsRejectsEmptyBodyAndSavesRejection()
            throws Exception {

        Path tempDir = Files.createTempDirectory("healthlogger-http-test");
        Path eventsPath = tempDir.resolve("events.jsonl");
        Path rejectedPath = tempDir.resolve("rejected-events.jsonl");

        int port = 0;

        ClientSpec spec = testSpec(port);
        JsonlStore store = new JsonlStore(eventsPath);
        RejectedEventStore rejectedStore =
                new RejectedEventStore(rejectedPath);

        HealthHttpServer server =
                new HealthHttpServer(spec, store, rejectedStore);

        server.start();

        port = server.port();

        try {
            HttpResponse<String> response = send(
                    "POST",
                    port,
                    "/events",
                    ""
            );

            assertEquals(400, response.statusCode());

            assertTrue(
                    store.query(null).isEmpty()
            );

            assertTrue(
                    Files.exists(rejectedPath)
            );

            String rejection =
                    Files.readString(rejectedPath);

            assertTrue(
                    rejection.contains("malformed JSON")
            );
        } finally {
            server.stop();
        }
    }

    @Test
    void postEventsRejectsHugePayload() throws Exception {
        Path tempDir = Files.createTempDirectory("healthlogger-http-test");
        Path eventsPath = tempDir.resolve("events.jsonl");
        Path rejectedPath = tempDir.resolve("rejected-events.jsonl");

        int port = 0;

        ClientSpec spec = testSpec(port);
        JsonlStore store = new JsonlStore(eventsPath);
        RejectedEventStore rejectedStore =
                new RejectedEventStore(rejectedPath);

        HealthHttpServer server =
                new HealthHttpServer(spec, store, rejectedStore);

        server.start();

        port = server.port();

        try {
            String hugeBody = "x".repeat(
                    HealthHttpServer.MAX_REQUEST_BODY_BYTES + 1
            );

            HttpResponse<String> response = send(
                    "POST",
                    port,
                    "/events",
                    hugeBody
            );

            assertEquals(413, response.statusCode());

            assertTrue(
                    store.query(null).isEmpty()
            );
        } finally {
            server.stop();
        }
    }


@Test
void postEventsAcceptsExtraFields() throws Exception {
    Path tempDir = Files.createTempDirectory("healthlogger-http-test");
    Path eventsPath = tempDir.resolve("events.jsonl");
    Path rejectedPath = tempDir.resolve("rejected-events.jsonl");

    int port = 0;
    ClientSpec spec = testSpec(port);
    JsonlStore store = new JsonlStore(eventsPath);
    RejectedEventStore rejectedStore =
            new RejectedEventStore(rejectedPath);

    HealthHttpServer server =
            new HealthHttpServer(spec, store, rejectedStore);

    server.start();
    port = server.port();

    try {
        String body = """
                {
                  "service": "payments-api",
                  "status": "ok",
                  "timestamp": "2026-09-29T10:00:00Z",
                  "region": "accra",
                  "version": "2.1.0"
                }
                """;

        HttpResponse<String> response = send(
                "POST",
                port,
                "/events",
                body
        );

        assertEquals(201, response.statusCode());

        List<HealthEvent> events = store.query(null);
        assertEquals(1, events.size());

        Map<String, Object> fields = events.get(0).fields();

        assertEquals("accra", fields.get("region"));
        assertEquals("2.1.0", fields.get("version"));
    } finally {
        server.stop();
    }
}


@Test
void postEventsEnforcesRegionWhenRequiredByClientSpec() throws Exception {
    Path tempDir = Files.createTempDirectory("healthlogger-region-test");
    Path specPath = tempDir.resolve("client-spec.json");
    Path eventsPath = tempDir.resolve("events.jsonl");
    Path rejectedPath = tempDir.resolve("rejected-events.jsonl");

    String specJson = """
            {
              "schemaVersion": "1.0.0",
              "client": "Harbor Ops Region Test",
              "requiredFields": ["service", "status", "timestamp", "region"],
              "allowedStatuses": ["ok", "degraded", "down"],
              "severityMap": {
                "ok": "info",
                "degraded": "warn",
                "down": "error"
              },
              "httpPort": 0
            }
            """;

    Files.writeString(specPath, specJson);

    ClientSpec spec = Json.loadSpec(specPath);
    JsonlStore store = new JsonlStore(eventsPath);
    RejectedEventStore rejectedStore =
            new RejectedEventStore(rejectedPath);

    HealthHttpServer server =
            new HealthHttpServer(spec, store, rejectedStore);

    server.start();
    int port = server.port();

    try {
        String missingRegion = """
                {
                  "service": "payments-api",
                  "status": "ok",
                  "timestamp": "2026-10-22T10:00:00Z"
                }
                """;

        HttpResponse<String> rejectedResponse = send(
                "POST",
                port,
                "/events",
                missingRegion
        );

        assertEquals(400, rejectedResponse.statusCode());
        assertTrue(store.query(null).isEmpty());
        assertTrue(Files.exists(rejectedPath));
        assertTrue(
                Files.readString(rejectedPath).contains("region"),
                "Rejection should explain that region is required"
        );

        String withRegion = """
                {
                  "service": "payments-api",
                  "status": "ok",
                  "timestamp": "2026-10-22T10:01:00Z",
                  "region": "accra"
                }
                """;

        HttpResponse<String> acceptedResponse = send(
                "POST",
                port,
                "/events",
                withRegion
        );

        assertEquals(201, acceptedResponse.statusCode());

        List<HealthEvent> events = store.query(null);
        assertEquals(1, events.size());
        assertEquals("accra", events.get(0).fields().get("region"));
    } finally {
        server.stop();
    }
}

    
@Test
void postEventsRejectsJsonNullAndSavesRejection() throws Exception {
    Path tempDir = Files.createTempDirectory("healthlogger-http-test");
    Path eventsPath = tempDir.resolve("events.jsonl");
    Path rejectedPath = tempDir.resolve("rejected-events.jsonl");

    int port = 0;
    ClientSpec spec = testSpec(port);
    JsonlStore store = new JsonlStore(eventsPath);
    RejectedEventStore rejectedStore =
            new RejectedEventStore(rejectedPath);

    HealthHttpServer server =
            new HealthHttpServer(spec, store, rejectedStore);

    server.start();

        port = server.port();

    try {
        HttpResponse<String> response = send(
                "POST",
                port,
                "/events",
                "null"
        );

        assertEquals(400, response.statusCode());
        assertTrue(store.query(null).isEmpty());
        assertTrue(Files.exists(rejectedPath));

        String rejection = Files.readString(rejectedPath);
        assertTrue(rejection.contains("malformed JSON"));
    } finally {
        server.stop();
    }
}




    @Test
    void bindsToLoopbackByDefault() throws Exception {
        Path tempDir = Files.createTempDirectory("healthlogger-http-test");
        HealthHttpServer server = new HealthHttpServer(
                testSpec(0),
                new JsonlStore(tempDir.resolve("events.jsonl")),
                new RejectedEventStore(tempDir.resolve("rejected-events.jsonl"))
        );

        assertEquals("127.0.0.1", server.bindHost());

        server.start();
        try {
            assertTrue(server.isBoundToLoopback());
        } finally {
            server.stop();
        }
    }

    @Test
    void blankOrMissingBindHostFallsBackToLoopback() throws Exception {
        Path tempDir = Files.createTempDirectory("healthlogger-http-test");
        JsonlStore store = new JsonlStore(tempDir.resolve("events.jsonl"));
        RejectedEventStore rejected =
                new RejectedEventStore(tempDir.resolve("rejected-events.jsonl"));

        assertEquals(
                "127.0.0.1",
                new HealthHttpServer(testSpec(0), store, rejected, null).bindHost()
        );
        assertEquals(
                "127.0.0.1",
                new HealthHttpServer(testSpec(0), store, rejected, "  ").bindHost()
        );
    }

    @Test
    void explicitBindHostIsUsed() throws Exception {
        Path tempDir = Files.createTempDirectory("healthlogger-http-test");
        HealthHttpServer server = new HealthHttpServer(
                testSpec(0),
                new JsonlStore(tempDir.resolve("events.jsonl")),
                new RejectedEventStore(tempDir.resolve("rejected-events.jsonl")),
                "0.0.0.0"
        );

        assertEquals("0.0.0.0", server.bindHost());

        server.start();
        try {
            assertFalse(server.isBoundToLoopback());
        } finally {
            server.stop();
        }
    }

    private static ClientSpec testSpec(int port) {
        return new ClientSpec(
                "1.0.0",
                "Test Client",
                List.of(
                        "service",
                        "status",
                        "timestamp"
                ),
                List.of(
                        "ok",
                        "degraded",
                        "down"
                ),
                Map.of(
                        "ok", "info",
                        "degraded", "warn",
                        "down", "error"
                ),
                port
        );
    }


    private static HttpResponse<String> send(
            String method,
            int port,
            String path,
            String body
    ) throws Exception {

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + path
                                )
                        );

        if ("POST".equals(method)) {
            builder.header(
                    "Content-Type",
                    "application/json"
            );

            builder.POST(
                    HttpRequest.BodyPublishers.ofString(
                            body == null ? "" : body
                    )
            );
        } else {
            builder.GET();
        }

        return client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}