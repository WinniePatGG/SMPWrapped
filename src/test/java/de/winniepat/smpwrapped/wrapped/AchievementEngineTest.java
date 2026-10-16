package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.model.Achievement;
import de.winniepat.smpwrapped.model.PlayerStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class AchievementEngineTest {

    private static Set<String> ids(List<Achievement> achievements) {
        Set<String> ids = new HashSet<>();
        for (Achievement a : achievements) ids.add(a.id());
        return ids;
    }

    @Test
    @DisplayName("Night Owl unlocks when most playtime is late at night")
    void testNightOwl() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(7200); 
        stats.addHourlyPlaytime(23, 2000);
        stats.addHourlyPlaytime(1, 1000);
        stats.addHourlyPlaytime(2, 1000);
        stats.addHourlyPlaytime(12, 500);

        Set<String> ids = ids(AchievementEngine.evaluate(stats, Map.of()));
        assertThat(ids).contains("night_owl");
        assertThat(ids).doesNotContain("early_bird");
    }

    @Test
    @DisplayName("Early Bird unlocks when most playtime is in the morning")
    void testEarlyBird() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(7200);
        stats.addHourlyPlaytime(6, 3000);
        stats.addHourlyPlaytime(7, 2000);
        stats.addHourlyPlaytime(20, 500);

        Set<String> ids = ids(AchievementEngine.evaluate(stats, Map.of()));
        assertThat(ids).contains("early_bird");
        assertThat(ids).doesNotContain("night_owl");
    }

    @Test
    @DisplayName("Untouchable unlocks after five hours without dying")
    void testUntouchable() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(6 * 3600);

        Set<String> ids = ids(AchievementEngine.evaluate(stats, Map.of()));
        assertThat(ids).contains("flawless", "untouchable");
        assertThat(ids).doesNotContain("survivor");
    }

    @Test
    @DisplayName("Untouchable stays locked once the player dies")
    void testUntouchableLockedAfterDeath() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(6 * 3600);
        stats.addDeath();

        Set<String> ids = ids(AchievementEngine.evaluate(stats, Map.of()));
        assertThat(ids).doesNotContain("flawless", "untouchable");
    }

    @Test
    @DisplayName("Combat and mining milestones unlock from breakdowns")
    void testCombatAndMining() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addMobsKilled(50);
        stats.addMobKilled("CREEPER", 50);
        stats.addBlocksMined(50);
        stats.addBlockMined("DIAMOND_ORE", 50);

        Set<String> ids = ids(AchievementEngine.evaluate(stats, Map.of()));
        assertThat(ids).contains("creeper_bane", "diamond_hands");
    }

    @Test
    @DisplayName("Dimension Hopper unlocks after visiting all three dimensions")
    void testDimensionHopper() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addDimensionTime("NORMAL", 600);
        stats.addDimensionTime("NETHER", 600);
        stats.addDimensionTime("THE_END", 600);

        Set<String> ids = ids(AchievementEngine.evaluate(stats, Map.of()));
        assertThat(ids).contains("dimension_hopper");
    }

    @Test
    @DisplayName("Saturday/Sunday playtime unlocks Weekend Warrior")
    void testWeekendWarrior() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addDailyPlaytime("2026-10-17", 3600); 
        stats.addDailyPlaytime("2026-10-18", 3600); 
        stats.addDailyPlaytime("2026-10-14", 600);  

        Set<String> ids = ids(AchievementEngine.evaluate(stats, Map.of()));
        assertThat(ids).contains("weekend_warrior");
    }

    @Test
    @DisplayName("Consecutive days build a streak achievement")
    void testLoginStreak() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        for (int i = 0; i < 7; i++) {
            stats.addDailyPlaytime("2026-10-0" + (i + 1), 600);
        }

        Set<String> ids = ids(AchievementEngine.evaluate(stats, Map.of()));
        assertThat(ids).contains("unbreakable");
    }

    @Test
    @DisplayName("Percentiles unlock server-wide achievements")
    void testServerAchievements() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addBlocksMined(100_000);
        Map<String, Double> percentiles = Map.of(
            "blocks_mined", 95.0,
            "playtime_seconds", 72.0
        );

        Set<String> ids = ids(AchievementEngine.evaluate(stats, percentiles));
        assertThat(ids).contains("elite", "master_miner");
        assertThat(ids).doesNotContain("top_one_percent", "no_life");
    }

    @Test
    @DisplayName("An empty player only gets the Newcomer achievement")
    void testNewcomerOnly() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        List<Achievement> achievements = AchievementEngine.evaluate(stats, Map.of());
        assertThat(ids(achievements)).isEmpty();
    }

    @Test
    @DisplayName("Achievements are sorted best tier first")
    void testSortedByTier() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(300 * 3600); 

        List<Achievement> achievements = AchievementEngine.evaluate(stats, Map.of());
        assertThat(achievements).isNotEmpty();
        for (int i = 1; i < achievements.size(); i++) {
            assertThat(achievements.get(i - 1).tier().ordinal())
                .isGreaterThanOrEqualTo(achievements.get(i).tier().ordinal());
        }
    }

    @Test
    @DisplayName("Achievement encode/decode round-trips")
    void testEncodeDecode() {
        Achievement original = new Achievement(
            "night_owl", "Night Owl", "Mostly online at night.",
            Achievement.Tier.GOLD, Achievement.Category.ACTIVITY);

        Achievement decoded = Achievement.decode(original.encode());
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    @DisplayName("Legacy plain-string achievements decode as a name-only entry")
    void testLegacyDecode() {
        Achievement decoded = Achievement.decode("First Hour");
        assertThat(decoded.name()).isEqualTo("First Hour");
        assertThat(decoded.tier()).isEqualTo(Achievement.Tier.BRONZE);
        assertThat(decoded.category()).isEqualTo(Achievement.Category.FUN);
    }
}
