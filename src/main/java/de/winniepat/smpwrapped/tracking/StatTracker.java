package de.winniepat.smpwrapped.tracking;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.model.*;
import de.winniepat.smpwrapped.config.PluginConfig;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;


public class StatTracker implements Listener {

    private static final SMPLogger LOG = SMPLogger.get("tracking");

    private final SMPWrapped plugin;
    private final PlayerDataBuffer buffer;

    private volatile String seasonId;
    private final Map<UUID, Long> lastSampleTimes = new HashMap<>();
    private final Map<UUID, String> playerDimensions = new HashMap<>();

    public StatTracker(SMPWrapped plugin) {
        this.plugin = plugin;
        this.seasonId = plugin.getConfigManager().get().currentSeasonId();

        this.buffer = new PlayerDataBuffer(seasonId,
            id -> plugin.getDatabase().loadPlayerStats(id, this.seasonId));
        LOG.info("Stat tracker started", LogContext.of("season", seasonId));
    }

    public String getSeasonId() {
        return seasonId;
    }

    public synchronized void switchSeason(String newSeasonId) {
        if (newSeasonId == null || newSeasonId.isBlank()) {
            LOG.warn("Refusing to switch the stat tracker to a blank season");
            return;
        }
        if (newSeasonId.equals(this.seasonId)) {
            LOG.debug("Stat tracker already tracks this season",
                LogContext.of("season", newSeasonId));
            return;
        }

        String previous = this.seasonId;
        try {
            flushAll();
        } catch (Throwable t) {
            LOG.error("Could not flush stats before switching seasons, some progress may be lost", t,
                LogContext.of("from", previous, "to", newSeasonId));
        }

        buffer.clear();
        try {
            buffer.setSeasonId(newSeasonId);
        } catch (RuntimeException e) {
            LOG.error("Could not repoint the stat buffer at the new season", e, LogContext.of(
                "from", previous, "to", newSeasonId));
            return;
        }
        this.seasonId = newSeasonId;
        lastSampleTimes.clear();
        playerDimensions.clear();

        LOG.info("Stat tracker now tracks a new season", LogContext.of(
            "previousSeason", previous, "season", newSeasonId));
    }

    public PlayerDataBuffer getBuffer() {
        return buffer;
    }

    

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        guard("onPlayerJoin", () -> {
            Player player = event.getPlayer();
            if (!plugin.getConfigManager().get().trackPlaytime()) return;
            if (!player.hasPlayedBefore()) {
                
                plugin.getDatabase().upsertPlayer(new TrackedPlayer(
                    player.getUniqueId(),
                    player.getName(),
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    false
                ));
            }
            String dimension = player.getWorld().getEnvironment().name();
            playerDimensions.put(player.getUniqueId(), dimension);

            PlayerStats stats = buffer.getBuffer(player.getUniqueId());
            stats.addLoginSession();

            
            
            String today = LocalDate.now().toString();
            if (!stats.getDailyPlaytime().containsKey(today)) {
                stats.addUniqueDay();
            }
            stats.addDailyPlaytime(today, 0L);
            lastSampleTimes.put(player.getUniqueId(), System.currentTimeMillis());

            LOG.debug("Player joined", LogContext.of(
                "player", player.getName(),
                "uuid", player.getUniqueId(),
                "dimension", dimension));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        guard("onPlayerQuit", () -> {
            Player player = event.getPlayer();

            
            samplePlayer(player, System.currentTimeMillis());
            lastSampleTimes.remove(player.getUniqueId());
            playerDimensions.remove(player.getUniqueId());

            
            flushPlayer(player.getUniqueId());
            LOG.debug("Player left", LogContext.of(
                "player", player.getName(), "uuid", player.getUniqueId()));
        });
    }

    
    private void samplePlayer(Player player, long now) {
        UUID uuid = player.getUniqueId();
        Long last = lastSampleTimes.put(uuid, now);
        if (last == null) {
            LOG.trace("No previous sample point for this player, skipping",
                LogContext.of("player", uuid));
            return;
        }

        long seconds = (now - last) / 1000;
        if (seconds <= 0) {
            return;
        }

        PlayerStats stats = buffer.getBuffer(uuid);
        stats.addPlaytime(seconds);
        stats.addHourlyPlaytime(LocalTime.now().getHour(), seconds);
        stats.addDailyPlaytime(LocalDate.now().toString(), seconds);

        String dimension = playerDimensions.computeIfAbsent(
            uuid, id -> player.getWorld().getEnvironment().name());
        stats.addDimensionTime(dimension, seconds);

        LOG.trace("Sampled playtime", LogContext.of(
            "player", uuid, "seconds", seconds, "dimension", dimension));
    }

    
    public void sampleOnlinePlayers() {
        if (!plugin.getConfigManager().get().trackPlaytime()) return;
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                samplePlayer(player, now);
            } catch (Throwable t) {
                LOG.error("Could not sample playtime for a player", t, LogContext.of(
                    "player", player.getName(), "uuid", player.getUniqueId()));
            }
        }
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfigManager().get().trackBlocks()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return; 

