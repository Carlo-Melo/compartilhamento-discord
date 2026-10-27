package br.com.compartilhamentodiscord.security;

import br.com.compartilhamentodiscord.exception.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** No auxiliary implementation is exposed through the product's fallback chain. */
public final class UnavailableProofFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.equals("/api/dev/livekit") || path.startsWith("/api/dev/livekit/")
                || path.equals("/api/dev/capture") || path.startsWith("/api/dev/capture/")) {
            ApiErrors.write(request, response, 404, "NOT_FOUND", "Recurso não encontrado.", false);
            return;
        }
        chain.doFilter(request, response);
    }
}
