package de.niklasvoelker.beerpongstats.exception;

// Die Anfrage widerspricht dem Zustand der Gruppe (schon Mitglied, Name vergeben, letzter Admin, ...)
public class GroupConflictException extends RuntimeException {

    public GroupConflictException(String message) {
        super(message);
    }
}
