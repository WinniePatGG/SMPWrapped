package de.winniepat.smpwrapped;

import de.winniepat.smpwrapped.command.WrappedCommand;
import de.winniepat.smpwrapped.config.ConfigManager;
import de.winniepat.smpwrapped.db.DatabaseManager;
import de.winniepat.smpwrapped.season.SeasonManager;
import de.winniepat.smpwrapped.tracking.StatTracker;
import de.winniepat.smpwrapped.web.WebServerManager;
import de.winniepat.smpwrapped.wrapped.WrappedGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SMPWrapped - Minecraft SMP Wrapped Plugin + Web Dashboard
 *
 * Tracks player statistics across an SMP season and generates
 * beautiful, personalized Minecraft Wrapped pages.
 */
public final class SMPWrapped extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager database;
    private StatTracker statTracker;
    private SeasonManager seasonManager;
    private WrappedGenerator wrappedGenerator;
    private WebServerManager webServerManager;
    private ScheduledExecutorService autosaveExecutor;

    @Override
    public void onEnable() {
        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.load();

        // Initialize database
        database = new DatabaseManager(this);
        database.initialize();

        // Initialize tracking
        statTracker = new StatTracker(this);
        getServer().getPluginManager().registerEvents(statTracker, this);

        // Sample online sessions every minute (main thread) so playtime is
        // attributed to the correct hour/day and dimension.
        getServer().getScheduler().runTaskTimer(this, statTracker::sampleOnlinePlayers, 1200L, 1200L);

        // Initialize season management
        seasonManager = new SeasonManager(this);

        // Initialize wrapped generator
        wrappedGenerator = new WrappedGenerator(this);

        // Initialize web server
        webServerManager = new WebServerManager(this);
        webServerManager.start();

        // Register commands
        WrappedCommand command = new WrappedCommand(this);
        getCommand("wrapped").setExecutor(command);
        getCommand("wrapped").setTabCompleter(command);

        // Start autosave
        startAutosave();

        getLogger().info("SMPWrapped enabled successfully!");
        getLogger().info("Web dashboard: " + configManager.get().publicBaseUrl());
    }

    @Override
    public void onDisable() {
        // Stop autosave
        if (autosaveExecutor != null) {
            autosaveExecutor.shutdown();
        }

        // Flush all stats
        if (statTracker != null) {
            statTracker.shutdown();
        }

        // Stop web server
        if (webServerManager != null) {
            webServerManager.stop();
        }

        // Close database
        if (database != null) {
            database.shutdown();
        }

        getLogger().info("SMPWrapped disabled.");
    }

    private void startAutosave() {
        int interval = configManager.get().autosaveIntervalSeconds();
        autosaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SMPWrapped-Autosave");
            t.setDaemon(true);
            return t;
        });
        autosaveExecutor.scheduleAtFixedRate(() -> {
            try {
                statTracker.flushAll();
            } catch (Exception e) {
                getLogger().warning("Autosave failed: " + e.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    // Getters
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabase() { return database; }
    public StatTracker getStatTracker() { return statTracker; }
    public SeasonManager getSeasonManager() { return seasonManager; }
    public WrappedGenerator getWrappedGenerator() { return wrappedGenerator; }
}
