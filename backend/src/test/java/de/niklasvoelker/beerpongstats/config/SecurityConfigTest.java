package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    private final PlayerRepository repository = mock(PlayerRepository.class);
    private final UserDetailsService service = new SecurityConfig().userDetailsService(repository);

    @Test
    void userDetailsCarryTheIdAndTheStoredSpelling() {
        Player max = Player.builder().id(7L).name("Max").password("hash").build();
        when(repository.findByNameIgnoreCase("MAX")).thenReturn(Optional.of(max));

        UserDetails details = service.loadUserByUsername("MAX");

        PlayerUserDetails player = assertInstanceOf(PlayerUserDetails.class, details);
        assertEquals(7L, player.getId());
        assertEquals("Max", player.getUsername());
        assertEquals("hash", player.getPassword());
    }

    @Test
    void unknownNameIsRejected() {
        when(repository.findByNameIgnoreCase("Nobody")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("Nobody"));
    }
}
