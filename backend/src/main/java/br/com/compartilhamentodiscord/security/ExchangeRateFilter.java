package br.com.compartilhamentodiscord.security;

import br.com.compartilhamentodiscord.exception.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, process-local rolling limits; never trust forwarded client addresses. */
public final class ExchangeRateFilter extends OncePerRequestFilter {
    private static final long WINDOW = TimeUnit.MINUTES.toNanos(1);
    private static final int MAX_KEYS = 10_000;
    private final Map<String, ArrayDeque<Long>> ips = new HashMap<>();
    private final Map<String, ArrayDeque<Long>> sessions = new HashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/api/access/exchange".equals(request.getServletPath())) {
            var session = request.getSession(false);
            if (session == null || !allow(request.getRemoteAddr(), session.getId(), System.nanoTime())) {
                response.setHeader("Retry-After", "60");
                ApiErrors.write(request, response, 429, "RATE_LIMITED", "Muitas tentativas. Aguarde e obtenha novo acesso pelo bot.", true);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String ip, String session, long now) {
        clean(ips, now);
        clean(sessions, now);
        if ((!ips.containsKey(ip) && ips.size() >= MAX_KEYS)
                || (!sessions.containsKey(session) && sessions.size() >= MAX_KEYS)) return false;
        var byIp = ips.computeIfAbsent(ip, ignored -> new ArrayDeque<>());
        var bySession = sessions.computeIfAbsent(session, ignored -> new ArrayDeque<>());
        if (byIp.size() >= 10 || bySession.size() >= 10) return false;
        byIp.addLast(now);
        bySession.addLast(now);
        return true;
    }

    private static void clean(Map<String, ArrayDeque<Long>> entries, long now) {
        entries.values().removeIf(times -> {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW) times.removeFirst();
            return times.isEmpty();
        });
    }
}
