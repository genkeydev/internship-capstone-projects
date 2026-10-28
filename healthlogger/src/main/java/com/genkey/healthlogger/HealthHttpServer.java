package com.genkey.healthlogger;

import com.genkey.healthlogger.model.HealthEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Tiny local HTTP surface (JDK HttpServer, not a servlet container).
 *
 * POST /events â€” record a valid event or save a rejected submission.
 * GET /health  â€” liveness check.
 */
public final class HealthHttpServer {

    /**
     * Maximum HTTP request body accepted by the server.
     * Requests larger than this are rejected with HTTP 413.
     */
    public static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;

    private final ClientSpec spec;
    private final JsonlStore store;
    private final RejectedEventStore rejectedStore;
    private final String bindHost;
    private HttpServer server;

    /** Default bind address: loopback only, because the API has no authentication. */
    public static final String DEFAULT_BIND_HOST = "127.0.0.1";

    public HealthHttpServer(
            ClientSpec spec,
            JsonlStore store,
            RejectedEventStore rejectedStore
    ) {
        this(spec, store, rejectedStore, DEFAULT_BIND_HOST);
    }

    public HealthHttpServer(
            ClientSpec spec,
            JsonlStore store,
            RejectedEventStore rejectedStore,
            String bindHost
    ) {
        this.spec = spec;
        this.store = store;
        this.rejectedStore = rejectedStore;
        this.bindHost = bindHost == null || bindHost.isBlank()
                ? DEFAULT_BIND_HOST
                : bindHost.trim();
    }

    public void start() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(bindHost, spec.port()),
                0
        );

        server.createContext("/health", this::health);
        server.createContext("/events", this::events);

        server.start();

            System.err.println(
                "listening on http://" + bindHost + ":" + server.getAddress().getPort()
        );
    }

    /** Host or address this server binds to. */
    public String bindHost() {
        return bindHost;
    }

    /** True when the listening socket is bound to a loopback address. */
    public boolean isBoundToLoopback() {
        return server != null && server.getAddress().getAddress().isLoopbackAddress();
    }

   

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
    public int port() {
    if (server == null) {
        throw new IllegalStateException("Server has not started");
    }

    return server.getAddress().getPort();
}

    private void health(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(
                    ex,
                    405,
                    "{\"error\":\"method not allowed\"}"
            );
            return;
        }

        send(
                ex,
                200,
                "{\"status\":\"ok\"}"
        );
    }

    private void events(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(
                    ex,
                    405,
                    "{\"error\":\"method not allowed\"}"
            );
            return;
        }

        byte[] bodyBytes;

        try {
            bodyBytes = readRequestBody(
                    ex.getRequestBody(),
                    MAX_REQUEST_BODY_BYTES
            );

        } catch (RequestTooLargeException e) {
            try {
                rejectedStore.append(
                        "[request body omitted: exceeds maximum size]",
                        "request body exceeds maximum size"
                );
            } catch (IOException loggingError) {
                System.err.println(
                        "Failed to record oversized request: "
                                + loggingError.getMessage()
                 );
                send(
                        ex,
                        500,
                        "{\"error\":\"failed to record rejected request\"}"
                );
                return;
            }

            send(
                    ex,
                    413,
                    "{\"error\":\"request body too large\"}"
            );
            return;
        }


        String body = new String(
                bodyBytes,
                StandardCharsets.UTF_8
        );

        HealthEvent event;


        try {
            event = Json.parseEvent(body);
        } catch (Exception e) {
            try {
                rejectedStore.append(
                        body,
                        "malformed JSON: " + e.getMessage()
                );
            } catch (IOException storageError) {
                System.err.println(
                        "Failed to store rejected malformed JSON: "
                                + storageError.getMessage()
                );
            }

            send(
                    ex,
                    400,
                    "{\"error\":\"malformed JSON\"}"
            );
            return;
        }



        List<String> issues = Json.validate(event, spec);

         if (!issues.isEmpty()) {
            try {
                rejectedStore.append(
                        event.fields(),
                        String.join("; ", issues)
                );
            } catch (IOException storageError) {
                System.err.println(
                        "Failed to store rejected event: "
                                + storageError.getMessage()
                );
            }

            send(
                    ex,
                    400,
                    Json.toJson(
                            Map.of(
                                    "issues",
                                    issues
                            )
                    )
            );
            return;
        }


        store.append(event);

        StructuredLogger.event(spec, event);

        send(
                ex,
                201,
                Json.toJson(event.fields())
        );
    }

    private static byte[] readRequestBody(
            InputStream input,
            int maxBytes
    ) throws IOException, RequestTooLargeException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];

        int total = 0;
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {

            total += bytesRead;

            if (total > maxBytes) {
                throw new RequestTooLargeException();
            }

            output.write(
                    buffer,
                    0,
                    bytesRead
            );
        }

        return output.toByteArray();
    }

    private static void send(
            HttpExchange ex,
            int code,
            String json
    ) throws IOException {

        byte[] bytes = json.getBytes(
                StandardCharsets.UTF_8
        );

        ex.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );

        ex.sendResponseHeaders(
                code,
                bytes.length
        );

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final class RequestTooLargeException
            extends Exception {

        private RequestTooLargeException() {
            super("request body exceeds maximum size");
        }
    }
}
