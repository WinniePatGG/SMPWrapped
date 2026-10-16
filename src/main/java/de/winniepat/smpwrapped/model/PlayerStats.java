package de.winniepat.smpwrapped.model;

import java.util.*;


public class PlayerStats {
    private final UUID playerId;
    private final String seasonId;

    
    private long playtimeSeconds;
    private long blocksMined;
    private long blocksPlaced;
    private long mobsKilled;
    private long deaths;
    private long damageDealt;
    private long damageReceived;
    private long itemsCrafted;
    private long itemsCollected;
    private long fishingCatches;
    private long loginSessions;
    private long uniqueDaysPlayed;

    private double distanceTraveled;

    
    private final Map<String, Long> dimensionTime = new HashMap<>();

    
    private final Map<String, Long> blocksMinedByType = new HashMap<>();
    private final Map<String, Long> blocksPlacedByType = new HashMap<>();

    
    private final Map<String, Long> mobsKilledByType = new HashMap<>();

    
    private final Map<String, Long> dailyPlaytime = new HashMap<>();

    
    private final Map<Integer, Long> hourlyPlaytime = new HashMap<>();

    public PlayerStats(UUID playerId, String seasonId) {
        this.playerId = playerId;
        this.seasonId = seasonId;
    }

    
    public UUID getPlayerId() { return playerId; }
    public String getSeasonId() { return seasonId; }
    public long getPlaytimeSeconds() { return playtimeSeconds; }
    public long getBlocksMined() { return blocksMined; }
    public long getBlocksPlaced() { return blocksPlaced; }
    public long getDistanceTraveled() { return Math.round(distanceTraveled); }
    public long getMobsKilled() { return mobsKilled; }
    public long getDeaths() { return deaths; }
    public long getDamageDealt() { return damageDealt; }
    public long getDamageReceived() { return damageReceived; }
    public long getItemsCrafted() { return itemsCrafted; }
    public long getItemsCollected() { return itemsCollected; }
    public long getFishingCatches() { return fishingCatches; }
    public long getLoginSessions() { return loginSessions; }
    public long getUniqueDaysPlayed() { return uniqueDaysPlayed; }
    public Map<String, Long> getDimensionTime() { return Collections.unmodifiableMap(dimensionTime); }
    public Map<String, Long> getBlocksMinedByType() { return Collections.unmodifiableMap(blocksMinedByType); }
    public Map<String, Long> getBlocksPlacedByType() { return Collections.unmodifiableMap(blocksPlacedByType); }
    public Map<String, Long> getMobsKilledByType() { return Collections.unmodifiableMap(mobsKilledByType); }
    public Map<String, Long> getDailyPlaytime() { return Collections.unmodifiableMap(dailyPlaytime); }
    public Map<Integer, Long> getHourlyPlaytime() { return Collections.unmodifiableMap(hourlyPlaytime); }

    
    public void addPlaytime(long seconds) { this.playtimeSeconds += seconds; }
    public void addBlocksMined(long count) { this.blocksMined += count; }
    public void addBlocksPlaced(long count) { this.blocksPlaced += count; }
    public void addDistance(double blocks) {
        if (!Double.isFinite(blocks) || blocks <= 0.0) {
            return;
        }
        this.distanceTraveled += blocks;
    }
    public void addMobsKilled(long count) { this.mobsKilled += count; }
    public void addDeath() { this.deaths++; }
    public void addDamageDealt(long amount) { this.damageDealt += amount; }
    public void addDamageReceived(long amount) { this.damageReceived += amount; }
    public void addItemsCrafted(long count) { this.itemsCrafted += count; }
    public void addItemsCollected(long count) { this.itemsCollected += count; }
    public void addFishingCatch() { this.fishingCatches++; }
    public void addLoginSession() { this.loginSessions++; }
    public void addUniqueDay() { this.uniqueDaysPlayed++; }

    public void addDimensionTime(String dimension, long seconds) {
        dimensionTime.merge(dimension, seconds, Long::sum);
    }

    public void addBlockMined(String material, long count) {
        blocksMinedByType.merge(material, count, Long::sum);
    }

    public void addBlockPlaced(String material, long count) {
        blocksPlacedByType.merge(material, count, Long::sum);
    }

    public void addMobKilled(String entityType, long count) {
        mobsKilledByType.merge(entityType, count, Long::sum);
    }

    public void addDailyPlaytime(String day, long seconds) {
        dailyPlaytime.merge(day, seconds, Long::sum);
    }

    public void addHourlyPlaytime(int hour, long seconds) {
        hourlyPlaytime.merge(hour, seconds, Long::sum);
    }

    
    public void merge(PlayerStats other) {
        if (!this.playerId.equals(other.playerId) || !this.seasonId.equals(other.seasonId)) {
            throw new IllegalArgumentException("Cannot merge stats from different player/season");
        }
        this.playtimeSeconds += other.playtimeSeconds;
        this.blocksMined += other.blocksMined;
        this.blocksPlaced += other.blocksPlaced;
        this.distanceTraveled += other.distanceTraveled;
        this.mobsKilled += other.mobsKilled;
        this.deaths += other.deaths;
        this.damageDealt += other.damageDealt;
        this.damageReceived += other.damageReceived;
        this.itemsCrafted += other.itemsCrafted;
        this.itemsCollected += other.itemsCollected;
        this.fishingCatches += other.fishingCatches;
        this.loginSessions += other.loginSessions;
        this.uniqueDaysPlayed += other.uniqueDaysPlayed;
        other.dimensionTime.forEach((k, v) -> this.dimensionTime.merge(k, v, Long::sum));
        other.blocksMinedByType.forEach((k, v) -> this.blocksMinedByType.merge(k, v, Long::sum));
        other.blocksPlacedByType.forEach((k, v) -> this.blocksPlacedByType.merge(k, v, Long::sum));
        other.mobsKilledByType.forEach((k, v) -> this.mobsKilledByType.merge(k, v, Long::sum));
        other.dailyPlaytime.forEach((k, v) -> this.dailyPlaytime.merge(k, v, Long::sum));
        other.hourlyPlaytime.forEach((k, v) -> this.hourlyPlaytime.merge(k, v, Long::sum));
    }

    
    public Optional<Map.Entry<String, Long>> getTopMinedBlock() {
        return blocksMinedByType.entrySet().stream()
            .max(Map.Entry.comparingByValue());
    }

    
    public Optional<Map.Entry<String, Long>> getTopMob() {
        return mobsKilledByType.entrySet().stream()
            .max(Map.Entry.comparingByValue());
    }

    
    public Optional<Map.Entry<String, Long>> getTopDimension() {
        return dimensionTime.entrySet().stream()
            .max(Map.Entry.comparingByValue());
    }

    
    public Optional<Map.Entry<String, Long>> getTopDay() {
        return dailyPlaytime.entrySet().stream()
            .max(Map.Entry.comparingByValue());
    }

    
    public Optional<Map.Entry<Integer, Long>> getTopHour() {
        return hourlyPlaytime.entrySet().stream()
            .max(Map.Entry.comparingByValue());
    }
}
