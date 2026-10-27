package de.niklasvoelker.beerpongstats.repository;

import de.niklasvoelker.beerpongstats.model.PongGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<PongGroup, Long> {
}
