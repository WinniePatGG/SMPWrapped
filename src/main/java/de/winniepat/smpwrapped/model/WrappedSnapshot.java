package de.winniepat.smpwrapped.model;

import java.util.*;


public final class WrappedSnapshot {
    private final UUID playerId;
    private final String seasonId;
    private final String playerName;
    private final long generatedAt;
    private final String archetype;
    private final String archetypeDescription;
    private final Map<String, Object> stats;
    private final List<String> achievements;
    private final Map<String, Double> percentiles;
    private final List<String> milestones;

    public WrappedSnapshot(UUID playerId, String seasonId, String playerName, long generatedAt,
                           String archetype, String archetypeDescription,
                           Map<String, Object> stats, List<String> achievements,
                           Map<String, Double> percentiles, List<String> milestones) {
        this.playerId = playerId;
        this.seasonId = seasonId;
        this.playerName = playerName;
        this.generatedAt = generatedAt;
        this.archetype = archetype;
        this.archetypeDescription = archetypeDescription;
        this.stats = Collections.unmodifiableMap(new LinkedHashMap<>(stats));
        this.achievements = Collections.unmodifiableList(new ArrayList<>(achievements));
        this.percentiles = Collections.unmodifiableMap(new LinkedHashMap<>(percentiles));
        this.milestones = Collections.unmodifiableList(new ArrayList<>(milestones));
    }

    public UUID playerId() { return playerId; }
    public String seasonId() { return seasonId; }
    public String playerName() { return playerName; }
    public long generatedAt() { return generatedAt; }
    public String archetype() { return archetype; }
    public String archetypeDescription() { return archetypeDescription; }
    public Map<String, Object> stats() { return stats; }
    public List<String> achievements() { return achievements; }
    public Map<String, Double> percentiles() { return percentiles; }
    public List<String> milestones() { return milestones; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WrappedSnapshot)) return false;
        WrappedSnapshot that = (WrappedSnapshot) other;
        return generatedAt == that.generatedAt
            && Objects.equals(playerId, that.playerId)
            && Objects.equals(seasonId, that.seasonId)
            && Objects.equals(playerName, that.playerName)
            && Objects.equals(archetype, that.archetype)
            && Objects.equals(archetypeDescription, that.archetypeDescription)
            && Objects.equals(stats, that.stats)
            && Objects.equals(achievements, that.achievements)
            && Objects.equals(percentiles, that.percentiles)
            && Objects.equals(milestones, that.milestones);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, seasonId, playerName, generatedAt, archetype,
            archetypeDescription, stats, achievements, percentiles, milestones);
    }

    @Override
    public String toString() {
        return "WrappedSnapshot[playerId=" + playerId + ", seasonId=" + seasonId
            + ", playerName=" + playerName + ", generatedAt=" + generatedAt
            + ", archetype=" + archetype + ", achievements=" + achievements.size() + "]";
    }
}