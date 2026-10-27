package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.PongGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<PongGroup, Long> {

    // Sperrt die Zeile bis zum Ende der Transaktion. Gleichzeitige Beitritte in dieselbe Gruppe laufen so nacheinander,
    // und die Prüfung der Mitgliederzahl kann nicht von zwei Beitritten gleichzeitig unterlaufen werden.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM PongGroup g WHERE g.id = :groupId")
    Optional<PongGroup> findForUpdate(@Param("groupId") Long groupId);

    // Als Bulk-Statement wie die Löschungen der Gruppendaten: Ein deleteById würde die Gruppe im Persistenzkontext als
    // gelöscht markieren, während dort noch geladene Spieler auf sie zeigen
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PongGroup g WHERE g.id = :groupId")
    void deleteGroup(@Param("groupId") Long groupId);
}
