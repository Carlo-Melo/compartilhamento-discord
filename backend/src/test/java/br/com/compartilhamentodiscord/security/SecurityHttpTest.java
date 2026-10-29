package br.com.compartilhamentodiscord.security;

import static br.com.compartilhamentodiscord.security.HttpSecurityServer.*;
import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SecurityHttpTest {
    private static HttpSecurityServer server;
    private static HttpClient client;

    @BeforeAll static void start() { server = new HttpSecurityServer(); client = server.client(); }
    @AfterAll static void stop() { if (server != null) server.close(); }

    @Test void publicLivenessHasOnlyStatusAndSecurityHeaders() throws Exception {
        var live = server.request(client, "GET", "/health/live", Map.of(), null);
        assertEquals(200, live.statusCode());
        assertEquals("{\"status\":\"UP\"}", live.body());
        assertEquals("nosniff", live.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals("DENY", live.headers().firstValue("X-Frame-Options").orElse(""));
        assertEquals("no-referrer", live.headers().firstValue("Referrer-Policy").orElse(""));
        assertTrue(live.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"));
        assertTrue(live.headers().firstValue("Permissions-Policy").orElse("").contains("display-capture=(self)"));
        assertTrue(live.headers().firstValue("Set-Cookie").isEmpty());
        error(server.request(client, "POST", "/health/live", Map.of("Origin", ORIGIN), null), 403, "CSRF_INVALID");
    }

    @Test void readinessAndManagementDetailsStayOnRestrictedInterface() throws Exception {
        var ready = server.request(client, server.managementPort, "GET", "/health/ready", Map.of(), null);
        assertEquals(503, ready.statusCode());
        assertEquals("{\"status\":\"DOWN\"}", ready.body());
        assertTrue(server.request(client, "GET", "/health/ready", Map.of(), null).statusCode() >= 400);
        for (String path : new String[]{"/env", "/configprops", "/heapdump", "/health", "/health/db", "/actuator/health"}) {
            var denied = server.request(client, server.managementPort, "GET", path, Map.of(), null);
            assertTrue(denied.statusCode() >= 400, path);
            assertFalse(denied.body().contains("t05_test_only"));
            assertFalse(denied.body().contains("jdbc:"));
        }
    }

    @Test void contextCannotBeForgedByBrowserHeadersCookiesOrBasicCredentials() throws Exception {
        error(server.request(client, "GET", "/api/access/session", Map.of(
                "X-Discord-User-Id", "123", "X-Role", "OWNER", "X-Guild-Id", "456",
                "Cookie", "ACCESS_SESSION=invented", "Authorization", "Basic dGVzdDp0ZXN0"), null), 401, "ACCESS_REQUIRED");
        for (String path : new String[]{"/login", "/oauth2/authorization/discord", "/api/sessions/00000000-0000-0000-0000-000000000000", "/anything"}) {
            error(server.request(client, "GET", path, Map.of(), null), 401, "ACCESS_REQUIRED");
        }
    }

    @Test void csrfBootstrapDoesNotCreateAnAuthorizedAccessContext() throws Exception {
        HttpClient browser = server.browser();
        var bootstrap = server.request(browser, "GET", "/api/csrf", Map.of(), null);
        assertEquals(200, bootstrap.statusCode());
        String token = JSON.readTree(bootstrap.body()).path("token").asText();
        assertFalse(token.isBlank());
        var cookies = bootstrap.headers().allValues("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=")));
        assertTrue(cookies.stream().anyMatch(cookie -> cookie.startsWith("ACCESS_SESSION=") && cookie.contains("HttpOnly")),
                () -> cookies.stream().map(cookie -> cookie.replaceFirst("=[^;]*", "=<redacted>")).toList().toString());
        assertTrue(cookies.stream().allMatch(cookie -> !cookie.toLowerCase().contains("domain=")));
        error(server.request(browser, "GET", "/api/access/session", Map.of(), null), 401, "ACCESS_REQUIRED");
        error(server.request(browser, "POST", "/api/access/exchange", Map.of(
                "Origin", ORIGIN, "X-XSRF-TOKEN", token, "Content-Type", "application/json"), "{\"ticket\":\"untrusted\"}"), 401, "ACCESS_INVALID");
    }

    @Test void mutationsRequireExactOriginAndCsrfBoundToBootstrapSession() throws Exception {
        HttpClient browser = server.browser();
        var bootstrap = server.request(browser, "GET", "/api/csrf", Map.of(), null);
        String token = JSON.readTree(bootstrap.body()).path("token").asText();
        error(server.request(browser, "POST", "/api/access/exchange", Map.of("X-XSRF-TOKEN", token), "{}"), 403, "ORIGIN_INVALID");
        error(server.request(browser, "POST", "/api/access/exchange", Map.of("Origin", "https://outside.invalid", "X-XSRF-TOKEN", token), "{}"), 403, "ORIGIN_INVALID");
        error(server.request(browser, "POST", "/api/access/exchange", Map.of("Origin", "null", "X-XSRF-TOKEN", token), "{}"), 403, "ORIGIN_INVALID");
        error(server.request(browser, "POST", "/api/access/exchange", Map.of("Origin", ORIGIN), "{}"), 403, "CSRF_INVALID");
        error(server.request(browser, "POST", "/api/access/exchange", Map.of("Origin", ORIGIN, "X-XSRF-TOKEN", "wrong"), "{}"), 403, "CSRF_INVALID");
        error(server.request(client, "POST", "/api/access/exchange", Map.of(
                "Origin", ORIGIN, "X-XSRF-TOKEN", token, "Cookie", "XSRF-TOKEN=" + token), "{}"), 403, "CSRF_INVALID");
        error(server.request(browser, "POST", "/integrations/discord/interactions", Map.of(
                "Origin", ORIGIN, "X-XSRF-TOKEN", token), "{}"), 401, "ACCESS_REQUIRED");
        error(server.request(browser, "POST", "/integrations/livekit/webhook", Map.of(
                "Origin", ORIGIN, "X-XSRF-TOKEN", token), "{}"), 401, "ACCESS_REQUIRED");
    }

    @Test void corsDoesNotAuthorizeExternalOriginsOrPreflight() throws Exception {
        var read = server.request(client, "GET", "/health/live", Map.of("Origin", "https://outside.invalid"), null);
        assertTrue(read.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        var preflight = server.request(client, "OPTIONS", "/api/access/exchange", Map.of(
                "Origin", "https://outside.invalid", "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "X-XSRF-TOKEN"), null);
        assertTrue(preflight.statusCode() >= 400);
        assertTrue(preflight.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        assertTrue(preflight.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());
    }

    @Test void requestIdsAreGeneratedInsteadOfReflectingCallerInput() throws Exception {
        String supplied = "untrusted-request-id-with-sensitive-content";
        var first = server.request(client, "GET", "/api/access/session", Map.of("X-Request-Id", supplied), null);
        var second = server.request(client, "GET", "/api/access/session", Map.of("X-Request-Id", supplied), null);
        var firstError = error(first, 401, "ACCESS_REQUIRED");
        var secondError = error(second, 401, "ACCESS_REQUIRED");
        assertFalse(first.body().contains(supplied));
        assertNotEquals(firstError.path("requestId").asText(), secondError.path("requestId").asText());
    }

    @Test void csrfRequiresHeaderAndRejectsDifferentOrRestartedBootstrapSession() throws Exception {
        HttpClient first = server.browser();
        HttpClient second = server.browser();
        String firstToken = JSON.readTree(server.request(first, "GET", "/api/csrf", Map.of(), null).body()).path("token").asText();
        String secondToken = JSON.readTree(server.request(second, "GET", "/api/csrf", Map.of(), null).body()).path("token").asText();
        error(server.request(first, "POST", "/api/access/exchange", Map.of(
                "Origin", ORIGIN, "Content-Type", "application/x-www-form-urlencoded"), "_csrf=" + firstToken), 403, "CSRF_INVALID");
        error(server.request(second, "POST", "/api/access/exchange", Map.of(
                "Origin", ORIGIN, "X-XSRF-TOKEN", firstToken), "{}"), 403, "CSRF_INVALID");
        error(server.request(second, "POST", "/api/access/exchange", Map.of(
                "Origin", ORIGIN, "X-XSRF-TOKEN", secondToken, "Content-Type", "application/json"), "{}"), 400, "INVALID_REQUEST");
        try (var restarted = new HttpSecurityServer()) {
            error(restarted.request(first, "POST", "/api/access/exchange", Map.of(
                    "Origin", ORIGIN, "X-XSRF-TOKEN", firstToken), "{}"), 403, "CSRF_INVALID");
        }
    }

    @Test void httpsConfiguredOriginMakesBothBootstrapCookiesSecureWithoutTrustingForwardedHeaders() throws Exception {
        try (var secureOrigin = new HttpSecurityServer("--app.security.origin=https://app.example.invalid")) {
            var bootstrap = secureOrigin.request(secureOrigin.client(), "GET", "/api/csrf", Map.of(), null);
            assertEquals(200, bootstrap.statusCode());
            var cookies = bootstrap.headers().allValues("Set-Cookie");
            assertTrue(cookies.stream().anyMatch(cookie -> cookie.startsWith("ACCESS_SESSION=")
                    && cookie.contains("Secure") && cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax")));
            assertTrue(cookies.stream().anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=")
                    && cookie.contains("Secure") && !cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax")));
        }
        var spoofed = server.request(client, "GET", "/api/csrf", Map.of(
                "Forwarded", "proto=https;host=outside.invalid", "X-Forwarded-Proto", "https"), null);
        assertTrue(spoofed.headers().allValues("Set-Cookie").stream().noneMatch(cookie -> cookie.contains("Secure")));
    }

    @Test void firewallRejectsAmbiguousPathsWithTheSameSafeErrorContract() throws Exception {
        error(server.request(client, "GET", "/api/access/;session", Map.of(), null), 400, null);
    }
}
