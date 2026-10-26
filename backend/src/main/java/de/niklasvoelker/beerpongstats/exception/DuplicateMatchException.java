package de.niklasvoelker.beerpongstats.exception;

public class DuplicateMatchException extends RuntimeException {
    public DuplicateMatchException(String message) {
        super(message);
    }
}
