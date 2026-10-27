package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.PongGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<PongGroup, Long> {

    // Als Bulk-Statement wie die Löschungen der Gruppendaten: Ein deleteById würde die Gruppe im Persistenzkontext als
    // gelöscht markieren, während dort noch geladene Spieler auf sie zeigen
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PongGroup g WHERE g.id = :groupId")
    void deleteGroup(@Param("groupId") Long groupId);
}
