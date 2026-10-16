package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.model.PlayerStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

class ArchetypeEngineTest {

    @Test
    @DisplayName("Should return The Newcomer for zero stats")
    void testNewcomerForZeroStats() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        ArchetypeEngine.ArchetypeResult result = ArchetypeEngine.determineArchetype(stats);
        assertThat(result.name()).isEqualTo("The Newcomer");
    }

    @Test
    @DisplayName("Should return The Miner for high block mining")
    void testMinerArchetype() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(3600); 
        stats.addBlocksMined(5000);
        stats.addBlocksPlaced(100);
        stats.addMobsKilled(10);

        ArchetypeEngine.ArchetypeResult result = ArchetypeEngine.determineArchetype(stats);
        assertThat(result.name()).isEqualTo("The Miner");
    }

    @Test
    @DisplayName("Should return The Explorer for high distance traveled")
    void testExplorerArchetype() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(3600);
        stats.addDistance(50000);
        stats.addBlocksMined(100);

        ArchetypeEngine.ArchetypeResult result = ArchetypeEngine.determineArchetype(stats);
        assertThat(result.name()).isEqualTo("The Explorer");
    }

    @Test
    @DisplayName("Should return The Builder for high block placing")
    void testBuilderArchetype() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(3600);
        stats.addBlocksPlaced(5000);
        stats.addBlocksMined(100);

        ArchetypeEngine.ArchetypeResult result = ArchetypeEngine.determineArchetype(stats);
        assertThat(result.name()).isEqualTo("The Builder");
    }

    @Test
    @DisplayName("Should return The Monster Hunter for high mob kills")
    void testMonsterHunterArchetype() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(3600);
        stats.addMobsKilled(500);
        stats.addBlocksMined(100);

        ArchetypeEngine.ArchetypeResult result = ArchetypeEngine.determineArchetype(stats);
        assertThat(result.name()).isEqualTo("The Monster Hunter");
    }

    @Test
    @DisplayName("Should return The Survivor for low death ratio")
    void testSurvivorArchetype() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(36000); 
        stats.addDeath();
        stats.addBlocksMined(100);

        ArchetypeEngine.ArchetypeResult result = ArchetypeEngine.determineArchetype(stats);
        assertThat(result.name()).isEqualTo("The Survivor");
    }

    @Test
    @DisplayName("Should return The Collector for high item collection")
    void testCollectorArchetype() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        stats.addPlaytime(3600);
        stats.addItemsCollected(2000);
        stats.addItemsCrafted(1000);
        stats.addBlocksMined(100);

        ArchetypeEngine.ArchetypeResult result = ArchetypeEngine.determineArchetype(stats);
        assertThat(result.name()).isEqualTo("The Collector");
    }

    @Test
    @DisplayName("Should always return a non-null description")
    void testDescriptionNotNull() {
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), "season-1");
        ArchetypeEngine.ArchetypeResult result = ArchetypeEngine.determineArchetype(stats);
        assertThat(result.description()).isNotNull().isNotEmpty();
    }
}
