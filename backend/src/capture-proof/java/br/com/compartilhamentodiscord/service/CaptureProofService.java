package br.com.compartilhamentodiscord.service;

import br.com.compartilhamentodiscord.dto.response.CaptureAccessResponse;
import br.com.compartilhamentodiscord.dto.response.CaptureStatusResponse;
import br.com.compartilhamentodiscord.integration.livekit.CaptureProofMedia;
import br.com.compartilhamentodiscord.integration.livekit.MediaRole;
import br.com.compartilhamentodiscord.security.CaptureProofAccessPolicy;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** One ephemeral room and two stable role identities. No persistence or product authentication. */
public final class CaptureProofService {
    private final CaptureProofMedia media;
    private final CaptureProofAccessPolicy access;
    private final URI browserUrl;
    private final Object stateLock = new Object();
    private OwnedRoom active;
    private int allocated;
    private boolean changing;

    public CaptureProofService(CaptureProofMedia media, CaptureProofAccessPolicy access, URI browserUrl) {
        this.media = media; this.access = access; this.browserUrl = browserUrl;
    }

    public CaptureAccessResponse create(String key) {
        access.require(key, MediaRole.PRESENTER);
        OwnedRoom room;
        synchronized (stateLock) {
            if (changing || active != null) throw conflict("Encerre a prova atual antes de iniciar outra.");
            if (allocated >= 32) throw new CaptureProofException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de 32 provas por execução atingido. Limpe e reinicie o ambiente experimental.");
            allocated++;
            room = new OwnedRoom("t03-" + UUID.randomUUID());
            active = room;
            changing = true;
        }
        try {
            media.create(room.name);
            synchronized (stateLock) { room.ready = true; }
            return issue(room, MediaRole.PRESENTER);
        } finally {
            // Keep even uncertain CreateRoom outcomes available for explicit deletion.
            synchronized (stateLock) { changing = false; }
        }
    }

    public CaptureAccessResponse viewer(String key) {
        access.require(key, MediaRole.VIEWER);
        OwnedRoom room = currentReady();
        if (media.find(room.name).isEmpty()) throw conflict("A prova foi encerrada. Aguarde uma nova captura.");
        CaptureAccessResponse response = issue(room, MediaRole.VIEWER);
        synchronized (stateLock) {
            if (active != room || changing || !room.ready) throw conflict("A prova está sendo encerrada.");
        }
        return response;
    }

    public CaptureStatusResponse status(String key) {
        access.authenticate(key);
        OwnedRoom room;
        synchronized (stateLock) { room = active; }
        if (room == null) return new CaptureStatusResponse(false, false, null, null, null);
        boolean exists = media.find(room.name).isPresent();
        boolean video = false;
        boolean audio = false;
        if (exists) {
            for (var participant : media.participants(room.name)) {
                if (!room.presenter.equals(participant.identity())) continue;
                for (var track : participant.tracks()) {
                    if (track.muted()) continue;
                    if ("VIDEO".equals(track.kind()) && "SCREEN_SHARE".equals(track.source())) video = true;
                    if ("AUDIO".equals(track.kind()) && "SCREEN_SHARE_AUDIO".equals(track.source())) audio = true;
                }
            }
        }
        synchronized (stateLock) {
            if (active != room) return new CaptureStatusResponse(false, false, null, null, null);
            return new CaptureStatusResponse(exists, exists && room.ready && !changing && video && audio,
                    room.presenter, room.viewer, room.name);
        }
    }

    public void delete(String key) { access.require(key, MediaRole.PRESENTER); cleanup(); }

    @PreDestroy
    public void shutdown() {
        try { cleanup(); }
        catch (RuntimeException ignored) { /* Operator must tear down the isolated SFU after uncertain cleanup. */ }
    }

    private void cleanup() {
        OwnedRoom room;
        synchronized (stateLock) {
            if (changing) throw conflict("Operação em andamento. Tente encerrar novamente.");
            room = active;
            if (room == null) return;
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
            if (active == null || !active.ready || changing) throw conflict("Nenhuma captura pronta. Aguarde o apresentador.");
            return active;
        }
    }

    private CaptureAccessResponse issue(OwnedRoom room, MediaRole role) {
        String identity = role == MediaRole.PRESENTER ? room.presenter : room.viewer;
        var token = media.token(room.name, identity, role);
        return new CaptureAccessResponse(room.name, identity, room.presenter, role, browserUrl, token.token(),
                Instant.now().plusSeconds(token.expiresInSeconds()));
    }

    private static CaptureProofException conflict(String message) { return new CaptureProofException(HttpStatus.CONFLICT, message); }

    private static final class OwnedRoom {
        final String name;
        final String presenter = "t03-presenter-" + UUID.randomUUID();
        final String viewer = "t03-viewer-" + UUID.randomUUID();
        boolean ready;
        OwnedRoom(String name) { this.name = name; }
    }
}
