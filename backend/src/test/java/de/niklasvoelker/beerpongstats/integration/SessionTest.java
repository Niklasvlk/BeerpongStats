package de.niklasvoelker.beerpongstats.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Anmelden, Abmelden und Registrieren so, wie der Browser es mit dem CSRF-Cookie tut: Das Cookie wird aus jeder Antwort
 * übernommen und beim nächsten POST als Header zurückgeschickt (so macht es Angular).
 *
 * <p>Die Test-Abkürzung csrf() wird hier bewusst nicht benutzt. Sie tauscht den Token-Speicher im CSRF-Filter aus und
 * würde das echte Cookie-Verhalten verdecken. Die Property gibt dieser Klasse einen eigenen Spring-Kontext, damit
 * csrf() aus anderen Testklassen den Filter hier nicht verändert.
 */
@TestPropertySource(properties = "beerpong.test.real-csrf-cookie=true")
class SessionTest extends ApiIntegrationTest {

    // Das XSRF-TOKEN-Cookie des "Browsers"; null, solange keines gesetzt ist
    private String csrfToken;

    @BeforeEach
    void freshBrowser() {
        csrfToken = null;
    }

    private MvcResult send(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        if (csrfToken != null) {
            request.cookie(new Cookie("XSRF-TOKEN", csrfToken)).header("X-XSRF-TOKEN", csrfToken);
        }
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        // Set-Cookie-Header werden der Reihe nach angewendet: ein leerer oder abgelaufener Wert löscht das Cookie
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith("XSRF-TOKEN=")) {
                String value = header.substring("XSRF-TOKEN=".length(), header.indexOf(';'));
                csrfToken = value.isEmpty() || header.contains("Max-Age=0") || header.contains("Expires=Thu, 01 Jan 1970")
                        ? null
                        : value;
            }
        }
        return result;
    }

    private MvcResult postJson(String url, String body, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = post(url);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return send(request, session);
    }

    private String credentials() {
        return json("username", "Max", "password", PASSWORD);
    }

    // Die App holt sich beim Start mit GET /api/auth/me das erste CSRF-Cookie
    private void openApp() throws Exception {
        assertEquals(401, send(get("/api/auth/me"), null).getResponse().getStatus());
        assertNotNull(csrfToken, "Der App-Start muss ein CSRF-Cookie bekommen");
    }

    private MockHttpSession registerAndLoginLikeABrowser() throws Exception {
        assertEquals(201, postJson("/api/auth/register", credentials(), null).getResponse().getStatus());
        MvcResult login = postJson("/api/auth/login", credentials(), null);
        assertEquals(200, login.getResponse().getStatus());
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    @Test
    void theAppStartGivesTheFirstCsrfCookie() throws Exception {
        openApp();

        assertEquals(201, postJson("/api/auth/register", credentials(), null).getResponse().getStatus());
    }

    @Test
    void loggingInRightAfterLoggingOutWorks() throws Exception {
        openApp();
        MockHttpSession session = registerAndLoginLikeABrowser();

        assertEquals(204, postJson("/api/auth/logout", null, session).getResponse().getStatus());
        assertNotNull(csrfToken, "Das Abmelden muss ein frisches CSRF-Cookie hinterlassen");

        // Genau das ist beim ersten Versuch nach dem Abmelden früher mit 403 gescheitert
        assertEquals(200, postJson("/api/auth/login", credentials(), null).getResponse().getStatus());
    }

    @Test
    void registeringAndLoggingInRightAfterDeletingTheAccountWork() throws Exception {
        openApp();
        MockHttpSession session = registerAndLoginLikeABrowser();

        assertEquals(204, send(delete("/api/auth/me"), session).getResponse().getStatus());
        assertNotNull(csrfToken, "Das Löschen muss ein frisches CSRF-Cookie hinterlassen");

        assertEquals(201, postJson("/api/auth/register", credentials(), null).getResponse().getStatus());
        assertEquals(200, postJson("/api/auth/login", credentials(), null).getResponse().getStatus());
    }

    @Test
    void aPostWithoutAnyTokenIsStillRefused() throws Exception {
        assertNull(csrfToken);

        assertEquals(403, postJson("/api/auth/register", credentials(), null).getResponse().getStatus());
    }
}
