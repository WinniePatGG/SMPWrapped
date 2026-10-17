package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.*;


public final class PercentileCalculator {

    private static final SMPLogger LOG = SMPLogger.get("percentiles");

    private static final int MIN_COMPARABLE_PLAYERS = 3;

    private PercentileCalculator() {}

    
    public static Map<String, Double> calculatePercentiles(PlayerStats playerStats, Map<UUID, PlayerStats> allStats) {
        Map<String, Double> percentiles = new LinkedHashMap<>();

        if (playerStats == null || allStats == null || allStats.isEmpty()) {
            LOG.trace("Not enough data to calculate percentiles",
                LogContext.of("hasPlayerStats", playerStats != null,
                    "comparisonPlayers", allStats == null ? 0 : allStats.size()));
            return percentiles;
        }

        
        String[] statKeys = {
            "playtime_seconds", "blocks_mined", "blocks_placed", "distance_traveled",
            "mobs_killed", "items_crafted", "fishing_catches", "unique_days"
        };

        for (String key : statKeys) {
            double playerValue = getStatValue(playerStats, key);
            if (playerValue <= 0) continue; 

            
            List<Double> allValues = new ArrayList<>();
            for (PlayerStats stats : allStats.values()) {
                if (stats == null) {
                    continue;
                }
                double v = getStatValue(stats, key);
                if (v > 0) allValues.add(v);
            }

            
            if (allValues.size() < MIN_COMPARABLE_PLAYERS) {
                LOG.trace("Skipping percentile for a stat, too few comparable players",
                    LogContext.of("stat", key, "comparablePlayers", allValues.size()));
                continue;
            }

            
            long beaten = allValues.stream().filter(v -> v < playerValue).count();
            double percentile = (double) beaten / allValues.size() * 100.0;
            percentiles.put(key, Math.round(percentile * 10.0) / 10.0);
        }

        LOG.trace("Calculated percentiles", LogContext.of(
            "computed", percentiles.size(), "comparisonPlayers", allStats.size()));
        return percentiles;
    }

    private static double getStatValue(PlayerStats stats, String key) {
        if (key == null || stats == null) {
            return 0;
        }
        switch (key) {
            case "playtime_seconds":
                return stats.getPlaytimeSeconds();
            case "blocks_mined":
                return stats.getBlocksMined();
            case "blocks_placed":
                return stats.getBlocksPlaced();
            case "distance_traveled":
                return stats.getDistanceTraveled();
            case "mobs_killed":
                return stats.getMobsKilled();
            case "items_crafted":
                return stats.getItemsCrafted();
            case "fishing_catches":
                return stats.getFishingCatches();
            case "unique_days":
                return stats.getUniqueDaysPlayed();
            default:
                return 0;
        }
    }
}
