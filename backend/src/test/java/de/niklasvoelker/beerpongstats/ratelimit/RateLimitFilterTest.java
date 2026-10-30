package de.niklasvoelker.beerpongstats.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private final MutableClock clock = new MutableClock();
    // 2 Login-Versuche pro Minute, 2 Registrierungen pro Stunde, 2 Beitritte pro Minute
    private final RateLimiters limiters = new RateLimiters(new RateLimitProperties(true, 2, 2, 2, 10), clock);
    private final RateLimitFilter filter = new RateLimitFilter(limiters);

    private MockHttpServletRequest request(String method, String servletPath, String pathInfo, String address) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, servletPath + (pathInfo == null ? "" : pathInfo));
        request.setServletPath(servletPath);
        request.setPathInfo(pathInfo);
        request.setRemoteAddr(address);
        return request;
    }

    // Gibt die Antwort zurück; chain.getRequest() ist null, wenn der Filter die Anfrage abgewiesen hat
    private MockHttpServletResponse send(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private boolean passes(String method, String path, String address) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = send(request(method, path, null, address), chain);
        return chain.getRequest() != null && response.getStatus() != 429;
    }

    @Test
    void letsLoginsThroughUpToTheLimitAndThenRefusesWith429() throws Exception {
        assertTrue(passes("POST", "/api/auth/login", "1.1.1.1"));
        assertTrue(passes("POST", "/api/auth/login", "1.1.1.1"));

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = send(request("POST", "/api/auth/login", null, "1.1.1.1"), chain);

        assertEquals(429, response.getStatus());
        assertNull(chain.getRequest(), "Die abgewiesene Anfrage darf nicht weitergereicht werden");
    }

    @Test
    void theRefusalLooksLikeEveryOtherErrorOfTheApi() throws Exception {
        passes("POST", "/api/auth/login", "1.1.1.1");
        passes("POST", "/api/auth/login", "1.1.1.1");

        MockHttpServletResponse response = send(request("POST", "/api/auth/login", null, "1.1.1.1"), new MockFilterChain());

        assertEquals("application/problem+json;charset=UTF-8", response.getContentType());
        assertEquals("60", response.getHeader("Retry-After"));
        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":429"), body);
        assertTrue(body.contains("\"title\":\"Too Many Requests\""), body);
        assertTrue(body.contains("\"detail\":\"Zu viele Versuche. Bitte versuche es in 60 Sekunden erneut.\""), body);
    }

    @Test
    void retryAfterShrinksWhileTimePasses() throws Exception {
        passes("POST", "/api/auth/login", "1.1.1.1");
        passes("POST", "/api/auth/login", "1.1.1.1");
        clock.advance(Duration.ofSeconds(45));

        MockHttpServletResponse response = send(request("POST", "/api/auth/login", null, "1.1.1.1"), new MockFilterChain());

        assertEquals("15", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("in 15 Sekunden"));
    }

    @Test
    void allowsAgainAfterTheWindow() throws Exception {
        passes("POST", "/api/auth/login", "1.1.1.1");
        passes("POST", "/api/auth/login", "1.1.1.1");
        assertFalse(passes("POST", "/api/auth/login", "1.1.1.1"));

        clock.advance(Duration.ofSeconds(61));

        assertTrue(passes("POST", "/api/auth/login", "1.1.1.1"));
    }

    @Test
    void everyAddressHasItsOwnLimit() throws Exception {
        passes("POST", "/api/auth/login", "1.1.1.1");
        passes("POST", "/api/auth/login", "1.1.1.1");

        assertFalse(passes("POST", "/api/auth/login", "1.1.1.1"));
        assertTrue(passes("POST", "/api/auth/login", "2.2.2.2"));
    }

    @Test
    void ipv6AddressesOfOneNetworkShareTheLimit() throws Exception {
        passes("POST", "/api/auth/login", "2001:db8:1:2::1");
        passes("POST", "/api/auth/login", "2001:db8:1:2::2");

        assertFalse(passes("POST", "/api/auth/login", "2001:db8:1:2:ffff::9"));
    }

    @Test
    void loginRegisterAndJoinHaveSeparateLimits() throws Exception {
        passes("POST", "/api/auth/login", "1.1.1.1");
        passes("POST", "/api/auth/login", "1.1.1.1");
        assertFalse(passes("POST", "/api/auth/login", "1.1.1.1"));

        assertTrue(passes("POST", "/api/auth/register", "1.1.1.1"));
        assertTrue(passes("POST", "/api/invitations/abc/accept", "1.1.1.1"));
    }

    @Test
    void registrationIsLimited() throws Exception {
        assertTrue(passes("POST", "/api/auth/register", "1.1.1.1"));
        assertTrue(passes("POST", "/api/auth/register", "1.1.1.1"));
        assertFalse(passes("POST", "/api/auth/register", "1.1.1.1"));
    }

    @Test
    void theRegistrationLimitLastsAnHour() throws Exception {
        passes("POST", "/api/auth/register", "1.1.1.1");
        passes("POST", "/api/auth/register", "1.1.1.1");
        clock.advance(Duration.ofMinutes(59));
        assertFalse(passes("POST", "/api/auth/register", "1.1.1.1"));

        MockHttpServletResponse response = send(request("POST", "/api/auth/register", null, "1.1.1.1"), new MockFilterChain());
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("in 1 Minuten") || response.getContentAsString().contains("in 60 Sekunden"));

        clock.advance(Duration.ofMinutes(2));
        assertTrue(passes("POST", "/api/auth/register", "1.1.1.1"));
    }

    @Test
    void joiningWithAnInvitationLinkIsLimited() throws Exception {
        assertTrue(passes("POST", "/api/invitations/abc/accept", "1.1.1.1"));
        assertTrue(passes("POST", "/api/invitations/xyz/accept", "1.1.1.1"));
        assertFalse(passes("POST", "/api/invitations/another/accept", "1.1.1.1"));
    }

    @Test
    void onlyThePostsOfThoseThreeEndpointsAreCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertTrue(passes("GET", "/api/auth/login", "1.1.1.1"));
            assertTrue(passes("GET", "/api/invitations/abc", "1.1.1.1"));
            assertTrue(passes("GET", "/api/groups", "1.1.1.1"));
            assertTrue(passes("POST", "/api/groups", "1.1.1.1"));
            assertTrue(passes("POST", "/api/auth/logout", "1.1.1.1"));
            assertTrue(passes("POST", "/api/invitations/abc/accept/more", "1.1.1.1"));
            assertTrue(passes("POST", "/api/invitations/a/b/accept", "1.1.1.1"));
            assertTrue(passes("POST", "/index.html", "1.1.1.1"));
        }
    }

    @Test
    void theLimitAppliesWhenThePathArrivesInThePathInfo() throws Exception {
        // So füllt MockMvc und mancher Servlet-Container die Pfadteile
        for (int i = 0; i < 2; i++) {
            send(request("POST", "", "/api/auth/login", "1.1.1.1"), new MockFilterChain());
        }

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = send(request("POST", "", "/api/auth/login", "1.1.1.1"), chain);

        assertEquals(429, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void theOtherCallsPassTheChainUntouched() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = send(request("GET", "/api/groups", null, "1.1.1.1"), chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
    }

    @Test
    void nothingIsLimitedWhenSwitchedOff() throws Exception {
        RateLimitFilter off = new RateLimitFilter(new RateLimiters(new RateLimitProperties(false, 1, 1, 1, 1), clock));

        for (int i = 0; i < 50; i++) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();
            off.doFilter(request("POST", "/api/auth/login", null, "1.1.1.1"), response, chain);

            assertNotNull(chain.getRequest());
            assertNotEquals(429, response.getStatus());
        }
    }

    @Test
    void aRefusedRequestStillRunsTheRestOfTheChainNever() throws Exception {
        passes("POST", "/api/auth/register", "1.1.1.1");
        passes("POST", "/api/auth/register", "1.1.1.1");
        boolean[] reached = new boolean[1];
        FilterChain chain = (req, res) -> reached[0] = true;

        filter.doFilter(request("POST", "/api/auth/register", null, "1.1.1.1"), new MockHttpServletResponse(), chain);

        assertFalse(reached[0]);
    }
}
