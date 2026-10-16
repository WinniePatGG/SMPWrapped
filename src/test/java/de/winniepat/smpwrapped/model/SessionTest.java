package de.winniepat.smpwrapped.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

class SessionTest {

    @Test
    @DisplayName("Should calculate duration correctly for ended session")
    void testEndedSessionDuration() {
        long loginTime = 1000000L;
        long logoutTime = 1003600L; // 3.6 seconds later
        Session session = new Session(UUID.randomUUID(), loginTime, logoutTime, "OVERWORLD");

        assertThat(session.durationSeconds()).isEqualTo(3);
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should identify active session correctly")
    void testActiveSession() {
        Session session = new Session(UUID.randomUUID(), System.currentTimeMillis(), -1, "OVERWORLD");
        assertThat(session.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should return zero duration for same login/logout time")
    void testZeroDuration() {
        Session session = new Session(UUID.randomUUID(), 1000L, 1000L, "OVERWORLD");
        assertThat(session.durationSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle negative duration gracefully")
    void testNegativeDuration() {
        Session session = new Session(UUID.randomUUID(), 2000L, 1000L, "OVERWORLD");
        assertThat(session.durationSeconds()).isEqualTo(0);
    }
}
