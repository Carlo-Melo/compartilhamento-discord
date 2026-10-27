package br.com.compartilhamentodiscord.security;

import br.com.compartilhamentodiscord.service.CaptureProofException;
import br.com.compartilhamentodiscord.exception.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Only a local reverse proxy may reach this backend; role secrets are also required. */
public final class CaptureProofAccessFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(CaptureProofAccessFilter.class);
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
            reject(request, response);
            return;
        }
        try { policy.authenticate(request.getHeader("X-Capture-Key")); }
        catch (CaptureProofException exception) { reject(request, response); return; }
        chain.doFilter(request, response);
        // Low-cardinality diagnostics only: never log a key, JWT, identity, body or URL/query.
        if (!"GET".equals(request.getMethod()) || request.getServletPath().endsWith("/access")
                || response.getStatus() >= 400) {
            LOG.info("capture-proof requestId={} action={} status={}", RequestIdFilter.requestId(request),
                    action(request), response.getStatus());
        }
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiErrors.write(request, response, 403, "CAPTURE_PROOF_REJECTED",
                "Acesso experimental restrito. Confira a origem e a chave do papel.", false);
        LOG.warn("capture-proof requestId={} action={} status=403", RequestIdFilter.requestId(request), action(request));
    }

    private static String action(HttpServletRequest request) {
        if (request.getServletPath().endsWith("/access")) return "validate-key";
        if (request.getServletPath().endsWith("/viewer")) {
            return "DELETE".equals(request.getMethod()) ? "leave-viewer" : "join-viewer";
        }
        return switch (request.getMethod()) {
            case "POST" -> "create-room";
            case "DELETE" -> "delete-room";
            default -> "status";
        };
    }
}
