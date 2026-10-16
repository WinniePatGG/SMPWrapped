package de.winniepat.smpwrapped.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorTrackerTest {

    @Test
    @DisplayName("Every level is counted, including when throttling is off")
    void levelsAreCounted() {
        ErrorTracker tracker = new ErrorTracker(true, 1, 60_000L, 0L);

        tracker.decide(LogLevel.INFO, "a", null);
        tracker.decide(LogLevel.WARN, "b", null);
        tracker.decide(LogLevel.ERROR, "c", null);

        assertThat(tracker.snapshot()).containsEntry("INFO", 1L);
        assertThat(tracker.snapshot()).containsEntry("WARN", 1L);
        assertThat(tracker.snapshot()).containsEntry("ERROR", 1L);
    }

    @Test
    @DisplayName("Identical errors log until the threshold, then get suppressed")
    void identicalErrorsAreThrottled() {
        ErrorTracker tracker = new ErrorTracker(true, 2, 60_000L, 60_000L);

        assertThat(tracker.decide(LogLevel.ERROR, "boom", null).shouldLog()).isTrue();
        assertThat(tracker.decide(LogLevel.ERROR, "boom", null).shouldLog()).isTrue();
        assertThat(tracker.decide(LogLevel.ERROR, "boom", null).shouldLog()).isFalse();
        assertThat(tracker.decide(LogLevel.ERROR, "boom", null).shouldLog()).isFalse();

        assertThat(tracker.suppressedTotal()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Different errors are throttled independently")
    void differentErrorsAreIndependent() {
        ErrorTracker tracker = new ErrorTracker(true, 1, 60_000L, 60_000L);

        assertThat(tracker.decide(LogLevel.ERROR, "first", null).shouldLog()).isTrue();
        assertThat(tracker.decide(LogLevel.ERROR, "second", null).shouldLog()).isTrue();
        assertThat(tracker.decide(LogLevel.ERROR, "first", null).shouldLog()).isFalse();
        assertThat(tracker.decide(LogLevel.ERROR, "second", null).shouldLog()).isFalse();
    }

    @Test
    @DisplayName("Dynamic values inside a message do not defeat deduplication")
    void dynamicValuesAreNormalised() {
        ErrorTracker tracker = new ErrorTracker(true, 1, 60_000L, 60_000L);

        assertThat(tracker.decide(LogLevel.ERROR, "save failed for 12345", null).shouldLog()).isTrue();
        assertThat(tracker.decide(LogLevel.ERROR, "save failed for 67890", null).shouldLog()).isFalse();
        assertThat(tracker.decide(LogLevel.ERROR, "save failed for 11111", null).shouldLog()).isFalse();
    }

    @Test
    @DisplayName("Long uuid values are normalised too")
    void uuidsAreNormalised() {
        ErrorTracker tracker = new ErrorTracker(true, 1, 60_000L, 60_000L);

        assertThat(tracker.decide(LogLevel.ERROR, "player a3f1b2c4d5e6f7a8 failed", null).shouldLog()).isTrue();
        assertThat(tracker.decide(LogLevel.ERROR, "player 1122334455667788 failed", null).shouldLog()).isFalse();
    }

    @Test
    @DisplayName("After the throttle window elapses the error is logged again")
    void throttledErrorIsLoggedAgain() throws InterruptedException {
        ErrorTracker tracker = new ErrorTracker(true, 1, 60_000L, 1L);

        assertThat(tracker.decide(LogLevel.ERROR, "flaky", null).shouldLog()).isTrue();
        assertThat(tracker.decide(LogLevel.ERROR, "flaky", null).shouldLog()).isFalse();

        Thread.sleep(15L);

        ErrorTracker.Decision resumed = tracker.decide(LogLevel.ERROR, "flaky", null);
        assertThat(resumed.shouldLog()).isTrue();
        assertThat(resumed.suppressedRepeats()).isEqualTo(1);
        assertThat(tracker.suppressedTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Warnings and info are never throttled")
    void nonErrorLevelsAreNeverThrottled() {
        ErrorTracker tracker = new ErrorTracker(true, 1, 60_000L, 60_000L);

        for (int i = 0; i < 50; i++) {
            assertThat(tracker.decide(LogLevel.WARN, "repeated", null).shouldLog()).isTrue();
            assertThat(tracker.decide(LogLevel.INFO, "repeated", null).shouldLog()).isTrue();
        }
        assertThat(tracker.suppressedTotal()).isZero();
    }

    @Test
    @DisplayName("Deduplication can be switched off entirely")
    void dedupeCanBeDisabled() {
        ErrorTracker tracker = new ErrorTracker(false, 1, 60_000L, 60_000L);

        for (int i = 0; i < 20; i++) {
            assertThat(tracker.decide(LogLevel.ERROR, "boom", null).shouldLog()).isTrue();
        }
        assertThat(tracker.suppressedTotal()).isZero();
    }

    @Test
    @DisplayName("Different exception types are tracked separately")
    void exceptionTypesSeparateSignatures() {
        ErrorTracker tracker = new ErrorTracker(true, 1, 60_000L, 60_000L);

        assertThat(tracker.decide(LogLevel.ERROR, "same message", new IllegalStateException("a")).shouldLog()).isTrue();
        assertThat(tracker.decide(LogLevel.ERROR, "same message", new IllegalArgumentException("b")).shouldLog()).isTrue();
    }

    @Test
    @DisplayName("Different throw sites are tracked separately")
    void throwSitesSeparateSignatures() {
        ErrorTracker tracker = new ErrorTracker(true, 1, 60_000L, 60_000L);

        assertThat(tracker.decide(LogLevel.ERROR, "boom", throwFromHelperOne()).shouldLog()).isTrue();
        assertThat(tracker.decide(LogLevel.ERROR, "boom", throwFromHelperTwo()).shouldLog()).isTrue();
    }

    private RuntimeException throwFromHelperOne() {
        return new RuntimeException("from one");
    }

    private RuntimeException throwFromHelperTwo() {
        return new RuntimeException("from two");
    }

    @Test
    @DisplayName("Stale signatures are purged and reported")
    void staleSignaturesArePurged() {
        ErrorTracker tracker = new ErrorTracker(true, 1, 1_000L, 60_000L);

        tracker.decide(LogLevel.ERROR, "old", null);
        assertThat(tracker.trackedSignatures()).isEqualTo(1);

        tracker.purge();
        assertThat(tracker.trackedSignatures()).isEqualTo(1);
        assertThat(tracker.topSignatures(3)).contains("old");
        assertThat(tracker.topSignatures(0)).isEqualTo("none");
    }
}