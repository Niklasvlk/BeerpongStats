package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.CreateInvitationRequest;
import de.niklasvoelker.beerpongstats.api.dto.CreatedInvitationResponse;
import de.niklasvoelker.beerpongstats.api.dto.InvitationResponse;
import de.niklasvoelker.beerpongstats.config.AppUserDetails;
import de.niklasvoelker.beerpongstats.service.InvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Einladungen verwalten, nur für Admins der Gruppe (zusätzlich zur Mitgliedschaftsprüfung des Interceptors)
@RestController
@RequestMapping("/api/groups/{groupId}/invitations")
public class GroupInvitationApiController {

    private final InvitationService invitationService;

    public GroupInvitationApiController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedInvitationResponse create(@PathVariable Long groupId,
                                            @RequestBody(required = false) CreateInvitationRequest request,
                                            @AuthenticationPrincipal AppUserDetails user) {
        Integer expiresInDays = request == null ? null : request.expiresInDays();
        Integer maxUses = request == null ? null : request.maxUses();
        return CreatedInvitationResponse.from(
                invitationService.createInvitation(groupId, user.getId(), expiresInDays, maxUses));
    }

    @GetMapping
    public List<InvitationResponse> list(@PathVariable Long groupId, @AuthenticationPrincipal AppUserDetails user) {
        return invitationService.getInvitations(groupId, user.getId()).stream().map(InvitationResponse::from).toList();
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long groupId, @PathVariable Long invitationId,
                       @AuthenticationPrincipal AppUserDetails user) {
        invitationService.revoke(groupId, user.getId(), invitationId);
    }
}
