package de.winniepat.smpwrapped.config;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.LogLevel;
import de.winniepat.smpwrapped.log.SMPLogger;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DayOfWeek;
import java.util.*;


public final class ConfigManager {

    private static final SMPLogger LOG = SMPLogger.get("config");

    private final SMPWrapped plugin;
    private PluginConfig config;

    public ConfigManager(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            doLoad();
        } catch (RuntimeException e) {
            LOG.fatal("Configuration could not be loaded", e);
            throw e;
        } catch (Throwable t) {
            LOG.fatal("Configuration could not be loaded", t);
            throw new IllegalStateException("Configuration load failed", t);
        }
    }

    private void doLoad() {
        plugin.saveDefaultConfig();
        FileConfiguration cfg = plugin.getConfig();

        
        cfg.addDefault("web.enabled", true);
        cfg.addDefault("web.host", "127.0.0.1");
        cfg.addDefault("web.port", 8080);
        cfg.addDefault("web.public-base-url", "http://localhost:8080");

        cfg.addDefault("database.path", "smpwrapped.db");

        cfg.addDefault("season.current", "season-1");

        cfg.addDefault("tracking.playtime", true);
        cfg.addDefault("tracking.blocks", true);
        cfg.addDefault("tracking.distance", true);
        cfg.addDefault("tracking.mobs", true);
        cfg.addDefault("tracking.deaths", true);
        cfg.addDefault("tracking.damage", true);
        cfg.addDefault("tracking.crafting", true);
        cfg.addDefault("tracking.fishing", true);
        cfg.addDefault("tracking.dimensions", true);
        cfg.addDefault("tracking.sessions", true);
        cfg.addDefault("tracking.achievements", true);
        cfg.addDefault("tracking.ignore-creative", true);

        cfg.addDefault("autosave.interval-seconds", 30);

        cfg.addDefault("web.cache-seconds", 300);
        cfg.addDefault("web.snapshot-refresh-seconds", 30);

        cfg.addDefault("rate-limit.requests-per-minute", 60);

        cfg.addDefault("admin.token", "");

        cfg.addDefault("leaderboard.public", true);
        cfg.addDefault("leaderboard.min-sample-size", 3);

        cfg.addDefault("privacy.player-profiles-public", true);
        cfg.addDefault("privacy.allow-player-opt-out", true);

        cfg.addDefault("avatar.url-template", "https://mc-heads.net/avatar/{uuid}/64");

        cfg.addDefault("branding.server-name", "Our SMP Server");
        cfg.addDefault("branding.server-icon-url", "");

        cfg.addDefault("share-card.enabled", true);
        cfg.addDefault("share-card.footer", "Wrapped with SMPWrapped");

        cfg.addDefault("publication.auto-publish-on-end", false);

        cfg.addDefault("schedule.weekly.enabled", true);
        cfg.addDefault("schedule.weekly.day", "MONDAY");
        cfg.addDefault("schedule.weekly.time", "20:00");
        cfg.addDefault("schedule.monthly.enabled", true);
        cfg.addDefault("schedule.monthly.day", 1);
        cfg.addDefault("schedule.monthly.time", "20:00");

        cfg.addDefault("logging.level", "INFO");
        cfg.addDefault("logging.console", true);
        cfg.addDefault("logging.file.enabled", true);
        cfg.addDefault("logging.file.max-size-mb", 5L);
        cfg.addDefault("logging.file.keep-files", 5);
        cfg.addDefault("logging.file.queue-capacity", 8192);
        cfg.addDefault("logging.stack-traces", true);
        cfg.addDefault("logging.stack-trace-depth", 30);
        cfg.addDefault("logging.dedupe.enabled", true);
        cfg.addDefault("logging.dedupe.threshold", 5);
        cfg.addDefault("logging.dedupe.window-seconds", 300L);
        cfg.addDefault("logging.dedupe.throttle-seconds", 300L);
        cfg.addDefault("logging.redact-secrets", true);
        cfg.addDefault("logging.summary-on-shutdown", true);

        cfg.options().copyDefaults(true);
        plugin.saveConfig();

        
        List<String> errors = new ArrayList<>();

        int port = cfg.getInt("web.port", 8080);
        if (port < 1 || port > 65535) {
            errors.add("web.port must be between 1 and 65535");
        }

        int autosave = cfg.getInt("autosave.interval-seconds", 30);
        if (autosave < 5) {
            errors.add("autosave.interval-seconds must be at least 5");
        }

        int cache = cfg.getInt("web.cache-seconds", 300);
        if (cache < 0) {
            errors.add("web.cache-seconds must be non-negative");
        }

        int snapshotRefresh = cfg.getInt("web.snapshot-refresh-seconds", 30);
        if (snapshotRefresh < 0) {
            errors.add("web.snapshot-refresh-seconds must not be negative");
        }

        int rateLimit = cfg.getInt("rate-limit.requests-per-minute", 60);
        if (rateLimit < 1) {
            errors.add("rate-limit.requests-per-minute must be at least 1");
        }

        int minSample = cfg.getInt("leaderboard.min-sample-size", 3);
        if (minSample < 1) {
            errors.add("leaderboard.min-sample-size must be at least 1");
        }

        String host = cfg.getString("web.host", "127.0.0.1");
        if (host == null || host.isBlank()) {
            errors.add("web.host must not be empty");
        }

        String dbPath = cfg.getString("database.path", "");
        if (dbPath == null || dbPath.isBlank()) {
            errors.add("database.path must not be empty");
        }

        
        int weeklyDay = parseDayOfWeek(cfg.getString("schedule.weekly.day", "MONDAY"), errors);
        int[] weeklyTime = parseTime(cfg.getString("schedule.weekly.time", "20:00"), "schedule.weekly.time", errors);
        int monthlyDay = cfg.getInt("schedule.monthly.day", 1);
        if (monthlyDay < 1 || monthlyDay > 31) {
            errors.add("schedule.monthly.day must be between 1 and 31");
        }
        int[] monthlyTime = parseTime(cfg.getString("schedule.monthly.time", "20:00"), "schedule.monthly.time", errors);

        
        String logLevel = LogLevel.parse(cfg.getString("logging.level", "INFO"), LogLevel.INFO).name();
        String requestedLevel = cfg.getString("logging.level", "INFO");
        if (!requestedLevel.equalsIgnoreCase(logLevel)) {
            LOG.warn("Unknown logging level, falling back to INFO",
                LogContext.of("configured", requestedLevel, "fallback", logLevel));
        }

        int logKeepFiles = cfg.getInt("logging.file.keep-files", 5);
        if (logKeepFiles < 0 || logKeepFiles > 100) {
            errors.add("logging.file.keep-files must be between 0 and 100");
        }

        int logQueueCapacity = cfg.getInt("logging.file.queue-capacity", 8192);
        if (logQueueCapacity < 256 || logQueueCapacity > 1_000_000) {
            errors.add("logging.file.queue-capacity must be between 256 and 1000000");
        }

        int logStackTraceDepth = cfg.getInt("logging.stack-trace-depth", 30);
        if (logStackTraceDepth < 0 || logStackTraceDepth > 500) {
            errors.add("logging.stack-trace-depth must be between 0 and 500");
        }

        int logDedupeThreshold = cfg.getInt("logging.dedupe.threshold", 5);
        if (logDedupeThreshold < 0) {
            errors.add("logging.dedupe.threshold must not be negative");
        }

        long logDedupeWindowSeconds = cfg.getLong("logging.dedupe.window-seconds", 300L);
        if (logDedupeWindowSeconds < 1) {
            errors.add("logging.dedupe.window-seconds must be at least 1");
        }

        long logDedupeThrottleSeconds = cfg.getLong("logging.dedupe.throttle-seconds", 300L);
        if (logDedupeThrottleSeconds < 0) {
            errors.add("logging.dedupe.throttle-seconds must not be negative");
        }

        long logMaxFileSizeMb = cfg.getLong("logging.file.max-size-mb", 5L);
        if (logMaxFileSizeMb < 0) {
            errors.add("logging.file.max-size-mb must not be negative");
        }

        String adminToken = cfg.getString("admin.token", "");
        boolean generatedNewToken = false;
        if (adminToken == null || adminToken.isBlank()) {
            
            
            adminToken = UUID.randomUUID().toString().replace("-", "");
            cfg.set("admin.token", adminToken);
            plugin.saveConfig();
            generatedNewToken = true;
        }

        if (!errors.isEmpty()) {
            for (String err : errors) {
                LOG.error("Config validation error", LogContext.of("reason", err));
            }
            throw new IllegalStateException("Configuration validation failed with " + errors.size() + " error(s)");
        }

        this.config = new PluginConfig(
            cfg.getBoolean("web.enabled", true),
            host,
            port,
            cfg.getString("web.public-base-url", "http://localhost:8080"),
            cfg.getString("database.path", "smpwrapped.db"),
            cfg.getString("season.current", "season-1"),
            cfg.getBoolean("tracking.playtime", true),
            cfg.getBoolean("tracking.blocks", true),
            cfg.getBoolean("tracking.distance", true),
            cfg.getBoolean("tracking.mobs", true),
            cfg.getBoolean("tracking.deaths", true),
            cfg.getBoolean("tracking.damage", true),
            cfg.getBoolean("tracking.crafting", true),
            cfg.getBoolean("tracking.fishing", true),
            cfg.getBoolean("tracking.dimensions", true),
            cfg.getBoolean("tracking.sessions", true),
            cfg.getBoolean("tracking.achievements", true),
            cfg.getBoolean("tracking.ignore-creative", true),
            autosave,
            cache,
            snapshotRefresh,
            rateLimit,
            adminToken,
            cfg.getBoolean("leaderboard.public", true),
            minSample,
            cfg.getBoolean("privacy.player-profiles-public", true),
            cfg.getBoolean("privacy.allow-player-opt-out", true),
            cfg.getString("avatar.url-template", "https://mc-heads.net/avatar/{uuid}/64"),
            cfg.getString("branding.server-name", "Our SMP Server"),
            cfg.getString("branding.server-icon-url", ""),
            cfg.getBoolean("share-card.enabled", true),
            cfg.getString("share-card.footer", "Wrapped with SMPWrapped"),
            cfg.getBoolean("publication.auto-publish-on-end", false),
            cfg.getBoolean("schedule.weekly.enabled", true),
            weeklyDay,
            weeklyTime[0],
            weeklyTime[1],
            cfg.getBoolean("schedule.monthly.enabled", true),
            monthlyDay,
            monthlyTime[0],
            monthlyTime[1],
            logLevel,
            cfg.getBoolean("logging.console", true),
            cfg.getBoolean("logging.file.enabled", true),
            logMaxFileSizeMb,
            logKeepFiles,
            logQueueCapacity,
            cfg.getBoolean("logging.stack-traces", true),
            logStackTraceDepth,
            cfg.getBoolean("logging.dedupe.enabled", true),
            logDedupeThreshold,
            logDedupeWindowSeconds,
            logDedupeThrottleSeconds,
            cfg.getBoolean("logging.redact-secrets", true),
            cfg.getBoolean("logging.summary-on-shutdown", true)
        );

        LOG.info("Configuration loaded successfully");
        if (generatedNewToken) {
            LOG.info("Generated a new admin API token and saved it to config.yml",
                LogContext.of("hint", "Read admin.token from config.yml"));
        } else {
            LOG.debug("Admin API token loaded from config.yml");
        }
    }

    public PluginConfig get() {
        if (config == null) {
            throw new IllegalStateException("Configuration not loaded yet");
        }
        return config;
    }

    public void setCurrentSeasonId(String seasonId) {
        if (seasonId == null || seasonId.isBlank()) {
            LOG.warn("Refusing to switch to a blank season id");
            throw new IllegalArgumentException("seasonId must not be blank");
        }
        String previous = config == null ? null : config.currentSeasonId();
        try {
            plugin.getConfig().set("season.current", seasonId);
            plugin.saveConfig();
            plugin.reloadConfig();
            load();
        } catch (RuntimeException e) {
            LOG.error("Could not make the new season current; rolling back season.current", e, LogContext.of(
                "requestedSeason", seasonId, "previousSeason", previous));
            restoreCurrentSeason(previous);
            throw e;
        }
        LOG.info("Current season changed", LogContext.of(
            "previousSeason", previous, "season", config.currentSeasonId()));
    }

    private void restoreCurrentSeason(String previous) {
        if (previous == null || previous.isBlank()) {
            return;
        }
        try {
            plugin.getConfig().set("season.current", previous);
            plugin.saveConfig();
            plugin.reloadConfig();
            load();
        } catch (Throwable t) {
            LOG.error("Could not roll back season.current, fix season.current manually in config.yml", t,
                LogContext.of("expectedSeason", previous));
        }
    }

    public void reload() {
        try {
            plugin.reloadConfig();
            load();
            LOG.info("Configuration reloaded");
        } catch (Throwable t) {
            LOG.error("Configuration reload failed", t);
            throw t instanceof RuntimeException runtime ? runtime : new IllegalStateException(t);
        }
    }

    
    private static int parseDayOfWeek(String value, List<String> errors) {
        if (value != null) {
            String v = value.trim().toUpperCase(Locale.ROOT);
            try {
                int n = Integer.parseInt(v);
                if (n >= 1 && n <= 7) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                
            }
            try {
                return DayOfWeek.valueOf(v).getValue();
            } catch (IllegalArgumentException ignored) {
                
            }
        }
        errors.add("schedule.weekly.day must be a weekday name (MONDAY..SUNDAY) or 1-7, got: " + value);
        return DayOfWeek.MONDAY.getValue();
    }

    
    private static int[] parseTime(String value, String key, List<String> errors) {
        String v = value == null ? "" : value.trim();
        String[] parts = v.split(":");
        if (parts.length == 2) {
            try {
                int hour = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());
                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                    return new int[]{hour, minute};
                }
            } catch (NumberFormatException ignored) {
                
            }
        }
        errors.add(key + " must be in 24-hour HH:mm format, got: " + value);
        return new int[]{20, 0};
    }
}