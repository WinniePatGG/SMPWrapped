package de.winniepat.smpwrapped.config;

import de.winniepat.smpwrapped.log.LogLevel;
import de.winniepat.smpwrapped.log.LogSettings;

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
    boolean ignoreCreativeBlocks,

    
    int autosaveIntervalSeconds,

    
    int webCacheSeconds,
    int webSnapshotRefreshSeconds,

    
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
    int monthlyWrappedMinute,

    
    String logLevel,
    boolean logConsole,
    boolean logFile,
    long logMaxFileSizeMb,
    int logKeepFiles,
    int logQueueCapacity,
    boolean logStackTraces,
    int logStackTraceDepth,
    boolean logDedupe,
    int logDedupeThreshold,
    long logDedupeWindowSeconds,
    long logDedupeThrottleSeconds,
    boolean logRedactSecrets,
    boolean logSummaryOnShutdown
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

    public LogSettings toLogSettings() {
        return LogSettings.builder()
            .level(LogLevel.parse(logLevel, LogLevel.INFO))
            .consoleEnabled(logConsole)
            .fileEnabled(logFile)
            .maxFileSizeMb(logMaxFileSizeMb)
            .keepFiles(logKeepFiles)
            .queueCapacity(logQueueCapacity)
            .stackTraces(logStackTraces)
            .stackTraceDepth(logStackTraceDepth)
            .dedupeEnabled(logDedupe)
            .dedupeThreshold(logDedupeThreshold)
            .dedupeWindowSeconds(logDedupeWindowSeconds)
            .dedupeThrottleSeconds(logDedupeThrottleSeconds)
            .redactSecrets(logRedactSecrets)
            .summaryOnShutdown(logSummaryOnShutdown)
            .build();
    }
}
