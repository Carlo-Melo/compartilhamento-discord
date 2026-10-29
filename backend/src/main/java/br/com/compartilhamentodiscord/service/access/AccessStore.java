package br.com.compartilhamentodiscord.service.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

/** Process-local access state. The caller is responsible for validating the trusted interaction
 * before issuance and revalidating the grant after consumption, before activation. */
@Service
public final class AccessStore {
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);
    private static final Duration IDLE_TTL = Duration.ofMinutes(30);
    private static final Duration ABSOLUTE_TTL = Duration.ofHours(8);
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_LIMIT = 10_000;
    private final Clock clock;
    private final SecureRandom random;
    private final int ticketLimit;
    private final int contextLimit;
    private final UUID bootId = UUID.randomUUID();
    private final Map<String, Ticket> tickets = new HashMap<>();
    private final Map<String, ContextState> contexts = new HashMap<>();
    private final Map<UserSession, ArrayDeque<Instant>> userRates = new HashMap<>();
    private final Map<String, ArrayDeque<Instant>> guildRates = new HashMap<>();

    @Autowired
    public AccessStore() { this(Clock.systemUTC(), new SecureRandom(), DEFAULT_LIMIT, DEFAULT_LIMIT); }

    public AccessStore(Clock clock, SecureRandom random, int ticketLimit, int contextLimit) {
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        if (ticketLimit < 1 || contextLimit < 1) throw new IllegalArgumentException("Invalid access limits");
        this.ticketLimit = ticketLimit;
        this.contextLimit = contextLimit;
    }

    public enum Role { OWNER, VIEWER }

    public record Grant(UUID sessionId, UUID botId, String guildId, String discordUserId,
                        String displayName, Role role) {
        public Grant {
            Objects.requireNonNull(sessionId); Objects.requireNonNull(botId); Objects.requireNonNull(role);
            if (guildId == null || guildId.isBlank() || discordUserId == null || discordUserId.isBlank()
                    || displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Invalid grant");
        }
    }

    /** A capability created only by successful consumption; never serialize or expose it. */
    public static final class ConsumedTicket {
        private final Grant grant;
        private final UUID bootId;
        private final String ticketDigest;
        private final Ticket ticket;
        private boolean activated;
        private ConsumedTicket(Grant grant, UUID bootId, String ticketDigest, Ticket ticket) {
            this.grant = grant; this.bootId = bootId; this.ticketDigest = ticketDigest; this.ticket = ticket;
        }
        public Grant grant() { return grant; }
        @Override public String toString() { return "ConsumedTicket[redacted]"; }
    }

    public record Context(String id, Grant grant, Instant expiresAt, UUID bootId) {
        @Override public String toString() { return "Context[redacted]"; }
    }

    public static final class LimitException extends RuntimeException {
        public LimitException() { super("Access capacity or rate limit reached"); }
    }

    /** Call only after authenticating and authorizing a Discord interaction. */
    public synchronized String issue(Grant grant) {
        Objects.requireNonNull(grant);
        Instant now = clock.instant();
        cleanUpAt(now);
        UserSession user = new UserSession(grant.sessionId(), grant.discordUserId());
        if (tickets.size() >= ticketLimit || rateFull(userRates.get(user), 5)
                || rateFull(guildRates.get(grant.guildId()), 30)
                || (!userRates.containsKey(user) && userRates.size() >= ticketLimit)
                || (!guildRates.containsKey(grant.guildId()) && guildRates.size() >= ticketLimit)) {
            throw new LimitException();
        }
        String code;
        String digest;
        do { code = randomCode(); digest = digest(code); } while (tickets.containsKey(digest));
        tickets.put(digest, new Ticket(grant, now.plus(TICKET_TTL)));
        userRates.computeIfAbsent(user, ignored -> new ArrayDeque<>()).addLast(now);
        guildRates.computeIfAbsent(grant.guildId(), ignored -> new ArrayDeque<>()).addLast(now);
        return code;
    }

    /** Mark consumed before any external work. Failure of revalidation never restores the ticket. */
    public synchronized Optional<ConsumedTicket> consume(String code) {
        Instant now = clock.instant();
        cleanUpAt(now);
        if (code == null || !code.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        String key = digest(code);
        Ticket ticket = tickets.get(key);
        if (ticket == null || ticket.consumed) return Optional.empty();
        ticket.consumed = true;
        return Optional.of(new ConsumedTicket(ticket.grant, bootId, key, ticket));
    }

    /** Activate only after successful revalidation. Old cookie and same-user access are replaced atomically. */
    public synchronized Optional<Context> activate(ConsumedTicket consumed, String previousId) {
        Instant now = clock.instant();
        cleanUpAt(now);
        if (consumed == null || consumed.activated || !bootId.equals(consumed.bootId)
                || tickets.get(consumed.ticketDigest) != consumed.ticket) return Optional.empty();
        // Claim once even when capacity is unavailable: a consumed ticket cannot be retried.
        consumed.activated = true;
        Grant grant = consumed.grant;
        boolean replacing = contexts.entrySet().stream().anyMatch(entry -> entry.getKey().equals(previousId)
                || sameUserSession(entry.getValue().grant, grant));
        if (contexts.size() >= contextLimit && !replacing) throw new LimitException();
        contexts.entrySet().removeIf(entry -> entry.getKey().equals(previousId)
                || sameUserSession(entry.getValue().grant, grant));
        String id;
        do { id = randomCode(); } while (contexts.containsKey(id));
        ContextState state = new ContextState(grant, now.plus(ABSOLUTE_TTL), now.plus(IDLE_TTL));
        contexts.put(id, state);
        return Optional.of(snapshot(id, state));
    }

    public synchronized Optional<Context> resolve(String id) {
        Instant now = clock.instant();
        cleanUpAt(now);
        ContextState state = contexts.get(id);
        if (state == null) return Optional.empty();
        state.idleUntil = now.plus(IDLE_TTL);
        return Optional.of(snapshot(id, state));
    }

    public synchronized void revoke(String id) { if (id != null) contexts.remove(id); }

    public synchronized void invalidateSession(UUID sessionId) {
        Objects.requireNonNull(sessionId);
        tickets.values().removeIf(ticket -> ticket.grant.sessionId().equals(sessionId));
        contexts.values().removeIf(context -> context.grant.sessionId().equals(sessionId));
    }

    @Scheduled(fixedDelay = 5000)
    public synchronized void cleanUp() { cleanUpAt(clock.instant()); }

    private void cleanUpAt(Instant now) {
        tickets.values().removeIf(ticket -> !now.isBefore(ticket.expiresAt));
        contexts.values().removeIf(context -> !now.isBefore(context.absoluteUntil) || !now.isBefore(context.idleUntil));
        cleanRates(userRates, now); cleanRates(guildRates, now);
    }

    private static <K> void cleanRates(Map<K, ArrayDeque<Instant>> rates, Instant now) {
        rates.values().removeIf(times -> {
            while (!times.isEmpty() && !now.isBefore(times.peekFirst().plus(RATE_WINDOW))) times.removeFirst();
            return times.isEmpty();
        });
    }
    private static boolean rateFull(ArrayDeque<Instant> times, int limit) { return times != null && times.size() >= limit; }
    private static boolean sameUserSession(Grant left, Grant right) {
        return left.sessionId().equals(right.sessionId()) && left.discordUserId().equals(right.discordUserId());
    }
    private Context snapshot(String id, ContextState state) {
        Instant expires = state.idleUntil.isBefore(state.absoluteUntil) ? state.idleUntil : state.absoluteUntil;
        return new Context(id, state.grant, expires, bootId);
    }
    private String randomCode() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String digest(String code) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private record UserSession(UUID sessionId, String userId) { }
    private static final class Ticket {
        private final Grant grant; private final Instant expiresAt; private boolean consumed;
        private Ticket(Grant grant, Instant expiresAt) { this.grant = grant; this.expiresAt = expiresAt; }
    }
    private static final class ContextState {
        private final Grant grant; private final Instant absoluteUntil; private Instant idleUntil;
        private ContextState(Grant grant, Instant absoluteUntil, Instant idleUntil) {
            this.grant = grant; this.absoluteUntil = absoluteUntil; this.idleUntil = idleUntil;
        }
    }
}
