package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.*;


public final class ArchetypeEngine {

    private static final SMPLogger LOG = SMPLogger.get("archetype");

    private ArchetypeEngine() {}

    
    public static ArchetypeResult determineArchetype(PlayerStats stats) {
        if (stats == null) {
            LOG.error("Cannot determine an archetype without stats");
            return new ArchetypeResult("The Newcomer",
                "Just getting started on their journey. Every legend begins with a single block.");
        }

        
        boolean hasAnyStats = stats.getBlocksMined() > 0 || stats.getBlocksPlaced() > 0 ||
            stats.getDistanceTraveled() > 0 || stats.getMobsKilled() > 0 ||
            stats.getItemsCollected() > 0 || stats.getItemsCrafted() > 0 ||
            stats.getDeaths() > 0 || stats.getPlaytimeSeconds() > 0;

        if (!hasAnyStats) {
            LOG.trace("Player has no recorded stats yet, assigning The Newcomer");
            return new ArchetypeResult("The Newcomer",
                "Just getting started on their journey. Every legend begins with a single block.");
        }

        
        Map<String, Double> scores = new HashMap<>();

        
        double minerScore = stats.getBlocksMined() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Miner", minerScore);

        
        double explorerScore = stats.getDistanceTraveled() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Explorer", explorerScore);

        
        double builderScore = stats.getBlocksPlaced() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Builder", builderScore);

        
        double hunterScore = stats.getMobsKilled() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Monster Hunter", hunterScore);

        
        double deathRatio = stats.getDeaths() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        double survivorScore = deathRatio < 0.5 ? 10.0 / Math.max(0.1, deathRatio) : 0;
        scores.put("The Survivor", survivorScore);

        
        double collectorScore = (stats.getItemsCollected() + stats.getItemsCrafted()) / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Collector", collectorScore);

        
        Map.Entry<String, Double> best = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);

        if (best == null) {
            LOG.error("No archetype scores could be computed, falling back to The Newcomer",
                LogContext.of("scoresComputed", scores.size()));
            return new ArchetypeResult("The Newcomer",
                "Just getting started on their journey. Every legend begins with a single block.");
        }

        LOG.trace("Determined archetype", LogContext.of(
            "archetype", best.getKey(), "score", best.getValue(), "allScores", scores));
        return new ArchetypeResult(best.getKey(), getDescription(best.getKey()));
    }

    private static String getDescription(String archetype) {
        if (archetype == null) {
            return "A unique player with their own story to tell.";
        }
        switch (archetype) {
            case "The Miner":
                return "Driven by the call of the deep. Every ore vein is a treasure waiting to be uncovered.";
            case "The Explorer":
                return "No cave is too dark, no mountain too high. The world is theirs to discover.";
            case "The Builder":
                return "Turning imagination into reality, one block at a time. A true architect of the world.";
            case "The Monster Hunter":
                return "Fearless in the face of danger. No mob is safe from their blade.";
            case "The Survivor":
                return "Against all odds, they persist. Death is just a minor inconvenience.";
            case "The Collector":
                return "Every item has a place, and they'll find it. A master of organization and gathering.";
            default:
                return "A unique player with their own story to tell.";
        }
    }

    public static final class ArchetypeResult {
        private final String name;
        private final String description;

        public ArchetypeResult(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String name() { return name; }
        public String description() { return description; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ArchetypeResult)) return false;
            ArchetypeResult that = (ArchetypeResult) other;
            return java.util.Objects.equals(name, that.name)
                && java.util.Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, description);
        }

        @Override
        public String toString() {
            return "ArchetypeResult[name=" + name + ", description=" + description + "]";
        }
    }
}
