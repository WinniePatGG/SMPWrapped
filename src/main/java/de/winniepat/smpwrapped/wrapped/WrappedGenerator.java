package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.model.*;

import java.util.*;

/**
 * Generates Wrapped snapshots for players.
 * Combines stats, archetypes, percentiles, and achievements.
 */
public class WrappedGenerator {

    private final SMPWrapped plugin;

    public WrappedGenerator(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    /**
     * Generate a Wrapped snapshot for a player in a season.
     */
    public WrappedSnapshot generate(UUID playerId, String seasonId, String playerName) {
        PlayerStats stats = plugin.getDatabase().loadPlayerStats(playerId, seasonId);

        // Determine archetype
        ArchetypeEngine.ArchetypeResult archetype = ArchetypeEngine.determineArchetype(stats);

        // Calculate percentiles
        Map<UUID, PlayerStats> allStats = new HashMap<>();
        for (TrackedPlayer p : plugin.getDatabase().getAllPlayers()) {
            allStats.put(p.uuid(), plugin.getDatabase().loadPlayerStats(p.uuid(), seasonId));
        }
        Map<String, Double> percentiles = PercentileCalculator.calculatePercentiles(stats, allStats);

        // Build stats map
        Map<String, Object> statsMap = new LinkedHashMap<>();
        statsMap.put("playtimeSeconds", stats.getPlaytimeSeconds());
        statsMap.put("playtimeHours", Math.round(stats.getPlaytimeSeconds() / 36.0) / 100.0);
        statsMap.put("blocksMined", stats.getBlocksMined());
        statsMap.put("blocksPlaced", stats.getBlocksPlaced());
        statsMap.put("distanceTraveled", stats.getDistanceTraveled());
        statsMap.put("mobsKilled", stats.getMobsKilled());
        statsMap.put("deaths", stats.getDeaths());
        statsMap.put("damageDealt", stats.getDamageDealt());
        statsMap.put("damageReceived", stats.getDamageReceived());
        statsMap.put("itemsCrafted", stats.getItemsCrafted());
        statsMap.put("itemsCollected", stats.getItemsCollected());
        statsMap.put("fishingCatches", stats.getFishingCatches());
        statsMap.put("loginSessions", stats.getLoginSessions());
        // Derive unique days from the daily activity map as well, so it stays
        // accurate even if older data only had per-day buckets.
        statsMap.put("uniqueDaysPlayed", Math.max(stats.getUniqueDaysPlayed(), stats.getDailyPlaytime().size()));

        // Top categories
        stats.getTopMinedBlock().ifPresent(e -> statsMap.put("topMinedBlock", e.getKey()));
        stats.getTopMinedBlock().ifPresent(e -> statsMap.put("topMinedBlockCount", e.getValue()));
        stats.getTopMob().ifPresent(e -> statsMap.put("topMob", e.getKey()));
        stats.getTopMob().ifPresent(e -> statsMap.put("topMobCount", e.getValue()));
        stats.getTopDimension().ifPresent(e -> statsMap.put("topDimension", e.getKey()));
        stats.getTopDay().ifPresent(e -> statsMap.put("topDay", e.getKey()));
        stats.getTopHour().ifPresent(e -> statsMap.put("topHour", e.getKey()));

        // Achievements
        List<String> achievements = plugin.getDatabase().getAchievements(playerId, seasonId);
        if (achievements.isEmpty()) {
            achievements = generateDefaultAchievements(stats);
        }

        // Milestones
        List<String> milestones = generateMilestones(stats);

        return new WrappedSnapshot(
            playerId, seasonId, playerName, System.currentTimeMillis(),
            archetype.name(), archetype.description(),
            statsMap, achievements, percentiles, milestones
        );
    }

    private List<String> generateDefaultAchievements(PlayerStats stats) {
        List<String> achievements = new ArrayList<>();
        if (stats.getPlaytimeSeconds() > 3600) achievements.add("First Hour");
        if (stats.getPlaytimeSeconds() > 36000) achievements.add("Dedicated Player");
        if (stats.getBlocksMined() > 1000) achievements.add("Getting Started");
        if (stats.getBlocksMined() > 10000) achievements.add("Serious Miner");
        if (stats.getMobsKilled() > 100) achievements.add("Monster Hunter");
        if (stats.getDeaths() == 0 && stats.getPlaytimeSeconds() > 3600) achievements.add("Untouchable");
        if (stats.getDistanceTraveled() > 10000) achievements.add("Wanderer");
        if (stats.getUniqueDaysPlayed() > 7) achievements.add("Regular");
        if (stats.getItemsCrafted() > 500) achievements.add("Crafter");
        return achievements;
    }

    private List<String> generateMilestones(PlayerStats stats) {
        List<String> milestones = new ArrayList<>();
        if (stats.getPlaytimeSeconds() > 0) {
            milestones.add("Joined the server for the first time");
        }
        if (stats.getBlocksMined() > 0) {
            milestones.add("Mined their first block");
        }
        if (stats.getMobsKilled() > 0) {
            milestones.add("Defeated their first mob");
        }
        if (stats.getDeaths() > 0) {
            milestones.add("Experienced their first death");
        }
        if (stats.getUniqueDaysPlayed() > 1) {
            milestones.add("Returned for another day of play");
        }
        return milestones;
    }

    /**
     * Generate and save a snapshot.
     */
    public WrappedSnapshot generateAndSave(UUID playerId, String seasonId, String playerName) {
        WrappedSnapshot snapshot = generate(playerId, seasonId, playerName);
        plugin.getDatabase().saveWrappedSnapshot(snapshot);
        return snapshot;
    }
}
