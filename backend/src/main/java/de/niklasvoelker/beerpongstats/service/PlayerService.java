package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

// Alle Methoden arbeiten innerhalb einer Gruppe; ob der Aufrufer Mitglied ist, prüft schon der GroupAccessInterceptor
@Service
public class PlayerService {

    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    public List<Player> getLeaderboard(Long groupId) {
        Pageable top10 = PageRequest.of(0, 10);
        return repository.findTopPlayersByWinrate(groupId, 10, top10);
    }

    public List<Player> getAllPlayers(Long groupId) {
        return repository.findByGroupId(groupId, Sort.unsorted());
    }

    public Player getPlayerById(Long groupId, Long id) {
        return repository.findByIdAndGroupId(id, groupId)
                .orElseThrow(() -> new PlayerNotFoundByIDException(id));
    }

    public List<Player> findByNameContaining(Long groupId, String name) {
        return repository.findByGroupIdAndNameContainingIgnoreCase(groupId, name);
    }

    public List<Player> getAllPlayersSortedByWinRate(Long groupId) {
        return repository.findByGroupId(groupId, Sort.by(Sort.Direction.DESC, "winRate"));
    }

    public List<Player> getAllPlayersSortedByAvgCups(Long groupId) {
        return repository.findByGroupId(groupId, Sort.by(Sort.Direction.DESC, "avgCups"));
    }

    public List<Player> getAllPlayersSortedByName(Long groupId) {
        return repository.findByGroupId(groupId, Sort.by(Sort.Direction.ASC, "name"));
    }
}
