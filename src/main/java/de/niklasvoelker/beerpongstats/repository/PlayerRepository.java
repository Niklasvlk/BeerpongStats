package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByName(String name);

    @Query("SELECT p FROM Player p WHERE (p.wins + p.losses) >= :minGames ORDER BY p.wins DESC")
    List<Player> findLeaderboard(@Param("minGames") int minGames);

    List<Player> findByNameContainingIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}