package de.niklasvoelker.beerpongstats.exception;

public class PlayerAlreadyExistsException extends RuntimeException {
    public PlayerAlreadyExistsException(String name) {
        super("Ein Spieler mit dem Namen '" + name + "' existiert bereits.");
    }
}