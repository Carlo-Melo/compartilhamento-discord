package br.com.compartilhamentodiscord.security;

import static br.com.compartilhamentodiscord.security.HttpSecurityServer.*;
import static org.junit.jupiter.api.Assertions.*;

import br.com.compartilhamentodiscord.service.access.AccessStore;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import testsupport.AllowedAccessVerification;

/** Loopback HTTP tests; only the future external verification boundary is substituted. */
class AccessHttpTest {
    private static final Class<?>[] ALLOWED = {AllowedAccessVerification.class};

    private static AccessStore.Grant grant() {
        return new AccessStore.Grant(UUID.randomUUID(), UUID.randomUUID(), "123456", "234567",
                "Participante", AccessStore.Role.OWNER);
    }

    private static String csrf(HttpSecurityServer server, HttpClient browser) throws Exception {
        var response = server.request(browser, "GET", "/api/csrf", Map.of(), null);
        assertEquals(200, response.statusCode());
        return JSON.readTree(response.body()).path("token").asText();
    }

    private static HttpResponse<String> exchange(HttpSecurityServer server, HttpClient browser,
                                                  String csrf, String ticket) throws Exception {
        return server.request(browser, "POST", "/api/access/exchange", Map.of(
                "Origin", ORIGIN, "X-XSRF-TOKEN", csrf, "Content-Type", "application/json"),
                "{\"ticket\":\"" + ticket + "\"}");
    }

