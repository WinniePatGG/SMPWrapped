package de.winniepat.smpwrapped;

import de.winniepat.smpwrapped.command.WrappedCommand;
import de.winniepat.smpwrapped.config.ConfigManager;
import de.winniepat.smpwrapped.db.DatabaseManager;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.LogSettings;
import de.winniepat.smpwrapped.log.Logging;
import de.winniepat.smpwrapped.log.SMPLogger;
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

    private static final SMPLogger LOG = SMPLogger.get("plugin");

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
        Logging.init(getDescription().getVersion(), getDataFolder(), LogSettings.defaults(), getLogger());

        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                LOG.fatal("Uncaught exception in thread", throwable, LogContext.of("thread", thread.getName())));
        } catch (Throwable t) {
            LOG.warn("Could not install the default uncaught exception handler", t);
        }

        LOG.info("Starting SMPWrapped", LogContext.of("version", getDescription().getVersion()));

        configManager = new ConfigManager(this);
        if (!guard("config", () -> configManager.load())) {
            LOG.fatal("Configuration could not be loaded; disabling SMPWrapped");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Logging.init(getDescription().getVersion(), getDataFolder(), configManager.get().toLogSettings(), getLogger());

        if (guard("database", () -> {
            database = new DatabaseManager(this);
            database.initialize();
        })) {
            guard("tracking", () -> {
                statTracker = new StatTracker(this);
                getServer().getPluginManager().registerEvents(statTracker, this);
                getServer().getScheduler().runTaskTimer(this,
                    () -> guard("sampleOnlinePlayers", statTracker::sampleOnlinePlayers),
                    1200L, 1200L);
            });
        }

        guard("seasons", () -> seasonManager = new SeasonManager(this));
        guard("generator", () -> wrappedGenerator = new WrappedGenerator(this));
        guard("scheduler", () -> {
            wrappedScheduler = new WrappedScheduler(this);
            wrappedScheduler.start();
        });
        guard("web", () -> {
            webServerManager = new WebServerManager(this);
            webServerManager.start();
        });

        guard("command", () -> {
            WrappedCommand command = new WrappedCommand(this);
            if (getCommand("wrapped") == null) {
                throw new IllegalStateException("Command 'wrapped' is missing from plugin.yml");
            }
            getCommand("wrapped").setExecutor(command);
            getCommand("wrapped").setTabCompleter(command);
        });

        guard("autosave", this::startAutosave);

        LOG.info("SMPWrapped enabled successfully", LogContext.of(
            "webDashboard", configManager.get().publicBaseUrl(),
            "season", configManager.get().currentSeasonId(),
            "logFile", Logging.logFilePath()));
    }

    @Override
    public void onDisable() {
        LOG.info("Disabling SMPWrapped");

        guard("shutdown-scheduler", () -> {
            if (wrappedScheduler != null) {
                wrappedScheduler.stop();
            }
        });

        if (autosaveExecutor != null) {
            autosaveExecutor.shutdown();
            autosaveExecutor = null;
        }

        guard("shutdown-tracking", () -> {
            if (statTracker != null) {
                statTracker.shutdown();
            }
        });

        guard("shutdown-web", () -> {
            if (webServerManager != null) {
                webServerManager.stop();
            }
        });

        guard("shutdown-database", () -> {
            if (database != null) {
                database.shutdown();
            }
        });

        LOG.info("SMPWrapped disabled");

        Logging.shutdown();
    }

    private void startAutosave() {
        int interval = configManager.get().autosaveIntervalSeconds();
        autosaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SMPWrapped-Autosave");
            t.setDaemon(true);
            return t;
        });
        autosaveExecutor.scheduleAtFixedRate(
            () -> guard("autosave-flush", () -> statTracker.flushAll()),
            interval, interval, TimeUnit.SECONDS);
        LOG.info("Autosave scheduled", LogContext.of("intervalSeconds", interval));
    }

    private boolean guard(String operation, Runnable action) {
        try {
            action.run();
            return true;
        } catch (Throwable t) {
            LOG.error("Startup/shutdown step failed", t, LogContext.of("step", operation));
            return false;
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabase() { return database; }
    public StatTracker getStatTracker() { return statTracker; }
    public SeasonManager getSeasonManager() { return seasonManager; }
    public WrappedGenerator getWrappedGenerator() { return wrappedGenerator; }
    public WrappedScheduler getWrappedScheduler() { return wrappedScheduler; }
}