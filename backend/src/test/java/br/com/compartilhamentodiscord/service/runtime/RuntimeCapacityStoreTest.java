package br.com.compartilhamentodiscord.service.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RuntimeCapacityStoreTest {
    @Test
    void parallelGuildsAndSessionsCompeteForOneGlobalSlot() throws Exception {
        RuntimeCapacityStore store = new RuntimeCapacityStore();
        int attempts = 40;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                String guild = "guild-" + i % 5;
                UUID session = UUID.randomUUID();
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start timed out");
                    }
                    return store.tryReserve(guild, session);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int winners = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) winners++;
            }
            assertEquals(1, winners);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void staleOrWrongGuildCannotReleaseAnotherReservation() {
        RuntimeCapacityStore store = new RuntimeCapacityStore();
        UUID oldSession = UUID.randomUUID();
        UUID currentSession = UUID.randomUUID();
        assertTrue(store.tryReserve("guild-a", oldSession));
        assertTrue(store.tryReserve("guild-a", oldSession));
        assertFalse(store.tryReserve("guild-a", currentSession));
        assertFalse(store.release("guild-b", oldSession));
        assertTrue(store.release("guild-a", oldSession));
        assertTrue(store.tryReserve("guild-a", currentSession));
        assertFalse(store.release("guild-a", oldSession));
        assertFalse(store.tryReserve("guild-b", oldSession));
        assertTrue(store.release("guild-a", currentSession));
        assertTrue(store.tryReserve("guild-b", oldSession));
    }

    @Test
    void freshProcessStateHasNoReservationAndRejectsInvalidKeys() {
        RuntimeCapacityStore previous = new RuntimeCapacityStore();
        UUID id = UUID.randomUUID();
        assertTrue(previous.tryReserve("guild-a", id));
        RuntimeCapacityStore next = new RuntimeCapacityStore();
        assertFalse(next.release("guild-a", id));
        assertTrue(next.tryReserve("guild-b", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> next.tryReserve(" ", id));
        assertThrows(NullPointerException.class, () -> next.tryReserve(null, id));
        assertThrows(NullPointerException.class, () -> next.release("guild-a", null));
    }
}
