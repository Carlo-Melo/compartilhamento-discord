package br.com.compartilhamentodiscord.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "bot_guild_binding")
public class BotGuildBinding {
    @EmbeddedId
    private BotGuildBindingId id;

    @MapsId("botId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bot_id", nullable = false)
    private DiscordBot bot;

    protected BotGuildBinding() { }

    public BotGuildBinding(DiscordBot bot, String discordGuildId) {
        this.bot = Objects.requireNonNull(bot);
        this.id = new BotGuildBindingId(bot.getId(), discordGuildId);
    }

    public BotGuildBindingId getId() { return id; }
    public DiscordBot getBot() { return bot; }
}
