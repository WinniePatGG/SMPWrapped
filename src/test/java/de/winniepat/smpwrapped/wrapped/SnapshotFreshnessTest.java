package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.model.PlayerStats;
import de.winniepat.smpwrapped.model.WrappedSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotFreshnessTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final String SEASON_ID = "season-1";

    private WrappedSnapshot snapshotOf(Map<String, Object> stats) {
        return new WrappedSnapshot(PLAYER_ID, SEASON_ID, "WinniePat",
            System.currentTimeMillis(), "The Miner", "desc",
            stats, List.of(), Map.of(), List.of());
    }

    private Map<String, Object> fullStats(PlayerStats stats) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("playtimeSeconds", stats.getPlaytimeSeconds());
        map.put("blocksMined", stats.getBlocksMined());
        map.put("blocksPlaced", stats.getBlocksPlaced());
        map.put("distanceTraveled", stats.getDistanceTraveled());
        map.put("mobsKilled", stats.getMobsKilled());
        map.put("deaths", stats.getDeaths());
        map.put("damageDealt", stats.getDamageDealt());
        map.put("damageReceived", stats.getDamageReceived());
        map.put("itemsCrafted", stats.getItemsCrafted());
        map.put("itemsCollected", stats.getItemsCollected());
        map.put("fishingCatches", stats.getFishingCatches());
        map.put("loginSessions", stats.getLoginSessions());
        map.put("uniqueDaysPlayed",
            Math.max(stats.getUniqueDaysPlayed(), stats.getDailyPlaytime().size()));
        return map;
    }

    @Test
    @DisplayName("A snapshot built from the same stats is considered current")
    void identicalStatsAreCurrent() {
        PlayerStats stats = new PlayerStats(PLAYER_ID, SEASON_ID);
        stats.addPlaytime(3600);
        stats.addBlocksMined(500);

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshotOf(fullStats(stats)), stats)).isTrue();
    }

    @Test
    @DisplayName("A fresh player with no stats matches a fresh snapshot")
    void emptyStatsAreCurrent() {
        PlayerStats stats = new PlayerStats(PLAYER_ID, SEASON_ID);

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshotOf(fullStats(stats)), stats)).isTrue();
    }

    @Test
    @DisplayName("New playtime makes a snapshot stale")
    void newPlaytimeIsStale() {
        PlayerStats before = new PlayerStats(PLAYER_ID, SEASON_ID);
        before.addPlaytime(3600);
        WrappedSnapshot snapshot = snapshotOf(fullStats(before));

        PlayerStats after = new PlayerStats(PLAYER_ID, SEASON_ID);
        after.addPlaytime(3660);

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshot, after)).isFalse();
    }

    @Test
    @DisplayName("Newly mined blocks make a snapshot stale")
    void newBlocksAreStale() {
        PlayerStats before = new PlayerStats(PLAYER_ID, SEASON_ID);
        before.addBlocksMined(100);
        WrappedSnapshot snapshot = snapshotOf(fullStats(before));

        PlayerStats after = new PlayerStats(PLAYER_ID, SEASON_ID);
        after.addBlocksMined(101);

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshot, after)).isFalse();
    }

    @Test
    @DisplayName("Every tracked counter is checked, not just playtime")
    void everyCounterIsChecked() {
        PlayerStats before = new PlayerStats(PLAYER_ID, SEASON_ID);

        Runnable[] mutators = {
            () -> { },
            () -> before.addBlocksMined(1),
            () -> before.addBlocksPlaced(1),
            () -> before.addDistance(10),
            () -> before.addMobsKilled(1),
            () -> before.addDeath(),
            () -> before.addDamageDealt(1),
            () -> before.addDamageReceived(1),
            () -> before.addItemsCrafted(1),
            () -> before.addItemsCollected(1),
            () -> before.addFishingCatch(),
            () -> before.addLoginSession(),
            () -> before.addUniqueDay()
        };

        for (Runnable mutate : mutators) {
            PlayerStats after = new PlayerStats(PLAYER_ID, SEASON_ID);
            mutate.run();
            assertThat(WrappedGenerator.isSnapshotCurrent(snapshotOf(fullStats(after)), after))
                .as("mutator %s", mutate.getClass().getName())
                .isTrue();
        }
    }

    @Test
    @DisplayName("A single changed counter is enough to mark the snapshot stale")
    void oneChangedCounterIsEnough() {
        PlayerStats before = new PlayerStats(PLAYER_ID, SEASON_ID);
        WrappedSnapshot snapshot = snapshotOf(fullStats(before));

        PlayerStats after = new PlayerStats(PLAYER_ID, SEASON_ID);
        after.addMobsKilled(1);

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshot, after)).isFalse();
    }

    @Test
    @DisplayName("Numeric values stored as doubles or longs still compare equal")
    void numericTypesAreInterchangeable() {
        PlayerStats stats = new PlayerStats(PLAYER_ID, SEASON_ID);
        stats.addPlaytime(120);

        Map<String, Object> asDoubles = new LinkedHashMap<>(fullStats(stats));
        asDoubles.replaceAll((key, value) -> value instanceof Long l ? l.doubleValue() : value);

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshotOf(asDoubles), stats)).isTrue();
    }

    @Test
    @DisplayName("A snapshot with missing keys is treated as stale")
    void incompleteSnapshotIsStale() {
        PlayerStats stats = new PlayerStats(PLAYER_ID, SEASON_ID);
        stats.addPlaytime(60);

        WrappedSnapshot snapshot = snapshotOf(Map.of("playtimeSeconds", 60L));

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshot, stats)).isFalse();
    }

    @Test
    @DisplayName("A snapshot with no stats at all is treated as stale")
    void emptySnapshotIsStale() {
        PlayerStats stats = new PlayerStats(PLAYER_ID, SEASON_ID);

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshotOf(Map.of()), stats)).isFalse();
    }

    @Test
    @DisplayName("Null inputs are treated as stale rather than throwing")
    void nullInputsAreStale() {
        PlayerStats stats = new PlayerStats(PLAYER_ID, SEASON_ID);

        assertThat(WrappedGenerator.isSnapshotCurrent(null, stats)).isFalse();
        assertThat(WrappedGenerator.isSnapshotCurrent(snapshotOf(fullStats(stats)), null)).isFalse();
    }

    @Test
    @DisplayName("Non numeric stored values are treated as stale")
    void nonNumericValuesAreStale() {
        PlayerStats stats = new PlayerStats(PLAYER_ID, SEASON_ID);

        Map<String, Object> corrupted = new LinkedHashMap<>(fullStats(stats));
        corrupted.put("playtimeSeconds", "a lot");

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshotOf(corrupted), stats)).isFalse();
    }

    @Test
    @DisplayName("Daily playtime counts towards unique days when comparing")
    void dailyPlaytimeCountsTowardsUniqueDays() {
        PlayerStats stats = new PlayerStats(PLAYER_ID, SEASON_ID);
        stats.addDailyPlaytime("2026-10-17", 120);

        assertThat(WrappedGenerator.isSnapshotCurrent(snapshotOf(fullStats(stats)), stats)).isTrue();
    }
}