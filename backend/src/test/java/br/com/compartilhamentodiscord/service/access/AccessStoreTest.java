package br.com.compartilhamentodiscord.service.access;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AccessStoreTest {
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID BOT = UUID.randomUUID();
    private final MutableClock clock = new MutableClock();
    private final AccessStore store = new AccessStore(clock, new SecureRandom(), 100, 100);

    private static AccessStore.Grant grant(String user) {
        return new AccessStore.Grant(SESSION, BOT, "guild", user, "Nome", AccessStore.Role.VIEWER);
    }
    private AccessStore.Context create(AccessStore.Grant grant, String oldId) {
        return store.activate(store.consume(store.issue(grant)).orElseThrow(), oldId).orElseThrow();
    }

    @Test void randomCodeHas256BitsAndOnlyDigestIsKept() throws Exception {
        String ticket = store.issue(grant("one"));
        assertEquals(43, ticket.length());
        assertEquals(32, Base64.getUrlDecoder().decode(ticket).length);
        Field field = AccessStore.class.getDeclaredField("tickets");
        field.setAccessible(true);
        Map<?, ?> stored = (Map<?, ?>) field.get(store);
        assertEquals(1, stored.size());
        assertFalse(stored.containsKey(ticket));
        assertEquals(64, stored.keySet().iterator().next().toString().length());
        assertFalse(stored.toString().contains(ticket));
        assertFalse(store.consume(ticket).orElseThrow().toString().contains(ticket));
    }

    @Test void validBeforeDeadlineAndExpiresExactlyAtSixtySeconds() {
        String early = store.issue(grant("early"));
        String late = store.issue(grant("late"));
        clock.advance(Duration.ofMillis(59_999));
        assertTrue(store.consume(early).isPresent());
        clock.advance(Duration.ofMillis(1));
        assertTrue(store.consume(late).isEmpty());
        assertTrue(store.consume(early).isEmpty());
        assertTrue(store.consume("bad").isEmpty());
    }

    @Test void concurrentConsumersHaveAtMostOneSuccess() throws Exception {
        String ticket = store.issue(grant("one"));
        int count = 48;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var results = java.util.stream.IntStream.range(0, count).mapToObj(i -> executor.submit(() -> {
                ready.countDown(); go.await(); return store.consume(ticket).isPresent();
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(1, results.stream().filter(f -> {
                try { return f.get(10, TimeUnit.SECONDS); } catch (Exception e) { throw new AssertionError(e); }
            }).count());
        }
    }

    @Test void issuanceRatesAndCapacityAreBoundedAndExpire() {
        AccessStore small = new AccessStore(clock, new SecureRandom(), 5, 2);
        for (int i = 0; i < 5; i++) small.issue(grant("one"));
        assertThrows(AccessStore.LimitException.class, () -> small.issue(grant("one")));
        assertThrows(AccessStore.LimitException.class, () -> small.issue(grant("other")));
        clock.advance(Duration.ofSeconds(60));
        assertDoesNotThrow(() -> small.issue(grant("one")));

        AccessStore rate = new AccessStore(clock, new SecureRandom(), 100, 100);
        for (int i = 0; i < 5; i++) rate.issue(grant("rate"));
        assertThrows(AccessStore.LimitException.class, () -> rate.issue(grant("rate")));
        for (int i = 0; i < 25; i++) rate.issue(grant("user-" + i));
        assertThrows(AccessStore.LimitException.class, () -> rate.issue(grant("new-user")));
        clock.advance(Duration.ofSeconds(60));
        rate.cleanUp();
        assertDoesNotThrow(() -> rate.issue(grant("rate")));
    }

    @Test void replacementRevocationAndLimitsDoNotResurrectAccess() {
        AccessStore small = new AccessStore(clock, new SecureRandom(), 100, 1);
        AccessStore.Grant firstGrant = grant("one");
        var first = small.activate(small.consume(small.issue(firstGrant)).orElseThrow(), null).orElseThrow();
        var denied = small.consume(small.issue(grant("two"))).orElseThrow();
        assertThrows(AccessStore.LimitException.class, () -> small.activate(denied, null));
        assertTrue(small.activate(denied, null).isEmpty());
        var replacement = small.activate(small.consume(small.issue(firstGrant)).orElseThrow(), null).orElseThrow();
        assertTrue(small.resolve(first.id()).isEmpty());
        assertTrue(small.resolve(replacement.id()).isPresent());
        var other = small.activate(small.consume(small.issue(grant("two"))).orElseThrow(), replacement.id()).orElseThrow();
        assertTrue(small.resolve(replacement.id()).isEmpty());
        small.revoke(other.id());
        assertTrue(small.resolve(other.id()).isEmpty());
    }

    @Test void idleAndAbsoluteDeadlinesAndExplicitInvalidationAreEnforced() {
        var idle = create(grant("idle"), null);
        clock.advance(Duration.ofMinutes(30));
        assertTrue(store.resolve(idle.id()).isEmpty());
        var active = create(grant("active"), null);
        for (int i = 0; i < 16; i++) {
            clock.advance(Duration.ofMinutes(29));
            assertTrue(store.resolve(active.id()).isPresent());
        }
        clock.advance(Duration.ofMinutes(16));
        assertTrue(store.resolve(active.id()).isEmpty()); // exactly 8 hours
        var cancelled = store.consume(store.issue(grant("pending"))).orElseThrow();
        var remaining = create(grant("remaining"), null);
        store.invalidateSession(SESSION);
        assertTrue(store.activate(cancelled, null).isEmpty());
        assertTrue(store.resolve(remaining.id()).isEmpty());
    }

    @Test void freshStoreHasNoTicketsContextsOrPendingCapabilities() {
        String ticket = store.issue(grant("pending"));
        var consumed = store.consume(ticket).orElseThrow();
        var existing = create(grant("existing"), null);
        String unused = store.issue(grant("unused"));
        AccessStore restarted = new AccessStore(clock, new SecureRandom(), 100, 100);
        assertTrue(restarted.consume(unused).isEmpty());
        assertTrue(restarted.resolve(existing.id()).isEmpty());
        assertTrue(restarted.activate(consumed, null).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
