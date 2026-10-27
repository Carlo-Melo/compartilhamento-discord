package br.com.compartilhamentodiscord.exception;

import br.com.compartilhamentodiscord.dto.response.ApiError;
import br.com.compartilhamentodiscord.security.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import tools.jackson.databind.json.JsonMapper;

/** Shared serialization for MVC and security-filter failures. */
public final class ApiErrors {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ApiErrors() { }

    public static ApiError body(HttpServletRequest request, String code, String message, boolean retryable) {
        return new ApiError(code, message, RequestIdFilter.requestId(request), retryable);
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, int status,
                             String code, String message, boolean retryable) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Request-Id", RequestIdFilter.requestId(request));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(body(request, code, message, retryable)));
    }
}
