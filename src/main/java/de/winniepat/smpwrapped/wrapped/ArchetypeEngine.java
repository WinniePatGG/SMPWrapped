package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.*;

/**
 * Deterministic archetype selection based on player statistics.
 * Rules are transparent and based on actual data.
 */
public final class ArchetypeEngine {

    private ArchetypeEngine() {}

    /**
     * Determine the player's archetype based on their stats.
     * Returns the archetype name and description.
     */
    public static ArchetypeResult determineArchetype(PlayerStats stats) {
        // Check if player has any meaningful stats
        boolean hasAnyStats = stats.getBlocksMined() > 0 || stats.getBlocksPlaced() > 0 ||
            stats.getDistanceTraveled() > 0 || stats.getMobsKilled() > 0 ||
            stats.getItemsCollected() > 0 || stats.getItemsCrafted() > 0 ||
            stats.getDeaths() > 0 || stats.getPlaytimeSeconds() > 0;

        if (!hasAnyStats) {
            return new ArchetypeResult("The Newcomer",
                "Just getting started on their journey. Every legend begins with a single block.");
        }

        // Calculate scores for each archetype
        Map<String, Double> scores = new HashMap<>();

        // The Miner: high blocks mined relative to playtime
        double minerScore = stats.getBlocksMined() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Miner", minerScore);

        // The Explorer: high distance traveled
        double explorerScore = stats.getDistanceTraveled() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Explorer", explorerScore);

        // The Builder: high blocks placed
        double builderScore = stats.getBlocksPlaced() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Builder", builderScore);

        // The Monster Hunter: high mobs killed
        double hunterScore = stats.getMobsKilled() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Monster Hunter", hunterScore);

        // The Survivor: low death ratio (deaths per hour)
        double deathRatio = stats.getDeaths() / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        double survivorScore = deathRatio < 0.5 ? 10.0 / Math.max(0.1, deathRatio) : 0;
        scores.put("The Survivor", survivorScore);

        // The Collector: high items collected/crafted
        double collectorScore = (stats.getItemsCollected() + stats.getItemsCrafted()) / Math.max(1, stats.getPlaytimeSeconds() / 3600.0);
        scores.put("The Collector", collectorScore);

        // Find the highest scoring archetype
        Map.Entry<String, Double> best = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElseThrow();

        return new ArchetypeResult(best.getKey(), getDescription(best.getKey()));
    }

    private static String getDescription(String archetype) {
        return switch (archetype) {
            case "The Miner" -> "Driven by the call of the deep. Every ore vein is a treasure waiting to be uncovered.";
            case "The Explorer" -> "No cave is too dark, no mountain too high. The world is theirs to discover.";
            case "The Builder" -> "Turning imagination into reality, one block at a time. A true architect of the world.";
            case "The Monster Hunter" -> "Fearless in the face of danger. No mob is safe from their blade.";
            case "The Survivor" -> "Against all odds, they persist. Death is just a minor inconvenience.";
            case "The Collector" -> "Every item has a place, and they'll find it. A master of organization and gathering.";
            default -> "A unique player with their own story to tell.";
        };
    }

    public record ArchetypeResult(String name, String description) {}
}
