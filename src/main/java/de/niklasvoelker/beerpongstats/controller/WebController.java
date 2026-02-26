package de.niklasvoelker.beerpongstats.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import de.niklasvoelker.beerpongstats.dto.MatchDTO;
import de.niklasvoelker.beerpongstats.service.MatchService;
import de.niklasvoelker.beerpongstats.service.PlayerService;

@Controller
public class WebController {

    private final PlayerService playerService;
    private final MatchService matchService;

    public WebController(PlayerService playerService, MatchService matchService) {
        this.playerService = playerService;
        this.matchService = matchService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("players", playerService.getTopPlayers());
        model.addAttribute("matches", matchService.getRecentMatches());
        return "index";
    }

    @GetMapping("/newPlayer")
    public String getAddPlayer() {
        return "newPlayer";
    }

    @PostMapping("/newPlayer")
    public String addPlayer(@RequestParam String name, @RequestParam String password) {
        playerService.createPlayer(name, password);
        return "redirect:/";
    }

    @GetMapping("/newMatch")
    public String getAddMatch() {
        return "newMatch";
    }

    @PostMapping("/newMatch")
    public String postAddMatch(@RequestParam String winner1,
                               @RequestParam(required = false) Integer winner1cups,
                               @RequestParam(required = false) String winner2,
                               @RequestParam(required = false) Integer winner2cups,
                               @RequestParam String loser1,
                               @RequestParam(required = false) Integer loser1cups,
                               @RequestParam(required = false) String loser2,
                               @RequestParam(required = false) Integer loser2cups){
        matchService.createMatch(new MatchDTO(winner1, winner2, loser1, loser2, winner1cups, winner2cups, loser1cups, loser2cups));
        return "redirect:/";
    }
}