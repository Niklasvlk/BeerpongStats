package de.niklasvoelker.beerpongstats.exception;

public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(message(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    // Der Text, den auch der Rate-Limit-Filter ausliefert
    public static String message(long retryAfterSeconds) {
        String wait = retryAfterSeconds < 120
                ? retryAfterSeconds + (retryAfterSeconds == 1 ? " Sekunde" : " Sekunden")
                : ((retryAfterSeconds + 59) / 60) + " Minuten";
        return "Zu viele Versuche. Bitte versuche es in " + wait + " erneut.";
    }
}
