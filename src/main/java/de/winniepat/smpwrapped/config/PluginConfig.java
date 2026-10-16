package de.winniepat.smpwrapped.config;

import java.util.*;

/**
 * Immutable configuration holder for SMPWrapped.
 * Loaded and validated by {@link ConfigManager}.
 */
public record PluginConfig(
    // Web server
    boolean webEnabled,
    String webHost,
    int webPort,
    String publicBaseUrl,

    // Database
    String databasePath,

    // Current season
    String currentSeasonId,

    // Tracking toggles
    boolean trackPlaytime,
    boolean trackBlocks,
    boolean trackDistance,
    boolean trackMobs,
    boolean trackDeaths,
    boolean trackDamage,
    boolean trackCrafting,
    boolean trackFishing,
    boolean trackDimensions,
    boolean trackSessions,
    boolean trackAchievements,

    // Autosave
    int autosaveIntervalSeconds,

    // Web cache
    int webCacheSeconds,

    // Rate limiting
    int rateLimitRequestsPerMinute,

    // Admin
    String adminToken,

    // Leaderboard
    boolean leaderboardsPublic,
    int leaderboardMinSampleSize,

    // Privacy
    boolean playerProfilesPublic,
    boolean allowPlayerOptOut,

    // Avatars
    String avatarUrlTemplate,

    // Branding
    String serverName,
    String serverIconUrl,

    // Share card
    boolean shareCardEnabled,
    String shareCardFooter,

    // Publication
    boolean autoPublishOnEnd
) {
    public PluginConfig {
        Objects.requireNonNull(webHost, "webHost");
        Objects.requireNonNull(publicBaseUrl, "publicBaseUrl");
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(currentSeasonId, "currentSeasonId");
        Objects.requireNonNull(adminToken, "adminToken");
        Objects.requireNonNull(serverName, "serverName");
        Objects.requireNonNull(shareCardFooter, "shareCardFooter");
    }

    public boolean isTrackingEnabled() {
        return trackPlaytime || trackBlocks || trackDistance || trackMobs ||
               trackDeaths || trackDamage || trackCrafting || trackFishing ||
               trackDimensions || trackSessions || trackAchievements;
    }
}
