package de.niklasvoelker.beerpongstats.api.dto;

import de.niklasvoelker.beerpongstats.service.GroupService.GroupMembership;

// Eine Gruppe samt der Rolle und den Statistiken des angemeldeten Mitglieds darin
public record GroupResponse(
        Long id,
        String name,
        String role,
        Long playerId,
        int wins,
        int losses,
        double winRate,
        double avgCups,
        long memberCount
) {
    public static GroupResponse from(GroupMembership membership) {
        return new GroupResponse(
                membership.group().getId(),
                membership.group().getName(),
                membership.player().getRole().name(),
                membership.player().getId(),
                membership.player().getWins(),
                membership.player().getLosses(),
                membership.player().getWinRate(),
                membership.player().getAvgCups(),
                membership.memberCount()
        );
    }
}
