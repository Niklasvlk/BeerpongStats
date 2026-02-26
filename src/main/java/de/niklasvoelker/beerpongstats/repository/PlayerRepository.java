package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByName(String name);

    List<Player> findTop10ByOrderByWinRateDesc();

    List<Player> findByNameContainingIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}