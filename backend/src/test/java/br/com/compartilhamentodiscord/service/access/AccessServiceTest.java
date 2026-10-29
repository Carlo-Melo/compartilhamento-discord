package br.com.compartilhamentodiscord.service.access;

import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AccessServiceTest {
    private static final UUID SESSION = UUID.randomUUID();
    private static final AccessStore.Grant GRANT = new AccessStore.Grant(SESSION, UUID.randomUUID(),
            "guild", "user", "Nome", AccessStore.Role.VIEWER);

    @Test void deniedRevalidationConsumesTicketWithoutCreatingContext() {
        var clock = new MutableClock();
        var store = new AccessStore(clock, new SecureRandom(), 100, 100);
        var service = new AccessService(store, grant -> AccessVerifier.Result.DENIED);
        var request = bootstrap();
        String ticket = store.issue(GRANT);
        assertEquals(AccessFailure.Kind.DENIED, failure(() -> service.exchange(ticket, request)));
        assertEquals(AccessFailure.Kind.INVALID, failure(() -> service.exchange(ticket, request)));
        assertEquals(AccessFailure.Kind.REQUIRED, failure(() -> service.current(request)));
    }

    @Test void expiringOrInvalidatingWhileVerifierRunsCannotCreateContext() {
        for (boolean expired : new boolean[]{true, false}) {
            var clock = new MutableClock();
            var store = new AccessStore(clock, new SecureRandom(), 100, 100);
            var service = new AccessService(store, grant -> {
                if (expired) clock.advance(Duration.ofSeconds(60));
                else store.invalidateSession(SESSION);
                return AccessVerifier.Result.ALLOWED;
            });
            var request = bootstrap();
            String ticket = store.issue(GRANT);
            assertEquals(AccessFailure.Kind.INVALID, failure(() -> service.exchange(ticket, request)));
            assertEquals(AccessFailure.Kind.INVALID, failure(() -> service.exchange(ticket, request)));
            assertEquals(AccessFailure.Kind.REQUIRED, failure(() -> service.current(request)));
        }
    }

    private static MockHttpServletRequest bootstrap() {
        var request = new MockHttpServletRequest();
        request.getSession(true);
        return request;
    }

    private static AccessFailure.Kind failure(org.junit.jupiter.api.function.Executable action) {
        return assertThrows(AccessFailure.class, action).kind();
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
