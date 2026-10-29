package br.com.compartilhamentodiscord.service.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

/**
 * Process-local capacity reservation. This does not create a session or contact the SFU.
 * A reservation must remain held until its owner has confirmed any required cleanup.
 */
@Service
public class RuntimeCapacityStore {
    private static final int GUILD_LOCK_COUNT = 64;

    private final ReentrantLock globalLock = new ReentrantLock();
    // Fixed stripes bound the lock memory even if many distinct guild IDs are supplied.
    private final ReentrantLock[] guildLocks = new ReentrantLock[GUILD_LOCK_COUNT];
    private Reservation reservation;

    public RuntimeCapacityStore() {
        for (int i = 0; i < guildLocks.length; i++) {
            guildLocks[i] = new ReentrantLock();
        }
    }

    /** Reserves the single pilot slot. A repeat for the same guild and session is idempotent. */
    public boolean tryReserve(String guildId, UUID sessionId) {
        Reservation requested = reservation(guildId, sessionId);
        ReentrantLock guildLock = lockFor(requested.guildId());
        globalLock.lock();
        try {
            guildLock.lock();
            try {
                if (reservation == null) {
                    reservation = requested;
                    return true;
                }
                return reservation.equals(requested);
            } finally {
                guildLock.unlock();
            }
        } finally {
            globalLock.unlock();
        }
    }

    /** A stale operation cannot release a different session's reservation. */
    public boolean release(String guildId, UUID sessionId) {
        Reservation requested = reservation(guildId, sessionId);
        ReentrantLock guildLock = lockFor(requested.guildId());
        globalLock.lock();
        try {
            guildLock.lock();
            try {
                if (!requested.equals(reservation)) {
                    return false;
                }
                reservation = null;
                return true;
            } finally {
                guildLock.unlock();
            }
        } finally {
            globalLock.unlock();
        }
    }

    private ReentrantLock lockFor(String guildId) {
        return guildLocks[Math.floorMod(guildId.hashCode(), guildLocks.length)];
    }

    private static Reservation reservation(String guildId, UUID sessionId) {
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (guildId.isBlank()) {
            throw new IllegalArgumentException("guildId must not be blank");
        }
        return new Reservation(guildId, sessionId);
    }

    private record Reservation(String guildId, UUID sessionId) {}
}
