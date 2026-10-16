package de.winniepat.smpwrapped.model;

import java.util.UUID;


public record TrackedPlayer(
    UUID uuid,
    String lastName,
    long firstSeen,
    long lastSeen,
    boolean optedOut
) {}
