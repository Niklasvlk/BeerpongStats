package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.GroupConflictException;
import de.niklasvoelker.beerpongstats.exception.InvalidUserDataException;
import de.niklasvoelker.beerpongstats.exception.UserAlreadyExistsException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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

    @Mock
    private GroupService groupService;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private UserService userService;

    @Test
    void register_shouldSaveUserCorrectly() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(false);

        userService.register("Max", "geheim1234");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());

        AppUser saved = captor.getValue();
        assertEquals("Max", saved.getUsername());
        assertNotEquals("geheim1234", saved.getPassword());
        assertTrue(passwordEncoder.matches("geheim1234", saved.getPassword()));
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void register_shouldTrimTheUsername() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(false);

        userService.register("  Max ", "geheim1234");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        assertEquals("Max", captor.getValue().getUsername());
    }

    @Test
    void register_shouldThrowIfUsernameEmpty() {
        InvalidUserDataException ex = assertThrows(
                InvalidUserDataException.class,
                () -> userService.register("   ", "geheim1234")
        );
        assertEquals("Der Benutzername darf nicht leer sein.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldThrowIfUsernameNull() {
        InvalidUserDataException ex = assertThrows(
                InvalidUserDataException.class,
                () -> userService.register(null, "geheim1234")
        );
        assertEquals("Der Benutzername darf nicht leer sein.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldThrowIfUsernameTooLong() {
        String tooLong = "a".repeat(UserService.MAX_USERNAME_LENGTH + 1);

        assertThrows(InvalidUserDataException.class, () -> userService.register(tooLong, "geheim1234"));
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldAcceptAUsernameOfTheMaximumLength() {
        String longest = "a".repeat(UserService.MAX_USERNAME_LENGTH);

        userService.register(longest, "geheim1234");

        verify(repository).save(any());
    }

    @Test
    void register_shouldThrowIfPasswordTooShort() {
        InvalidUserDataException ex = assertThrows(
                InvalidUserDataException.class,
                () -> userService.register("Max", "1234567")
        );
        assertEquals("Das Passwort muss mindestens 8 Zeichen lang sein.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldAcceptAPasswordOfExactlyTheMinimumLength() {
        userService.register("Max", "a".repeat(UserService.MIN_PASSWORD_LENGTH));

        verify(repository).save(any());
    }

    @Test
    void register_shouldAcceptAPasswordOfExactlyTheMaximumLength() {
        userService.register("Max", "a".repeat(UserService.MAX_PASSWORD_BYTES));

        verify(repository).save(any());
    }

    @Test
    void register_shouldRejectAPasswordBcryptCannotTake() {
        InvalidUserDataException ex = assertThrows(InvalidUserDataException.class,
                () -> userService.register("Max", "a".repeat(UserService.MAX_PASSWORD_BYTES + 1)));

        assertTrue(ex.getMessage().contains("zu lang"));
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldCountBytesNotCharactersForTheMaximum() {
        // 37 Umlaute sind 37 Zeichen, aber 74 Bytes
        String umlauts = "ä".repeat(37);

        assertThrows(InvalidUserDataException.class, () -> userService.register("Max", umlauts));
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldNotCountASpaceOnlyPasswordAsTooShort() {
        // Nur die Länge zählt; Leerzeichen sind erlaubte Zeichen
        userService.register("Max", "        ");

        verify(repository).save(any());
    }

    @Test
    void register_shouldThrowIfPasswordNull() {
        InvalidUserDataException ex = assertThrows(
                InvalidUserDataException.class,
                () -> userService.register("Max", null)
        );
        assertEquals("Das Passwort muss mindestens 8 Zeichen lang sein.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldThrowIfUserExists() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.register("Max", "geheim1234")
        );
        assertTrue(ex.getMessage().contains("Max"));
        verify(repository, never()).save(any());
    }

    @Test
    void register_shouldReportDuplicateWhenTheUniqueIndexRejectsTheInsert() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("ux_app_user_username_lower"));

        assertThrows(UserAlreadyExistsException.class, () -> userService.register("Max", "geheim1234"));
    }

    @Test
    void register_shouldStoreTheCreationTimeInUtc() {
        when(repository.existsByUsernameIgnoreCase("Max")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);

        AppUser saved = userService.register("Max", "geheim1234");

        assertFalse(saved.getCreatedAt().isBefore(before));
        assertFalse(saved.getCreatedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
    }

    @Test
    void deleteUser_shouldReleaseTheMembershipsBeforeDeletingTheAccount() {
        userService.deleteUser(1L);

        InOrder order = inOrder(groupService, repository);
        order.verify(groupService).releaseMemberships(1L);
        order.verify(repository).deleteById(1L);
    }

    @Test
    void deleteUser_shouldKeepTheAccountWhenAMembershipCannotBeReleased() {
        doThrow(new GroupConflictException("letzter Admin")).when(groupService).releaseMemberships(1L);

        assertThrows(GroupConflictException.class, () -> userService.deleteUser(1L));

        verify(repository, never()).deleteById(any());
    }
}
