package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Matches werden immer über ihre Gruppe abgefragt. Die Duplikat-Prüfung braucht keine Gruppe, weil Spieler genau
// einer Gruppe gehören.
@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findTop10ByGroupIdOrderByPlayedAtDesc(Long groupId);

    List<Match> findAllByGroupIdOrderByPlayedAtDesc(Long groupId);

    Optional<Match> findByIdAndGroupId(Long id, Long groupId);

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

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Match m WHERE m.group.id = :groupId")
    void deleteAllOfGroup(@Param("groupId") Long groupId);
}
