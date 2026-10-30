package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.InvalidUserDataException;
import de.niklasvoelker.beerpongstats.exception.UserAlreadyExistsException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class UserService {

    static final int MAX_USERNAME_LENGTH = 30;
    static final int MIN_PASSWORD_LENGTH = 8;
    // BCrypt verarbeitet höchstens 72 Bytes; längere Passwörter würden abgelehnt oder abgeschnitten
    static final int MAX_PASSWORD_BYTES = 72;

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final GroupService groupService;

    public UserService(AppUserRepository repository, PasswordEncoder passwordEncoder, GroupService groupService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.groupService = groupService;
    }

    public AppUser register(String username, String password) {

        if (username == null || username.trim().isEmpty()) {
            throw new InvalidUserDataException("Der Benutzername darf nicht leer sein.");
        }

        if (username.trim().length() > MAX_USERNAME_LENGTH) {
            throw new InvalidUserDataException(
                    "Der Benutzername darf höchstens " + MAX_USERNAME_LENGTH + " Zeichen lang sein.");
        }

        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidUserDataException(
                    "Das Passwort muss mindestens " + MIN_PASSWORD_LENGTH + " Zeichen lang sein.");
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidUserDataException(
                    "Das Passwort ist zu lang. Es darf höchstens " + MAX_PASSWORD_BYTES + " Bytes lang sein "
                            + "(Umlaute und Sonderzeichen zählen mehrfach).");
        }

        if (repository.existsByUsernameIgnoreCase(username.trim())) {
            throw new UserAlreadyExistsException(username.trim());
        }

        AppUser user = AppUser.builder()
                .username(username.trim())
                .password(passwordEncoder.encode(password))
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        try {
            return repository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Gleichzeitige Registrierung: der Unique-Index auf lower(username) hat die zweite abgelehnt
            throw new UserAlreadyExistsException(username.trim());
        }
    }

    // Seine Spieler bleiben in den Gruppen (ohne Account), Gruppen ohne weitere Mitglieder verschwinden mit ihm.
    // Gibt es den Benutzer nicht mehr, ist nichts zu tun (z. B. zweiter Löschversuch mit alter Session).
    @Transactional
    public void deleteUser(Long id) {
        groupService.releaseMemberships(id);
        repository.deleteById(id);
    }
}
