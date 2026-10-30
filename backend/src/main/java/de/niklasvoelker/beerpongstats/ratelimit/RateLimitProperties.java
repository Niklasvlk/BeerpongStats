package de.niklasvoelker.beerpongstats.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Grenzen gegen das Durchprobieren von Passwörtern und gegen massenhaftes Anlegen von Accounts, einstellbar über
 * beerpong.rate-limit.* in application.properties.
 *
 * <p>Die Grenzen pro Adresse sind bewusst großzügig: Auf einer Party sitzen alle im selben WLAN, und im Mobilfunk teilen
 * sich oft viele Leute eine Adresse. Dabei zählt auch die Registrierung doppelt, denn sie loggt gleich ein. Das gezielte
 * Raten gegen einen Account bremst ohnehin die Sperre pro Benutzername, unabhängig von der Adresse.
 *
 * @param enabled                 schaltet alles ab (nur für Tests gedacht)
 * @param loginPerMinute          Login-Versuche pro Minute und Client-Adresse
 * @param registerPerHour         Registrierungen pro Stunde und Client-Adresse
 * @param joinPerMinute           Beitritte über Einladungslinks pro Minute und Client-Adresse
 * @param failedLoginsPerUsername falsche Passwörter pro 15 Minuten und Benutzername, egal von welcher Adresse
 */
@ConfigurationProperties(prefix = "beerpong.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int loginPerMinute,
        @DefaultValue("40") int registerPerHour,
        @DefaultValue("20") int joinPerMinute,
        @DefaultValue("10") int failedLoginsPerUsername
) {
}
