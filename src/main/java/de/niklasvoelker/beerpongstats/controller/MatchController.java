package de.niklasvoelker.beerpongstats.controller;

import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.service.MatchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    // 1️⃣ Alle Matches anzeigen
    @GetMapping
    public String listMatches(Model model) {
        List<Match> matches = matchService.getAllMatches();
        model.addAttribute("matches", matches);
        return "matches/list"; // Thymeleaf Template
    }

    // 2️⃣ Match Details
    @GetMapping("/{id}")
    public String matchDetails(@PathVariable Long id, Model model) {
        Match match = matchService.getMatchById(id)
                .orElseThrow(() -> new RuntimeException("Match nicht gefunden: " + id));
        model.addAttribute("match", match);

        int maxCups = Math.max(Math.max(match.getWinner1Cups(), match.getWinner2Cups() != null ? match.getWinner2Cups() : 0),
                Math.max(match.getLoser1Cups(), match.getLoser2Cups() != null ? match.getLoser2Cups() : 0));

        model.addAttribute("mvpCups", maxCups);

        return "matches/details"; // Thymeleaf Template
    }
}
