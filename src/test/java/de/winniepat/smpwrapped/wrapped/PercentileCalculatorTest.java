package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.model.PlayerStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.util.*;

class PercentileCalculatorTest {

    @Test
    @DisplayName("Should return empty percentiles for no data")
    void testEmptyPercentiles() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        Map<UUID, PlayerStats> allStats = new HashMap<>();
        allStats.put(stats.getPlayerId(), stats);

        Map<String, Double> percentiles = PercentileCalculator.calculatePercentiles(stats, allStats);
        assertThat(percentiles).isEmpty();
    }

    @Test
    @DisplayName("Should calculate correct percentile for top player")
    void testTopPlayerPercentile() {
        UUID topPlayerId = UUID.randomUUID();
        UUID midPlayerId = UUID.randomUUID();
        UUID lowPlayerId = UUID.randomUUID();

        PlayerStats topStats = new PlayerStats(topPlayerId, "season-1");
        topStats.addPlaytime(10000);
        topStats.addBlocksMined(5000);

        PlayerStats midStats = new PlayerStats(midPlayerId, "season-1");
        midStats.addPlaytime(5000);
        midStats.addBlocksMined(2500);

        PlayerStats lowStats = new PlayerStats(lowPlayerId, "season-1");
        lowStats.addPlaytime(1000);
        lowStats.addBlocksMined(500);

        Map<UUID, PlayerStats> allStats = new HashMap<>();
        allStats.put(topPlayerId, topStats);
        allStats.put(midPlayerId, midStats);
        allStats.put(lowPlayerId, lowStats);

        Map<String, Double> percentiles = PercentileCalculator.calculatePercentiles(topStats, allStats);
        assertThat(percentiles).containsKey("playtime_seconds");
        assertThat(percentiles.get("playtime_seconds")).isEqualTo(66.7);
    }

    @Test
    @DisplayName("Should calculate correct percentile for middle player")
    void testMiddlePlayerPercentile() {
        UUID topPlayerId = UUID.randomUUID();
        UUID midPlayerId = UUID.randomUUID();
        UUID lowPlayerId = UUID.randomUUID();

        PlayerStats topStats = new PlayerStats(topPlayerId, "season-1");
        topStats.addPlaytime(10000);

        PlayerStats midStats = new PlayerStats(midPlayerId, "season-1");
        midStats.addPlaytime(5000);

        PlayerStats lowStats = new PlayerStats(lowPlayerId, "season-1");
        lowStats.addPlaytime(1000);

        Map<UUID, PlayerStats> allStats = new HashMap<>();
        allStats.put(topPlayerId, topStats);
        allStats.put(midPlayerId, midStats);
        allStats.put(lowPlayerId, lowStats);

        Map<String, Double> percentiles = PercentileCalculator.calculatePercentiles(midStats, allStats);
        assertThat(percentiles).containsKey("playtime_seconds");
        assertThat(percentiles.get("playtime_seconds")).isEqualTo(33.3);
    }

    @Test
    @DisplayName("Should skip stats with insufficient sample size")
    void testMinimumSampleSize() {
        UUID player1Id = UUID.randomUUID();
        UUID player2Id = UUID.randomUUID();

        PlayerStats stats1 = new PlayerStats(player1Id, "season-1");
        stats1.addPlaytime(1000);

        PlayerStats stats2 = new PlayerStats(player2Id, "season-1");
        stats2.addPlaytime(2000);

        Map<UUID, PlayerStats> allStats = new HashMap<>();
        allStats.put(player1Id, stats1);
        allStats.put(player2Id, stats2);

        // Only 2 players, minimum sample size is 3
        Map<String, Double> percentiles = PercentileCalculator.calculatePercentiles(stats1, allStats);
        assertThat(percentiles).isEmpty();
    }
}
