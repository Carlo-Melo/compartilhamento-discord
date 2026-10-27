package br.com.compartilhamentodiscord.security;

import static br.com.compartilhamentodiscord.security.HttpSecurityServer.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProofSecurityHttpTest {
    private static final String CAPTURE_ORIGIN = "https://192.168.1.50:4443";
    private static final String PRESENTER = "t05_test_presenter_capability_0123456789";
    private static final String VIEWER = "t05_test_viewer_capability_9876543210";

    @Test void auxiliaryRoutesStayAbsentWithoutOptInOrInProduction() throws Exception {
        String[] profiles = {"", "livekit-proof,capture-proof", "livekit-proof,capture-proof,prod",
                "livekit-proof,capture-proof,production"};
        for (int i = 0; i < profiles.length; i++) {
            String enabled = i == 1 ? "false" : "true";
            try (var server = new HttpSecurityServer("--spring.profiles.active=" + profiles[i],
                    "--livekit.proof.enabled=" + enabled, "--capture.proof.enabled=" + enabled)) {
                var client = server.client();
                for (String path : new String[]{"/api/dev/livekit/rooms", "/api/dev/capture/session", "/api/dev/capture/session/access", "/api/dev/capture/session/viewer"}) {
                    error(server.request(client, "GET", path, Map.of(), null), 404, null);
                    error(server.request(client, "POST", path, Map.of("Origin", "https://outside.invalid"), "{}"), 404, null);
                }
            }
        }
    }

    @Test void localLiveKitProofKeepsExactTransportAndExplicitRequestValidation() throws Exception {
        assumeTrue(classPresent("controller.LiveKitProofController"), "T-02 source is absent from this artifact");
        try (var server = new HttpSecurityServer(livekitOptions())) {
            var client = server.client();
            String path = "/api/dev/livekit/rooms/not-owned/tokens";
            Map<String, String> headers = livekitHeaders();
            error(server.request(client, "GET", "/api/dev/livekit/rooms/not-owned", without(headers, "Origin"), null), 404, "LOCAL_PROOF_REJECTED");
            assertEquals(204, server.request(client, "DELETE", "/api/dev/livekit/rooms", headers, null).statusCode());
            error(server.request(client, "POST", path, without(headers, "Origin"), "{}"), 403, "LOCAL_PROOF_ONLY");
            error(server.request(client, "POST", path, changed(headers, "Origin", "https://outside.invalid"), "{}"), 403, "LOCAL_PROOF_ONLY");
            error(server.request(client, "POST", path, changed(headers, "Host", "outside.invalid"), "{}"), 403, "LOCAL_PROOF_ONLY");
            error(server.request(client, "POST", path, without(headers, "X-LiveKit-Proof"), "{}"), 403, "LOCAL_PROOF_ONLY");
            error(server.request(client, "POST", path, headers, "{\"role\":"), 400, null);
            error(server.request(client, "POST", path, headers, "{}"), 400, null);
            error(server.request(client, "POST", path, headers, "{\"role\":\"OWNER\"}"), 400, null);
            var integration = server.request(client, "POST", "/api/dev/livekit/rooms", headers, null);
            assertTrue(error(integration, 503, "SFU_UNAVAILABLE").path("retryable").asBoolean());
            assertFalse(integration.body().contains("t05_test_integration_secret"));
            assertFalse(integration.body().contains("127.0.0.1"));
        }
    }

    private static String[] livekitOptions() {
        return new String[]{"--spring.profiles.active=livekit-proof", "--livekit.enabled=true",
                "--livekit.proof.enabled=true", "--livekit.api-url=http://127.0.0.1:1",
                "--livekit.browser-url=ws://127.0.0.1:7880", "--livekit.api-key=t05-test-api",
                "--livekit.api-secret=t05_test_integration_secret_0123456789", "--livekit.request-timeout=1s"};
    }

    @Test void captureProofPreservesCapabilitiesOriginAndSpectatorRestrictions() throws Exception {
        assumeTrue(classPresent("controller.CaptureProofController"), "T-03 source is absent from this artifact");
        try (var server = new HttpSecurityServer("--spring.profiles.active=capture-proof", "--livekit.enabled=true",
                "--capture.proof.enabled=true", "--capture.proof.origin=" + CAPTURE_ORIGIN,
                "--capture.proof.presenter-key=" + PRESENTER, "--capture.proof.viewer-key=" + VIEWER,
                "--livekit.api-url=http://127.0.0.1:1", "--livekit.browser-url=wss://192.168.1.50:4443",
                "--livekit.api-key=t05-test-api", "--livekit.api-secret=t05_test_integration_secret_0123456789",
                "--livekit.request-timeout=1s")) {
            var client = server.client();
            String path = "/api/dev/capture/session";
            var viewer = captureHeaders(VIEWER);
            var presenter = captureHeaders(PRESENTER);
            // Validation is independent of SFU availability and never returns a media credential.
            for (var capability : new String[]{PRESENTER, VIEWER}) {
                var validated = server.request(client, "GET", path + "/access", captureHeaders(capability), null);
                assertEquals(200, validated.statusCode());
                assertEquals(capability.equals(PRESENTER) ? "PRESENTER" : "VIEWER", JSON.readTree(validated.body()).path("role").asText());
                assertEquals(1, JSON.readTree(validated.body()).size());
                assertFalse(validated.body().contains(capability));
                assertEquals("no-store", validated.headers().firstValue("Cache-Control").orElse(""));
            }
            error(server.request(client, "GET", path + "/access", without(viewer, "X-Capture-Key"), null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "GET", path + "/access", changed(viewer, "X-Capture-Key", "invalid"), null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "GET", path + "/access", changed(viewer, "Origin", "https://outside.invalid"), null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "POST", path + "/access", viewer, null), 404, "NOT_FOUND");
            var empty = server.request(client, "GET", path, without(viewer, "Origin"), null);
            assertEquals(200, empty.statusCode());
            assertFalse(JSON.readTree(empty.body()).path("exists").asBoolean());
            assertFalse(JSON.readTree(empty.body()).path("live").asBoolean());
            assertEquals(200, server.request(client, "GET", path, presenter, null).statusCode());
            error(server.request(client, "GET", path, without(viewer, "X-Capture-Key"), null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "GET", path, without(viewer, "X-Capture-Proof"), null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "GET", path, changed(viewer, "Host", "127.0.0.1:" + server.port), null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "GET", path, changed(viewer, "Origin", "https://outside.invalid"), null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "POST", path, without(presenter, "Origin"), null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "POST", path, viewer, null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "DELETE", path, viewer, null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "POST", path + "/viewer", presenter, null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "DELETE", path + "/viewer", presenter, null), 403, "CAPTURE_PROOF_REJECTED");
            error(server.request(client, "POST", path + "/viewer", viewer, null), 400, "CAPTURE_PROOF_VIEWER_INVALID");
            error(server.request(client, "DELETE", path + "/viewer", viewer, null), 400, "CAPTURE_PROOF_VIEWER_INVALID");
            var admission = changed(viewer, "X-Capture-Viewer", "29a0b79a-74aa-4f21-973a-cb97ae71e051");
            error(server.request(client, "POST", path + "/viewer", admission, null), 409, "CAPTURE_PROOF_REJECTED");
            assertEquals(204, server.request(client, "DELETE", path + "/viewer", admission, null).statusCode());
            for (String method : new String[]{"POST", "DELETE"}) {
                error(server.request(client, method, path + "/viewer", without(admission, "Origin"), null), 403, "CAPTURE_PROOF_REJECTED");
                error(server.request(client, method, path + "/viewer", without(admission, "X-Capture-Proof"), null), 403, "CAPTURE_PROOF_REJECTED");
                error(server.request(client, method, path + "/viewer", without(admission, "X-Capture-Key"), null), 403, "CAPTURE_PROOF_REJECTED");
                error(server.request(client, method, path + "/viewer", changed(admission, "Host", "outside.invalid"), null), 403, "CAPTURE_PROOF_REJECTED");
                error(server.request(client, method, path + "/viewer", changed(admission, "Origin", "https://outside.invalid"), null), 403, "CAPTURE_PROOF_REJECTED");
            }
            error(server.request(client, "PUT", path + "/viewer", admission, null), 404, "NOT_FOUND");
            error(server.request(client, "DELETE", path + "/viewer/other", admission, null), 404, "NOT_FOUND");
            assertEquals(204, server.request(client, "DELETE", path, presenter, null).statusCode());
            var integration = server.request(client, "POST", path, presenter, null);
            assertTrue(error(integration, 503, "SFU_UNAVAILABLE").path("retryable").asBoolean());
            assertFalse(integration.body().contains(PRESENTER));
            assertFalse(integration.body().contains(VIEWER));
            assertFalse(integration.body().contains("127.0.0.1"));
        }
    }

    private static Map<String, String> livekitHeaders() {
        return new HashMap<>(Map.of("Host", "127.0.0.1:8080", "Origin", ORIGIN,
                "X-LiveKit-Proof", "local-development", "Content-Type", "application/json"));
    }

    private static Map<String, String> captureHeaders(String key) {
        return new HashMap<>(Map.of("Host", "192.168.1.50:4443", "Origin", CAPTURE_ORIGIN,
                "X-Capture-Proof", "restricted-development", "X-Capture-Key", key));
    }

    private static Map<String, String> without(Map<String, String> headers, String key) {
        var result = new HashMap<>(headers); result.remove(key); return result;
    }

    private static Map<String, String> changed(Map<String, String> headers, String key, String value) {
        var result = new HashMap<>(headers); result.put(key, value); return result;
    }
}
