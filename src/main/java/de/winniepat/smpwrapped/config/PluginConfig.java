package de.winniepat.smpwrapped.config;

import de.winniepat.smpwrapped.log.LogLevel;
import de.winniepat.smpwrapped.log.LogSettings;

import java.util.*;


public final class PluginConfig {

    private final boolean webEnabled;
    private final String webHost;
    private final int webPort;
    private final String publicBaseUrl;
    private final String databasePath;
    private final String currentSeasonId;
    private final boolean trackPlaytime;
    private final boolean trackBlocks;
    private final boolean trackDistance;
    private final boolean trackMobs;
    private final boolean trackDeaths;
    private final boolean trackDamage;
    private final boolean trackCrafting;
    private final boolean trackFishing;
    private final boolean trackDimensions;
    private final boolean trackSessions;
    private final boolean trackAchievements;
    private final boolean ignoreCreativeBlocks;
    private final int autosaveIntervalSeconds;
    private final int webCacheSeconds;
    private final int webSnapshotRefreshSeconds;
    private final int rateLimitRequestsPerMinute;
    private final String adminToken;
    private final boolean leaderboardsPublic;
    private final int leaderboardMinSampleSize;
    private final boolean playerProfilesPublic;
    private final boolean allowPlayerOptOut;
    private final String avatarUrlTemplate;
    private final String serverName;
    private final String serverIconUrl;
    private final boolean shareCardEnabled;
    private final String shareCardFooter;
    private final boolean autoPublishOnEnd;
    private final boolean weeklyWrappedEnabled;
    private final int weeklyWrappedDayOfWeek;
    private final int weeklyWrappedHour;
    private final int weeklyWrappedMinute;
    private final boolean monthlyWrappedEnabled;
    private final int monthlyWrappedDay;
    private final int monthlyWrappedHour;
    private final int monthlyWrappedMinute;
    private final String logLevel;
    private final boolean logConsole;
    private final boolean logFile;
    private final long logMaxFileSizeMb;
    private final int logKeepFiles;
    private final int logQueueCapacity;
    private final boolean logStackTraces;
    private final int logStackTraceDepth;
    private final boolean logDedupe;
    private final int logDedupeThreshold;
    private final long logDedupeWindowSeconds;
    private final long logDedupeThrottleSeconds;
    private final boolean logRedactSecrets;
    private final boolean logSummaryOnShutdown;

    public PluginConfig(
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
        Objects.requireNonNull(webHost, "webHost");
        Objects.requireNonNull(publicBaseUrl, "publicBaseUrl");
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(currentSeasonId, "currentSeasonId");
        Objects.requireNonNull(adminToken, "adminToken");
        Objects.requireNonNull(serverName, "serverName");
        Objects.requireNonNull(shareCardFooter, "shareCardFooter");
        this.webEnabled = webEnabled;
        this.webHost = webHost;
        this.webPort = webPort;
        this.publicBaseUrl = publicBaseUrl;
        this.databasePath = databasePath;
        this.currentSeasonId = currentSeasonId;
        this.trackPlaytime = trackPlaytime;
        this.trackBlocks = trackBlocks;
        this.trackDistance = trackDistance;
        this.trackMobs = trackMobs;
        this.trackDeaths = trackDeaths;
        this.trackDamage = trackDamage;
        this.trackCrafting = trackCrafting;
        this.trackFishing = trackFishing;
        this.trackDimensions = trackDimensions;
        this.trackSessions = trackSessions;
        this.trackAchievements = trackAchievements;
        this.ignoreCreativeBlocks = ignoreCreativeBlocks;
        this.autosaveIntervalSeconds = autosaveIntervalSeconds;
        this.webCacheSeconds = webCacheSeconds;
        this.webSnapshotRefreshSeconds = webSnapshotRefreshSeconds;
        this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
        this.adminToken = adminToken;
        this.leaderboardsPublic = leaderboardsPublic;
        this.leaderboardMinSampleSize = leaderboardMinSampleSize;
        this.playerProfilesPublic = playerProfilesPublic;
        this.allowPlayerOptOut = allowPlayerOptOut;
        this.avatarUrlTemplate = avatarUrlTemplate;
        this.serverName = serverName;
        this.serverIconUrl = serverIconUrl;
        this.shareCardEnabled = shareCardEnabled;
        this.shareCardFooter = shareCardFooter;
        this.autoPublishOnEnd = autoPublishOnEnd;
        this.weeklyWrappedEnabled = weeklyWrappedEnabled;
        this.weeklyWrappedDayOfWeek = weeklyWrappedDayOfWeek;
        this.weeklyWrappedHour = weeklyWrappedHour;
        this.weeklyWrappedMinute = weeklyWrappedMinute;
        this.monthlyWrappedEnabled = monthlyWrappedEnabled;
        this.monthlyWrappedDay = monthlyWrappedDay;
        this.monthlyWrappedHour = monthlyWrappedHour;
        this.monthlyWrappedMinute = monthlyWrappedMinute;
        this.logLevel = logLevel;
        this.logConsole = logConsole;
        this.logFile = logFile;
        this.logMaxFileSizeMb = logMaxFileSizeMb;
        this.logKeepFiles = logKeepFiles;
        this.logQueueCapacity = logQueueCapacity;
        this.logStackTraces = logStackTraces;
        this.logStackTraceDepth = logStackTraceDepth;
        this.logDedupe = logDedupe;
        this.logDedupeThreshold = logDedupeThreshold;
        this.logDedupeWindowSeconds = logDedupeWindowSeconds;
        this.logDedupeThrottleSeconds = logDedupeThrottleSeconds;
        this.logRedactSecrets = logRedactSecrets;
        this.logSummaryOnShutdown = logSummaryOnShutdown;
    }

