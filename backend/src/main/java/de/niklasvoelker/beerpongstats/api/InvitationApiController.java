package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.AcceptInvitationRequest;
import de.niklasvoelker.beerpongstats.api.dto.GroupResponse;
import de.niklasvoelker.beerpongstats.api.dto.InvitationPreviewResponse;
import de.niklasvoelker.beerpongstats.config.AppUserDetails;
import de.niklasvoelker.beerpongstats.service.InvitationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

// Der Weg über den Link: Die Vorschau ist ohne Login erreichbar, das Beitreten braucht einen Account
@RestController
@RequestMapping("/api/invitations/{token}")
public class InvitationApiController {

    private final InvitationService invitationService;

    public InvitationApiController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping
    public InvitationPreviewResponse preview(@PathVariable String token) {
        return InvitationPreviewResponse.from(invitationService.preview(token));
    }

    @PostMapping("/accept")
    public ResponseEntity<GroupResponse> accept(@PathVariable String token,
                                                @RequestBody(required = false) AcceptInvitationRequest request,
                                                @AuthenticationPrincipal AppUserDetails user) {
        GroupResponse group = GroupResponse.from(invitationService.accept(
                token, user.getId(), request == null ? null : request.displayName()));
        return ResponseEntity.created(URI.create("/api/groups/" + group.id())).body(group);
    }
}
