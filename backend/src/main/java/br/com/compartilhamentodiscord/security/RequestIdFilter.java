package br.com.compartilhamentodiscord.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

/** A new server-generated identifier per request; incoming IDs are never trusted. */
public final class RequestIdFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Request-Id", requestId(request));
        chain.doFilter(request, response);
    }

    public static String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String value) return value;
        String generated = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, generated);
        return generated;
    }
}
