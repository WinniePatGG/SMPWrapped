package de.winniepat.smpwrapped.config;

import java.util.*;


public record PluginConfig(
    
    boolean webEnabled,
    String webHost,
    int webPort,
    String publicBaseUrl,

    
    String databasePath,

    
    String currentSeasonId,

    
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

    
    int autosaveIntervalSeconds,

    
    int webCacheSeconds,

    
    int rateLimitRequestsPerMinute,

    
    String adminToken,

    
    boolean leaderboardsPublic,
    int leaderboardMinSampleSize,

    
    boolean playerProfilesPublic,
    boolean allowPlayerOptOut,

    
    String avatarUrlTemplate,

    
    String serverName,
    String serverIconUrl,

    
    boolean shareCardEnabled,
    String shareCardFooter,

    
    boolean autoPublishOnEnd,

    
    boolean weeklyWrappedEnabled,
    int weeklyWrappedDayOfWeek,   
    int weeklyWrappedHour,        
    int weeklyWrappedMinute,      

    boolean monthlyWrappedEnabled,
    int monthlyWrappedDay,        
    int monthlyWrappedHour,
    int monthlyWrappedMinute
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
