package de.winniepat.smpwrapped.model;

import java.util.*;


public record WrappedSnapshot(
    UUID playerId,
    String seasonId,
    String playerName,
    long generatedAt,
    String archetype,
    String archetypeDescription,
    Map<String, Object> stats,
    List<String> achievements,
    Map<String, Double> percentiles,
    List<String> milestones
) {
    public WrappedSnapshot {
        stats = Collections.unmodifiableMap(new LinkedHashMap<>(stats));
        achievements = Collections.unmodifiableList(new ArrayList<>(achievements));
        percentiles = Collections.unmodifiableMap(new LinkedHashMap<>(percentiles));
        milestones = Collections.unmodifiableList(new ArrayList<>(milestones));
    }
}
