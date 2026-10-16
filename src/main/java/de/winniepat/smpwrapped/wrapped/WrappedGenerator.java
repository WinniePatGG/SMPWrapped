package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.model.*;

import java.util.*;


public class WrappedGenerator {

    private final SMPWrapped plugin;

    public WrappedGenerator(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    
    public WrappedSnapshot generate(UUID playerId, String seasonId, String playerName) {
        PlayerStats stats = plugin.getDatabase().loadPlayerStats(playerId, seasonId);

        
        ArchetypeEngine.ArchetypeResult archetype = ArchetypeEngine.determineArchetype(stats);

        
        Map<UUID, PlayerStats> allStats = new HashMap<>();
        for (TrackedPlayer p : plugin.getDatabase().getAllPlayers()) {
            allStats.put(p.uuid(), plugin.getDatabase().loadPlayerStats(p.uuid(), seasonId));
        }
        Map<String, Double> percentiles = PercentileCalculator.calculatePercentiles(stats, allStats);

        
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
        
        
        statsMap.put("uniqueDaysPlayed", Math.max(stats.getUniqueDaysPlayed(), stats.getDailyPlaytime().size()));

        
        stats.getTopMinedBlock().ifPresent(e -> statsMap.put("topMinedBlock", e.getKey()));
        stats.getTopMinedBlock().ifPresent(e -> statsMap.put("topMinedBlockCount", e.getValue()));
        stats.getTopMob().ifPresent(e -> statsMap.put("topMob", e.getKey()));
        stats.getTopMob().ifPresent(e -> statsMap.put("topMobCount", e.getValue()));
        stats.getTopDimension().ifPresent(e -> statsMap.put("topDimension", e.getKey()));
        stats.getTopDay().ifPresent(e -> statsMap.put("topDay", e.getKey()));
        stats.getTopHour().ifPresent(e -> statsMap.put("topHour", e.getKey()));

        
        
        
        List<String> achievements = new ArrayList<>();
        if (plugin.getConfigManager().get().trackAchievements()) {
            for (Achievement achievement : AchievementEngine.evaluate(stats, percentiles)) {
                achievements.add(achievement.encode());
                plugin.getDatabase().saveAchievement(playerId, seasonId, achievement.id());
            }
        }

        
        List<String> milestones = generateMilestones(stats);

        return new WrappedSnapshot(
            playerId, seasonId, playerName, System.currentTimeMillis(),
            archetype.name(), archetype.description(),
            statsMap, achievements, percentiles, milestones
        );
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

    
    public WrappedSnapshot generateAndSave(UUID playerId, String seasonId, String playerName) {
        WrappedSnapshot snapshot = generate(playerId, seasonId, playerName);
        plugin.getDatabase().saveWrappedSnapshot(snapshot);
        return snapshot;
    }
}
