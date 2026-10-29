package br.com.compartilhamentodiscord.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "discord_bot")
public class DiscordBot {
    @Id
    private UUID id;

    @Column(name = "discord_application_id", nullable = false, unique = true, columnDefinition = "text")
    private String discordApplicationId;

    @Column(name = "discord_bot_user_id", nullable = false, unique = true, columnDefinition = "text")
    private String discordBotUserId;

    protected DiscordBot() { }

    public DiscordBot(UUID id, String discordApplicationId, String discordBotUserId) {
        this.id = Objects.requireNonNull(id);
        this.discordApplicationId = Objects.requireNonNull(discordApplicationId);
        this.discordBotUserId = Objects.requireNonNull(discordBotUserId);
    }

    public UUID getId() { return id; }
    public String getDiscordApplicationId() { return discordApplicationId; }
    public String getDiscordBotUserId() { return discordBotUserId; }
}