    private static String sessionCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("ACCESS_SESSION="))
                .map(value -> value.split(";", 2)[0]).findFirst().orElseThrow();
    }

    @Test void exchangeRequiresOriginAndCsrfAndRotatesSession() throws Exception {
        try (var server = new HttpSecurityServer(ALLOWED)) {
            var browser = server.browser();
            var bootstrap = server.request(browser, "GET", "/api/csrf", Map.of(), null);
            String initialCookie = sessionCookie(bootstrap);
            String token = JSON.readTree(bootstrap.body()).path("token").asText();
            var issuedGrant = grant();
            String ticket = server.bean(AccessStore.class).issue(issuedGrant);
            String body = "{\"ticket\":\"" + ticket + "\"}";
            error(server.request(browser, "POST", "/api/access/exchange", Map.of(
                    "X-XSRF-TOKEN", token, "Content-Type", "application/json"), body), 403, "ORIGIN_INVALID");
            error(server.request(browser, "POST", "/api/access/exchange", Map.of(
                    "Origin", ORIGIN, "Content-Type", "application/json"), body), 403, "CSRF_INVALID");
            var exchanged = exchange(server, browser, token, ticket);
            assertEquals(200, exchanged.statusCode());
            assertFalse(initialCookie.equals(sessionCookie(exchanged)), "session ID must rotate");
            assertFalse(exchanged.body().contains(ticket));
            var access = JSON.readTree(exchanged.body()).path("access");
            assertEquals(issuedGrant.sessionId().toString(), access.path("sessionId").asText());
            assertEquals(issuedGrant.botId().toString(), access.path("botId").asText());
            assertEquals(issuedGrant.guildId(), access.path("guildId").asText());
            assertEquals(issuedGrant.discordUserId(), access.path("discordUserId").asText());
            assertEquals("OWNER", access.path("role").asText());
            assertEquals("/s/" + issuedGrant.sessionId() + "/transmitir", JSON.readTree(exchanged.body()).path("nextPath").asText());
            assertEquals("no-store", exchanged.headers().firstValue("Cache-Control").orElse(""));
            assertEquals(200, server.request(browser, "GET", "/api/access/session", Map.of(), null).statusCode());
            error(server.request(server.client(), "GET", "/api/access/session", Map.of("Cookie", initialCookie), null), 401, "ACCESS_REQUIRED");
            error(exchange(server, browser, csrf(server, browser), ticket), 401, "ACCESS_INVALID");
            String second = server.bean(AccessStore.class).issue(grant());
            error(server.request(browser, "POST", "/api/access/exchange", Map.of(
                    "Origin", ORIGIN, "X-XSRF-TOKEN", "wrong", "Content-Type", "application/json"),
                    "{\"ticket\":\"" + second + "\"}"), 403, "CSRF_INVALID");
            String rotatedToken = csrf(server, browser);
            error(server.request(browser, "POST", "/api/access/exchange", Map.of(
                    "Origin", ORIGIN, "X-XSRF-TOKEN", rotatedToken, "Content-Type", "application/json"),
                    "{\"ticket\":\"" + second + "\",\"role\":\"OWNER\"}"), 400, "INVALID_REQUEST");
            assertEquals(200, exchange(server, browser, rotatedToken, second).statusCode());
        }
    }

    @Test void concurrentHttpExchangesCreateAtMostOneContext() throws Exception {
        try (var server = new HttpSecurityServer(ALLOWED)) {
            String ticket = server.bean(AccessStore.class).issue(grant());
            List<HttpClient> browsers = new ArrayList<>();
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                HttpClient browser = server.browser();
                browsers.add(browser);
                tokens.add(csrf(server, browser));
            }
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(8)) {
                var futures = new ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
                for (int i = 0; i < browsers.size(); i++) {
                    final int index = i;
                    futures.add(executor.submit(() -> {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                        return exchange(server, browsers.get(index), tokens.get(index), ticket);
                    }));
                }
                start.countDown();
                int successes = 0;
                for (var future : futures) {
                    var response = future.get(20, TimeUnit.SECONDS);
                    if (response.statusCode() == 200) successes++;
                    else error(response, 401, "ACCESS_INVALID");
                }
                assertEquals(1, successes);
            }
            int authorized = 0;
            for (var browser : browsers) {
                int status = server.request(browser, "GET", "/api/access/session", Map.of(), null).statusCode();
                if (status == 200) authorized++;
                else assertEquals(401, status);
            }
            assertEquals(1, authorized);
        }
    }

    @Test void restartInvalidatesTicketsAndAuthorizedContext() throws Exception {
        HttpClient browser;
        String unusedTicket;
        try (var original = new HttpSecurityServer(ALLOWED)) {
            browser = original.browser();
            String token = csrf(original, browser);
            String ticket = original.bean(AccessStore.class).issue(grant());
            assertEquals(200, exchange(original, browser, token, ticket).statusCode());
            assertEquals(200, original.request(browser, "GET", "/api/access/session", Map.of(), null).statusCode());
            unusedTicket = original.bean(AccessStore.class).issue(grant());
        }
        try (var restarted = new HttpSecurityServer(ALLOWED)) {
            error(restarted.request(browser, "GET", "/api/access/session", Map.of(), null), 401, "ACCESS_REQUIRED");
            error(exchange(restarted, browser, csrf(restarted, browser), unusedTicket), 401, "ACCESS_INVALID");
        }
    }

    @Test void aNewlyConsumedTicketReplacesThePreviousContextForThatUserAndSession() throws Exception {
        try (var server = new HttpSecurityServer(ALLOWED)) {
            var first = server.browser();
            var second = server.browser();
            var originalGrant = grant();
            String firstToken = csrf(server, first);
            String secondToken = csrf(server, second);
            String firstTicket = server.bean(AccessStore.class).issue(originalGrant);
            assertEquals(200, exchange(server, first, firstToken, firstTicket).statusCode());
            String replacement = server.bean(AccessStore.class).issue(originalGrant);
            assertEquals(200, server.request(first, "GET", "/api/access/session", Map.of(), null).statusCode());
            assertEquals(200, exchange(server, second, secondToken, replacement).statusCode());
            error(server.request(first, "GET", "/api/access/session", Map.of(), null), 401, "ACCESS_REQUIRED");
            assertEquals(200, server.request(second, "GET", "/api/access/session", Map.of(), null).statusCode());
        }
    }

    @Test void unavailableExternalVerifierConsumesTicketAndFailsClosed() throws Exception {
        try (var server = new HttpSecurityServer()) {
            var browser = server.browser();
            String token = csrf(server, browser);
            String ticket = server.bean(AccessStore.class).issue(grant());
            var unavailable = exchange(server, browser, token, ticket);
            assertEquals(503, unavailable.statusCode());
            assertFalse(unavailable.body().contains(ticket));
            error(exchange(server, browser, token, ticket), 401, "ACCESS_INVALID");
            error(server.request(browser, "GET", "/api/access/session", Map.of(), null), 401, "ACCESS_REQUIRED");
        }
    }

    @Test void exchangeAttemptsAreBoundedPerBootstrapAndIp() throws Exception {
        try (var server = new HttpSecurityServer(ALLOWED)) {
            var browser = server.browser();
            String token = csrf(server, browser);
            // A well-formed but never-issued value, not a credential.
            String unknown = "A".repeat(43);
            for (int i = 0; i < 10; i++) {
                error(exchange(server, browser, token, unknown), 401, "ACCESS_INVALID");
            }
            var limited = exchange(server, browser, token, unknown);
            error(limited, 429, "RATE_LIMITED");
            assertTrue(limited.headers().firstValue("Retry-After").isPresent());
        }
    }
}
