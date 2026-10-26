package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.PlayerResponse;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.service.PlayerService;
import org.springframework.web.bind.annotation.*;

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
}
