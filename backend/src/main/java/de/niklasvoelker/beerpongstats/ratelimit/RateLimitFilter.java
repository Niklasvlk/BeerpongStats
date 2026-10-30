package de.niklasvoelker.beerpongstats.ratelimit;

import de.niklasvoelker.beerpongstats.exception.TooManyRequestsException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Begrenzt, wie oft ein Client Login, Registrierung und Beitritt aufrufen darf. Der Filter sitzt vor der Prüfung von
 * Passwörtern, abgewiesene Anfragen kosten also fast nichts.
 *
 * <p>Der Client ist die Adresse, die der Server sieht. Hinter einem Proxy ist das nur dann die des Besuchers, wenn der
 * Server die weitergeleiteten Header auswertet (server.forward-headers-strategy=native). Sonst sähe er alle Besucher
 * unter der Adresse des Proxys und würde sie gemeinsam begrenzen.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiters limiters;

    public RateLimitFilter(RateLimiters limiters) {
        this.limiters = limiters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SlidingWindowLimiter limiter = limiters.enabled() ? limiterFor(request) : null;
        if (limiter != null) {
            String client = ClientKey.of(request.getRemoteAddr());
            if (!limiter.tryAcquire(client)) {
                reject(response, limiter.retryAfterSeconds(client));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private SlidingWindowLimiter limiterFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        // Servlet-Pfad plus Pfadrest: Tomcat liefert hier den dekodierten und normalisierten Pfad, auch wenn jemand
        // /api/auth/%6Cogin schreibt. Je nach Servlet-Zuordnung steht er im einen oder im anderen Teil.
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        return switch (path) {
            case "/api/auth/login" -> limiters.login();
            case "/api/auth/register" -> limiters.register();
            default -> path.matches("/api/invitations/[^/]+/accept") ? limiters.join() : null;
        };
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json");
        // Dieselbe Form wie alle anderen Fehler der API (ProblemDetail), damit das Frontend die Meldung anzeigt
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\""
                + TooManyRequestsException.message(retryAfterSeconds) + "\"}");
    }
}
