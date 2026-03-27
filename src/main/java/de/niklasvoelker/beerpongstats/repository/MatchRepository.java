package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findTop10ByOrderByPlayedAtDesc();

    List<Match> findAllByOrderByPlayedAtDesc();

    boolean existsByWinner1AndWinner2AndLoser1AndLoser2AndWinner1CupsAndWinner2CupsAndLoser1CupsAndLoser2CupsAndPlayedAtAfter(
            Player winner1,
            Player winner2,
            Player loser1,
            Player loser2,
            Integer winner1Cups,
            Integer winner2Cups,
            Integer loser1Cups,
            Integer loser2Cups,
            LocalDateTime time
    );
}
