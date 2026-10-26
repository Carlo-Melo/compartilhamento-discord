package br.com.compartilhamentodiscord.security;

import br.com.compartilhamentodiscord.service.CaptureProofException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.web.filter.OncePerRequestFilter;

/** Only a local reverse proxy may reach this backend; role secrets are also required. */
public final class CaptureProofAccessFilter extends OncePerRequestFilter {
    private final String origin;
    private final String host;
    private final CaptureProofAccessPolicy policy;

    public CaptureProofAccessFilter(String origin, CaptureProofAccessPolicy policy) {
        this.origin = origin;
        this.host = URI.create(origin).getRawAuthority();
        this.policy = policy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        String remote = request.getRemoteAddr();
        boolean loopback = "127.0.0.1".equals(remote) || "::1".equals(remote)
                || "0:0:0:0:0:0:0:1".equals(remote);
        String requestOrigin = request.getHeader("Origin");
        boolean allowedOrigin = origin.equals(requestOrigin)
                || (requestOrigin == null && "GET".equals(request.getMethod()));
        if (!loopback || !host.equals(request.getHeader("Host")) || !allowedOrigin
                || !"restricted-development".equals(request.getHeader("X-Capture-Proof"))) {
            reject(response);
            return;
        }
        try { policy.authenticate(request.getHeader("X-Capture-Key")); }
        catch (CaptureProofException exception) { reject(response); return; }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"CAPTURE_PROOF_REJECTED\",\"message\":\"Acesso experimental restrito. Confira a origem e a chave do papel.\"}");
    }
}
