package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordMigrationRunnerTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Mock
    private PlayerRepository repository;

    @Test
    void run_shouldHashPlaintextPasswords() {
        Player player = Player.builder().name("Max").password("1234").build();
        when(repository.findAll()).thenReturn(List.of(player));

        new PasswordMigrationRunner(repository, encoder).run();

        assertNotEquals("1234", player.getPassword());
        assertTrue(encoder.matches("1234", player.getPassword()));
        verify(repository).save(player);
    }

    @Test
    void run_shouldLeaveExistingHashesUntouched() {
        String hash = encoder.encode("1234");
        Player player = Player.builder().name("Max").password(hash).build();
        when(repository.findAll()).thenReturn(List.of(player));

        new PasswordMigrationRunner(repository, encoder).run();

        assertEquals(hash, player.getPassword());
        verify(repository, never()).save(any());
    }
}
