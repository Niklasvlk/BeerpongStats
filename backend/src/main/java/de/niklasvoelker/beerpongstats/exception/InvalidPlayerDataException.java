package de.niklasvoelker.beerpongstats.exception;

public class InvalidPlayerDataException extends RuntimeException {
    public InvalidPlayerDataException(String message) {
        super(message);
    }
}
