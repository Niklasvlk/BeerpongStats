package de.niklasvoelker.beerpongstats.api.dto;

import de.niklasvoelker.beerpongstats.service.InvitationService.CreatedInvitation;

import java.time.Instant;

// Der Token wird nur diesmal ausgeliefert, gespeichert ist davon nur der Hash
public record CreatedInvitationResponse(
        Long id,
        String token,
        Instant expiresAt,
        Integer maxUses
) {
    public static CreatedInvitationResponse from(CreatedInvitation created) {
        return new CreatedInvitationResponse(
                created.invitation().getId(),
                created.token(),
                Timestamps.toInstant(created.invitation().getExpiresAt()),
                created.invitation().getMaxUses()
        );
    }
}
