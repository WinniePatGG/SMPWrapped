package de.winniepat.smpwrapped.model;

import java.util.UUID;
import java.util.Objects;


public final class TrackedPlayer {
    private final UUID uuid;
    private final String lastName;
    private final long firstSeen;
    private final long lastSeen;
    private final boolean optedOut;

    public TrackedPlayer(UUID uuid, String lastName, long firstSeen, long lastSeen, boolean optedOut) {
        this.uuid = uuid;
        this.lastName = lastName;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.optedOut = optedOut;
    }

    public UUID uuid() { return uuid; }
    public String lastName() { return lastName; }
    public long firstSeen() { return firstSeen; }
    public long lastSeen() { return lastSeen; }
    public boolean optedOut() { return optedOut; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TrackedPlayer)) return false;
        TrackedPlayer that = (TrackedPlayer) other;
        return firstSeen == that.firstSeen
            && lastSeen == that.lastSeen
            && optedOut == that.optedOut
            && Objects.equals(uuid, that.uuid)
            && Objects.equals(lastName, that.lastName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, lastName, firstSeen, lastSeen, optedOut);
    }

    @Override
    public String toString() {
        return "TrackedPlayer[uuid=" + uuid + ", lastName=" + lastName
            + ", firstSeen=" + firstSeen + ", lastSeen=" + lastSeen
            + ", optedOut=" + optedOut + "]";
    }
}