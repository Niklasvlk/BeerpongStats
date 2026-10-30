package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
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

    private final AppUserRepository repository = mock(AppUserRepository.class);
    private final UserDetailsService service = new SecurityConfig().userDetailsService(repository);

    @Test
    void userDetailsCarryTheIdAndTheStoredSpelling() {
        AppUser max = AppUser.builder().id(7L).username("Max").password("hash").build();
        when(repository.findByUsernameIgnoreCase("MAX")).thenReturn(Optional.of(max));

        UserDetails details = service.loadUserByUsername("MAX");

        AppUserDetails user = assertInstanceOf(AppUserDetails.class, details);
        assertEquals(7L, user.getId());
        assertEquals("Max", user.getUsername());
        assertEquals("hash", user.getPassword());
    }

    @Test
    void unknownNameIsRejected() {
        when(repository.findByUsernameIgnoreCase("Nobody")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("Nobody"));
    }
}
