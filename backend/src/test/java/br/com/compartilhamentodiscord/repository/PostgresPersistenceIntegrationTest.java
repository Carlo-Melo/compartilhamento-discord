package br.com.compartilhamentodiscord.repository;

import static org.junit.jupiter.api.Assertions.*;

import br.com.compartilhamentodiscord.CompartilhamentoDiscordApplication;
import br.com.compartilhamentodiscord.entity.BotGuildBinding;
import br.com.compartilhamentodiscord.entity.BotGuildBindingId;
import br.com.compartilhamentodiscord.entity.DiscordBot;
import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Opt-in: Testcontainers or an explicitly supplied disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "T06_POSTGRES_TEST_ENABLED", matches = "true")
class PostgresPersistenceIntegrationTest {
    private String jdbcUrl;
    private String username;
    private String password;

    @Test
    void migrationConstraintsJpaAndRestart() throws Exception {
        String explicitUrl = System.getenv("T06_POSTGRES_JDBC_URL");
        if (explicitUrl != null && !explicitUrl.isBlank()) {
            URI address = URI.create(explicitUrl.substring("jdbc:".length()));
            if (!"postgresql".equals(address.getScheme()) ||
                    !Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(address.getHost())) {
                throw new IllegalArgumentException("The disposable PostgreSQL must be on loopback");
            }
            jdbcUrl = explicitUrl;
            username = requireEnvironment("T06_POSTGRES_USERNAME");
            password = requireEnvironment("T06_POSTGRES_PASSWORD");
            verifyPersistence();
        } else {
            try (var postgres = new PostgreSQLContainer("postgres:18.6-bookworm")) {
                postgres.start();
                jdbcUrl = postgres.getJdbcUrl();
                username = postgres.getUsername();
                password = postgres.getPassword();
                verifyPersistence();
            }
        }
    }

    private void verifyPersistence() throws Exception {
        // Refuse existing schemas before Flyway or test inserts can modify them.
        try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1), "Test requires an empty disposable public schema");
        }
        UUID botId = UUID.randomUUID();
        try (ConfigurableApplicationContext context = start()) {
            var bots = context.getBean(DiscordBotRepository.class);
            var bindings = context.getBean(BotGuildBindingRepository.class);
            bots.saveAndFlush(new DiscordBot(botId, "123456789012345678", "234567890123456789"));
            bindings.saveAndFlush(new BotGuildBinding(bots.findById(botId).orElseThrow(), "345678901234567890"));
            bindings.saveAndFlush(new BotGuildBinding(bots.findById(botId).orElseThrow(), "456789012345678901"));
            assertEquals(botId, bots.findByDiscordApplicationId("123456789012345678").orElseThrow().getId());
            assertTrue(bindings.existsById(new BotGuildBindingId(botId, "345678901234567890")));
        }

        try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
             var statement = connection.createStatement()) {
            // SQL constraints are checked directly, without conflating repository merge semantics with INSERT.
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO discord_bot VALUES ('" + UUID.randomUUID() + "', '123456789012345678', 'other')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO discord_bot VALUES ('" + UUID.randomUUID() + "', 'other', '234567890123456789')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO discord_bot VALUES ('" + UUID.randomUUID() + "', NULL, 'other')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO bot_guild_binding VALUES ('" + botId + "', '345678901234567890')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO bot_guild_binding VALUES ('" + UUID.randomUUID() + "', 'new')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO bot_guild_binding VALUES ('" + botId + "', NULL)"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM discord_bot WHERE id = '" + botId + "'"));
            Set<String> tables = new HashSet<>();
            try (var rows = statement.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")) {
                while (rows.next()) tables.add(rows.getString(1));
            }
            assertEquals(Set.of("discord_bot", "bot_guild_binding", "flyway_schema_history"), tables);
        }

        // A fresh application context reruns Flyway validation without reapplying V1.
        try (ConfigurableApplicationContext restarted = start()) {
            assertTrue(restarted.getBean(DiscordBotRepository.class).existsById(botId));
            assertTrue(restarted.getBean(BotGuildBindingRepository.class)
                    .existsById(new BotGuildBindingId(botId, "345678901234567890")));
        }
        try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(CompartilhamentoDiscordApplication.class)
                .registerShutdownHook(false).run(
                        "--server.port=0", "--management.server.port=0", "--spring.main.banner-mode=off",
                        "--logging.level.root=OFF", "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=" + username,
                        "--spring.datasource.password=" + password);
    }

    private String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for the disposable database");
        return value;
    }
}
