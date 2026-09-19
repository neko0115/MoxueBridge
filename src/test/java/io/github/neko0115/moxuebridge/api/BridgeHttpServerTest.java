package io.github.neko0115.moxuebridge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.neko0115.moxuebridge.model.BridgeSnapshot;
import io.github.neko0115.moxuebridge.model.BridgeStatus;
import io.github.neko0115.moxuebridge.model.Capability;
import io.github.neko0115.moxuebridge.model.CapabilitySource;
import io.github.neko0115.moxuebridge.model.CapabilityUsage;
import io.github.neko0115.moxuebridge.model.PluginInfo;
import io.github.neko0115.moxuebridge.model.ResourceDescriptor;
import io.github.neko0115.moxuebridge.registry.CapabilityRegistry;
import io.github.neko0115.moxuebridge.security.BearerTokenValidator;
import io.github.neko0115.moxuebridge.workspace.WorkspaceSelectionPoint;
import io.github.neko0115.moxuebridge.workspace.WorkspaceSelectionStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BridgeHttpServerTest {

    private BridgeHttpServer server;
    private HttpClient client;
    private String baseUrl;
    private WorkspaceSelectionStore workspaceSelections;

    @BeforeEach
    void setUp() throws Exception {
        var snapshot = new BridgeSnapshot(
                "2026-09-17T10:30:00Z",
                new BridgeStatus(
                        "MoxueBridge",
                        "0.1.0",
                        "1.21.1",
                        "Paper",
                        true),
                List.of(
                        new PluginInfo(
                                "VeinMiner",
                                "2.11.2",
                                true,
                                true,
                                List.of())),
                List.of(
                        new Capability(
                                "vein_mining",
                                "連鎖挖礦",
                                "一次挖掘相連的礦物方塊",
                                true,
                                new CapabilitySource(
                                        "VeinMiner",
                                        "2.11.2",
                                        "integration"),
                                new CapabilityUsage(
                                        "sneak_and_break",
                                        "蹲下並使用十字鎬挖掘礦物"),
                                Map.of(
                                        "max_chain", 100))),
                List.of(
                        new ResourceDescriptor(
                                "examplemod:rubber_log",
                                "log",
                                List.of(),
                                List.of("examplemod:rubber_log"),
                                List.of("examplemod:rubber_log"),
                                1,
                                "axe",
                                List.of(),
                                "tree_felling",
                                Map.of(
                                        "leaves",
                                        List.of("examplemod:rubber_leaves")),
                                "natural_decay",
                                "authoritative")));

        var registry =
                new CapabilityRegistry(snapshot);

        workspaceSelections =
                new WorkspaceSelectionStore();

        server = new BridgeHttpServer(
                "127.0.0.1",
                0,
                registry,
                workspaceSelections,
                new BearerTokenValidator("test-token"),
                Logger.getLogger(
                        BridgeHttpServerTest.class.getName()));

        server.start();

        client = HttpClient.newHttpClient();

        baseUrl =
                "http://127.0.0.1:"
                        + server.boundPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void rejectsMissingBearerToken() throws Exception {
        var response = sendGet(
                "/api/v1/status",
                null);

        assertEquals(401, response.statusCode());

        assertEquals(
                "application/json; charset=utf-8",
                response.headers()
                        .firstValue("Content-Type")
                        .orElse(""));
    }

    @Test
    void exposesAuthenticatedStatus() throws Exception {
        var response = sendGet(
                "/api/v1/status",
                "Bearer test-token");

        assertEquals(200, response.statusCode());

        var body = response.body();

        assertTrue(body.contains(
                "\"bridge\":\"MoxueBridge\""));

        assertTrue(body.contains(
                "\"bridge_version\":\"0.1.0\""));

        assertTrue(body.contains(
                "\"minecraft\":\"1.21.1\""));

        assertTrue(body.contains(
                "\"server_software\":\"Paper\""));

        assertTrue(body.contains(
                "\"online\":true"));
    }

    @Test
    void exposesPlugins() throws Exception {
        var response = sendGet(
                "/api/v1/plugins",
                "Bearer test-token");

        assertEquals(200, response.statusCode());

        assertTrue(response.body().contains(
                "\"name\":\"VeinMiner\""));

        assertTrue(response.body().contains(
                "\"version\":\"2.11.2\""));

        assertTrue(response.body().contains(
                "\"enabled\":true"));

        assertTrue(response.body().contains(
                "\"integrated\":true"));
    }

    @Test
    void exposesCapabilities() throws Exception {
        var response = sendGet(
                "/api/v1/capabilities",
                "Bearer test-token");

        assertEquals(200, response.statusCode());

        assertTrue(response.body().contains(
                "\"id\":\"vein_mining\""));

        assertTrue(response.body().contains(
                "\"plugin\":\"VeinMiner\""));

        assertTrue(response.body().contains(
                "\"max_chain\":100"));
    }

    @Test
    void exposesResourceCatalog() throws Exception {
        var response = sendGet(
                "/api/v1/resources",
                "Bearer test-token");

        assertEquals(200, response.statusCode());

        assertTrue(response.body().contains(
                "\"id\":\"examplemod:rubber_log\""));

        assertTrue(response.body().contains(
                "\"minimum_drop_count\":1"));

        assertTrue(response.body().contains(
                "\"examplemod:rubber_leaves\""));

        assertTrue(response.body().contains(
                "\"cleanup_policy\":\"natural_decay\""));
    }

    @Test
    void exposesAuthenticatedWorkspaceSelections()
            throws Exception {

        workspaceSelections.setPointA(
                "world-1",
                "overworld",
                "player-1",
                "Boss",
                new WorkspaceSelectionPoint(
                        0,
                        64,
                        0));

        workspaceSelections.setPointB(
                "world-1",
                "overworld",
                "player-1",
                "Boss",
                new WorkspaceSelectionPoint(
                        8,
                        64,
                        8));

        var response = sendGet(
                "/api/v1/workspace-selections",
                "Bearer test-token");

        assertEquals(200, response.statusCode());

        var body = response.body();

        assertTrue(body.contains(
                "\"version\":1"));
        assertTrue(body.contains(
                "\"generated_at\":"));
        assertTrue(body.contains(
                "\"player_id\":\"player-1\""));
        assertTrue(body.contains(
                "\"player_name\":\"Boss\""));
        assertTrue(body.contains(
                "\"dimension\":\"overworld\""));
        assertTrue(body.contains(
                "\"point_a\":{\"x\":0,\"y\":64,\"z\":0}"));
        assertTrue(body.contains(
                "\"point_b\":{\"x\":8,\"y\":64,\"z\":8}"));
        assertTrue(
                !body.contains("world-1"));
    }

    @Test
    void workspaceSelectionsRequireAuthentication()
            throws Exception {

        var response = sendGet(
                "/api/v1/workspace-selections",
                null);

        assertEquals(
                401,
                response.statusCode());
    }

    @Test
    void rejectsUnsupportedMethod() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(
                        baseUrl
                                + "/api/v1/status"))
                .header(
                        "Authorization",
                        "Bearer test-token")
                .POST(
                        HttpRequest.BodyPublishers
                                .noBody())
                .build();

        var response = client.send(
                request,
                HttpResponse.BodyHandlers
                        .ofString());

        assertEquals(405, response.statusCode());
    }

    @Test
    void returnsNotFoundForUnknownEndpoint()
            throws Exception {

        var response = sendGet(
                "/api/v1/nope",
                "Bearer test-token");

        assertEquals(404, response.statusCode());
    }

    private HttpResponse<String> sendGet(
            String path,
            String authorization)
            throws Exception {

        var builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET();

        if (authorization != null) {
            builder.header(
                    "Authorization",
                    authorization);
        }

        return client.send(
                builder.build(),
                HttpResponse.BodyHandlers
                        .ofString());
    }
}