        Material material = event.getBlock().getType();
        if (material.isAir()) return;

        PlayerStats stats = buffer.getBuffer(player.getUniqueId());
        stats.addBlocksMined(1);
        stats.addBlockMined(material.name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfigManager().get().trackBlocks()) return;
        Player player = event.getPlayer();

        Material material = event.getBlock().getType();
        if (material.isAir()) return;

        PlayerStats stats = buffer.getBuffer(player.getUniqueId());
        stats.addBlocksPlaced(1);
        stats.addBlockPlaced(material.name(), 1);
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityKill(EntityDeathEvent event) {
        if (!plugin.getConfigManager().get().trackMobs()) return;
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        String entityType = entity.getType().name();
        PlayerStats stats = buffer.getBuffer(killer.getUniqueId());
        stats.addMobsKilled(1);
        stats.addMobKilled(entityType, 1);
    }

    

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().get().trackDeaths()) return;
        Player player = event.getEntity();
        PlayerStats stats = buffer.getBuffer(player.getUniqueId());
        stats.addDeath();
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigManager().get().trackDamage()) return;

        if (event.getDamager() instanceof Player player) {
            PlayerStats stats = buffer.getBuffer(player.getUniqueId());
            stats.addDamageDealt((long) event.getDamage());
        }

        if (event.getEntity() instanceof Player player) {
            PlayerStats stats = buffer.getBuffer(player.getUniqueId());
            stats.addDamageReceived((long) event.getDamage());
        }
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!plugin.getConfigManager().get().trackCrafting()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe().getResult();
        int amount = result.getAmount();

        PlayerStats stats = buffer.getBuffer(player.getUniqueId());
        stats.addItemsCrafted(amount);
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (!plugin.getConfigManager().get().trackCrafting()) return;
        Player player = event.getPlayer();
        PlayerStats stats = buffer.getBuffer(player.getUniqueId());
        stats.addItemsCollected(event.getItem().getItemStack().getAmount());
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!plugin.getConfigManager().get().trackFishing()) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        PlayerStats stats = buffer.getBuffer(player.getUniqueId());
        stats.addFishingCatch();
    }

    

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        guard("onWorldChange", () -> {
            Player player = event.getPlayer();

            
            samplePlayer(player, System.currentTimeMillis());
            String dimension = player.getWorld().getEnvironment().name();
            playerDimensions.put(player.getUniqueId(), dimension);
            LOG.trace("Player changed dimension",
                LogContext.of("player", player.getName(), "dimension", dimension));
        });
    }

    

    public void flushAll() {
        Map<UUID, PlayerStats> all = buffer.getAllBuffers();
        int saved = 0;
        int failed = 0;
        for (Map.Entry<UUID, PlayerStats> entry : all.entrySet()) {
            try {
                plugin.getDatabase().savePlayerStats(entry.getValue());
                saved++;
            } catch (Throwable t) {
                failed++;
                LOG.error("Could not flush a player stat buffer", t, LogContext.of(
                    "player", entry.getKey(),
                    "season", entry.getValue().getSeasonId()));
            }
        }
        if (failed > 0) {
            LOG.error("Flushing player stats finished with failures", null, LogContext.of(
                "requested", all.size(), "saved", saved, "failed", failed));
        } else {
            LOG.info("Flushed player stat buffers to the database",
                LogContext.of("buffers", all.size()));
        }
    }

    public void flushPlayer(UUID playerId) {
        try {
            PlayerStats stats = buffer.getBuffer(playerId);
            plugin.getDatabase().savePlayerStats(stats);
        } catch (Throwable t) {
            LOG.error("Could not flush a player stat buffer", t, LogContext.of("player", playerId));
        }
    }

    public void shutdown() {
        try {
            sampleOnlinePlayers();
            flushAll();
        } catch (Throwable t) {
            LOG.error("Stat tracker shutdown failed", t);
        } finally {
            lastSampleTimes.clear();
            playerDimensions.clear();
            LOG.info("Stat tracker stopped");
        }
    }

    private void guard(String operation, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            LOG.error("Tracking operation failed", t, LogContext.of("operation", operation));
        }
    }
}