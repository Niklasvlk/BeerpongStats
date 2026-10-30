package de.niklasvoelker.beerpongstats.api.dto;

// displayName ist optional, ohne ihn heißt man in der Gruppe wie der eigene Benutzername
public record AcceptInvitationRequest(String displayName) {
}
