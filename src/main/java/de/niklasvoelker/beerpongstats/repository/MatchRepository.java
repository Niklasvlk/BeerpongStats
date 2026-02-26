package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findTop10ByOrderByPlayedAtDesc();
}
