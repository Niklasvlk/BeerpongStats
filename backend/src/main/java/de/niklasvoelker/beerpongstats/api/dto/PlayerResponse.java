package de.niklasvoelker.beerpongstats.api.dto;

import de.niklasvoelker.beerpongstats.model.Player;

import java.time.Instant;

public record PlayerResponse(
        Long id,
        String name,
        int wins,
        int losses,
        double winRate,
        double avgCups,
        Instant createdAt
) {
    public static PlayerResponse from(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getName(),
                player.getWins(),
                player.getLosses(),
                player.getWinRate(),
                player.getAvgCups(),
                Timestamps.toInstant(player.getCreatedAt())
        );
    }
}
