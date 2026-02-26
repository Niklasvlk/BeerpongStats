package de.niklasvoelker.beerpongstats.controller;

import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.service.PlayerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    public String listPlayers(@RequestParam(value = "sort", required = false) String sort, Model model) {
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

        model.addAttribute("players", players);
        return "players/list";
    }

    @GetMapping("/{id}")
    public String playerDetails(@PathVariable Long id, Model model) {
        Player player = playerService.getPlayerById(id);
        model.addAttribute("player", player);
        return "players/details";
    }

    @GetMapping("/search")
    public String searchPlayer(@RequestParam("name") String name, Model model) {
        List<Player> players = playerService.findByNameContaining(name);
        model.addAttribute("players", players);
        return "players/list"; // gleiche Seite wie Liste
    }
}
