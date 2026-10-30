package de.niklasvoelker.beerpongstats.api.dto;

import de.niklasvoelker.beerpongstats.model.Player;

import java.time.Instant;

// active = der Spieler gehört zu einem Account; sonst ist er ein ehemaliges Mitglied
public record MemberResponse(
        Long id,
        String name,
        String role,
        boolean active,
        int wins,
        int losses,
        double winRate,
        double avgCups,
        Instant createdAt
) {
    public static MemberResponse from(Player player) {
        return new MemberResponse(
                player.getId(),
                player.getName(),
                player.getRole().name(),
                player.getUser() != null,
                player.getWins(),
                player.getLosses(),
                player.getWinRate(),
                player.getAvgCups(),
                Timestamps.toInstant(player.getCreatedAt())
        );
    }
}
