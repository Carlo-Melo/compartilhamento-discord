package br.com.compartilhamentodiscord.dto.response;

import br.com.compartilhamentodiscord.integration.livekit.MediaRole;
import java.net.URI;
import java.time.Instant;

public record CaptureAccessResponse(String roomName, String identity, String presenterIdentity, MediaRole role, URI serverUrl,
                                    String token, Instant expiresAt) {
    @Override public String toString() { return "CaptureAccessResponse[credentials=REDACTED]"; }
}
