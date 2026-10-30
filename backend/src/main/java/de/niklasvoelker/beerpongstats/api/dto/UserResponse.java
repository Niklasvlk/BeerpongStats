package de.niklasvoelker.beerpongstats.api.dto;

import de.niklasvoelker.beerpongstats.model.AppUser;

public record UserResponse(Long id, String username) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername());
    }
}
