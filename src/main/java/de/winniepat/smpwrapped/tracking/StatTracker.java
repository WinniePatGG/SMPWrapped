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
import java.util.concurrent.ConcurrentHashMap;


public class StatTracker implements Listener {

    private static final SMPLogger LOG = SMPLogger.get("tracking");

    private static final double MAX_STEP_DISTANCE = 150.0;

    private final SMPWrapped plugin;
    private final PlayerDataBuffer buffer;

    private volatile String seasonId;
    private final Map<UUID, Long> lastSampleTimes = new HashMap<>();
    private final Map<UUID, String> playerDimensions = new HashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

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
        dirtyPlayers.clear();
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

    public int dirtyPlayerCount() {
        return dirtyPlayers.size();
    }

    private PlayerStats markDirty(UUID playerId) {
        dirtyPlayers.add(playerId);
        return buffer.getBuffer(playerId);
    }

    

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        guard("onPlayerJoin", () -> {
            Player player = event.getPlayer();
            if (!plugin.getConfigManager().get().trackPlaytime()) return;

            plugin.getDatabase().upsertPlayer(new TrackedPlayer(
                player.getUniqueId(),
                player.getName(),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false
            ));

            String dimension = player.getWorld().getEnvironment().name();
            playerDimensions.put(player.getUniqueId(), dimension);

            PlayerStats stats = markDirty(player.getUniqueId());
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

        PlayerStats stats = markDirty(uuid);
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
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigManager().get().trackDistance()) return;

        Location to = event.getTo();
        if (to == null) return;

        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double step = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(step) || step <= 0.0) {
            return;
        }
        if (step > MAX_STEP_DISTANCE) {
            LOG.trace("Ignoring an implausible movement step (teleport)",
                LogContext.of("player", event.getPlayer().getName(), "step", step));
            return;
        }

        markDirty(event.getPlayer().getUniqueId()).addDistance(step);
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfigManager().get().trackBlocks()) return;
        Player player = event.getPlayer();
        if (isIgnoredGameMode(player)) return;

        Material material = event.getBlock().getType();
        if (isAir(material)) return;

        PlayerStats stats = markDirty(player.getUniqueId());
        stats.addBlocksMined(1);
        stats.addBlockMined(material.name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfigManager().get().trackBlocks()) return;
        Player player = event.getPlayer();
        if (isIgnoredGameMode(player)) return;

        Material material = event.getBlock().getType();
        if (isAir(material)) return;

        PlayerStats stats = markDirty(player.getUniqueId());
        stats.addBlocksPlaced(1);
        stats.addBlockPlaced(material.name(), 1);
    }

    private boolean isIgnoredGameMode(Player player) {
        if (!plugin.getConfigManager().get().ignoreCreativeBlocks()) {
            return false;
        }
        GameMode gameMode = player.getGameMode();
        return gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityKill(EntityDeathEvent event) {
        if (!plugin.getConfigManager().get().trackMobs()) return;
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        String entityType = entity.getType().name();
        PlayerStats stats = markDirty(killer.getUniqueId());
        stats.addMobsKilled(1);
        stats.addMobKilled(entityType, 1);
    }

    

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().get().trackDeaths()) return;
        Player player = event.getEntity();
        PlayerStats stats = markDirty(player.getUniqueId());
        stats.addDeath();
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigManager().get().trackDamage()) return;

        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            PlayerStats stats = markDirty(player.getUniqueId());
            stats.addDamageDealt((long) event.getDamage());
        }

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            PlayerStats stats = markDirty(player.getUniqueId());
            stats.addDamageReceived((long) event.getDamage());
        }
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!plugin.getConfigManager().get().trackCrafting()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ItemStack result = event.getRecipe().getResult();
        int amount = result.getAmount();

        PlayerStats stats = markDirty(player.getUniqueId());
        stats.addItemsCrafted(amount);
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (!plugin.getConfigManager().get().trackCrafting()) return;
        Player player = event.getPlayer();
        PlayerStats stats = markDirty(player.getUniqueId());
        stats.addItemsCollected(event.getItem().getItemStack().getAmount());
    }

    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!plugin.getConfigManager().get().trackFishing()) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        PlayerStats stats = markDirty(player.getUniqueId());
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
        Set<UUID> pending = new HashSet<>(dirtyPlayers);
        dirtyPlayers.removeAll(pending);
        if (pending.isEmpty()) {
            LOG.trace("Nothing to flush, no stats changed since the last autosave");
            return;
        }

        int saved = 0;
        int failed = 0;
        for (UUID playerId : pending) {
            PlayerStats stats = buffer.peekBuffer(playerId).orElse(null);
            if (stats == null) {
                continue;
            }
            try {
                plugin.getDatabase().savePlayerStats(stats);
                saved++;
            } catch (Throwable t) {
                failed++;
                dirtyPlayers.add(playerId);
                LOG.error("Could not flush a player stat buffer", t, LogContext.of(
                    "player", playerId, "season", stats.getSeasonId()));
            }
        }

        if (failed > 0) {
            LOG.error("Flushing player stats finished with failures", null, LogContext.of(
                "pending", pending.size(), "saved", saved, "failed", failed));
        } else {
            LOG.info("Flushed player stat buffers to the database", LogContext.of(
                "flushed", saved, "tracked", buffer.getAllBuffers().size()));
        }
    }

    public void flushPlayer(UUID playerId) {
        PlayerStats stats = buffer.peekBuffer(playerId).orElse(null);
        if (stats == null) {
            return;
        }
        try {
            plugin.getDatabase().savePlayerStats(stats);
            dirtyPlayers.remove(playerId);
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
            dirtyPlayers.clear();
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