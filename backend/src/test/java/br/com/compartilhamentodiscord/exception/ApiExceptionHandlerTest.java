package br.com.compartilhamentodiscord.exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import br.com.compartilhamentodiscord.security.RequestIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** MVC-only error tests complement the real HTTP security suites. No production authorization is replaced. */
class ApiExceptionHandlerTest {
    private static final String PRIVATE_DETAIL = "private-marker-jdbc:postgresql://internal/path";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new Fixture(), new ApiErrorController())
                .setControllerAdvice(new ApiExceptionHandler()).setValidator(validator)
                .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void malformedJsonAndInvalidEntriesNeverEchoRejectedContent() throws Exception {
        for (String input : new String[]{"{\"value\":\"" + PRIVATE_DETAIL, "{}", "{\"value\":\"\"}",
                "{\"value\":\"" + PRIVATE_DETAIL + "\"}"}) {
            var response = mvc.perform(post("/fixture").contentType("application/json").content(input))
                    .andReturn().getResponse();
            assertError(response, 400, "INVALID_REQUEST", false);
        }
    }

    @Test
    void unexpectedExceptionDoesNotDiscloseItsMessageOrStack() throws Exception {
        var response = mvc.perform(get("/fixture/failure")).andReturn().getResponse();
        assertError(response, 500, "INTERNAL_ERROR", false);
        assertFalse(response.getContentAsString().contains("IllegalStateException"));
        assertFalse(response.getContentAsString().contains("stackTrace"));
    }

    @Test
    void routingAndContentTypeFailuresUseTheSameContract() throws Exception {
        assertError(mvc.perform(get("/fixture")).andReturn().getResponse(), 405, "METHOD_NOT_ALLOWED", false);
        assertError(mvc.perform(post("/fixture").contentType("text/plain").content(PRIVATE_DETAIL))
                .andReturn().getResponse(), 415, "UNSUPPORTED_MEDIA_TYPE", false);
        assertError(mvc.perform(get("/missing")).andReturn().getResponse(), 404, "NOT_FOUND", false);
    }

    @Test
    void servletFallbackSanitizesErrorAttributesAndKeepsStatus() throws Exception {
        var response = mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 503)
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, PRIVATE_DETAIL)
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException(PRIVATE_DETAIL)))
                .andReturn().getResponse();
        assertError(response, 503, "SERVICE_UNAVAILABLE", true);
    }

    @Test
    void requestIdsAreGeneratedPerRequestAndIncomingValuesAreIgnored() throws Exception {
        var first = mvc.perform(get("/fixture/failure").header("X-Request-Id", PRIVATE_DETAIL))
                .andReturn().getResponse();
        var second = mvc.perform(get("/fixture/failure").header("X-Request-Id", PRIVATE_DETAIL))
                .andReturn().getResponse();
        assertError(first, 500, "INTERNAL_ERROR", false);
        assertError(second, 500, "INTERNAL_ERROR", false);
        assertNotEquals(first.getHeader("X-Request-Id"), second.getHeader("X-Request-Id"));
    }

    private static void assertError(MockHttpServletResponse response, int status, String code, boolean retryable)
            throws Exception {
        assertEquals(status, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        JsonNode body = JSON.readTree(response.getContentAsString());
        var fields = new java.util.HashSet<String>();
        body.propertyNames().forEach(fields::add);
        assertEquals(Set.of("code", "message", "requestId", "retryable"), fields);
        assertEquals(code, body.path("code").asText());
        assertEquals(retryable, body.path("retryable").asBoolean());
        String id = body.path("requestId").asText();
        assertDoesNotThrow(() -> UUID.fromString(id));
        assertEquals(id, response.getHeader("X-Request-Id"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertFalse(response.getContentAsString().contains(PRIVATE_DETAIL));
        assertFalse(body.path("message").asText().isBlank());
    }

    /** Explicit profile prevents discovery by the separate HTTP test application's component scan. */
    @RestController
    @Profile("error-handler-fixture")
    static class Fixture {
        @PostMapping("/fixture")
        Input input(@Valid @RequestBody Input input) { return input; }
        @GetMapping("/fixture/failure")
        Input failure() { throw new IllegalStateException(PRIVATE_DETAIL); }
    }

    record Input(@NotBlank @Size(max = 8) String value) { }
}
