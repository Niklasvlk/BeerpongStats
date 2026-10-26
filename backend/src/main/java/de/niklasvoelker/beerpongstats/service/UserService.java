package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.InvalidUserDataException;
import de.niklasvoelker.beerpongstats.exception.UserAlreadyExistsException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class UserService {

    static final int MAX_USERNAME_LENGTH = 30;

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser register(String username, String password) {

        if (username == null || username.trim().isEmpty()) {
            throw new InvalidUserDataException("Der Benutzername darf nicht leer sein.");
        }

        if (username.trim().length() > MAX_USERNAME_LENGTH) {
            throw new InvalidUserDataException(
                    "Der Benutzername darf höchstens " + MAX_USERNAME_LENGTH + " Zeichen lang sein.");
        }

        if (password == null || password.length() < 4) {
            throw new InvalidUserDataException("Das Passwort ist zu kurz.");
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

    // Gibt es den Benutzer nicht mehr, ist nichts zu tun (z. B. zweiter Löschversuch mit alter Session)
    public void deleteUser(Long id) {
        repository.deleteById(id);
    }
}
