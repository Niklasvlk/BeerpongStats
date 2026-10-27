package de.niklasvoelker.beerpongstats.integration;

import com.jayway.jsonpath.JsonPath;
import de.niklasvoelker.beerpongstats.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Die Grenzen gegen das Durchprobieren von Passwörtern, durch die ganze Anwendung hindurch. Die Werte sind hier klein
 * eingestellt, damit wenige Anfragen reichen. Jede Anfrage kommt von einer wählbaren Client-Adresse.
 *
 * <p>Die Zähler leben im Spring-Kontext und bleiben zwischen den Tests erhalten. Deshalb bekommt jeder Test eigene
 * Benutzernamen und eigene Adressen (siehe {@link #ip(int)}); so beeinflussen sich die Tests nicht.
 */
@TestPropertySource(properties = {
        "beerpong.rate-limit.enabled=true",
        "beerpong.rate-limit.login-per-minute=3",
        "beerpong.rate-limit.register-per-hour=2",
        "beerpong.rate-limit.join-per-minute=2",
        "beerpong.rate-limit.failed-logins-per-username=3"
})
class RateLimitTest extends ApiIntegrationTest {

    private static final AtomicInteger TESTS = new AtomicInteger();

    @Autowired
    private UserService userService;

    private int testNumber;
    private String max;
    private String anna;

    @BeforeEach
    void createUsers() {
        testNumber = TESTS.incrementAndGet();
        max = "Max" + testNumber;
        anna = "Anna" + testNumber;
        // Direkt über den Service: Registrierungen über die API würden selbst gegen das Limit zählen
        userService.register(max, PASSWORD);
        userService.register(anna, PASSWORD);
    }

    // Eine Adresse, die nur dieser Test benutzt
    private String ip(int host) {
        return "10." + testNumber + ".0." + host;
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private MvcResult login(String address, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).with(from(address))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", password)))
                .andReturn();
    }

    private int loginStatus(String address, String username, String password) throws Exception {
        return login(address, username, password).getResponse().getStatus();
    }

    private int registerStatus(String address, String username) throws Exception {
        return mockMvc.perform(post("/api/auth/register").with(csrf()).with(from(address))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", PASSWORD)))
                .andReturn().getResponse().getStatus();
    }

    // --- Login pro Client-Adresse ---

    @Test
    void loginsFromOneAddressAreLimitedAndTheRefusalExplainsItself() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(200, loginStatus(ip(1), max, PASSWORD));
        }

        MvcResult refused = login(ip(1), max, PASSWORD);

        assertEquals(429, refused.getResponse().getStatus());
        assertEquals("application/problem+json;charset=UTF-8", refused.getResponse().getContentType());
        long retryAfter = Long.parseLong(refused.getResponse().getHeader("Retry-After"));
        assertTrue(retryAfter >= 1 && retryAfter <= 60, "Retry-After: " + retryAfter);
        String body = refused.getResponse().getContentAsString();
        assertTrue(JsonPath.<String>read(body, "$.detail").startsWith("Zu viele Versuche. Bitte versuche es in"), body);
        assertEquals(429, JsonPath.<Integer>read(body, "$.status"));
    }

    @Test
    void anotherAddressIsNotAffected() throws Exception {
        for (int i = 0; i < 4; i++) {
            loginStatus(ip(1), max, PASSWORD);
        }

        assertEquals(429, loginStatus(ip(1), max, PASSWORD));
        assertEquals(200, loginStatus(ip(2), max, PASSWORD));
    }

    @Test
    void theRefusalHappensBeforeThePasswordIsChecked() throws Exception {
        for (int i = 0; i < 3; i++) {
            loginStatus(ip(1), anna, PASSWORD);
        }

        // Auch das richtige Passwort kommt nicht durch, solange die Adresse gesperrt ist
        assertEquals(429, loginStatus(ip(1), anna, PASSWORD));
    }

    @Test
    void otherCallsFromABlockedAddressStillWork() throws Exception {
        for (int i = 0; i < 4; i++) {
            loginStatus(ip(1), max, PASSWORD);
        }

        assertEquals(401, mockMvc.perform(get("/api/auth/me").with(from(ip(1)))).andReturn().getResponse().getStatus());
        assertEquals(410, mockMvc.perform(get("/api/invitations/gibt-es-nicht").with(from(ip(1)))).andReturn().getResponse().getStatus());
    }

    // --- Fehlversuche pro Benutzername ---

    @Test
    void wrongPasswordsLockTheUsernameEvenWhenTheyComeFromManyAddresses() throws Exception {
        assertEquals(401, loginStatus(ip(1), max, "falsch1"));
        assertEquals(401, loginStatus(ip(2), max, "falsch2"));
        assertEquals(401, loginStatus(ip(3), max, "falsch3"));

        // Jetzt ist der Name gesperrt, auch für eine neue Adresse und mit dem richtigen Passwort
        MvcResult locked = login(ip(4), max, PASSWORD);

        assertEquals(429, locked.getResponse().getStatus());
        assertNotNull(locked.getResponse().getHeader("Retry-After"));
        assertTrue(JsonPath.<String>read(locked.getResponse().getContentAsString(), "$.detail").contains("Zu viele Versuche"));
    }

    @Test
    void theLockOfOneUsernameDoesNotTouchAnother() throws Exception {
        for (int i = 1; i <= 3; i++) {
            loginStatus(ip(i), max, "falsch");
        }

        assertEquals(429, loginStatus(ip(9), max, PASSWORD));
        assertEquals(200, loginStatus(ip(9), anna, PASSWORD));
    }

    @Test
    void theLockIgnoresCaseAndSpacesAroundTheUsername() throws Exception {
        loginStatus(ip(1), max, "falsch");
        loginStatus(ip(2), max.toUpperCase(), "falsch");
        loginStatus(ip(3), " " + max.toLowerCase() + " ", "falsch");

        assertEquals(429, loginStatus(ip(4), max, PASSWORD));
    }

    @Test
    void unknownNamesAreCountedLikeKnownOnesSoTheLockRevealsNothing() throws Exception {
        String nobody = "Niemand" + testNumber;
        for (int i = 1; i <= 3; i++) {
            assertEquals(401, loginStatus(ip(i), nobody, "falsch"));
        }

        assertEquals(429, loginStatus(ip(9), nobody, "falsch"));
    }

    @Test
    void aSuccessfulLoginStartsTheCountAgain() throws Exception {
        assertEquals(401, loginStatus(ip(1), max, "falsch"));
        assertEquals(401, loginStatus(ip(2), max, "falsch"));
        assertEquals(200, loginStatus(ip(3), max, PASSWORD));

        assertEquals(401, loginStatus(ip(4), max, "falsch"));
        assertEquals(401, loginStatus(ip(5), max, "falsch"));
        // Ohne den Erfolg dazwischen wären es jetzt vier Fehlversuche; so sind es erst zwei
        assertEquals(200, loginStatus(ip(6), max, PASSWORD));
    }

    // --- Registrierung und Beitritt ---

    @Test
    void registrationsFromOneAddressAreLimited() throws Exception {
        assertEquals(201, registerStatus(ip(1), "Neu1_" + testNumber));
        assertEquals(201, registerStatus(ip(1), "Neu2_" + testNumber));

        assertEquals(429, registerStatus(ip(1), "Neu3_" + testNumber));
        assertEquals(201, registerStatus(ip(2), "Neu3_" + testNumber));
    }

    @Test
    void aRefusedRegistrationCreatesNoAccount() throws Exception {
        registerStatus(ip(1), "Neu1_" + testNumber);
        registerStatus(ip(1), "Neu2_" + testNumber);

        assertEquals(429, registerStatus(ip(1), "Neu3_" + testNumber));

        assertFalse(userRepository.findByUsernameIgnoreCase("Neu3_" + testNumber).isPresent());
    }

    @Test
    void joiningWithInvitationLinksIsLimitedPerAddress() throws Exception {
        MockHttpSession maxSession = sessionOf(ip(1), max);
        long group = createGroupFrom(maxSession, "Kumpels");
        String token = createInvitationFrom(maxSession, group);
        MockHttpSession annaSession = sessionOf(ip(2), anna);

        // Der erste Versuch tritt bei, der zweite wird als "schon Mitglied" abgelehnt; beide zählen
        assertEquals(201, acceptStatus(ip(9), annaSession, token));
        assertEquals(409, acceptStatus(ip(9), annaSession, token));
        assertEquals(429, acceptStatus(ip(9), annaSession, token));
        // Von einer anderen Adresse geht es wieder
        assertEquals(409, acceptStatus(ip(10), annaSession, token));
    }

    // --- Helfer für den Beitritt ---

    private MockHttpSession sessionOf(String address, String username) throws Exception {
        MvcResult result = login(address, username, PASSWORD);
        assertEquals(200, result.getResponse().getStatus());
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long createGroupFrom(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/groups").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", name)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus());
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String createInvitationFrom(MockHttpSession session, long group) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/groups/" + group + "/invitations").with(csrf()).session(session))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus());
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private int acceptStatus(String address, MockHttpSession session, String token) throws Exception {
        return mockMvc.perform(post("/api/invitations/" + token + "/accept").with(csrf()).with(from(address)).session(session))
                .andReturn().getResponse().getStatus();
    }
}
