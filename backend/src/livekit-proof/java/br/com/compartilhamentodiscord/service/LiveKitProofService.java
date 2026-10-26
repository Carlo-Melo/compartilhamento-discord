package br.com.compartilhamentodiscord.service;

import br.com.compartilhamentodiscord.dto.response.ProofRoomResponse;
import br.com.compartilhamentodiscord.dto.response.ProofTokenResponse;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitGateway;
import br.com.compartilhamentodiscord.integration.livekit.LiveKitProperties;
import br.com.compartilhamentodiscord.integration.livekit.MediaRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;

/** Bounded, volatile scaffolding for T-02; no product sessions, tickets or persistence. */
public final class LiveKitProofService {
    private final LiveKitGateway gateway;
    private final LiveKitProperties properties;
    private final String namespace = "t02-" + UUID.randomUUID() + "-";
    private final ConcurrentHashMap<String, OwnedRoom> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger roomCount = new AtomicInteger();
    private final AtomicInteger activeCount = new AtomicInteger();

    public LiveKitProofService(LiveKitGateway gateway, LiveKitProperties properties) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public ProofRoomResponse create() {
        if (roomCount.incrementAndGet() > 32) {
            roomCount.decrementAndGet();
            throw limit();
        }
        if (activeCount.incrementAndGet() > 4) {
            activeCount.decrementAndGet();
            roomCount.decrementAndGet();
            throw limit();
        }
        String name = namespace + UUID.randomUUID();
        try {
            gateway.createRoom(name);
            rooms.put(name, new OwnedRoom());
            return inspect(name);
        } catch (RuntimeException exception) {
            // Keep the name available for explicit cleanup even after an uncertain network outcome.
            rooms.putIfAbsent(name, new OwnedRoom());
            throw exception;
        }
    }

    public ProofRoomResponse inspect(String name) {
        owned(name);
        var room = gateway.findRoom(name);
        if (room.isEmpty()) {
            return new ProofRoomResponse(name, false, LiveKitGateway.MAX_PARTICIPANTS, List.of());
        }
        var participants = gateway.listParticipants(name).stream().map(participant ->
                new ProofRoomResponse.Participant(participant.identity(), participant.sid(),
                        participant.tracks().stream().map(track -> new ProofRoomResponse.Track(track.sid(),
                                track.kind(), track.source(), track.muted())).toList())).toList();
        return new ProofRoomResponse(name, true, room.get().maxParticipants(), participants);
    }

    public ProofTokenResponse token(String name, MediaRole role) {
        OwnedRoom owned = owned(name);
        if (role == null) { throw new LiveKitProofException(HttpStatus.BAD_REQUEST, "Papel obrigatório."); }
        if (owned.deleted.get() || gateway.findRoom(name).isEmpty()) {
            throw new LiveKitProofException(HttpStatus.CONFLICT, "A sala de prova já foi encerrada.");
        }
        if (owned.tokens.incrementAndGet() > 8) {
            owned.tokens.decrementAndGet();
            throw limit();
        }
        String identity = "t02-" + role.name().toLowerCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID();
        var token = gateway.issueToken(name, identity, role);
        return new ProofTokenResponse(name, identity, role, properties.getBrowserUrl(), token.token(),
                Instant.now().plusSeconds(token.expiresInSeconds()));
    }

    public void delete(String name) {
        OwnedRoom owned = owned(name);
        gateway.deleteRoom(name);
        if (gateway.findRoom(name).isPresent()) {
            throw new LiveKitProofException(HttpStatus.SERVICE_UNAVAILABLE, "Exclusão ainda não confirmada na SFU.");
        }
        if (owned.deleted.compareAndSet(false, true)) { activeCount.decrementAndGet(); }
    }

    public void cleanup() {
        // Only names allocated by this process; never ListRooms/DeleteRoom across a namespace.
        boolean failed = false;
        for (String name : rooms.keySet()) {
            try { delete(name); }
            catch (RuntimeException exception) { failed = true; }
        }
        if (failed) {
            throw new LiveKitProofException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Limpeza não confirmada para todas as salas próprias. Mantenha o backend ativo e tente novamente.");
        }
    }

    private OwnedRoom owned(String name) {
        OwnedRoom owned = rooms.get(name);
        if (owned == null) {
            throw new LiveKitProofException(HttpStatus.NOT_FOUND, "Sala não pertence a esta execução da prova.");
        }
        return owned;
    }

    private static LiveKitProofException limit() {
        return new LiveKitProofException(HttpStatus.TOO_MANY_REQUESTS,
                "Limite da ferramenta local atingido. Limpe a prova antes de reiniciar o backend.");
    }

    private static final class OwnedRoom {
        private final AtomicBoolean deleted = new AtomicBoolean();
        private final AtomicInteger tokens = new AtomicInteger();
    }
}
