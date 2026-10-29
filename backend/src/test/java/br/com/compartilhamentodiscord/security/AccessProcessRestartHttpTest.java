package br.com.compartilhamentodiscord.security;

import static br.com.compartilhamentodiscord.security.HttpSecurityServer.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AccessProcessRestartHttpTest {
    @Test void oldTicketAndAuthorizedSessionDoNotSurviveSeparateJvmRestart() throws Exception {
        HttpClient browser = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
        String unusedTicket;
        String oldCsrf;
        try (var original = new Child()) {
            oldCsrf = csrf(browser, original.port);
            assertEquals(200, exchange(browser, original.port, oldCsrf, original.first).statusCode());
            assertEquals(200, request(browser, original.port, "GET", "/api/access/session", Map.of(), null).statusCode());
            unusedTicket = original.unused;
        }
        try (var restarted = new Child()) {
            error(request(browser, restarted.port, "GET", "/api/access/session", Map.of(), null), 401, "ACCESS_REQUIRED");
            error(exchange(browser, restarted.port, oldCsrf, unusedTicket), 403, "CSRF_INVALID");
            String newCsrf = csrf(browser, restarted.port);
            error(exchange(browser, restarted.port, newCsrf, unusedTicket), 401, "ACCESS_INVALID");
            assertEquals(200, exchange(browser, restarted.port, newCsrf, restarted.first).statusCode());
            assertEquals(200, request(browser, restarted.port, "GET", "/api/access/session", Map.of(), null).statusCode());
        }
    }

    private static String csrf(HttpClient browser, int port) throws Exception {
        var response = request(browser, port, "GET", "/api/csrf", Map.of(), null);
        assertEquals(200, response.statusCode());
        return JSON.readTree(response.body()).path("token").asText();
    }

    private static HttpResponse<String> exchange(HttpClient browser, int port, String csrf, String ticket) throws Exception {
        return request(browser, port, "POST", "/api/access/exchange", Map.of(
                "Origin", ORIGIN, "X-XSRF-TOKEN", csrf, "Content-Type", "application/json"),
                "{\"ticket\":\"" + ticket + "\"}");
    }

    private static HttpResponse<String> request(HttpClient browser, int port, String method, String path,
                                                Map<String, String> headers, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        headers.forEach(request::header);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final class Child implements AutoCloseable {
        private final Process process;
        final int port;
        final String first;
        final String unused;

        Child() throws Exception {
            String java = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            process = new ProcessBuilder(java, "-cp", classpath, ProcessAccessServer.class.getName()).start();
            var input = new DataInputStream(process.getInputStream());
            var startup = CompletableFuture.supplyAsync(() -> {
                try {
                    int candidate = input.readInt();
                    if (candidate == 0) throw new IOException("Child server failed");
                    return new Startup(candidate, input.readUTF(), input.readUTF());
                } catch (IOException e) { throw new CompletionException(e); }
            });
            Startup ready;
            try { ready = startup.get(30, TimeUnit.SECONDS); }
            catch (Exception failure) { process.destroyForcibly(); throw failure; }
            port = ready.port(); first = ready.first(); unused = ready.unused();
        }

        @Override public void close() throws Exception {
            process.getOutputStream().close();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                fail("Child JVM did not stop");
            }
            assertEquals(0, process.exitValue(), "Child JVM failed");
        }
    }

    private record Startup(int port, String first, String unused) {
        @Override public String toString() { return "Startup[redacted]"; }
    }
}
