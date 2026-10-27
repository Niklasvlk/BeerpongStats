package de.niklasvoelker.beerpongstats.exception;

// Auch für Gruppen, in denen der Benutzer kein Mitglied ist: So verrät die Antwort nicht, ob die Gruppe existiert
public class GroupNotFoundException extends RuntimeException {

    public GroupNotFoundException() {
        super("Gruppe wurde nicht gefunden.");
    }
}
