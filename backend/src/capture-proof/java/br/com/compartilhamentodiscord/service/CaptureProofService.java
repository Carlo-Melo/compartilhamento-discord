package br.com.compartilhamentodiscord.service;

import br.com.compartilhamentodiscord.dto.response.CaptureAccessResponse;
import br.com.compartilhamentodiscord.dto.response.CaptureStatusResponse;
import br.com.compartilhamentodiscord.dto.response.CaptureValidationResponse;
import br.com.compartilhamentodiscord.integration.livekit.CaptureProofMedia;
import br.com.compartilhamentodiscord.integration.livekit.MediaRole;
import br.com.compartilhamentodiscord.security.CaptureProofAccessPolicy;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;

/** Bounded experimental generation; correlation nonces are not Discord identities or product access. */
public final class CaptureProofService {
    public static final int MAX_VIEWERS = 9;
    public static final Duration GENERATION_LIFETIME = Duration.ofHours(1);
    private final CaptureProofMedia media;
    private final CaptureProofAccessPolicy access;
    private final URI browserUrl;
    private final Clock clock;
    private final Object stateLock = new Object();
    private OwnedRoom active;
    private int allocated;
    private boolean changing;

    public CaptureProofService(CaptureProofMedia media, CaptureProofAccessPolicy access, URI browserUrl) {
        this(media, access, browserUrl, Clock.systemUTC());
    }

    CaptureProofService(CaptureProofMedia media, CaptureProofAccessPolicy access, URI browserUrl, Clock clock) {
        this.media = media; this.access = access; this.browserUrl = browserUrl; this.clock = clock;
    }

    public CaptureValidationResponse validateAccess(String key) {
        return new CaptureValidationResponse(access.authenticate(key));
    }

    public CaptureAccessResponse create(String key) {
        access.require(key, MediaRole.PRESENTER);
        OwnedRoom room;
        synchronized (stateLock) {
            if (changing || active != null) throw conflict("Encerre a prova atual antes de iniciar outra.");
            if (allocated >= 32) throw new CaptureProofException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de 32 provas por execução atingido. Limpe e reinicie o ambiente experimental.");
            allocated++;
            room = new OwnedRoom("t03-" + UUID.randomUUID(), clock.instant().plus(GENERATION_LIFETIME));
            active = room;
            changing = true;
        }
        try {
            media.create(room.name);
            synchronized (stateLock) { room.ready = true; }
            return issue(room, room.presenter, MediaRole.PRESENTER);
        } finally {
            // Keep even uncertain CreateRoom outcomes available for explicit deletion.
            synchronized (stateLock) { changing = false; }
        }
    }

    public CaptureAccessResponse viewer(String key, String correlation) {
        access.require(key, MediaRole.VIEWER);
        UUID nonce = viewerNonce(correlation);
        OwnedRoom room = currentReady();
        if (media.find(room.name).isEmpty()) throw conflict("A prova foi encerrada. Aguarde uma nova captura.");
        Viewer slot;
        synchronized (stateLock) {
            requireCurrent(room);
            slot = room.viewers.get(nonce);
            if (slot == null) {
                if (room.viewers.size() >= MAX_VIEWERS) throw new CaptureProofException(HttpStatus.CONFLICT,
                        "CAPTURE_PROOF_CAPACITY", "As nove identidades desta prova já foram reservadas. Reutilize a mesma página ou aguarde uma nova prova.");
                slot = new Viewer();
                room.viewers.put(nonce, slot);
            }
            if (slot.leaving) throw conflict("Saída em andamento. Aguarde e tente entrar novamente.");
        }
        // A failed/ambiguous response retains the slot: retry with the same nonce never consumes another one.
        CaptureAccessResponse response = issue(room, slot.identity, MediaRole.VIEWER);
        synchronized (stateLock) { requireCurrent(room); }
        return response;
    }

    public void leave(String key, String correlation) {
        access.require(key, MediaRole.VIEWER);
        UUID nonce = viewerNonce(correlation);
        OwnedRoom room;
        Viewer slot;
        synchronized (stateLock) {
            room = active;
            if (room == null) return;
            if (changing) throw conflict("Operação em andamento. Tente sair novamente.");
            slot = room.viewers.get(nonce);
            if (slot == null) return;
            if (slot.leaving) throw conflict("Saída em andamento. Aguarde e tente novamente.");
            slot.leaving = true;
        }
        try { media.removeViewer(room.name, slot.identity); }
        finally { synchronized (stateLock) { slot.leaving = false; } }
        // Never reassign this identity to another nonce. Removal does not revoke initial/refreshed SFU JWTs.
    }

