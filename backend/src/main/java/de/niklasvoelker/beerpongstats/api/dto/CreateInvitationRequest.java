package de.niklasvoelker.beerpongstats.api.dto;

// Beide Werte sind optional: ohne Angabe gilt die Einladung 7 Tage und ohne Nutzungslimit
public record CreateInvitationRequest(Integer expiresInDays, Integer maxUses) {
}
