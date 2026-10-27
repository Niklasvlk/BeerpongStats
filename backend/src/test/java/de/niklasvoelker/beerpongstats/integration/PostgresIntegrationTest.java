package de.niklasvoelker.beerpongstats.integration;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Basis für Tests gegen ein echtes Postgres, mit dem vollständigen Spring-Kontext (Flyway, Hibernate-Validierung,
 * Security, Interceptor). Ohne Docker werden diese Tests übersprungen.
 *
 * <p>Der Container startet einmal pro JVM und wird von allen Testklassen geteilt. Spring cached den Kontext über
 * Testklassen hinweg; ein Container pro Klasse würde dazu führen, dass eine spätere Klasse den Kontext mit der
 * Adresse eines schon gestoppten Containers wiederverwendet. Beendet wird er mit der JVM (Ryuk).
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf(value = "de.niklasvoelker.beerpongstats.integration.PostgresIntegrationTest#dockerAvailable",
        disabledReason = "Docker ist nicht verfügbar")
abstract class PostgresIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15");

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static synchronized PostgreSQLContainer postgres() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        return POSTGRES;
    }

    // Läuft nur, wenn der Kontext gebaut wird, also nur wenn Docker da ist
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        PostgreSQLContainer postgres = postgres();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
