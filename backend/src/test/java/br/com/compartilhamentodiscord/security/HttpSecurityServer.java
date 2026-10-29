package br.com.compartilhamentodiscord.security;

import static org.junit.jupiter.api.Assertions.*;

import br.com.compartilhamentodiscord.CompartilhamentoDiscordApplication;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real loopback Tomcat and HTTP clients. No authorization doubles or test endpoints. */
final class HttpSecurityServer implements AutoCloseable {
    static final String ORIGIN = "http://127.0.0.1:4200";
    static final JsonMapper JSON = JsonMapper.builder().build();
    static { System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host"); }

    private final ConfigurableApplicationContext context;
    final int port;
    final int managementPort;

    HttpSecurityServer(String... options) {
        this(new Class<?>[0], options);
    }

    HttpSecurityServer(Class<?>[] extraSources, String... options) {
        List<String> arguments = new ArrayList<>(List.of(
                "--server.address=127.0.0.1", "--server.port=0", "--management.server.address=127.0.0.1",
                "--app.security.origin=" + ORIGIN,
                "--management.server.port=0", "--spring.main.banner-mode=off", "--logging.level.root=OFF",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/t05_unavailable",
                "--spring.datasource.username=t05_test", "--spring.datasource.password=t05_test_only",
                "--spring.datasource.hikari.initialization-fail-timeout=-1",
                "--spring.datasource.hikari.connection-timeout=250", "--spring.datasource.hikari.validation-timeout=250",
                // This suite deliberately uses an unavailable database; schema tests run separately.
                "--spring.flyway.enabled=false", "--spring.jpa.hibernate.ddl-auto=none",
                "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
                "--livekit.enabled=false", "--livekit.proof.enabled=false", "--capture.proof.enabled=false"));
        // Keep one value per argument: duplicate command-line properties become lists in Spring.
        for (String option : options) {
            String key = option.substring(0, option.indexOf('='));
            arguments.removeIf(existing -> existing.startsWith(key + "="));
            arguments.add(option);
        }
        context = new SpringApplicationBuilder(CompartilhamentoDiscordApplication.class).sources(extraSources)
                .registerShutdownHook(false).run(arguments.toArray(String[]::new));
        port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        managementPort = context.getEnvironment().getRequiredProperty("local.management.port", Integer.class);
    }

    <T> T bean(Class<T> type) { return context.getBean(type); }

    HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    HttpClient browser() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    HttpResponse<String> request(HttpClient client, String method, String path,
                                 Map<String, String> headers, String body) throws Exception {
        return request(client, port, method, path, headers, body);
    }

    HttpResponse<String> request(HttpClient client, int destination, String method, String path,
                                 Map<String, String> headers, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + destination + path))
                .timeout(Duration.ofSeconds(10));
        headers.forEach(builder::header);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode error(HttpResponse<String> response, int expectedStatus, String expectedCode) {
        assertEquals(expectedStatus, response.statusCode(), () -> "error code: " + JSON.readTree(response.body()).path("code").asText());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        JsonNode error = JSON.readTree(response.body());
        var names = new HashSet<String>();
        error.propertyNames().forEach(names::add);
        assertEquals(java.util.Set.of("code", "message", "requestId", "retryable"), names);
        if (expectedCode != null) assertEquals(expectedCode, error.path("code").asText());
        assertFalse(error.path("message").asText().isBlank());
        assertDoesNotThrow(() -> UUID.fromString(error.path("requestId").asText()));
        assertEquals(error.path("requestId").asText(), response.headers().firstValue("X-Request-Id").orElse(""));
        assertTrue(error.path("retryable").isBoolean());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
        assertTrue(response.headers().firstValue("WWW-Authenticate").isEmpty());
        assertTrue(response.headers().firstValue("Location").isEmpty());
        assertFalse(response.body().contains("Exception"));
        assertFalse(response.body().contains("jdbc:"));
        assertFalse(response.body().contains("java."));
        return error;
    }

    static boolean classPresent(String name) {
        try { Class.forName("br.com.compartilhamentodiscord." + name); return true; }
        catch (ClassNotFoundException missing) { return false; }
    }

    @Override public void close() { context.close(); }
}
