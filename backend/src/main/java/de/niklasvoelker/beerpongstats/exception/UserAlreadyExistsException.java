package de.niklasvoelker.beerpongstats.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String username) {
        super("Ein Benutzer mit dem Namen '" + username + "' existiert bereits.");
    }
}
