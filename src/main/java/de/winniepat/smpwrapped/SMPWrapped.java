package de.winniepat.smpwrapped;

import de.winniepat.smpwrapped.command.WrappedCommand;
import de.winniepat.smpwrapped.config.ConfigManager;
import de.winniepat.smpwrapped.db.DatabaseManager;
import de.winniepat.smpwrapped.season.SeasonManager;
import de.winniepat.smpwrapped.tracking.StatTracker;
import de.winniepat.smpwrapped.web.WebServerManager;
import de.winniepat.smpwrapped.wrapped.WrappedGenerator;
import de.winniepat.smpwrapped.wrapped.WrappedScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SMPWrapped extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager database;
    private StatTracker statTracker;
    private SeasonManager seasonManager;
    private WrappedGenerator wrappedGenerator;
    private WrappedScheduler wrappedScheduler;
    private WebServerManager webServerManager;
    private ScheduledExecutorService autosaveExecutor;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        database = new DatabaseManager(this);
        database.initialize();

        statTracker = new StatTracker(this);
        getServer().getPluginManager().registerEvents(statTracker, this);

        getServer().getScheduler().runTaskTimer(this, statTracker::sampleOnlinePlayers, 1200L, 1200L);

        seasonManager = new SeasonManager(this);

        wrappedGenerator = new WrappedGenerator(this);

        wrappedScheduler = new WrappedScheduler(this);
        wrappedScheduler.start();

        webServerManager = new WebServerManager(this);
        webServerManager.start();

        WrappedCommand command = new WrappedCommand(this);
        getCommand("wrapped").setExecutor(command);
        getCommand("wrapped").setTabCompleter(command);

        startAutosave();

        getLogger().info("SMPWrapped enabled successfully!");
        getLogger().info("Web dashboard: " + configManager.get().publicBaseUrl());
    }

    @Override
    public void onDisable() {
        if (wrappedScheduler != null) {
            wrappedScheduler.stop();
        }

        if (autosaveExecutor != null) {
            autosaveExecutor.shutdown();
        }

        if (statTracker != null) {
            statTracker.shutdown();
        }

        if (webServerManager != null) {
            webServerManager.stop();
        }

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

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabase() { return database; }
    public StatTracker getStatTracker() { return statTracker; }
    public SeasonManager getSeasonManager() { return seasonManager; }
    public WrappedGenerator getWrappedGenerator() { return wrappedGenerator; }
    public WrappedScheduler getWrappedScheduler() { return wrappedScheduler; }
}
