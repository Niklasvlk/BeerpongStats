package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.config.AppUserDetails;
import de.niklasvoelker.beerpongstats.config.SecurityConfig;
import de.niklasvoelker.beerpongstats.exception.InvalidUserDataException;
import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.exception.UserAlreadyExistsException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
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

@WebMvcTest({PlayerApiController.class, MatchApiController.class, AuthApiController.class})
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
        when(playerService.getPlayerById(1L)).thenReturn(player(1L, "Max"));

        mockMvc.perform(get("/api/players/1").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Max"))
                .andExpect(jsonPath("$.wins").value(3))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T12:00:00Z"));
    }

    @Test
    void getPlayer_shouldReturnProblemDetailWhenNotFound() throws Exception {
        when(playerService.getPlayerById(9L)).thenThrow(new PlayerNotFoundByIDException(9L));

        mockMvc.perform(get("/api/players/9").with(user(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void leaderboard_shouldReturnList() throws Exception {
        when(playerService.getLeaderboard()).thenReturn(List.of(player(1L, "Max"), player(2L, "Tom")));

        mockMvc.perform(get("/api/leaderboard").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("Tom"));
    }

    @Test
    void getMatch_shouldReturn404WhenMissing() throws Exception {
        when(matchService.getMatchById(5L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/matches/5").with(user(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getMatch_shouldHandleSoloMatchWithoutCups() throws Exception {
        Match match = Match.builder().id(5L).winner1(player(1L, "Max")).loser1(player(2L, "Tom"))
                .winner1Cups(6).loser1Cups(null).playedAt(LocalDateTime.of(2026, 1, 1, 12, 0)).build();
        when(matchService.getMatchById(5L)).thenReturn(Optional.of(match));

        mockMvc.perform(get("/api/matches/5").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winner1.name").value("Max"))
                .andExpect(jsonPath("$.winner2").doesNotExist())
                .andExpect(jsonPath("$.mvpCups").value(6))
                .andExpect(jsonPath("$.playedAt").value("2026-01-01T12:00:00Z"));
    }

    @Test
    void createMatch_shouldReturn201() throws Exception {
        Match match = Match.builder().id(7L).winner1(player(1L, "Max")).loser1(player(2L, "Tom")).build();
        when(matchService.createMatch(any())).thenReturn(match);

        mockMvc.perform(post("/api/matches").with(csrf()).with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winner1\":\"Max\",\"loser1\":\"Tom\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void dataEndpoints_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/leaderboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/players")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/players/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/players/search").param("name", "a")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/matches")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/matches/recent")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/matches/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/matches").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winner1\":\"Max\",\"loser1\":\"Tom\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(playerService, matchService);
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
