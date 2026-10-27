package br.com.compartilhamentodiscord.security;

import br.com.compartilhamentodiscord.exception.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** Exact configured origin; forwarded and browser identity headers never grant access. */
public final class BrowserOriginFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final String origin;

    public BrowserOriginFilter(String origin) {
        URI uri = URI.create(origin);
        boolean localHttp = "http".equals(uri.getScheme())
                && Set.of("127.0.0.1", "localhost", "[::1]").contains(String.valueOf(uri.getHost()));
        if ((!localHttp && !"https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new IllegalStateException("Configure uma origem HTTPS exata ou HTTP de loopback para desenvolvimento.");
        }
        this.origin = origin;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var origins = Collections.list(request.getHeaders("Origin"));
        boolean mutation = !SAFE.contains(request.getMethod());
        if ((mutation && origins.size() != 1) || origins.size() > 1
                || (!origins.isEmpty() && !origin.equals(origins.getFirst()))
                || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
            ApiErrors.write(request, response, 403, "ORIGIN_INVALID",
                    "Origem da requisição não permitida. Abra o site pelo endereço autorizado.", false);
            return;
        }
        chain.doFilter(request, response);
    }
}
