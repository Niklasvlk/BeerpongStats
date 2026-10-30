package de.niklasvoelker.beerpongstats.config;

import lombok.Getter;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/**
 * Angemeldeter Benutzer. Die ID gehört zur Identität in der Session: Wird ein Benutzer gelöscht und der Name später
 * neu vergeben, gehört eine alte Session damit nicht zum neuen Benutzer.
 */
@Getter
public class AppUserDetails extends User {

    private final Long id;

    public AppUserDetails(Long id, String username, String passwordHash) {
        super(username, passwordHash, AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.id = id;
    }

}
