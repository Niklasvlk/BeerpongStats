package de.niklasvoelker.beerpongstats.exception;

// Die Session gehört zu einem Account, den es nicht mehr gibt
public class UnknownUserException extends RuntimeException {

    public UnknownUserException() {
        super("Nicht angemeldet.");
    }
}