    public boolean webEnabled() { return webEnabled; }
    public String webHost() { return webHost; }
    public int webPort() { return webPort; }
    public String publicBaseUrl() { return publicBaseUrl; }
    public String databasePath() { return databasePath; }
    public String currentSeasonId() { return currentSeasonId; }
    public boolean trackPlaytime() { return trackPlaytime; }
    public boolean trackBlocks() { return trackBlocks; }
    public boolean trackDistance() { return trackDistance; }
    public boolean trackMobs() { return trackMobs; }
    public boolean trackDeaths() { return trackDeaths; }
    public boolean trackDamage() { return trackDamage; }
    public boolean trackCrafting() { return trackCrafting; }
    public boolean trackFishing() { return trackFishing; }
    public boolean trackDimensions() { return trackDimensions; }
    public boolean trackSessions() { return trackSessions; }
    public boolean trackAchievements() { return trackAchievements; }
    public boolean ignoreCreativeBlocks() { return ignoreCreativeBlocks; }
    public int autosaveIntervalSeconds() { return autosaveIntervalSeconds; }
    public int webCacheSeconds() { return webCacheSeconds; }
    public int webSnapshotRefreshSeconds() { return webSnapshotRefreshSeconds; }
    public int rateLimitRequestsPerMinute() { return rateLimitRequestsPerMinute; }
    public String adminToken() { return adminToken; }
    public boolean leaderboardsPublic() { return leaderboardsPublic; }
    public int leaderboardMinSampleSize() { return leaderboardMinSampleSize; }
    public boolean playerProfilesPublic() { return playerProfilesPublic; }
    public boolean allowPlayerOptOut() { return allowPlayerOptOut; }
    public String avatarUrlTemplate() { return avatarUrlTemplate; }
    public String serverName() { return serverName; }
    public String serverIconUrl() { return serverIconUrl; }
    public boolean shareCardEnabled() { return shareCardEnabled; }
    public String shareCardFooter() { return shareCardFooter; }
    public boolean autoPublishOnEnd() { return autoPublishOnEnd; }
    public boolean weeklyWrappedEnabled() { return weeklyWrappedEnabled; }
    public int weeklyWrappedDayOfWeek() { return weeklyWrappedDayOfWeek; }
    public int weeklyWrappedHour() { return weeklyWrappedHour; }
    public int weeklyWrappedMinute() { return weeklyWrappedMinute; }
    public boolean monthlyWrappedEnabled() { return monthlyWrappedEnabled; }
    public int monthlyWrappedDay() { return monthlyWrappedDay; }
    public int monthlyWrappedHour() { return monthlyWrappedHour; }
    public int monthlyWrappedMinute() { return monthlyWrappedMinute; }
    public String logLevel() { return logLevel; }
    public boolean logConsole() { return logConsole; }
    public boolean logFile() { return logFile; }
    public long logMaxFileSizeMb() { return logMaxFileSizeMb; }
    public int logKeepFiles() { return logKeepFiles; }
    public int logQueueCapacity() { return logQueueCapacity; }
    public boolean logStackTraces() { return logStackTraces; }
    public int logStackTraceDepth() { return logStackTraceDepth; }
    public boolean logDedupe() { return logDedupe; }
    public int logDedupeThreshold() { return logDedupeThreshold; }
    public long logDedupeWindowSeconds() { return logDedupeWindowSeconds; }
    public long logDedupeThrottleSeconds() { return logDedupeThrottleSeconds; }
    public boolean logRedactSecrets() { return logRedactSecrets; }
    public boolean logSummaryOnShutdown() { return logSummaryOnShutdown; }

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
