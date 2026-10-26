package de.niklasvoelker.beerpongstats.config;

import lombok.Getter;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/**
 * Angemeldeter Spieler. Die ID gehört zur Identität in der Session: Wird ein Spieler gelöscht und der Name später
 * neu vergeben, gehört eine alte Session damit nicht zum neuen Spieler.
 */
@Getter
public class PlayerUserDetails extends User {

    private final Long id;

    public PlayerUserDetails(Long id, String name, String passwordHash) {
        super(name, passwordHash, AuthorityUtils.createAuthorityList("ROLE_PLAYER"));
        this.id = id;
    }

}
