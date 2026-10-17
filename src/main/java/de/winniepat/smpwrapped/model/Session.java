package de.winniepat.smpwrapped.model;

import java.util.UUID;


public final class Session {
    private final UUID playerId;
    private final long loginTime;
    private final long logoutTime;
    private final String dimension;

    public Session(UUID playerId, long loginTime, long logoutTime, String dimension) {
        this.playerId = playerId;
        this.loginTime = loginTime;
        this.logoutTime = logoutTime;
        this.dimension = dimension;
    }

    public UUID playerId() { return playerId; }
    public long loginTime() { return loginTime; }
    public long logoutTime() { return logoutTime; }
    public String dimension() { return dimension; }

    public boolean isActive() {
        return logoutTime < 0;
    }

    public long durationSeconds() {
        long end = isActive() ? System.currentTimeMillis() : logoutTime;
        return Math.max(0, (end - loginTime) / 1000);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Session)) return false;
        Session that = (Session) other;
        return loginTime == that.loginTime
            && logoutTime == that.logoutTime
            && java.util.Objects.equals(playerId, that.playerId)
            && java.util.Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(playerId, loginTime, logoutTime, dimension);
    }

    @Override
    public String toString() {
        return "Session[playerId=" + playerId + ", loginTime=" + loginTime
            + ", logoutTime=" + logoutTime + ", dimension=" + dimension + "]";
    }
}