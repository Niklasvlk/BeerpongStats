package de.niklasvoelker.beerpongstats.api.dto;

import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.model.Player;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

public record MatchResponse(
        Long id,
        PlayerRef winner1,
        PlayerRef winner2,
        PlayerRef loser1,
        PlayerRef loser2,
        Integer winner1Cups,
        Integer winner2Cups,
        Integer loser1Cups,
        Integer loser2Cups,
        Integer mvpCups,
        Instant playedAt
) {
    public record PlayerRef(Long id, String name) {
        static PlayerRef from(Player player) {
            return player == null ? null : new PlayerRef(player.getId(), player.getName());
        }
    }

    public static MatchResponse from(Match match) {
        Integer mvpCups = Stream.of(match.getWinner1Cups(), match.getWinner2Cups(),
                        match.getLoser1Cups(), match.getLoser2Cups())
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(null);

        return new MatchResponse(
                match.getId(),
                PlayerRef.from(match.getWinner1()),
                PlayerRef.from(match.getWinner2()),
                PlayerRef.from(match.getLoser1()),
                PlayerRef.from(match.getLoser2()),
                match.getWinner1Cups(),
                match.getWinner2Cups(),
                match.getLoser1Cups(),
                match.getLoser2Cups(),
                mvpCups,
                Timestamps.toInstant(match.getPlayedAt())
        );
    }
}
