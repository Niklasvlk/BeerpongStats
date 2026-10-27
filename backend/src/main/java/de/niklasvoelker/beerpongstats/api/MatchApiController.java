package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.MatchResponse;
import de.niklasvoelker.beerpongstats.dto.MatchDTO;
import de.niklasvoelker.beerpongstats.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
// Der GroupAccessInterceptor lässt nur Mitglieder der Gruppe an diese Endpoints
@RequestMapping("/api/groups/{groupId}/matches")
public class MatchApiController {

    private final MatchService matchService;

    public MatchApiController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public List<MatchResponse> listMatches(@PathVariable Long groupId) {
        return matchService.getAllMatches(groupId).stream().map(MatchResponse::from).toList();
    }

    @GetMapping("/recent")
    public List<MatchResponse> recentMatches(@PathVariable Long groupId) {
        return matchService.getRecentMatches(groupId).stream().map(MatchResponse::from).toList();
    }

    @GetMapping("/{id}")
    public MatchResponse getMatch(@PathVariable Long groupId, @PathVariable Long id) {
        return matchService.getMatchById(groupId, id)
                .map(MatchResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match nicht gefunden: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponse createMatch(@PathVariable Long groupId, @RequestBody MatchDTO request) {
        return MatchResponse.from(matchService.createMatch(groupId, request));
    }
}
