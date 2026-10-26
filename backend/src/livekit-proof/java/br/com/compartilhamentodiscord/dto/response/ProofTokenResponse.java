package br.com.compartilhamentodiscord.dto.response;

import br.com.compartilhamentodiscord.integration.livekit.MediaRole;
import java.net.URI;
import java.time.Instant;

public record ProofTokenResponse(String roomName, String identity, MediaRole role, URI serverUrl,
                                 String token, Instant expiresAt) {
    @Override
    public String toString() { return "ProofTokenResponse[credential=REDACTED]"; }
}
