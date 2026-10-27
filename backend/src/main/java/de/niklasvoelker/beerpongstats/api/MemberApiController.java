package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.ChangeRoleRequest;
import de.niklasvoelker.beerpongstats.api.dto.MemberResponse;
import de.niklasvoelker.beerpongstats.config.AppUserDetails;
import de.niklasvoelker.beerpongstats.service.GroupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Der GroupAccessInterceptor lässt nur Mitglieder der Gruppe an diese Endpoints
@RestController
@RequestMapping("/api/groups/{groupId}/members")
public class MemberApiController {

    private final GroupService groupService;

    public MemberApiController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public List<MemberResponse> members(@PathVariable Long groupId) {
        return groupService.getPlayers(groupId).stream().map(MemberResponse::from).toList();
    }

    // Nur Admins
    @PatchMapping("/{playerId}")
    public MemberResponse changeRole(@PathVariable Long groupId, @PathVariable Long playerId,
                                     @RequestBody ChangeRoleRequest request,
                                     @AuthenticationPrincipal AppUserDetails user) {
        return MemberResponse.from(groupService.changeRole(groupId, user.getId(), playerId, request.role()));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long groupId, @AuthenticationPrincipal AppUserDetails user) {
        groupService.leaveGroup(groupId, user.getId());
    }
}
