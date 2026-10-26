package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    public List<Player> getLeaderboard() {
        Pageable top10 = PageRequest.of(0, 10);
        return repository.findTopPlayersByWinrate(10, top10);
    }

    public List<Player> getAllPlayers() {
        return repository.findAll();
    }

    public Player getPlayerById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new PlayerNotFoundByIDException(id));
    }

    public List<Player> findByNameContaining(String name) {
        return repository.findByNameContainingIgnoreCase(name);
    }

    public List<Player> getAllPlayersSortedByWinRate() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "winRate"));
    }

    public List<Player> getAllPlayersSortedByAvgCups() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "avgCups"));
    }

    public List<Player> getAllPlayersSortedByName() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }
}
