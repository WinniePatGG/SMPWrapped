package de.winniepat.smpwrapped.tracking;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.config.ConfigManager;
import de.winniepat.smpwrapped.config.PluginConfig;
import de.winniepat.smpwrapped.db.DatabaseManager;
import de.winniepat.smpwrapped.model.PlayerStats;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatTrackerBlockTest {

    private static final UUID PLAYER_ID = UUID.fromString("4d537abc-e205-4258-b289-ac70d814b5db");

    private StatTracker tracker;
    private PlayerStats stats;
    private Player player;

    @BeforeEach
void setUp() {
    initTracker(false);
    }

    @Test
    @DisplayName("Creative blocks are counted by default")
    void creativeIsCountedByDefault() {
        tracker.onBlockBreak(breakEvent(GameMode.CREATIVE, Material.STONE));
        tracker.onBlockPlace(placeEvent(GameMode.CREATIVE, Material.BLACK_WOOL));

        assertThat(stats.getBlocksMined()).isEqualTo(1);
        assertThat(stats.getBlocksPlaced()).isEqualTo(1);
    }

    @Test
    @DisplayName("Creative blocks are skipped when the ignore setting is enabled")
    void creativeIsSkippedWhenConfigured() {
        initTracker(true);

        tracker.onBlockBreak(breakEvent(GameMode.CREATIVE, Material.STONE));
        tracker.onBlockPlace(placeEvent(GameMode.CREATIVE, Material.BLACK_WOOL));

        assertThat(stats.getBlocksMined()).isZero();
        assertThat(stats.getBlocksPlaced()).isZero();
    }

    @Test
    @DisplayName("Survival blocks count regardless of the ignore setting")
    void survivalAlwaysCounts() {
        initTracker(true);

        tracker.onBlockBreak(breakEvent(GameMode.SURVIVAL, Material.STONE));
        tracker.onBlockPlace(placeEvent(GameMode.SURVIVAL, Material.BLACK_WOOL));

        assertThat(stats.getBlocksMined()).isEqualTo(1);
        assertThat(stats.getBlocksPlaced()).isEqualTo(1);
    }

    private void initTracker(boolean ignoreCreative) {
        SMPWrapped plugin = mock(SMPWrapped.class);
        ConfigManager configManager = mock(ConfigManager.class);
        PluginConfig config = mock(PluginConfig.class);
        DatabaseManager database = mock(DatabaseManager.class);

        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.get()).thenReturn(config);
        when(config.currentSeasonId()).thenReturn("season-1");
        when(config.trackBlocks()).thenReturn(true);
        when(config.ignoreCreativeBlocks()).thenReturn(ignoreCreative);
        when(plugin.getDatabase()).thenReturn(database);
        when(database.loadPlayerStats(any(UUID.class), anyString()))
            .thenAnswer(invocation -> new PlayerStats(invocation.getArgument(0), "season-1"));

        tracker = new StatTracker(plugin);
        stats = tracker.getBuffer().getBuffer(PLAYER_ID);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.getName()).thenReturn("WinniePat");
    }

    private BlockBreakEvent breakEvent(GameMode gameMode, Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(player.getGameMode()).thenReturn(gameMode);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getBlock()).thenReturn(block);
        return event;
    }

    private BlockPlaceEvent placeEvent(GameMode gameMode, Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(player.getGameMode()).thenReturn(gameMode);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getBlock()).thenReturn(block);
        return event;
    }

    @Test
    @DisplayName("Breaking a block in survival increments blocks mined")
    void survivalBreakCounts() {
        tracker.onBlockBreak(breakEvent(GameMode.SURVIVAL, Material.STONE));

        assertThat(stats.getBlocksMined()).isEqualTo(1);
        assertThat(stats.getBlocksMinedByType()).containsEntry("STONE", 1L);
    }

    @Test
    @DisplayName("Repeated breaks accumulate per material")
    void repeatedBreaksAccumulate() {
        tracker.onBlockBreak(breakEvent(GameMode.SURVIVAL, Material.STONE));
        tracker.onBlockBreak(breakEvent(GameMode.SURVIVAL, Material.STONE));
        tracker.onBlockBreak(breakEvent(GameMode.SURVIVAL, Material.DIRT));

        assertThat(stats.getBlocksMined()).isEqualTo(3);
        assertThat(stats.getBlocksMinedByType()).containsEntry("STONE", 2L);
        assertThat(stats.getBlocksMinedByType()).containsEntry("DIRT", 1L);
    }

    @Test
    @DisplayName("Adventurer breaks count too")
    void otherGameModesCount() {
        tracker.onBlockBreak(breakEvent(GameMode.ADVENTURE, Material.STONE));

        assertThat(stats.getBlocksMined()).isEqualTo(1);
    }

    @Test
    @DisplayName("Placing a block in survival increments blocks placed")
    void survivalPlaceCounts() {
        tracker.onBlockPlace(placeEvent(GameMode.SURVIVAL, Material.BLACK_WOOL));

        assertThat(stats.getBlocksPlaced()).isEqualTo(1);
        assertThat(stats.getBlocksPlacedByType()).containsEntry("BLACK_WOOL", 1L);
    }

    @Test
    @DisplayName("Spectator is skipped when the ignore setting is enabled")
    void spectatorIsSkippedWhenConfigured() {
        initTracker(true);

        tracker.onBlockBreak(breakEvent(GameMode.SPECTATOR, Material.STONE));
        tracker.onBlockPlace(placeEvent(GameMode.SPECTATOR, Material.BLACK_WOOL));

        assertThat(stats.getBlocksMined()).isZero();
        assertThat(stats.getBlocksPlaced()).isZero();
    }

    @Test
    @DisplayName("Air is never counted")
    void airIsIgnored() {
        tracker.onBlockBreak(breakEvent(GameMode.SURVIVAL, Material.AIR));
        tracker.onBlockPlace(placeEvent(GameMode.SURVIVAL, Material.AIR));

        assertThat(stats.getBlocksMined()).isZero();
        assertThat(stats.getBlocksPlaced()).isZero();
    }

    @Test
    @DisplayName("Cave air and void air are never counted")
    void otherAirVariantsAreIgnored() {
        tracker.onBlockBreak(breakEvent(GameMode.SURVIVAL, Material.CAVE_AIR));
        tracker.onBlockBreak(breakEvent(GameMode.SURVIVAL, Material.VOID_AIR));

        assertThat(stats.getBlocksMined()).isZero();
    }

    @Test
    @DisplayName("Block tracking marks the player dirty so the next autosave persists it")
    void blockTrackingMarksDirty() {
        assertThat(tracker.dirtyPlayerCount()).isZero();

        tracker.onBlockBreak(breakEvent(GameMode.SURVIVAL, Material.STONE));

        assertThat(tracker.dirtyPlayerCount()).isEqualTo(1);
    }
}