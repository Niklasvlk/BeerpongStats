package de.niklasvoelker.beerpongstats.exception;

public class PlayerNotFoundByIDException extends RuntimeException {

    public PlayerNotFoundByIDException(Long id) {
        super("Player mit ID " + id + " wurde nicht gefunden.");
    }
}
