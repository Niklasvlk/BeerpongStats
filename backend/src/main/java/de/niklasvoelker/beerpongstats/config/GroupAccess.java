package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import de.niklasvoelker.beerpongstats.exception.NotGroupAdminException;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.springframework.stereotype.Component;

/**
 * Zentrale Prüfung, ob ein Benutzer Mitglied einer Gruppe ist. Wer nicht Mitglied ist, bekommt dieselbe Antwort wie
 * bei einer Gruppe, die es nicht gibt.
 */
@Component
public class GroupAccess {

    private final PlayerRepository playerRepository;

    public GroupAccess(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public Player requireMember(Long groupId, Long userId) {
        return playerRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupNotFoundException::new);
    }

    // Admin-Endpoints rufen das zusätzlich zur Mitgliedschaftsprüfung des Interceptors auf
    public Player requireAdmin(Long groupId, Long userId) {
        Player member = requireMember(groupId, userId);
        if (member.getRole() != Role.ADMIN) {
            throw new NotGroupAdminException();
        }
        return member;
    }
}
