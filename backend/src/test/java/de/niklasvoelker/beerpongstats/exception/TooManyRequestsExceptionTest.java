package de.niklasvoelker.beerpongstats.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TooManyRequestsExceptionTest {

    @Test
    void saysWaitInSecondsForShortWaits() {
        assertEquals("Zu viele Versuche. Bitte versuche es in 1 Sekunde erneut.", TooManyRequestsException.message(1));
        assertEquals("Zu viele Versuche. Bitte versuche es in 45 Sekunden erneut.", TooManyRequestsException.message(45));
        assertEquals("Zu viele Versuche. Bitte versuche es in 119 Sekunden erneut.", TooManyRequestsException.message(119));
    }

    @Test
    void saysMinutesForLongWaitsAndRoundsUp() {
        assertEquals("Zu viele Versuche. Bitte versuche es in 2 Minuten erneut.", TooManyRequestsException.message(120));
        assertEquals("Zu viele Versuche. Bitte versuche es in 3 Minuten erneut.", TooManyRequestsException.message(121));
        assertEquals("Zu viele Versuche. Bitte versuche es in 60 Minuten erneut.", TooManyRequestsException.message(3600));
    }

    @Test
    void theExceptionCarriesTheWaitingTime() {
        TooManyRequestsException ex = new TooManyRequestsException(30);

        assertEquals(30, ex.getRetryAfterSeconds());
        assertEquals(TooManyRequestsException.message(30), ex.getMessage());
    }
}
