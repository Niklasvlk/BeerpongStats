package de.niklasvoelker.beerpongstats.exception;

// Unbekannt, abgelaufen, widerrufen oder aufgebraucht: von außen sieht man bewusst keinen Unterschied
public class InvitationInvalidException extends RuntimeException {

    public InvitationInvalidException() {
        super("Diese Einladung ist ungültig oder abgelaufen.");
    }
}
