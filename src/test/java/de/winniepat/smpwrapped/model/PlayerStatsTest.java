package de.winniepat.smpwrapped.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

class PlayerStatsTest {

    @Test
    @DisplayName("Should accumulate stats correctly")
    void testStatAccumulation() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(100);
        stats.addPlaytime(200);
        stats.addBlocksMined(50);
        stats.addBlocksMined(25);
        stats.addDeath();
        stats.addDeath();
        stats.addDeath();

        assertThat(stats.getPlaytimeSeconds()).isEqualTo(300);
        assertThat(stats.getBlocksMined()).isEqualTo(75);
        assertThat(stats.getDeaths()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should track block types correctly")
    void testBlockTypeTracking() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addBlocksMined(10);
        stats.addBlockMined("STONE", 10);
        stats.addBlocksMined(5);
        stats.addBlockMined("STONE", 5);
        stats.addBlocksMined(2);
        stats.addBlockMined("DIAMOND_ORE", 2);

        assertThat(stats.getBlocksMined()).isEqualTo(17);
        assertThat(stats.getBlocksMinedByType().get("STONE")).isEqualTo(15L);
        assertThat(stats.getBlocksMinedByType().get("DIAMOND_ORE")).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should track mob types correctly")
    void testMobTypeTracking() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addMobsKilled(5);
        stats.addMobKilled("ZOMBIE", 5);
        stats.addMobsKilled(3);
        stats.addMobKilled("SKELETON", 3);
        stats.addMobsKilled(2);
        stats.addMobKilled("ZOMBIE", 2);

        assertThat(stats.getMobsKilled()).isEqualTo(10);
        assertThat(stats.getMobsKilledByType().get("ZOMBIE")).isEqualTo(7L);
        assertThat(stats.getMobsKilledByType().get("SKELETON")).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should track dimension time correctly")
    void testDimensionTimeTracking() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addDimensionTime("OVERWORLD", 3600);
        stats.addDimensionTime("NETHER", 1800);
        stats.addDimensionTime("OVERWORLD", 600);

        assertThat(stats.getDimensionTime().get("OVERWORLD")).isEqualTo(4200L);
        assertThat(stats.getDimensionTime().get("NETHER")).isEqualTo(1800L);
    }

    @Test
    @DisplayName("Should merge stats correctly")
    void testMergeStats() {
        UUID playerId = UUID.randomUUID();
        PlayerStats stats1 = new PlayerStats(playerId, "season-1");
        stats1.addPlaytime(100);
        stats1.addBlocksMined(50);

        PlayerStats stats2 = new PlayerStats(playerId, "season-1");
        stats2.addPlaytime(200);
        stats2.addBlocksMined(30);

        stats1.merge(stats2);

        assertThat(stats1.getPlaytimeSeconds()).isEqualTo(300);
        assertThat(stats1.getBlocksMined()).isEqualTo(80);
    }

    @Test
    @DisplayName("Should throw when merging different players")
    void testMergeDifferentPlayersThrows() {
        PlayerStats stats1 = new PlayerStats(UUID.randomUUID(), "season-1");
        PlayerStats stats2 = new PlayerStats(UUID.randomUUID(), "season-1");

        assertThatThrownBy(() -> stats1.merge(stats2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should return top mined block correctly")
    void testTopMinedBlock() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addBlockMined("STONE", 10);
        stats.addBlockMined("DIAMOND_ORE", 50);
        stats.addBlockMined("IRON_ORE", 20);

        var topBlock = stats.getTopMinedBlock();
        assertThat(topBlock).isPresent();
        assertThat(topBlock.get().getKey()).isEqualTo("DIAMOND_ORE");
        assertThat(topBlock.get().getValue()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should return empty top mined block when no blocks mined")
    void testTopMinedBlockEmpty() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        assertThat(stats.getTopMinedBlock()).isEmpty();
    }

    @Test
    @DisplayName("Should return top mob correctly")
    void testTopMob() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addMobKilled("ZOMBIE", 5);
        stats.addMobKilled("CREEPER", 10);
        stats.addMobKilled("SKELETON", 3);

        var topMob = stats.getTopMob();
        assertThat(topMob).isPresent();
        assertThat(topMob.get().getKey()).isEqualTo("CREEPER");
        assertThat(topMob.get().getValue()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Should return top dimension correctly")
    void testTopDimension() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addDimensionTime("OVERWORLD", 100);
        stats.addDimensionTime("NETHER", 500);
        stats.addDimensionTime("END", 50);

        var topDimension = stats.getTopDimension();
        assertThat(topDimension).isPresent();
        assertThat(topDimension.get().getKey()).isEqualTo("NETHER");
        assertThat(topDimension.get().getValue()).isEqualTo(500L);
    }

    @Test
    @DisplayName("Sub block movements accumulate into whole blocks")
    void subBlockMovesAccumulate() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");

        for (int tick = 0; tick < 400; tick++) {
            stats.addDistance(0.25);
        }

        assertThat(stats.getDistanceTraveled()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Fractional block totals round instead of truncating")
    void fractionalTotalsRound() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");

        stats.addDistance(10.6);

        assertThat(stats.getDistanceTraveled()).isEqualTo(11L);
    }

    @Test
    @DisplayName("Whole block distances are still accepted")
    void wholeBlockDistances() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");

        stats.addDistance(50_000);

        assertThat(stats.getDistanceTraveled()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("Invalid distances are ignored")
    void invalidDistancesAreIgnored() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");

        stats.addDistance(-50);
        stats.addDistance(0);
        stats.addDistance(Double.NaN);
        stats.addDistance(Double.POSITIVE_INFINITY);

        assertThat(stats.getDistanceTraveled()).isZero();
    }

    @Test
    @DisplayName("Distance survives a merge")
    void distanceMerges() {
        UUID playerId = UUID.randomUUID();
        PlayerStats first = new PlayerStats(playerId, "season-1");
        first.addDistance(120.4);

        PlayerStats second = new PlayerStats(playerId, "season-1");
        second.addDistance(80.4);

        first.merge(second);

        assertThat(first.getDistanceTraveled()).isEqualTo(201L);
    }

    @Test
    @DisplayName("Distance starts at zero")
    void distanceStartsAtZero() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        assertThat(stats.getDistanceTraveled()).isZero();
    }
}
