package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.CreateGroupRequest;
import de.niklasvoelker.beerpongstats.api.dto.GroupResponse;
import de.niklasvoelker.beerpongstats.config.AppUserDetails;
import de.niklasvoelker.beerpongstats.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupApiController {

    private final GroupService groupService;

    public GroupApiController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public List<GroupResponse> myGroups(@AuthenticationPrincipal AppUserDetails user) {
        return groupService.getMyGroups(user.getId()).stream().map(GroupResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@RequestBody CreateGroupRequest request,
                                                     @AuthenticationPrincipal AppUserDetails user) {
        GroupResponse group = GroupResponse.from(
                groupService.createGroup(user.getId(), request.name(), request.displayName()));
        return ResponseEntity.created(URI.create("/api/groups/" + group.id())).body(group);
    }

    @GetMapping("/{groupId}")
    public GroupResponse getGroup(@PathVariable Long groupId, @AuthenticationPrincipal AppUserDetails user) {
        return GroupResponse.from(groupService.getGroup(groupId, user.getId()));
    }
}
