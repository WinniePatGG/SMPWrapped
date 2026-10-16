package de.winniepat.smpwrapped.model;

import java.util.UUID;

/**
 * Represents a player tracked by SMPWrapped.
 */
public record TrackedPlayer(
    UUID uuid,
    String lastName,
    long firstSeen,
    long lastSeen,
    boolean optedOut
) {}
