package br.com.compartilhamentodiscord.dto.response;

import br.com.compartilhamentodiscord.service.access.AccessStore;
import java.time.Instant;
import java.util.UUID;

public record AccessContextResponse(UUID sessionId, UUID botId, String guildId, String discordUserId,
                                    String displayName, AccessStore.Role role, Instant expiresAt, UUID bootId) {
    public static AccessContextResponse from(AccessStore.Context context) {
        var grant = context.grant();
        return new AccessContextResponse(grant.sessionId(), grant.botId(), grant.guildId(), grant.discordUserId(),
                grant.displayName(), grant.role(), context.expiresAt(), context.bootId());
    }
}
