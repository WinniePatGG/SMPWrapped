package de.winniepat.smpwrapped.model;

import java.util.UUID;


public record Session(
    UUID playerId,
    long loginTime,
    long logoutTime,    
    String dimension
) {
    public boolean isActive() {
        return logoutTime < 0;
    }

    public long durationSeconds() {
        long end = isActive() ? System.currentTimeMillis() : logoutTime;
        return Math.max(0, (end - loginTime) / 1000);
    }
}
