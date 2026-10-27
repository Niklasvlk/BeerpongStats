package de.niklasvoelker.beerpongstats.ratelimit;

import java.time.Clock;
import java.time.Duration;

/** Alle Limiter der Anwendung an einem Ort, damit Filter und Controller denselben Zustand benutzen. */
public class RateLimiters {

    private final boolean enabled;
    private final SlidingWindowLimiter login;
    private final SlidingWindowLimiter register;
    private final SlidingWindowLimiter join;
    private final SlidingWindowLimiter failedLogins;

    public RateLimiters(RateLimitProperties properties, Clock clock) {
        this.enabled = properties.enabled();
        this.login = new SlidingWindowLimiter(properties.loginPerMinute(), Duration.ofMinutes(1), clock);
        this.register = new SlidingWindowLimiter(properties.registerPerHour(), Duration.ofHours(1), clock);
        this.join = new SlidingWindowLimiter(properties.joinPerMinute(), Duration.ofMinutes(1), clock);
        this.failedLogins = new SlidingWindowLimiter(properties.failedLoginsPerUsername(), Duration.ofMinutes(15), clock);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Login-Versuche pro Client-Adresse. */
    public SlidingWindowLimiter login() {
        return login;
    }

    /** Registrierungen pro Client-Adresse. */
    public SlidingWindowLimiter register() {
        return register;
    }

    /** Beitritte über Einladungslinks pro Client-Adresse. */
    public SlidingWindowLimiter join() {
        return join;
    }

    /** Falsche Passwörter pro Benutzername, unabhängig von der Adresse. */
    public SlidingWindowLimiter failedLogins() {
        return failedLogins;
    }
}
