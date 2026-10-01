package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordMigrationRunner.class);

    private final PlayerRepository repository;
    private final PasswordEncoder passwordEncoder;

    public PasswordMigrationRunner(PlayerRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String @NonNull ... args) {
        int migrated = 0;
        for (Player player : repository.findAll()) {
            String stored = player.getPassword();
            if (stored != null && !isBcryptHash(stored)) {
                player.setPassword(passwordEncoder.encode(stored));
                repository.save(player);
                migrated++;
            }
        }
        log.info("Passwort-Migration: {} Passwörter gehasht.", migrated);
    }

    private boolean isBcryptHash(String value) {
        return value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }
}
