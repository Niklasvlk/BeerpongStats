package de.niklasvoelker.beerpongstats.exception;

public class PlayerNotFoundByNameException extends RuntimeException {

    public PlayerNotFoundByNameException(String name) {
        super("Player mit Name " + name + " wurde nicht gefunden.");
    }
}
