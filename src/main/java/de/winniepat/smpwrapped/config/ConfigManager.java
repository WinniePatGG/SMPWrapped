package de.winniepat.smpwrapped.config;

import de.winniepat.smpwrapped.SMPWrapped;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DayOfWeek;
import java.util.*;


public final class ConfigManager {

    private final SMPWrapped plugin;
    private PluginConfig config;

    public ConfigManager(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    public void load() {
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

        cfg.addDefault("autosave.interval-seconds", 120);

        cfg.addDefault("web.cache-seconds", 300);

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

        cfg.options().copyDefaults(true);
        plugin.saveConfig();

        
        List<String> errors = new ArrayList<>();

        int port = cfg.getInt("web.port", 8080);
        if (port < 1 || port > 65535) {
            errors.add("web.port must be between 1 and 65535");
        }

        int autosave = cfg.getInt("autosave.interval-seconds", 120);
        if (autosave < 10) {
            errors.add("autosave.interval-seconds must be at least 10");
        }

        int cache = cfg.getInt("web.cache-seconds", 300);
        if (cache < 0) {
            errors.add("web.cache-seconds must be non-negative");
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
                plugin.getLogger().severe("Config validation error: " + err);
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
            autosave,
            cache,
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
            monthlyTime[1]
        );

        plugin.getLogger().info("Configuration loaded successfully.");
        if (generatedNewToken) {
            plugin.getLogger().info("Generated new admin API token (saved to config.yml): " + adminToken);
            plugin.getLogger().info("Store this token securely.");
        } else {
            plugin.getLogger().info("Admin API token loaded from config.yml.");
        }
    }

    public PluginConfig get() {
        if (config == null) {
            throw new IllegalStateException("Configuration not loaded yet");
        }
        return config;
    }

    public void reload() {
        plugin.reloadConfig();
        load();
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
