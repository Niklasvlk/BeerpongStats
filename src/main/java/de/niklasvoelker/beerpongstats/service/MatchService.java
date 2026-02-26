package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByNameException;
import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.MatchRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import de.niklasvoelker.beerpongstats.dto.MatchDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;

    public MatchService(MatchRepository matchRepository, PlayerRepository playerRepository) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
    }

    @Transactional
    public void createMatch(MatchDTO dto) {

        validateMatchDTO(dto);

        Player winner1 = getPlayerByName(dto.getWinner1());
        Player winner2 = getOptionalPlayer(dto.getWinner2());

        Player loser1 = getPlayerByName(dto.getLoser1());
        Player loser2 = getOptionalPlayer(dto.getLoser2());

        validateNoDuplicatePlayers(winner1, winner2, loser1, loser2);
        validateTeamConsistency(winner2, loser2);

        Match match = Match.builder()
                .winner1(winner1)
                .winner2(winner2)
                .loser1(loser1)
                .loser2(loser2)
                .winner1Cups(dto.getWinner1Cups())
                .winner2Cups(dto.getWinner2Cups())
                .loser1Cups(dto.getLoser1Cups())
                .loser2Cups(dto.getLoser2Cups())
                .playedAt(LocalDateTime.now())
                .build();

        matchRepository.save(match);

        updateWinnerStats(winner1, dto.getWinner1Cups());
        updateWinnerStats(winner2, dto.getWinner2Cups());

        updateLoserStats(loser1, dto.getLoser1Cups());
        updateLoserStats(loser2, dto.getLoser2Cups());
    }

    public void deleteMatch(Long matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new RuntimeException("Match nicht gefunden: " + matchId);
        }
        matchRepository.deleteById(matchId);
    }

    public List<Match> getRecentMatches() {
        return matchRepository.findTop10ByOrderByPlayedAtDesc();
    }

    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }

    public Optional<Match> getMatchById(Long id) {
        return matchRepository.findById(id);
    }

    // Hilfsmethode
    private void updateAvgCups(Player player, Integer cups) {
        if (cups == null) return;
        double games = player.getWins() + player.getLosses() - 1; // Minus aktuelles Match
        player.setAvgCups((player.getAvgCups() * games + cups) / (games + 1));
    }

    private void updateWinRate(Player player) {
        int total = player.getWins() + player.getLosses();
        if (total == 0) {
            player.setWinRate(0);
        } else {
            player.setWinRate((double) player.getWins() / total);
        }
    }

    private void validateMatchDTO(MatchDTO dto) {

        if (dto.getWinner1() == null || dto.getWinner1().isBlank()) {
            throw new IllegalArgumentException("Winner 1 darf nicht leer sein.");
        }

        if (dto.getLoser1() == null || dto.getLoser1().isBlank()) {
            throw new IllegalArgumentException("Loser 1 darf nicht leer sein.");
        }

        boolean is2v2 =
                dto.getWinner2() != null && !dto.getWinner2().isBlank() ||
                        dto.getLoser2() != null && !dto.getLoser2().isBlank();

        if (is2v2) {
            if (dto.getWinner2() == null || dto.getWinner2().isBlank() ||
                    dto.getLoser2() == null || dto.getLoser2().isBlank()) {

                throw new IllegalArgumentException(
                        "Für ein 2v2 Match müssen beide Teams aus zwei Spielern bestehen."
                );
            }
        }

        validateCups(dto.getWinner1Cups(), "Winner 1 Cups");
        validateCups(dto.getLoser1Cups(), "Loser 1 Cups");

        if (is2v2) {
            validateCups(dto.getWinner2Cups(), "Winner 2 Cups");
            validateCups(dto.getLoser2Cups(), "Loser 2 Cups");
        }
    }

    private Player getPlayerByName(String name) {
        return playerRepository.findByName(name)
                .orElseThrow(() -> new PlayerNotFoundByNameException(name));
    }

    private Player getOptionalPlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return getPlayerByName(name);
    }

    private void validateNoDuplicatePlayers(Player... players) {

        Set<Player> unique = new HashSet<>();

        for (Player p : players) {
            if (p != null && !unique.add(p)) {
                throw new IllegalArgumentException("Ein Spieler darf nicht doppelt im Match vorkommen.");
            }
        }
    }

    private void validateTeamConsistency(Player winner2, Player loser2) {

        if ((winner2 == null && loser2 != null) ||
                (winner2 != null && loser2 == null)) {

            throw new IllegalArgumentException("2v2 Matches müssen auf beiden Seiten zwei Spieler haben.");
        }
    }

    private void updateWinnerStats(Player player, Integer cups) {
        if (player == null) return;

        player.setWins(player.getWins() + 1);
        updateAvgCups(player, cups);
        updateWinRate(player);
    }

    private void updateLoserStats(Player player, Integer cups) {
        if (player == null) return;

        player.setLosses(player.getLosses() + 1);
        updateAvgCups(player, cups);
        updateWinRate(player);
    }

    private void validateCups(Integer cups, String fieldName) {

        if (cups == null) {
            throw new IllegalArgumentException(fieldName + " müssen gesetzt sein.");
        }

        if (cups < 0) {
            throw new IllegalArgumentException(fieldName + " dürfen nicht negativ sein.");
        }
    }
}