package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.config.AppUserDetails;
import de.niklasvoelker.beerpongstats.config.GroupAccess;
import de.niklasvoelker.beerpongstats.config.SecurityConfig;
import de.niklasvoelker.beerpongstats.exception.GroupConflictException;
import de.niklasvoelker.beerpongstats.exception.InvitationInvalidException;
import de.niklasvoelker.beerpongstats.exception.NotGroupAdminException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.model.Invitation;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.PongGroup;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.service.GroupService;
import de.niklasvoelker.beerpongstats.service.GroupService.GroupMembership;
import de.niklasvoelker.beerpongstats.service.InvitationService;
import de.niklasvoelker.beerpongstats.service.InvitationService.CreatedInvitation;
import de.niklasvoelker.beerpongstats.service.InvitationService.Preview;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({MemberApiController.class, GroupInvitationApiController.class, InvitationApiController.class})
@Import(SecurityConfig.class)
class GroupManagementApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private InvitationService invitationService;

    // Der Interceptor unter /api/groups/{groupId} läuft mit; das Mock lässt ihn durch
    @MockitoBean
    private GroupAccess groupAccess;

    @MockitoBean
    private AppUserRepository userRepository;

    private AppUserDetails principal() {
        return new AppUserDetails(1L, "Max", "x");
    }

    private Player player(Long id, String name, Role role, boolean withAccount) {
        return Player.builder().id(id).name(name).role(role)
                .user(withAccount ? AppUser.builder().id(id).build() : null)
                .wins(3).losses(1).winRate(0.75).avgCups(4.5)
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0)).build();
    }

    private Invitation invitation(Long id, Integer maxUses, int uses) {
        return Invitation.builder().id(id).tokenHash("hash").uses(uses).maxUses(maxUses)
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .expiresAt(LocalDateTime.now().plusDays(3)).build();
    }

    // --- Mitglieder ---

    @Test
    void members_shouldListThePlayersWithRoleAndActiveFlag() throws Exception {
        when(groupService.getPlayers(5L)).thenReturn(List.of(
                player(1L, "Max", Role.ADMIN, true), player(2L, "Anna", Role.MEMBER, false)));

        mockMvc.perform(get("/api/groups/5/members").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Max"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].createdAt").value("2026-01-01T12:00:00Z"))
                .andExpect(jsonPath("$[0].user").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void changeRole_shouldReturnTheUpdatedMember() throws Exception {
        when(groupService.changeRole(5L, 1L, 2L, Role.ADMIN)).thenReturn(player(2L, "Anna", Role.ADMIN, true));

        mockMvc.perform(patch("/api/groups/5/members/2").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void changeRole_shouldReturn403ForANonAdmin() throws Exception {
        when(groupService.changeRole(5L, 1L, 2L, Role.ADMIN)).thenThrow(new NotGroupAdminException());

        mockMvc.perform(patch("/api/groups/5/members/2").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Das dürfen nur Admins der Gruppe."));
    }

    @Test
    void changeRole_shouldReturn409ForTheLastAdmin() throws Exception {
        when(groupService.changeRole(5L, 1L, 1L, Role.MEMBER))
                .thenThrow(new GroupConflictException("Die Gruppe braucht mindestens einen Admin."));

        mockMvc.perform(patch("/api/groups/5/members/1").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void changeRole_shouldReturn400ForAnUnknownRole() throws Exception {
        mockMvc.perform(patch("/api/groups/5/members/2").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"KING\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void changeRole_shouldRejectMissingCsrfToken() throws Exception {
        mockMvc.perform(patch("/api/groups/5/members/2").with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(groupService);
    }

    @Test
    void leave_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/groups/5/members/me").with(csrf()).with(user(principal())))
                .andExpect(status().isNoContent());

        verify(groupService).leaveGroup(5L, 1L);
    }

    @Test
    void leave_shouldReturn409ForTheLastAdmin() throws Exception {
        doThrow(new GroupConflictException("letzter Admin")).when(groupService).leaveGroup(5L, 1L);

        mockMvc.perform(delete("/api/groups/5/members/me").with(csrf()).with(user(principal())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("letzter Admin"));
    }

    // --- Einladungen verwalten ---

    @Test
    void createInvitation_shouldReturnTheTokenOnce() throws Exception {
        when(invitationService.createInvitation(5L, 1L, 3, 10))
                .thenReturn(new CreatedInvitation(invitation(7L, 10, 0), "der-token"));

        mockMvc.perform(post("/api/groups/5/invitations").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInDays\":3,\"maxUses\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.token").value("der-token"))
                .andExpect(jsonPath("$.maxUses").value(10))
                .andExpect(jsonPath("$.tokenHash").doesNotExist());
    }

    @Test
    void createInvitation_shouldWorkWithoutABody() throws Exception {
        when(invitationService.createInvitation(5L, 1L, null, null))
                .thenReturn(new CreatedInvitation(invitation(7L, null, 0), "der-token"));

        mockMvc.perform(post("/api/groups/5/invitations").with(csrf()).with(user(principal())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("der-token"));
    }

    @Test
    void createInvitation_shouldReturn403ForANonAdmin() throws Exception {
        when(invitationService.createInvitation(5L, 1L, null, null)).thenThrow(new NotGroupAdminException());

        mockMvc.perform(post("/api/groups/5/invitations").with(csrf()).with(user(principal())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createInvitation_shouldReturn400ForInvalidLimits() throws Exception {
        when(invitationService.createInvitation(5L, 1L, 99, null))
                .thenThrow(new IllegalArgumentException("zu lang"));

        mockMvc.perform(post("/api/groups/5/invitations").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInDays\":99}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listInvitations_shouldShowMetadataAndStatusButNeverTheToken() throws Exception {
        when(invitationService.getInvitations(5L, 1L)).thenReturn(List.of(invitation(7L, 2, 2), invitation(8L, null, 0)));

        mockMvc.perform(get("/api/groups/5/invitations").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].uses").value(2))
                .andExpect(jsonPath("$[0].status").value("EXHAUSTED"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist());
    }

    @Test
    void revokeInvitation_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/groups/5/invitations/7").with(csrf()).with(user(principal())))
                .andExpect(status().isNoContent());

        verify(invitationService).revoke(5L, 1L, 7L);
    }

    @Test
    void invitationEndpointsOfAGroup_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(post("/api/groups/5/invitations").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5/invitations")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/groups/5/invitations/7").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5/members")).andExpect(status().isUnauthorized());

        verifyNoInteractions(invitationService, groupService);
    }

    // --- Der Weg über den Link ---

    @Test
    void preview_shouldBeAvailableWithoutLogin() throws Exception {
        when(invitationService.preview("der-token")).thenReturn(new Preview("Kumpels", 4));

        mockMvc.perform(get("/api/invitations/der-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupName").value("Kumpels"))
                .andExpect(jsonPath("$.memberCount").value(4));
    }

    @Test
    void preview_shouldReturn410ForAnInvalidInvitation() throws Exception {
        when(invitationService.preview("alt")).thenThrow(new InvitationInvalidException());

        mockMvc.perform(get("/api/invitations/alt"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.detail").value("Diese Einladung ist ungültig oder abgelaufen."));
    }

    @Test
    void accept_shouldRequireLogin() throws Exception {
        mockMvc.perform(post("/api/invitations/der-token/accept").with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(invitationService);
    }

    @Test
    void accept_shouldReturn201WithTheJoinedGroup() throws Exception {
        PongGroup group = PongGroup.builder().id(5L).name("Kumpels").build();
        when(invitationService.accept(eq("der-token"), eq(1L), eq("Maxi")))
                .thenReturn(new GroupMembership(group, player(20L, "Maxi", Role.MEMBER, true), 4));

        mockMvc.perform(post("/api/invitations/der-token/accept").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Maxi\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/groups/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(4));
    }

    @Test
    void accept_shouldWorkWithoutABody() throws Exception {
        PongGroup group = PongGroup.builder().id(5L).name("Kumpels").build();
        when(invitationService.accept("der-token", 1L, null))
                .thenReturn(new GroupMembership(group, player(20L, "Max", Role.MEMBER, true), 2));

        mockMvc.perform(post("/api/invitations/der-token/accept").with(csrf()).with(user(principal())))
                .andExpect(status().isCreated());
    }

    @Test
    void accept_shouldReturn409WhenAlreadyAMember() throws Exception {
        when(invitationService.accept(any(), any(), any()))
                .thenThrow(new GroupConflictException("Du bist bereits Mitglied dieser Gruppe."));

        mockMvc.perform(post("/api/invitations/der-token/accept").with(csrf()).with(user(principal())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Du bist bereits Mitglied dieser Gruppe."));
    }

    @Test
    void accept_shouldReturn410ForAnInvalidInvitation() throws Exception {
        when(invitationService.accept(any(), any(), any())).thenThrow(new InvitationInvalidException());

        mockMvc.perform(post("/api/invitations/der-token/accept").with(csrf()).with(user(principal())))
                .andExpect(status().isGone());
    }

    @Test
    void accept_shouldRejectMissingCsrfToken() throws Exception {
        mockMvc.perform(post("/api/invitations/der-token/accept").with(user(principal())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(invitationService);
    }
}
