CREATE TABLE discord_bot (
    id UUID PRIMARY KEY,
    discord_application_id TEXT NOT NULL UNIQUE,
    discord_bot_user_id TEXT NOT NULL UNIQUE
);

CREATE TABLE bot_guild_binding (
    bot_id UUID NOT NULL REFERENCES discord_bot(id),
    discord_guild_id TEXT NOT NULL,
    PRIMARY KEY (bot_id, discord_guild_id)
);
