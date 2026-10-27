package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByIdAndGroupId(Long id, Long groupId);

    List<Invitation> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    /**
     * Zählt eine Nutzung hoch, aber nur wenn die Einladung noch gilt. Die Prüfung und das Hochzählen passieren in einem
     * einzigen UPDATE, damit zwei gleichzeitige Beitritte ein Nutzungslimit nicht überschreiten.
     *
     * @return 1, wenn die Nutzung vergeben wurde, 0, wenn die Einladung nicht (mehr) gilt
     */
    @Modifying(flushAutomatically = true)
    @Query("""
    UPDATE Invitation i
    SET i.uses = i.uses + 1
    WHERE i.id = :id
      AND i.revokedAt IS NULL
      AND i.expiresAt > :now
      AND (i.maxUses IS NULL OR i.uses < i.maxUses)
""")
    int consumeUse(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Invitation i WHERE i.group.id = :groupId")
    void deleteAllOfGroup(@Param("groupId") Long groupId);
}
