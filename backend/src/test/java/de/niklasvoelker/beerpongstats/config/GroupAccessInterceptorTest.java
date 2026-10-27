package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GroupAccessInterceptorTest {

    private final GroupAccess groupAccess = mock(GroupAccess.class);
    private final GroupAccessInterceptor interceptor = new GroupAccessInterceptor(groupAccess);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    private void signIn(Long userId) {
        AppUserDetails user = new AppUserDetails(userId, "Max", "x");
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    @Test
    void shouldCheckTheMembershipOfTheGroupInThePath() {
        signIn(7L);

        assertTrue(interceptor.preHandle(request("/api/groups/5/matches/recent"), new MockHttpServletResponse(), new Object()));

        verify(groupAccess).requireMember(5L, 7L);
    }

    @Test
    void shouldAlsoCheckTheGroupItself() {
        signIn(7L);

        assertTrue(interceptor.preHandle(request("/api/groups/5"), new MockHttpServletResponse(), new Object()));

        verify(groupAccess).requireMember(5L, 7L);
    }

    @Test
    void shouldRejectANonMember() {
        signIn(7L);
        doThrow(new GroupNotFoundException()).when(groupAccess).requireMember(5L, 7L);

        assertThrows(GroupNotFoundException.class,
                () -> interceptor.preHandle(request("/api/groups/5/players"), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldRejectAGroupIdThatIsNotANumber() {
        signIn(7L);

        assertThrows(GroupNotFoundException.class,
                () -> interceptor.preHandle(request("/api/groups/abc/players"), new MockHttpServletResponse(), new Object()));

        verifyNoInteractions(groupAccess);
    }

    @Test
    void shouldRejectARequestWithoutASignedInUser() {
        assertThrows(GroupNotFoundException.class,
                () -> interceptor.preHandle(request("/api/groups/5/players"), new MockHttpServletResponse(), new Object()));

        verifyNoInteractions(groupAccess);
    }

    @Test
    void shouldRejectAPathWithoutAGroupId() {
        signIn(7L);

        assertThrows(GroupNotFoundException.class,
                () -> interceptor.preHandle(request("/api/groups"), new MockHttpServletResponse(), new Object()));

        verifyNoInteractions(groupAccess);
    }
}
