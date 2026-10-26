package de.niklasvoelker.beerpongstats.controller;

import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final PlayerRepository playerRepository;

    public ProfileController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    // Login-Seite anzeigen, die Prüfung übernimmt Spring Security
    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "Name oder Passwort falsch");
        }
        return "profile/login";
    }

    // Eigenes Profil anzeigen
    @GetMapping
    public String profile(Authentication authentication, Model model) {
        Player player = playerRepository.findByName(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("player", player);
        return "profile/details";
    }

    // Eigenen Spieler löschen
    @PostMapping("/delete/{id}")
    public String deletePlayer(@PathVariable Long id, Authentication authentication, HttpServletRequest request)
            throws jakarta.servlet.ServletException {
        Player player = playerRepository.findByName(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!player.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        playerRepository.deleteById(id);
        request.logout();
        return "redirect:/"; // Zur Startseite nach Löschen
    }
}
