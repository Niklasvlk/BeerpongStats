package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.config.PlayerUserDetails;
import de.niklasvoelker.beerpongstats.config.SecurityConfig;
import de.niklasvoelker.beerpongstats.exception.PlayerAlreadyExistsException;
import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import de.niklasvoelker.beerpongstats.service.MatchService;
import de.niklasvoelker.beerpongstats.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private PlayerRepository playerRepository;

    private Player player(Long id, String name) {
        return Player.builder().id(id).name(name).password("geheim").wins(3).losses(1)
                .winRate(0.75).avgCups(4.5).createdAt(LocalDateTime.of(2026, 1, 1, 12, 0)).build();
    }

    @Test
    void getPlayer_shouldReturnJsonWithoutPassword() throws Exception {
        when(playerService.getPlayerById(1L)).thenReturn(player(1L, "Max"));

        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Max"))
                .andExpect(jsonPath("$.wins").value(3))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T12:00:00Z"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getPlayer_shouldReturnProblemDetailWhenNotFound() throws Exception {
        when(playerService.getPlayerById(9L)).thenThrow(new PlayerNotFoundByIDException(9L));

        mockMvc.perform(get("/api/players/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void leaderboard_shouldReturnList() throws Exception {
        when(playerService.getLeaderboard()).thenReturn(List.of(player(1L, "Max"), player(2L, "Tom")));

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("Tom"));
    }

    @Test
    void createPlayer_shouldReturn201WithLocation() throws Exception {
        when(playerService.createPlayer("Max", "1234")).thenReturn(player(1L, "Max"));

        mockMvc.perform(post("/api/players").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Max\",\"password\":\"1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/players/1"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void createPlayer_shouldReturn409WhenNameTaken() throws Exception {
        when(playerService.createPlayer("Max", "1234")).thenThrow(new PlayerAlreadyExistsException("Max"));

        mockMvc.perform(post("/api/players").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Max\",\"password\":\"1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createPlayer_shouldRejectMissingCsrfToken() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Max\",\"password\":\"1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMatch_shouldReturn404WhenMissing() throws Exception {
        when(matchService.getMatchById(5L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/matches/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getMatch_shouldHandleSoloMatchWithoutCups() throws Exception {
        Match match = Match.builder().id(5L).winner1(player(1L, "Max")).loser1(player(2L, "Tom"))
                .winner1Cups(6).loser1Cups(null).playedAt(LocalDateTime.of(2026, 1, 1, 12, 0)).build();
        when(matchService.getMatchById(5L)).thenReturn(Optional.of(match));

        mockMvc.perform(get("/api/matches/5"))
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

        mockMvc.perform(post("/api/matches").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winner1\":\"Max\",\"loser1\":\"Tom\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    private PlayerUserDetails principal() {
        return new PlayerUserDetails(1L, "Max", "x");
    }

    @Test
    void me_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_shouldReturnCurrentPlayer() throws Exception {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player(1L, "Max")));

        mockMvc.perform(get("/api/auth/me").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Max"));
    }

    @Test
    void me_shouldReturn401WhenPlayerWasDeleted() throws Exception {
        when(playerRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me").with(user(principal())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_shouldSucceedWithCorrectPassword() throws Exception {
        Player max = player(1L, "Max");
        max.setPassword(new BCryptPasswordEncoder().encode("1234"));
        when(playerRepository.findByNameIgnoreCase("Max")).thenReturn(Optional.of(max));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(max));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Max\",\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Max"));
    }

    @Test
    void login_shouldIgnoreCaseOfTheName() throws Exception {
        Player max = player(1L, "Max");
        max.setPassword(new BCryptPasswordEncoder().encode("1234"));
        when(playerRepository.findByNameIgnoreCase("max")).thenReturn(Optional.of(max));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(max));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"max\",\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Max"));
    }

    @Test
    void login_shouldReturn401WithWrongPassword() throws Exception {
        Player max = player(1L, "Max");
        max.setPassword(new BCryptPasswordEncoder().encode("1234"));
        when(playerRepository.findByNameIgnoreCase("Max")).thenReturn(Optional.of(max));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Max\",\"password\":\"falsch\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletePlayer_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(delete("/api/players/1").with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(playerService, never()).deletePlayer(1L);
    }

    @Test
    void deletePlayer_shouldRejectForeignProfile() throws Exception {
        mockMvc.perform(delete("/api/players/2").with(csrf()).with(user(principal())))
                .andExpect(status().isForbidden());

        verify(playerService, never()).deletePlayer(2L);
    }

    @Test
    void deletePlayer_shouldRejectSessionOfADeletedPlayerWithTheSameName() throws Exception {
        // Alte Session gehört zu Spieler 1; "Max" wurde inzwischen mit der ID 9 neu angelegt
        mockMvc.perform(delete("/api/players/9").with(csrf()).with(user(principal())))
                .andExpect(status().isForbidden());

        verify(playerService, never()).deletePlayer(9L);
    }

    @Test
    void deletePlayer_shouldReturn409WhenPlayerHasMatches() throws Exception {
        doThrow(new DataIntegrityViolationException("fk")).when(playerService).deletePlayer(1L);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(delete("/api/players/1").with(csrf()).session(session).with(user(principal())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Der Spieler hat bereits Matches gespielt und kann nicht gelöscht werden."));

        assertFalse(session.isInvalid());
    }

    @Test
    void deletePlayer_shouldDeleteOwnProfileAndEndTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(delete("/api/players/1").with(csrf()).session(session).with(user(principal())))
                .andExpect(status().isNoContent());

        verify(playerService).deletePlayer(1L);
        assertTrue(session.isInvalid());
    }
}