    public CaptureStatusResponse status(String key) {
        access.authenticate(key);
        OwnedRoom room;
        HashSet<String> viewers;
        synchronized (stateLock) {
            room = active;
            if (room == null) return emptyStatus();
            viewers = new HashSet<>();
            room.viewers.values().forEach(viewer -> viewers.add(viewer.identity));
        }
        boolean exists = media.find(room.name).isPresent();
        boolean video = false;
        boolean audio = false;
        HashSet<String> connected = new HashSet<>();
        if (exists) {
            for (var participant : media.participants(room.name)) {
                if (viewers.contains(participant.identity())) connected.add(participant.identity());
                if (!room.presenter.equals(participant.identity())) continue;
                for (var track : participant.tracks()) {
                    if (track.muted()) continue;
                    if ("VIDEO".equals(track.kind()) && "SCREEN_SHARE".equals(track.source())) video = true;
                    if ("AUDIO".equals(track.kind()) && "SCREEN_SHARE_AUDIO".equals(track.source())) audio = true;
                }
            }
        }
        synchronized (stateLock) {
            if (active != room) return emptyStatus();
            return new CaptureStatusResponse(exists,
                    exists && room.ready && !changing && !expired(room) && video && audio,
                    room.presenter, room.name, connected.size(), room.viewers.size(), MAX_VIEWERS);
        }
    }

    public void delete(String key) { access.require(key, MediaRole.PRESENTER); cleanup(false); }

    /** Automatic bounded lifecycle. An uncertain deletion retains the owned name for the next retry. */
    @Scheduled(fixedDelay = 30_000)
    public void expire() {
        try { cleanup(true); }
        catch (RuntimeException ignored) { /* State remains closed; operator can retry deletion without leaking errors. */ }
    }

    @PreDestroy
    public void shutdown() {
        try { cleanup(false); }
        catch (RuntimeException ignored) { /* Operator must tear down the isolated SFU after uncertain cleanup. */ }
    }

    private void cleanup(boolean onlyExpired) {
        OwnedRoom room;
        synchronized (stateLock) {
            room = active;
            if (room == null || (onlyExpired && !expired(room))) return;
            if (changing) throw conflict("Operação em andamento. Tente encerrar novamente.");
            changing = true;
            room.ready = false;
        }
        try {
            media.delete(room.name);
            if (media.find(room.name).isPresent()) throw new CaptureProofException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Exclusão não confirmada. Mantenha o backend ativo e tente encerrar novamente.");
            synchronized (stateLock) { if (active == room) active = null; }
        } finally { synchronized (stateLock) { changing = false; } }
    }

    private OwnedRoom currentReady() {
        synchronized (stateLock) {
            if (active == null) throw conflict("Nenhuma captura pronta. Aguarde o apresentador.");
            requireCurrent(active);
            return active;
        }
    }

    private void requireCurrent(OwnedRoom room) {
        if (active != room || !room.ready || changing || expired(room)) {
            throw conflict("A prova está indisponível ou expirou. Aguarde o apresentador encerrar e iniciar outra.");
        }
    }

    private boolean expired(OwnedRoom room) { return !clock.instant().isBefore(room.expiresAt); }

    private CaptureAccessResponse issue(OwnedRoom room, String identity, MediaRole role) {
        var token = media.token(room.name, identity, role);
        return new CaptureAccessResponse(room.name, identity, room.presenter, role, browserUrl, token.token(),
                clock.instant().plusSeconds(token.expiresInSeconds()));
    }

    private static UUID viewerNonce(String value) {
        if (value == null || !value.matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")) {
            throw new CaptureProofException(HttpStatus.BAD_REQUEST, "CAPTURE_PROOF_VIEWER_INVALID",
                    "Identificador experimental da página inválido. Reabra a ferramenta e tente novamente.");
        }
        return UUID.fromString(value);
    }

    private static CaptureStatusResponse emptyStatus() {
        return new CaptureStatusResponse(false, false, null, null, 0, 0, MAX_VIEWERS);
    }

    private static CaptureProofException conflict(String message) { return new CaptureProofException(HttpStatus.CONFLICT, message); }

    private static final class Viewer {
        final String identity = "t04-viewer-" + UUID.randomUUID();
        boolean leaving;
    }

    private static final class OwnedRoom {
        final String name;
        final String presenter = "t03-presenter-" + UUID.randomUUID();
        final Instant expiresAt;
        final Map<UUID, Viewer> viewers = new HashMap<>();
        boolean ready;
        OwnedRoom(String name, Instant expiresAt) { this.name = name; this.expiresAt = expiresAt; }
    }
}
