package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.CreatePlayerRequest;
import de.niklasvoelker.beerpongstats.api.dto.PlayerResponse;
import de.niklasvoelker.beerpongstats.config.PlayerUserDetails;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.service.PlayerService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PlayerApiController {

    private final PlayerService playerService;

    public PlayerApiController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping("/leaderboard")
    public List<PlayerResponse> leaderboard() {
        return playerService.getLeaderboard().stream().map(PlayerResponse::from).toList();
    }

    @GetMapping("/players")
    public List<PlayerResponse> listPlayers(@RequestParam(required = false) String sort) {
        List<Player> players;

        if ("winrate".equalsIgnoreCase(sort)) {
            players = playerService.getAllPlayersSortedByWinRate();
        } else if ("avgCups".equalsIgnoreCase(sort)) {
            players = playerService.getAllPlayersSortedByAvgCups();
        } else if ("name".equalsIgnoreCase(sort)) {
            players = playerService.getAllPlayersSortedByName();
        } else {
            players = playerService.getAllPlayers();
        }

        return players.stream().map(PlayerResponse::from).toList();
    }

    @GetMapping("/players/search")
    public List<PlayerResponse> search(@RequestParam String name) {
        return playerService.findByNameContaining(name).stream().map(PlayerResponse::from).toList();
    }

    @GetMapping("/players/{id}")
    public PlayerResponse getPlayer(@PathVariable Long id) {
        return PlayerResponse.from(playerService.getPlayerById(id));
    }

    @PostMapping("/players")
    public ResponseEntity<PlayerResponse> createPlayer(@RequestBody CreatePlayerRequest request) {
        Player player = playerService.createPlayer(request.name(), request.password());
        return ResponseEntity.created(URI.create("/api/players/" + player.getId()))
                .body(PlayerResponse.from(player));
    }

    // Nur das eigene Profil darf gelöscht werden
    @DeleteMapping("/players/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlayer(@PathVariable Long id, @AuthenticationPrincipal PlayerUserDetails principal,
                             HttpServletRequest request) throws ServletException {
        if (!principal.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nur das eigene Profil kann gelöscht werden.");
        }
        playerService.deletePlayer(id);
        // Die Session gehört zum gelöschten Spieler und darf nicht weiterleben
        request.logout();
    }
}
