package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.Player;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Jede Abfrage nach Spielern ist auf eine Gruppe eingeschränkt, nie auf die ganze Tabelle
@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByIdAndGroupId(Long id, Long groupId);

    Optional<Player> findByGroupIdAndNameIgnoreCase(Long groupId, String name);

    // Die Mitgliedschaft eines Accounts in einer Gruppe
    Optional<Player> findByGroupIdAndUserId(Long groupId, Long userId);

    List<Player> findByGroupId(Long groupId, Sort sort);

    List<Player> findByGroupIdAndNameContainingIgnoreCase(Long groupId, String name);

    long countByGroupId(Long groupId);

    @Query("""
    SELECT p
    FROM Player p
    WHERE p.group.id = :groupId AND (p.wins + p.losses) >= :minGames
    ORDER BY p.winRate DESC
""")
    List<Player> findTopPlayersByWinrate(@Param("groupId") Long groupId, @Param("minGames") int minGames,
                                         Pageable pageable);

    // Alle Mitgliedschaften eines Accounts, die Gruppe gleich mitgeladen
    @Query("""
    SELECT p
    FROM Player p JOIN FETCH p.group g
    WHERE p.user.id = :userId
    ORDER BY g.name
""")
    List<Player> findMemberships(@Param("userId") Long userId);
}
