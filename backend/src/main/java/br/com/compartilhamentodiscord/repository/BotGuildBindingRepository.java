package br.com.compartilhamentodiscord.repository;

import br.com.compartilhamentodiscord.entity.BotGuildBinding;
import br.com.compartilhamentodiscord.entity.BotGuildBindingId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotGuildBindingRepository extends JpaRepository<BotGuildBinding, BotGuildBindingId> { }
