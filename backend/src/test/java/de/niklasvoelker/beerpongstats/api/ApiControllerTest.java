package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.config.AppUserDetails;
import de.niklasvoelker.beerpongstats.config.GroupAccess;
import de.niklasvoelker.beerpongstats.config.SecurityConfig;
import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import de.niklasvoelker.beerpongstats.exception.InvalidUserDataException;
import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.exception.UserAlreadyExistsException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.PongGroup;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.service.GroupService;
import de.niklasvoelker.beerpongstats.service.GroupService.GroupMembership;
import de.niklasvoelker.beerpongstats.service.MatchService;
import de.niklasvoelker.beerpongstats.service.PlayerService;
import de.niklasvoelker.beerpongstats.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PlayerApiController.class, MatchApiController.class, AuthApiController.class, GroupApiController.class})
@Import(SecurityConfig.class)
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    @MockitoBean
    private MatchService matchService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private GroupService groupService;

    // Der Interceptor unter /api/groups/{groupId} läuft mit; das Mock lässt ihn durch, solange nichts anderes gestubbt ist
    @MockitoBean
    private GroupAccess groupAccess;

    @MockitoBean
    private AppUserRepository userRepository;

    private Player player(Long id, String name) {
        return Player.builder().id(id).name(name).wins(3).losses(1)
                .winRate(0.75).avgCups(4.5).createdAt(LocalDateTime.of(2026, 1, 1, 12, 0)).build();
    }

    private AppUser appUser(Long id, String username) {
        return AppUser.builder().id(id).username(username).password("geheim")
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0)).build();
    }

    private AppUserDetails principal() {
        return new AppUserDetails(1L, "Max", "x");
    }

    @Test
    void getPlayer_shouldReturnJson() throws Exception {
        when(playerService.getPlayerById(5L, 1L)).thenReturn(player(1L, "Max"));

        mockMvc.perform(get("/api/groups/5/players/1").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Max"))
                .andExpect(jsonPath("$.wins").value(3))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T12:00:00Z"));
    }

    @Test
    void getPlayer_shouldReturnProblemDetailWhenNotFound() throws Exception {
        when(playerService.getPlayerById(5L, 9L)).thenThrow(new PlayerNotFoundByIDException(9L));

        mockMvc.perform(get("/api/groups/5/players/9").with(user(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void leaderboard_shouldReturnList() throws Exception {
        when(playerService.getLeaderboard(5L)).thenReturn(List.of(player(1L, "Max"), player(2L, "Tom")));

        mockMvc.perform(get("/api/groups/5/leaderboard").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("Tom"));
    }

    @Test
    void getMatch_shouldReturn404WhenMissing() throws Exception {
        when(matchService.getMatchById(5L, 5L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/groups/5/matches/5").with(user(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getMatch_shouldHandleSoloMatchWithoutCups() throws Exception {
        Match match = Match.builder().id(5L).winner1(player(1L, "Max")).loser1(player(2L, "Tom"))
                .winner1Cups(6).loser1Cups(null).playedAt(LocalDateTime.of(2026, 1, 1, 12, 0)).build();
        when(matchService.getMatchById(5L, 5L)).thenReturn(Optional.of(match));

        mockMvc.perform(get("/api/groups/5/matches/5").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winner1.name").value("Max"))
                .andExpect(jsonPath("$.winner2").doesNotExist())
                .andExpect(jsonPath("$.mvpCups").value(6))
                .andExpect(jsonPath("$.playedAt").value("2026-01-01T12:00:00Z"));
    }

    @Test
    void createMatch_shouldReturn201() throws Exception {
        Match match = Match.builder().id(7L).winner1(player(1L, "Max")).loser1(player(2L, "Tom")).build();
        when(matchService.createMatch(eq(5L), any())).thenReturn(match);

        mockMvc.perform(post("/api/groups/5/matches").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winner1\":\"Max\",\"loser1\":\"Tom\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void dataEndpoints_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/groups")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5/leaderboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5/players")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5/players/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5/players/search").param("name", "a")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5/matches")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5/matches/recent")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/5/matches/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/groups").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kumpels\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/groups/5/matches").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winner1\":\"Max\",\"loser1\":\"Tom\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(playerService, matchService, groupService, groupAccess);
    }

    @Test
    void groupEndpoints_shouldReturn404ForANonMemberAndNeverReachTheService() throws Exception {
        doThrow(new GroupNotFoundException()).when(groupAccess).requireMember(5L, 1L);

        mockMvc.perform(get("/api/groups/5").with(user(principal()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/5/leaderboard").with(user(principal()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/5/players").with(user(principal()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/5/players/1").with(user(principal()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/5/players/search").param("name", "a").with(user(principal())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/5/matches").with(user(principal()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/5/matches/recent").with(user(principal()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/5/matches/1").with(user(principal()))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/groups/5/matches").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winner1\":\"Max\",\"loser1\":\"Tom\"}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(playerService, matchService, groupService);
    }

    @Test
    void groupEndpoints_shouldReturn404ForAGroupIdThatIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/groups/abc/players").with(user(principal())))
                .andExpect(status().isNotFound());

        verifyNoInteractions(playerService);
    }

    @Test
    void groupEndpoints_shouldCheckMembershipOfTheSignedInUser() throws Exception {
        when(playerService.getAllPlayers(5L)).thenReturn(List.of(player(1L, "Max")));

        mockMvc.perform(get("/api/groups/5/players").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Max"));

        verify(groupAccess).requireMember(5L, 1L);
    }

    private GroupMembership membership(Long groupId, String name, Role role) {
        PongGroup group = PongGroup.builder().id(groupId).name(name).build();
        Player me = player(20L, "Max");
        me.setRole(role);
        return new GroupMembership(group, me, 3);
    }

    @Test
    void myGroups_shouldListTheGroupsWithRoleAndStats() throws Exception {
        when(groupService.getMyGroups(1L)).thenReturn(List.of(membership(5L, "Kumpels", Role.ADMIN)));

        mockMvc.perform(get("/api/groups").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].name").value("Kumpels"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].playerId").value(20))
                .andExpect(jsonPath("$[0].wins").value(3))
                .andExpect(jsonPath("$[0].memberCount").value(3));
    }

    @Test
    void createGroup_shouldReturn201WithLocation() throws Exception {
        when(groupService.createGroup(1L, "Kumpels", "Maxi")).thenReturn(membership(5L, "Kumpels", Role.ADMIN));

        mockMvc.perform(post("/api/groups").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kumpels\",\"displayName\":\"Maxi\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/groups/5"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void createGroup_shouldReturn400ForAnInvalidName() throws Exception {
        when(groupService.createGroup(1L, " ", null))
                .thenThrow(new IllegalArgumentException("Der Gruppenname darf nicht leer sein."));

        mockMvc.perform(post("/api/groups").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Gruppenname darf nicht leer sein."));
    }

    @Test
    void createGroup_shouldRejectMissingCsrfToken() throws Exception {
        mockMvc.perform(post("/api/groups").with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kumpels\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(groupService);
    }

    @Test
    void getGroup_shouldReturnTheGroup() throws Exception {
        when(groupService.getGroup(5L, 1L)).thenReturn(membership(5L, "Kumpels", Role.MEMBER));

        mockMvc.perform(get("/api/groups/5").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kumpels"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void register_shouldReturn201WithoutPassword() throws Exception {
        when(userService.register("Max", "1234")).thenReturn(appUser(1L, "Max"));

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Max\",\"password\":\"1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/auth/me"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("Max"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_shouldReturn409WhenNameTaken() throws Exception {
        when(userService.register("Max", "1234")).thenThrow(new UserAlreadyExistsException("Max"));

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Max\",\"password\":\"1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void register_shouldReturnTheValidationMessage() throws Exception {
        when(userService.register("Max", "1")).thenThrow(new InvalidUserDataException("Das Passwort ist zu kurz."));

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Max\",\"password\":\"1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Das Passwort ist zu kurz."));
    }

    @Test
    void register_shouldRejectMissingCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Max\",\"password\":\"1234\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void me_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_shouldReturnCurrentUser() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(appUser(1L, "Max")));

        mockMvc.perform(get("/api/auth/me").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("Max"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void me_shouldReturn401WhenUserWasDeleted() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me").with(user(principal())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_shouldSucceedWithCorrectPassword() throws Exception {
        AppUser max = appUser(1L, "Max");
        max.setPassword(new BCryptPasswordEncoder().encode("1234"));
        when(userRepository.findByUsernameIgnoreCase("Max")).thenReturn(Optional.of(max));
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Max\",\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Max"));
    }

    @Test
    void login_shouldIgnoreCaseOfTheUsername() throws Exception {
        AppUser max = appUser(1L, "Max");
        max.setPassword(new BCryptPasswordEncoder().encode("1234"));
        when(userRepository.findByUsernameIgnoreCase("max")).thenReturn(Optional.of(max));
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"max\",\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Max"));
    }

    @Test
    void login_shouldReturn401WithWrongPassword() throws Exception {
        AppUser max = appUser(1L, "Max");
        max.setPassword(new BCryptPasswordEncoder().encode("1234"));
        when(userRepository.findByUsernameIgnoreCase("Max")).thenReturn(Optional.of(max));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Max\",\"password\":\"falsch\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Benutzername oder Passwort falsch"));
    }

    @Test
    void logout_shouldWorkWithoutASession() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMe_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(delete("/api/auth/me").with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).deleteUser(any());
    }

    @Test
    void deleteMe_shouldRejectMissingCsrfToken() throws Exception {
        mockMvc.perform(delete("/api/auth/me").with(user(principal())))
                .andExpect(status().isForbidden());

        verify(userService, never()).deleteUser(any());
    }

    @Test
    void deleteMe_shouldDeleteTheOwnAccountAndEndTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(delete("/api/auth/me").with(csrf()).session(session).with(user(principal())))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(1L);
        assertTrue(session.isInvalid());
    }
}
