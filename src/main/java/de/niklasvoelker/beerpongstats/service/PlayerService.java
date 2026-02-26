package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.InvalidPlayerDataException;
import de.niklasvoelker.beerpongstats.exception.PlayerAlreadyExistsException;
import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    public void createPlayer(String name, String password) {

        if (name == null || name.trim().isEmpty()) {
            throw new InvalidPlayerDataException("Der Spielername darf nicht leer sein.");
        }

        if (password == null || password.length() < 4) {
            throw new InvalidPlayerDataException("Das Passwort ist zu kurz.");
        }

        if (repository.existsByNameIgnoreCase(name.trim())) {
            throw new PlayerAlreadyExistsException(name);
        }

        Player player = Player.builder()
                .name(name.trim())
                .password(password)
                .wins(0)
                .losses(0)
                .avgCups(0)
                .winRate(0)
                .createdAt(LocalDateTime.now())
                .build();

        repository.save(player);
    }

    public List<Player> getTopPlayers() {
        return repository.findTop10ByOrderByWinRateDesc();
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
