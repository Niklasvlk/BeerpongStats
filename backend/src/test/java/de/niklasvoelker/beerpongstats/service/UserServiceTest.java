package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.InvalidUserDataException;
import de.niklasvoelker.beerpongstats.exception.UserAlreadyExistsException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository repository;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private UserService userService;

    @Test
    void register_shouldSaveUserCorrectly() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(false);

        userService.register("Max", "1234");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());

        AppUser saved = captor.getValue();
        assertEquals("Max", saved.getUsername());
        assertNotEquals("1234", saved.getPassword());
        assertTrue(passwordEncoder.matches("1234", saved.getPassword()));
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void register_shouldTrimTheUsername() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(false);

        userService.register("  Max ", "1234");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        assertEquals("Max", captor.getValue().getUsername());
    }

    @Test
    void register_shouldThrowIfUsernameEmpty() {
        InvalidUserDataException ex = assertThrows(
                InvalidUserDataException.class,
                () -> userService.register("   ", "1234")
        );
        assertEquals("Der Benutzername darf nicht leer sein.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldThrowIfUsernameNull() {
        InvalidUserDataException ex = assertThrows(
                InvalidUserDataException.class,
                () -> userService.register(null, "1234")
        );
        assertEquals("Der Benutzername darf nicht leer sein.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldThrowIfUsernameTooLong() {
        String tooLong = "a".repeat(UserService.MAX_USERNAME_LENGTH + 1);

        assertThrows(InvalidUserDataException.class, () -> userService.register(tooLong, "1234"));
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldAcceptAUsernameOfTheMaximumLength() {
        String longest = "a".repeat(UserService.MAX_USERNAME_LENGTH);

        userService.register(longest, "1234");

        verify(repository).save(any());
    }

    @Test
    void register_shouldThrowIfPasswordTooShort() {
        InvalidUserDataException ex = assertThrows(
                InvalidUserDataException.class,
                () -> userService.register("Max", "123")
        );
        assertEquals("Das Passwort ist zu kurz.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldThrowIfPasswordNull() {
        InvalidUserDataException ex = assertThrows(
                InvalidUserDataException.class,
                () -> userService.register("Max", null)
        );
        assertEquals("Das Passwort ist zu kurz.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldThrowIfUserExists() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.register("Max", "1234")
        );
        assertTrue(ex.getMessage().contains("Max"));
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldReportDuplicateWhenTheUniqueIndexRejectsTheInsert() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("ux_app_user_username_lower"));

        assertThrows(UserAlreadyExistsException.class, () -> userService.register("Max", "1234"));
    }

    @Test
    void register_shouldStoreTheCreationTimeInUtc() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);

        AppUser saved = userService.register("Max", "1234");

        assertFalse(saved.getCreatedAt().isBefore(before));
        assertFalse(saved.getCreatedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
    }

    @Test
    void deleteUser_shouldDeleteById() {
        userService.deleteUser(1L);

        verify(repository).deleteById(1L);
    }
}
