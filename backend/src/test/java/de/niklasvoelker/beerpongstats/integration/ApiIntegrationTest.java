package de.niklasvoelker.beerpongstats.integration;

import com.jayway.jsonpath.JsonPath;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.repository.GroupRepository;
import de.niklasvoelker.beerpongstats.repository.InvitationRepository;
import de.niklasvoelker.beerpongstats.repository.MatchRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gemeinsame Helfer für Tests, die die API wie ein Browser benutzen: Registrieren, Einloggen, Sessions. */
abstract class ApiIntegrationTest extends PostgresIntegrationTest {

    static final String PASSWORD = "geheim1234";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected AppUserRepository userRepository;
    @Autowired
    protected GroupRepository groupRepository;
    @Autowired
    protected PlayerRepository playerRepository;
    @Autowired
    protected MatchRepository matchRepository;
    @Autowired
    protected InvitationRepository invitationRepository;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE match, invitation, player, pong_group, app_user RESTART IDENTITY CASCADE");
    }

    protected static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            sb.append(i > 0 ? "," : "").append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    protected void register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", PASSWORD)))
                .andExpect(status().isCreated());
    }

    // Echter Login: die Session trägt den Security-Kontext, wie im Browser
    protected MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected MockHttpSession registerAndLogin(String username) throws Exception {
        register(username);
        return login(username);
    }

    protected long createGroup(MockHttpSession session, String name, String displayName) throws Exception {
        String body = displayName == null ? json("name", name) : json("name", name, "displayName", displayName);
        MvcResult result = mockMvc.perform(post("/api/groups").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    // Ein Spieler ohne Account, so wie später Platzhalter oder ehemalige Mitglieder aussehen
    protected void addPlaceholder(long groupId, String name) {
        playerRepository.save(Player.builder()
                .group(groupRepository.getReferenceById(groupId))
                .name(name)
                .role(Role.MEMBER)
                .createdAt(LocalDateTime.now())
                .build());
    }

    protected void playMatch(MockHttpSession session, long groupId, String winner, String loser, int loserCups) throws Exception {
        mockMvc.perform(post("/api/groups/" + groupId + "/matches").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winner1\":\"" + winner + "\",\"loser1\":\"" + loser
                                + "\",\"winner1Cups\":10,\"loser1Cups\":" + loserCups + "}"))
                .andExpect(status().isCreated());
    }

    protected ResultActions getAs(MockHttpSession session, String url) throws Exception {
        return mockMvc.perform(get(url).session(session));
    }

    protected ResultActions postAs(MockHttpSession session, String url, String body) throws Exception {
        var request = post(url).with(csrf()).session(session);
        return mockMvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions patchAs(MockHttpSession session, String url, String body) throws Exception {
        return mockMvc.perform(patch(url).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions deleteAs(MockHttpSession session, String url) throws Exception {
        return mockMvc.perform(delete(url).with(csrf()).session(session));
    }

    protected long idFromList(MockHttpSession session, String url, int index) throws Exception {
        String body = getAs(session, url).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$[" + index + "].id")).longValue();
    }

    protected <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    // Erzeugt eine Einladung und gibt den Token zurück
    protected String createInvitation(MockHttpSession admin, long groupId, String body) throws Exception {
        return read(postAs(admin, "/api/groups/" + groupId + "/invitations", body).andExpect(status().isCreated()), "$.token");
    }

    protected ResultActions accept(MockHttpSession session, String token, String displayName) throws Exception {
        return postAs(session, "/api/invitations/" + token + "/accept",
                displayName == null ? null : json("displayName", displayName));
    }

    // Lässt alle Einladungen ablaufen
    protected void expireAllInvitations() {
        jdbc.update("UPDATE invitation SET expires_at = now() at time zone 'utc' - interval '1 minute'");
    }
}
