package io.github.neko0115.moxuebridge.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.neko0115.moxuebridge.registry.CapabilityRegistry;
import io.github.neko0115.moxuebridge.security.BearerTokenValidator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BridgeHttpServer {

    private static final String API_PREFIX = "/api/v1";

    private final String bindAddress;
    private final int port;
    private final CapabilityRegistry registry;
    private final BearerTokenValidator tokenValidator;
    private final Logger logger;
    private final Gson gson;

    private HttpServer server;

    public BridgeHttpServer(
            String bindAddress,
            int port,
            CapabilityRegistry registry,
            BearerTokenValidator tokenValidator,
            Logger logger) {

        this.bindAddress = bindAddress;
        this.port = port;
        this.registry = registry;
        this.tokenValidator = tokenValidator;
        this.logger = logger;

        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(
                        FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .disableHtmlEscaping()
                .create();
    }

    public void start() throws IOException {
        if (server != null) {
            return;
        }

        server = HttpServer.create(
                new InetSocketAddress(
                        bindAddress,
                        port),
                0);

        server.createContext(
                API_PREFIX,
                this::handleApiRequest);

        server.start();
    }

    public void stop() {
        if (server == null) {
            return;
        }

        server.stop(0);
        server = null;
    }

    public int boundPort() {
        if (server == null) {
            return -1;
        }

        return server.getAddress().getPort();
    }

    private void handleApiRequest(
            HttpExchange exchange) {

        try {
            String authorization =
                    exchange.getRequestHeaders()
                            .getFirst("Authorization");

            if (!tokenValidator.isAuthorized(
                    authorization)) {

                sendJson(
                        exchange,
                        401,
                        "{\"error\":\"unauthorized\"}");

                return;
            }

            if (!"GET".equalsIgnoreCase(
                    exchange.getRequestMethod())) {

                sendJson(
                        exchange,
                        405,
                        "{\"error\":\"method_not_allowed\"}");

                return;
            }

            String path =
                    exchange.getRequestURI().getPath();

            switch (path) {
                case "/api/v1/status" ->
                        sendJson(
                                exchange,
                                200,
                                gson.toJson(
                                        registry.snapshot()
                                                .status()));

                case "/api/v1/plugins" ->
                        sendJson(
                                exchange,
                                200,
                                gson.toJson(
                                        registry.snapshot()
                                                .plugins()));

                case "/api/v1/capabilities" ->
                        sendJson(
                                exchange,
                                200,
                                gson.toJson(
                                        registry.snapshot()
                                                .capabilities()));

                case "/api/v1/resources" ->
                        sendJson(
                                exchange,
                                200,
                                gson.toJson(
                                        registry.snapshot()
                                                .resources()));

                default ->
                        sendJson(
                                exchange,
                                404,
                                "{\"error\":\"not_found\"}");
            }

        } catch (Exception ex) {
            logger.log(
                    Level.SEVERE,
                    "Unhandled MoxueBridge HTTP error",
                    ex);

            try {
                sendJson(
                        exchange,
                        500,
                        "{\"error\":\"internal_error\"}");

            } catch (IOException sendFailure) {
                logger.log(
                        Level.SEVERE,
                        "Failed to send HTTP error response",
                        sendFailure);
            }

        } finally {
            exchange.close();
        }
    }

    private void sendJson(
            HttpExchange exchange,
            int status,
            String json)
            throws IOException {

        byte[] body =
                json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=utf-8");

        exchange.sendResponseHeaders(
                status,
                body.length);

        exchange.getResponseBody().write(body);
    }
}