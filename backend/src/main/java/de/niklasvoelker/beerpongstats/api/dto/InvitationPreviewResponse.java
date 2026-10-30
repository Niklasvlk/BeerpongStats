package de.niklasvoelker.beerpongstats.api.dto;

import de.niklasvoelker.beerpongstats.service.InvitationService.Preview;

public record InvitationPreviewResponse(String groupName, long memberCount) {
    public static InvitationPreviewResponse from(Preview preview) {
        return new InvitationPreviewResponse(preview.groupName(), preview.memberCount());
    }
}
