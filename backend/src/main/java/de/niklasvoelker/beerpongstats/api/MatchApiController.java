package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.MatchResponse;
import de.niklasvoelker.beerpongstats.dto.MatchDTO;
import de.niklasvoelker.beerpongstats.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchApiController {

    private final MatchService matchService;

    public MatchApiController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public List<MatchResponse> listMatches() {
        return matchService.getAllMatches().stream().map(MatchResponse::from).toList();
    }

    @GetMapping("/recent")
    public List<MatchResponse> recentMatches() {
        return matchService.getRecentMatches().stream().map(MatchResponse::from).toList();
    }

    @GetMapping("/{id}")
    public MatchResponse getMatch(@PathVariable Long id) {
        return matchService.getMatchById(id)
                .map(MatchResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match nicht gefunden: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponse createMatch(@RequestBody MatchDTO request) {
        return MatchResponse.from(matchService.createMatch(request));
    }
}
