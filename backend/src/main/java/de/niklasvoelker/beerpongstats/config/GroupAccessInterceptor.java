package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Lässt Anfragen unter /api/groups/{groupId}/... nur für Mitglieder dieser Gruppe durch. Die Prüfung hängt am Pfad
 * und nicht an den einzelnen Endpoints, damit ein neuer Endpoint sie nicht vergessen kann.
 */
public class GroupAccessInterceptor implements HandlerInterceptor {

    static final String PATTERN = "/api/groups/{groupId}/**";

    private static final PathPattern GROUP_PATH = PathPatternParser.defaultInstance.parse(PATTERN);

    private final GroupAccess groupAccess;

    public GroupAccessInterceptor(GroupAccess groupAccess) {
        this.groupAccess = groupAccess;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long groupId = groupId(request);
        if (groupId == null) {
            throw new GroupNotFoundException();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails user)) {
            // Die Security-Kette lässt anonyme Anfragen unter /api gar nicht erst durch
            throw new GroupNotFoundException();
        }

        groupAccess.requireMember(groupId, user.getId());
        return true;
    }

    // Die Gruppen-ID aus dem Pfad, null wenn sie fehlt oder keine Zahl ist
    private static Long groupId(HttpServletRequest request) {
        RequestPath path = ServletRequestPathUtils.getParsedRequestPath(request);
        PathPattern.PathMatchInfo match = GROUP_PATH.matchAndExtract(PathContainer.parsePath(path.pathWithinApplication().value()));
        if (match == null) {
            return null;
        }
        try {
            return Long.parseLong(match.getUriVariables().get("groupId"));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
