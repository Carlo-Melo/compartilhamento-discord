package br.com.compartilhamentodiscord.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class BotGuildBindingId implements Serializable {
    @Column(name = "bot_id", nullable = false)
    private UUID botId;

    @Column(name = "discord_guild_id", nullable = false, columnDefinition = "text")
    private String discordGuildId;

    protected BotGuildBindingId() { }

    public BotGuildBindingId(UUID botId, String discordGuildId) {
        this.botId = Objects.requireNonNull(botId);
        this.discordGuildId = Objects.requireNonNull(discordGuildId);
    }

    public UUID getBotId() { return botId; }
    public String getDiscordGuildId() { return discordGuildId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BotGuildBindingId that)) return false;
        return Objects.equals(botId, that.botId) && Objects.equals(discordGuildId, that.discordGuildId);
    }

    @Override public int hashCode() { return Objects.hash(botId, discordGuildId); }
}
