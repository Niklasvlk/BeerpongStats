package de.niklasvoelker.beerpongstats.controller;

import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import de.niklasvoelker.beerpongstats.dto.LoginDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final PlayerRepository playerRepository;

    public ProfileController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    // Login-Seite anzeigen
    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("loginDTO", new LoginDTO());
        return "profile/login";
    }

    // Login prüfen
    @PostMapping("/login")
    public String loginSubmit(@ModelAttribute LoginDTO loginDTO, Model model) {
        Player player = playerRepository.findByName(loginDTO.getName())
                .orElse(null);

        if (player == null || !player.getPassword().equals(loginDTO.getPassword())) {
            model.addAttribute("error", "Name oder Passwort falsch");
            return "profile/login";
        }

        model.addAttribute("player", player);
        return "profile/details";
    }

    // Spieler löschen
    @PostMapping("/delete/{id}")
    public String deletePlayer(@PathVariable Long id) {
        playerRepository.deleteById(id);
        return "redirect:/"; // Zur Startseite nach Löschen
    }
}