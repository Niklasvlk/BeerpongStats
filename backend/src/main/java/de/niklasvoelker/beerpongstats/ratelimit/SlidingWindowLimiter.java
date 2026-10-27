package de.niklasvoelker.beerpongstats.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Erlaubt pro Schlüssel (z. B. Client-Adresse oder Benutzername) höchstens {@code maxHits} Ereignisse innerhalb des
 * letzten {@code window}. Der Zustand liegt im Speicher: Er gilt nur für diese Instanz und beginnt nach einem Neustart
 * von vorn.
 */
public class SlidingWindowLimiter {

    // Ab dieser Zahl von Schlüsseln werden abgelaufene aufgeräumt; über dem harten Limit wird alles verworfen, damit
    // ein Angreifer mit sehr vielen Adressen den Speicher nicht füllen kann
    private static final int SWEEP_ABOVE = 10_000;
    private static final int HARD_CAP = 100_000;
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    private final int maxHits;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> hits = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep;

    public SlidingWindowLimiter(int maxHits, Duration window, Clock clock) {
        if (maxHits < 1) {
            throw new IllegalArgumentException("Das Limit muss mindestens 1 sein.");
        }
        this.maxHits = maxHits;
        this.window = window;
        this.clock = clock;
        this.lastSweep = new AtomicReference<>(clock.instant());
    }

    /** Zählt ein Ereignis, wenn das Limit noch nicht erreicht ist. {@code false} heißt: gesperrt, nichts gezählt. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        boolean[] allowed = new boolean[1];
        hits.compute(key, (k, deque) -> {
            ArrayDeque<Instant> current = withoutExpired(deque, now);
            if (current.size() < maxHits) {
                current.addLast(now);
                allowed[0] = true;
            }
            return current;
        });
        sweepIfNeeded(now);
        return allowed[0];
    }

    /** Zählt ein Ereignis (z. B. einen Fehlversuch), ohne etwas zu entscheiden. */
    public void record(String key) {
        tryAcquire(key);
    }

    /** Ob der Schlüssel sein Limit erreicht hat, ohne ein Ereignis zu zählen. */
    public boolean isLimited(String key) {
        Instant now = clock.instant();
        boolean[] limited = new boolean[1];
        hits.computeIfPresent(key, (k, deque) -> {
            ArrayDeque<Instant> current = withoutExpired(deque, now);
            limited[0] = current.size() >= maxHits;
            return current.isEmpty() ? null : current;
        });
        return limited[0];
    }

    /** Vergisst alle Ereignisse des Schlüssels (z. B. nach einem erfolgreichen Login). */
    public void reset(String key) {
        hits.remove(key);
    }

    /** Wie viele Sekunden es dauert, bis der Schlüssel wieder darf; 0, wenn er nicht gesperrt ist. */
    public long retryAfterSeconds(String key) {
        Instant now = clock.instant();
        long[] seconds = new long[1];
        hits.computeIfPresent(key, (k, deque) -> {
            ArrayDeque<Instant> current = withoutExpired(deque, now);
            if (current.size() >= maxHits) {
                Duration wait = Duration.between(now, current.peekFirst().plus(window));
                // Aufrunden: wer genau nach dieser Zeit wiederkommt, soll nicht nochmal abgewiesen werden
                seconds[0] = Math.max(1, (wait.toMillis() + 999) / 1000);
            }
            return current.isEmpty() ? null : current;
        });
        return seconds[0];
    }

    /** Für Tests und Überwachung: wie viele Schlüssel gerade im Speicher sind. */
    public int trackedKeys() {
        return hits.size();
    }

    private ArrayDeque<Instant> withoutExpired(ArrayDeque<Instant> deque, Instant now) {
        ArrayDeque<Instant> current = deque == null ? new ArrayDeque<>() : deque;
        Instant cutoff = now.minus(window);
        while (!current.isEmpty() && !current.peekFirst().isAfter(cutoff)) {
            current.removeFirst();
        }
        return current;
    }

    private void sweepIfNeeded(Instant now) {
        if (hits.size() <= SWEEP_ABOVE) {
            return;
        }
        Instant last = lastSweep.get();
        if (Duration.between(last, now).compareTo(SWEEP_INTERVAL) < 0 || !lastSweep.compareAndSet(last, now)) {
            return;
        }
        for (String key : hits.keySet()) {
            hits.computeIfPresent(key, (k, deque) -> {
                ArrayDeque<Instant> current = withoutExpired(deque, now);
                return current.isEmpty() ? null : current;
            });
        }
        if (hits.size() > HARD_CAP) {
            hits.clear();
        }
    }
}
