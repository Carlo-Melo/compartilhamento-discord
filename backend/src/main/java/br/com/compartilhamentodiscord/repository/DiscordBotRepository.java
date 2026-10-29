package br.com.compartilhamentodiscord.repository;

import br.com.compartilhamentodiscord.entity.DiscordBot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscordBotRepository extends JpaRepository<DiscordBot, UUID> {
    Optional<DiscordBot> findByDiscordApplicationId(String discordApplicationId);
}
