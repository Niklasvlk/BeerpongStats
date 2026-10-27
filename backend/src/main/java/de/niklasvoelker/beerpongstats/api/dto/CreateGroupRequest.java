package de.niklasvoelker.beerpongstats.api.dto;

// displayName ist optional, ohne ihn heißt der Ersteller in der Gruppe wie sein Benutzername
public record CreateGroupRequest(String name, String displayName) {
}
