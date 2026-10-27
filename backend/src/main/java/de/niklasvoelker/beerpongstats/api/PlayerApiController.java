package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.PlayerResponse;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.service.PlayerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
// Der GroupAccessInterceptor lässt nur Mitglieder der Gruppe an diese Endpoints
@RequestMapping("/api/groups/{groupId}")
public class PlayerApiController {

    private final PlayerService playerService;

    public PlayerApiController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping("/leaderboard")
    public List<PlayerResponse> leaderboard(@PathVariable Long groupId) {
        return playerService.getLeaderboard(groupId).stream().map(PlayerResponse::from).toList();
    }

    @GetMapping("/players")
    public List<PlayerResponse> listPlayers(@PathVariable Long groupId, @RequestParam(required = false) String sort) {
        List<Player> players;

        if ("winrate".equalsIgnoreCase(sort)) {
            players = playerService.getAllPlayersSortedByWinRate(groupId);
        } else if ("avgCups".equalsIgnoreCase(sort)) {
            players = playerService.getAllPlayersSortedByAvgCups(groupId);
        } else if ("name".equalsIgnoreCase(sort)) {
            players = playerService.getAllPlayersSortedByName(groupId);
        } else {
            players = playerService.getAllPlayers(groupId);
        }

        return players.stream().map(PlayerResponse::from).toList();
    }

    @GetMapping("/players/search")
    public List<PlayerResponse> search(@PathVariable Long groupId, @RequestParam String name) {
        return playerService.findByNameContaining(groupId, name).stream().map(PlayerResponse::from).toList();
    }

    @GetMapping("/players/{id}")
    public PlayerResponse getPlayer(@PathVariable Long groupId, @PathVariable Long id) {
        return PlayerResponse.from(playerService.getPlayerById(groupId, id));
    }
}
