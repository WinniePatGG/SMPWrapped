package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.*;

/**
 * Calculates percentile rankings for a player compared to all eligible players.
 */
public final class PercentileCalculator {

    private PercentileCalculator() {}

    /**
     * Calculate percentiles for the given player across all tracked stats.
     * Only includes stats where the player has sufficient data.
     */
    public static Map<String, Double> calculatePercentiles(PlayerStats playerStats, Map<UUID, PlayerStats> allStats) {
        Map<String, Double> percentiles = new LinkedHashMap<>();

        // Define which stats to calculate percentiles for
        String[] statKeys = {
            "playtime_seconds", "blocks_mined", "blocks_placed", "distance_traveled",
            "mobs_killed", "items_crafted", "fishing_catches", "unique_days"
        };

        for (String key : statKeys) {
            double playerValue = getStatValue(playerStats, key);
            if (playerValue <= 0) continue; // Skip if player has no data

            // Collect all values for this stat
            List<Double> allValues = new ArrayList<>();
            for (PlayerStats stats : allStats.values()) {
                double v = getStatValue(stats, key);
                if (v > 0) allValues.add(v);
            }

            // Need minimum sample size
            if (allValues.size() < 3) continue;

            // Calculate percentile (percentage of players this player beats)
            long beaten = allValues.stream().filter(v -> v < playerValue).count();
            double percentile = (double) beaten / allValues.size() * 100.0;
            percentiles.put(key, Math.round(percentile * 10.0) / 10.0);
        }

        return percentiles;
    }

    private static double getStatValue(PlayerStats stats, String key) {
        return switch (key) {
            case "playtime_seconds" -> stats.getPlaytimeSeconds();
            case "blocks_mined" -> stats.getBlocksMined();
            case "blocks_placed" -> stats.getBlocksPlaced();
            case "distance_traveled" -> stats.getDistanceTraveled();
            case "mobs_killed" -> stats.getMobsKilled();
            case "items_crafted" -> stats.getItemsCrafted();
            case "fishing_catches" -> stats.getFishingCatches();
            case "unique_days" -> stats.getUniqueDaysPlayed();
            default -> 0;
        };
    }
}
