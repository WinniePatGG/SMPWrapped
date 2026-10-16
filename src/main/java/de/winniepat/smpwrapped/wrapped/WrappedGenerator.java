package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.*;

import java.util.*;


public class WrappedGenerator {

    private static final SMPLogger LOG = SMPLogger.get("generator");

    private final SMPWrapped plugin;

    public WrappedGenerator(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    
    public WrappedSnapshot generate(UUID playerId, String seasonId, String playerName) {
        long startedAt = System.currentTimeMillis();
        PlayerStats stats = plugin.getDatabase().loadPlayerStats(playerId, seasonId);

        
        ArchetypeEngine.ArchetypeResult archetype = ArchetypeEngine.determineArchetype(stats);

        
        
        Map<UUID, PlayerStats> allStats = new HashMap<>();
        List<TrackedPlayer> players;
        try {
            players = plugin.getDatabase().getAllPlayers();
        } catch (Throwable t) {
            LOG.error("Could not load the player list for percentile calculation", t, LogContext.of(
                "player", playerId, "season", seasonId));
            players = List.of();
        }
        for (TrackedPlayer p : players) {
            try {
                allStats.put(p.uuid(), plugin.getDatabase().loadPlayerStats(p.uuid(), seasonId));
            } catch (Throwable t) {
                LOG.error("Could not load stats of another player, excluding them from the percentile comparison", t,
                    LogContext.of("player", p.uuid(), "name", p.lastName(), "season", seasonId));
            }
        }
        Map<String, Double> percentiles = PercentileCalculator.calculatePercentiles(stats, allStats);
        if (allStats.size() < 3) {
            LOG.debug("Not enough players for percentile rankings",
                LogContext.of("player", playerId, "season", seasonId, "playersCompared", allStats.size()));
        }

        
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
            List<Achievement> earned;
            try {
                earned = AchievementEngine.evaluate(stats, percentiles);
            } catch (Throwable t) {
                LOG.error("Achievement evaluation failed, continuing without achievements", t, LogContext.of(
                    "player", playerId, "season", seasonId));
                earned = List.of();
            }
            for (Achievement achievement : earned) {
                achievements.add(achievement.encode());
                try {
                    plugin.getDatabase().saveAchievement(playerId, seasonId, achievement.id());
                } catch (Throwable t) {
                    LOG.error("Could not persist an earned achievement", t, LogContext.of(
                        "player", playerId, "season", seasonId, "achievement", achievement.id()));
                }
            }
        }

        
        List<String> milestones = generateMilestones(stats);

        LOG.debug("Generated Wrapped snapshot", LogContext.of(
            "player", playerId,
            "name", playerName,
            "season", seasonId,
            "archetype", archetype.name(),
            "achievements", achievements.size(),
            "percentiles", percentiles.size(),
            "durationMillis", System.currentTimeMillis() - startedAt));

        return new WrappedSnapshot(
            playerId, seasonId, playerName, System.currentTimeMillis(),
            archetype.name(), archetype.description(),
            statsMap, achievements, percentiles, milestones
        );
    }

    public static boolean isSnapshotCurrent(WrappedSnapshot snapshot, PlayerStats stats) {
        if (snapshot == null || stats == null) {
            return false;
        }
        Map<String, Object> map = snapshot.stats();
        return matches(map, "playtimeSeconds", stats.getPlaytimeSeconds())
            && matches(map, "blocksMined", stats.getBlocksMined())
            && matches(map, "blocksPlaced", stats.getBlocksPlaced())
            && matches(map, "distanceTraveled", stats.getDistanceTraveled())
            && matches(map, "mobsKilled", stats.getMobsKilled())
            && matches(map, "deaths", stats.getDeaths())
            && matches(map, "damageDealt", stats.getDamageDealt())
            && matches(map, "damageReceived", stats.getDamageReceived())
            && matches(map, "itemsCrafted", stats.getItemsCrafted())
            && matches(map, "itemsCollected", stats.getItemsCollected())
            && matches(map, "fishingCatches", stats.getFishingCatches())
            && matches(map, "loginSessions", stats.getLoginSessions())
            && matches(map, "uniqueDaysPlayed",
                Math.max(stats.getUniqueDaysPlayed(), stats.getDailyPlaytime().size()));
    }

    private static boolean matches(Map<String, Object> map, String key, long expected) {
        Object raw = map.get(key);
        return raw instanceof Number number && number.longValue() == expected;
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