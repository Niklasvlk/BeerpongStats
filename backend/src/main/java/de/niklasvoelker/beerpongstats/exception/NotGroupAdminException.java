package de.niklasvoelker.beerpongstats.exception;

public class NotGroupAdminException extends RuntimeException {

    public NotGroupAdminException() {
        super("Das dürfen nur Admins der Gruppe.");
    }
}
