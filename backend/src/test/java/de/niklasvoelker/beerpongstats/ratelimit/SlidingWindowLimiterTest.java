package de.niklasvoelker.beerpongstats.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final SlidingWindowLimiter limiter = new SlidingWindowLimiter(2, Duration.ofSeconds(60), clock);

    @Test
    void allowsUpToTheLimitAndThenBlocks() {
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void keysDoNotInfluenceEachOther() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void theWindowSlidesInsteadOfResettingAllAtOnce() {
        assertTrue(limiter.tryAcquire("a"));          // t = 0
        clock.advance(Duration.ofSeconds(30));
        assertTrue(limiter.tryAcquire("a"));          // t = 30
        clock.advance(Duration.ofSeconds(10));
        assertFalse(limiter.tryAcquire("a"));         // t = 40: beide Treffer zählen noch

        clock.advance(Duration.ofSeconds(21));        // t = 61: der erste ist draußen, der zweite nicht
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void anHitExactlyOneWindowOldNoLongerCounts() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        clock.advance(Duration.ofSeconds(60));

        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void blockedAttemptsAreNotCountedAndDoNotExtendTheBlock() {
        limiter.tryAcquire("a");                      // t = 0
        limiter.tryAcquire("a");
        for (int i = 0; i < 20; i++) {
            clock.advance(Duration.ofSeconds(2));
            assertFalse(limiter.tryAcquire("a"));     // bis t = 40, alle abgewiesen
        }

        clock.advance(Duration.ofSeconds(21));        // t = 61

        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void retryAfterIsTheTimeUntilTheOldestHitLeavesTheWindow() {
        limiter.tryAcquire("a");                      // t = 0
        clock.advance(Duration.ofSeconds(10));
        limiter.tryAcquire("a");                      // t = 10
        clock.advance(Duration.ofSeconds(10));        // t = 20

        assertEquals(40, limiter.retryAfterSeconds("a"));
    }

    @Test
    void retryAfterRoundsUpSoThatComingBackExactlyThenWorks() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMillis(20_500));

        long wait = limiter.retryAfterSeconds("a");

        assertEquals(40, wait);
        clock.advance(Duration.ofSeconds(wait));
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void retryAfterIsZeroWhenNotBlocked() {
        assertEquals(0, limiter.retryAfterSeconds("unknown"));
        limiter.tryAcquire("a");
        assertEquals(0, limiter.retryAfterSeconds("a"));
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMillis(59_900));

        assertEquals(1, limiter.retryAfterSeconds("a"));
    }

    @Test
    void isLimitedLooksWithoutCounting() {
        assertFalse(limiter.isLimited("a"));
        assertFalse(limiter.isLimited("a"));
        assertFalse(limiter.isLimited("a"));

        limiter.record("a");
        assertFalse(limiter.isLimited("a"));
        limiter.record("a");
        assertTrue(limiter.isLimited("a"));
        assertTrue(limiter.isLimited("a"));
    }

    @Test
    void isLimitedBecomesFalseAgainAfterTheWindow() {
        limiter.record("a");
        limiter.record("a");
        assertTrue(limiter.isLimited("a"));

        clock.advance(Duration.ofSeconds(61));

        assertFalse(limiter.isLimited("a"));
        assertEquals(0, limiter.trackedKeys());
    }

    @Test
    void resetForgetsEverythingOfAKey() {
        limiter.record("a");
        limiter.record("a");

        limiter.reset("a");

        assertFalse(limiter.isLimited("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void recordingFarBeyondTheLimitStaysBoundedAndKeepsTheOriginalBlockTime() {
        for (int i = 0; i < 1000; i++) {
            limiter.record("a");
        }
        clock.advance(Duration.ofSeconds(20));

        assertEquals(40, limiter.retryAfterSeconds("a"));
    }

    @Test
    void oldKeysAreCleanedUpWhenThereAreVeryMany() {
        for (int i = 0; i < 10_001; i++) {
            limiter.tryAcquire("client-" + i);
        }
        assertEquals(10_001, limiter.trackedKeys());

        clock.advance(Duration.ofMinutes(5));
        limiter.tryAcquire("late");

        assertEquals(1, limiter.trackedKeys());
    }

    @Test
    void theLimitIsRequiredToBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(0, Duration.ofSeconds(1), clock));
    }

    @Test
    void parallelRequestsNeverExceedTheLimit() throws Exception {
        SlidingWindowLimiter strict = new SlidingWindowLimiter(10, Duration.ofMinutes(1), clock);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return strict.tryAcquire("same");
            }));
        }
        int allowed = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                allowed++;
            }
        }
        pool.shutdown();

        assertEquals(10, allowed);
    }
}
