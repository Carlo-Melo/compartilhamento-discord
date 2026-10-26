package br.com.compartilhamentodiscord.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Local test isolation, not the product's future ticket authentication. */
public final class LiveKitProofAccessFilter extends OncePerRequestFilter {
    private final String allowedOrigin;

    public LiveKitProofAccessFilter(String allowedOrigin) { this.allowedOrigin = allowedOrigin; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        String remote = request.getRemoteAddr();
        String host = request.getHeader("Host");
        String origin = request.getHeader("Origin");
        boolean mutation = !"GET".equals(request.getMethod());
        boolean local = "127.0.0.1".equals(remote) || "::1".equals(remote)
                || "0:0:0:0:0:0:0:1".equals(remote);
        boolean expectedHost = "127.0.0.1:4200".equals(host) || "127.0.0.1:8080".equals(host);
        boolean expectedOrigin = origin == null ? !mutation : allowedOrigin.equals(origin);
        if (!local || !expectedHost || !expectedOrigin
                || !"local-development".equals(request.getHeader("X-LiveKit-Proof"))) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":\"LOCAL_PROOF_ONLY\",\"message\":\"Ferramenta restrita ao desenvolvimento local.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
