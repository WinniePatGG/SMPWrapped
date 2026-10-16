package de.winniepat.smpwrapped.tracking;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.model.*;
import de.winniepat.smpwrapped.config.PluginConfig;
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

    private final SMPWrapped plugin;
    private final PlayerDataBuffer buffer;
    
    private final Map<UUID, Long> lastSampleTimes = new HashMap<>();
    private final Map<UUID, String> playerDimensions = new HashMap<>();

    public StatTracker(SMPWrapped plugin) {
        this.plugin = plugin;
        String seasonId = plugin.getConfigManager().get().currentSeasonId();
        
        this.buffer = new PlayerDataBuffer(seasonId, id -> plugin.getDatabase().loadPlayerStats(id, seasonId));
    }

    public PlayerDataBuffer getBuffer() {
        return buffer;
    }

    

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().get().trackPlaytime()) return;
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            
            plugin.getDatabase().upsertPlayer(new TrackedPlayer(
                player.getUniqueId(),
                player.getName(),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false
            ));
        }
        playerDimensions.put(player.getUniqueId(), player.getWorld().getEnvironment().name());

        PlayerStats stats = buffer.getBuffer(player.getUniqueId());
        stats.addLoginSession();

        
        
        String today = LocalDate.now().toString();
        if (!stats.getDailyPlaytime().containsKey(today)) {
            stats.addUniqueDay();
        }
        stats.addDailyPlaytime(today, 0L);
        lastSampleTimes.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getConfigManager().get().trackPlaytime()) return;
        Player player = event.getPlayer();

        
        samplePlayer(player, System.currentTimeMillis());
        lastSampleTimes.remove(player.getUniqueId());
        playerDimensions.remove(player.getUniqueId());

        
        flushPlayer(player.getUniqueId());
    }

    
    private void samplePlayer(Player player, long now) {
        UUID uuid = player.getUniqueId();
        Long last = lastSampleTimes.put(uuid, now);
        if (last == null) return;

        long seconds = (now - last) / 1000;
        if (seconds <= 0) return;

        PlayerStats stats = buffer.getBuffer(uuid);
        stats.addPlaytime(seconds);
        stats.addHourlyPlaytime(LocalTime.now().getHour(), seconds);
        stats.addDailyPlaytime(LocalDate.now().toString(), seconds);

        String dimension = playerDimensions.computeIfAbsent(
            uuid, id -> player.getWorld().getEnvironment().name());
        stats.addDimensionTime(dimension, seconds);
    }

    
    public void sampleOnlinePlayers() {
        if (!plugin.getConfigManager().get().trackPlaytime()) return;
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            samplePlayer(player, now);
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
        if (!plugin.getConfigManager().get().trackDimensions()) return;
        Player player = event.getPlayer();

        
        samplePlayer(player, System.currentTimeMillis());
        playerDimensions.put(player.getUniqueId(), player.getWorld().getEnvironment().name());
    }

    

    public void flushAll() {
        for (Map.Entry<UUID, PlayerStats> entry : buffer.getAllBuffers().entrySet()) {
            plugin.getDatabase().savePlayerStats(entry.getValue());
        }
        plugin.getLogger().info("Flushed " + buffer.getAllBuffers().size() + " player stat buffers to database.");
    }

    public void flushPlayer(UUID playerId) {
        PlayerStats stats = buffer.getBuffer(playerId);
        plugin.getDatabase().savePlayerStats(stats);
    }

    public void shutdown() {
        
        sampleOnlinePlayers();
        flushAll();
        lastSampleTimes.clear();
        playerDimensions.clear();
    }
}
