package de.niklasvoelker.beerpongstats.api.dto;

import de.niklasvoelker.beerpongstats.model.Invitation;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

// Metadaten einer Einladung. Den Token enthält nur die Antwort auf das Erstellen (CreatedInvitationResponse).
public record InvitationResponse(
        Long id,
        Instant createdAt,
        Instant expiresAt,
        Integer maxUses,
        int uses,
        String status
) {
    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                Timestamps.toInstant(invitation.getCreatedAt()),
                Timestamps.toInstant(invitation.getExpiresAt()),
                invitation.getMaxUses(),
                invitation.getUses(),
                invitation.statusAt(LocalDateTime.now(ZoneOffset.UTC)).name()
        );
    }
}
