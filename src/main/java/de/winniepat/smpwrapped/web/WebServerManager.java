package de.winniepat.smpwrapped.web;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.config.PluginConfig;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.*;
import de.winniepat.smpwrapped.wrapped.WrappedGenerator;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class WebServerManager {

    private static final SMPLogger LOG = SMPLogger.get("web");

    private final SMPWrapped plugin;
    private Javalin app;
    private RateLimiter rateLimiter;
    private AdminAuth adminAuth;
    private final Set<String> refreshingSnapshots = ConcurrentHashMap.newKeySet();
    private volatile String indexHtmlCache;

    public WebServerManager(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    public void start() {
        PluginConfig config = plugin.getConfigManager().get();
        if (!config.webEnabled()) {
            LOG.info("Web server is disabled in config");
            return;
        }

        rateLimiter = new RateLimiter(config.rateLimitRequestsPerMinute());
        adminAuth = new AdminAuth(plugin);

        LOG.info("Starting the web server", LogContext.of(
            "host", config.webHost(),
            "port", config.webPort(),
            "rateLimitPerMinute", config.rateLimitRequestsPerMinute()));

        
        
        
        
        
        ClassLoader previousTccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            app = Javalin.create(javalinConfig -> {
                javalinConfig.jsonMapper(new JavalinJackson());
                javalinConfig.showJavalinBanner = false;

                
                File webappDir = new File(plugin.getDataFolder(), "webapp");
                if (webappDir.exists()) {
                    javalinConfig.staticFiles.add(webappDir.getAbsolutePath(), Location.EXTERNAL);
                    LOG.debug("Serving the webapp from disk",
                        LogContext.of("directory", webappDir.getAbsolutePath()));
                } else {
                    
                    javalinConfig.staticFiles.add("/webapp", Location.CLASSPATH);
                    LOG.debug("Serving the webapp from the plugin jar classpath");
                }

                
                
                
                
                javalinConfig.spaRoot.addFile("/", "/webapp/index.html", Location.CLASSPATH);

                
                javalinConfig.bundledPlugins.enableCors(cors -> {
                    cors.addRule(rule -> {
                        rule.anyHost();
                    });
                });
            });

            setupRoutes();
            setupErrorHandling();

            app.start(config.webHost(), config.webPort());
        } catch (Throwable t) {
            if (hasCause(t, BindException.class)) {
                LOG.fatal("Web server could not bind its port, another process may already use it", t,
                    LogContext.of("host", config.webHost(), "port", config.webPort()));
            } else {
                LOG.fatal("Web server failed to start", t, LogContext.of(
                    "host", config.webHost(), "port", config.webPort()));
            }
            app = null;
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw new IllegalStateException(t);
        } finally {
            Thread.currentThread().setContextClassLoader(previousTccl);
        }

        LOG.info("Web server started", LogContext.of(
            "host", config.webHost(), "port", config.webPort()));

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
            () -> guardRateLimiterCleanup(), 20L * 60L, 20L * 60L);
    }

    private void guardRateLimiterCleanup() {
        try {
            if (rateLimiter != null) {
                int before = rateLimiter.trackedClients();
                rateLimiter.cleanup();
                int after = rateLimiter.trackedClients();
                LOG.trace("Rate limiter buckets cleaned up",
                    LogContext.of("before", before, "after", after));
            }
        } catch (Throwable t) {
            LOG.error("Rate limiter cleanup failed", t);
        }
    }

    private void setupRoutes() {
        
        app.get("/api/v1/health", this::handleHealth);

        
        app.get("/api/v1/seasons", this::handleSeasons);
        app.get("/api/v1/seasons/current", this::handleCurrentSeason);
        app.get("/api/v1/seasons/{seasonId}/stats", this::handleSeasonStats);
        app.get("/api/v1/seasons/{seasonId}/leaderboards/{stat}", this::handleLeaderboard);

        
        app.get("/api/v1/players/by-name/{playerName}", this::handlePlayerByName);
        app.get("/api/v1/players/{playerId}", this::handlePlayer);
        app.get("/api/v1/players/{playerId}/wrapped/{seasonId}", this::handlePlayerWrapped);
        app.get("/api/v1/players/{playerId}/stats/{seasonId}", this::handlePlayerStats);

        
        app.post("/api/v1/admin/seasons", this::handleAdminCreateSeason);
        app.post("/api/v1/admin/seasons/{seasonId}/end", this::handleAdminEndSeason);
        app.post("/api/v1/admin/seasons/{seasonId}/publish", this::handleAdminPublishSeason);
        app.post("/api/v1/admin/seasons/{seasonId}/unpublish", this::handleAdminUnpublishSeason);
        app.post("/api/v1/admin/seasons/{seasonId}/switch", this::handleAdminSwitchSeason);
        app.post("/api/v1/admin/backup", this::handleAdminBackup);
        app.post("/api/v1/admin/rebuild/{playerId}/{seasonId}", this::handleAdminRebuildSnapshot);

        
        app.get("/", this::handleRoot);

        app.before(ctx -> applyRateLimit(ctx));
    }

    private void applyRateLimit(Context ctx) {
        if (rateLimiter == null || !ctx.path().startsWith("/api/")) {
            return;
        }
        if (rateLimiter.allowRequest(ctx.ip())) {
            return;
        }
        LOG.warn("Rate limit exceeded", LogContext.of(
            "ip", ctx.ip(),
            "path", ctx.path(),
            "limitPerMinute", plugin.getConfigManager().get().rateLimitRequestsPerMinute()));
        ctx.json(Map.of("error", "Too many requests")).status(HttpStatus.TOO_MANY_REQUESTS);
        ctx.skipRemainingHandlers();
    }

    private void setupErrorHandling() {
        app.error(404, ctx -> {
            LOG.debug("Request not found", LogContext.of(
                "path", ctx.path(), "method", String.valueOf(ctx.method()), "ip", ctx.ip()));
            if (hasNoBody(ctx)) {
                ctx.json(Map.of("error", "Not found")).status(HttpStatus.NOT_FOUND);
            }
        });

        app.error(500, ctx -> {
            LOG.error("Request failed with HTTP 500", null, LogContext.of(
                "path", ctx.path(),
                "method", String.valueOf(ctx.method()),
                "ip", ctx.ip(),
                "responseBody", ctx.result()));
            if (hasNoBody(ctx)) {
                ctx.json(Map.of("error", "Internal server error")).status(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });

        app.exception(Exception.class, (exception, ctx) -> {
            LOG.error("Unhandled exception while handling a request", exception, LogContext.of(
                "path", ctx.path(),
                "method", String.valueOf(ctx.method()),
                "ip", ctx.ip(),
                "userAgent", ctx.userAgent()));
            if (hasNoBody(ctx)) {
                ctx.json(Map.of("error", "Internal server error")).status(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });

        app.after(ctx -> {
            enforceApiJsonResponse(ctx);
            Integer status = ctx.statusCode();
            int code = status == null ? 0 : status;
            Object[] context = LogContext.of(
                "method", String.valueOf(ctx.method()),
                "path", ctx.path(),
                "status", code,
                "ip", ctx.ip());
            if (code >= 500) {
                LOG.error("Request completed with a server error", context);
            } else if (code >= 400) {
                LOG.debug("Request completed with a client error", context);
            } else {
                LOG.trace("Request completed", context);
            }
        });
    }

    private void enforceApiJsonResponse(Context ctx) {
        String path = ctx.path();
        if (path == null || !path.startsWith("/api/")) {
            return;
        }
        String body = ctx.result();
        if (body == null || body.isEmpty()) {
            return;
        }
        if (body.stripLeading().startsWith("<")) {
            LOG.debug("Rewriting an unknown API route as a JSON 404",
                LogContext.of("path", path, "ip", ctx.ip()));
            ctx.json(Map.of("error", "Not found")).status(HttpStatus.NOT_FOUND);
        }
    }

    

    private void handleHealth(Context ctx) {
        ctx.json(Map.of(
            "status", "ok",
            "version", plugin.getDescription().getVersion(),
            "timestamp", System.currentTimeMillis()
        ));
    }

    private void handleSeasons(Context ctx) {
        List<Season> seasons = plugin.getSeasonManager().getAllSeasons();
        String currentId = plugin.getSeasonManager().currentSeasonId();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Season s : seasons) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.id());
            map.put("name", s.name());
            map.put("startTime", s.startTime());
            map.put("endTime", s.endTime());
            map.put("state", s.state().name());
            map.put("active", s.isActive());
            map.put("current", s.id().equals(currentId));
            result.add(map);
        }
        ctx.json(Map.of("seasons", result));
    }

    private void handleCurrentSeason(Context ctx) {
        String currentId = plugin.getSeasonManager().currentSeasonId();
        plugin.getDatabase().getSeason(currentId).ifPresentOrElse(
            season -> ctx.json(Map.of(
                "id", season.id(),
                "name", season.name(),
                "startTime", season.startTime(),
                "endTime", season.endTime(),
                "state", season.state().name(),
                "active", season.isActive()
            )),
            () -> ctx.json(Map.of("error", "No active season")).status(HttpStatus.NOT_FOUND)
        );
    }

    private void handleSeasonStats(Context ctx) {
        String seasonId = ctx.pathParam("seasonId");
        if (!plugin.getSeasonManager().seasonExists(seasonId)) {
            ctx.json(Map.of("error", "Season not found")).status(HttpStatus.NOT_FOUND);
            return;
        }
        Map<String, Long> stats = plugin.getDatabase().getServerWideStats(seasonId);
        ctx.json(Map.of("seasonId", seasonId, "stats", stats));
    }

    private void handleLeaderboard(Context ctx) {
        String seasonId = ctx.pathParam("seasonId");
        String stat = ctx.pathParam("stat");

        if (!plugin.getSeasonManager().seasonExists(seasonId)) {
            ctx.json(Map.of("error", "Season not found")).status(HttpStatus.NOT_FOUND);
            return;
        }

        
        Set<String> validStats = Set.of(
            "playtime_seconds", "blocks_mined", "blocks_placed", "distance_traveled",
            "mobs_killed", "deaths", "items_crafted", "fishing_catches", "unique_days"
        );
        if (!validStats.contains(stat)) {
            ctx.json(Map.of("error", "Invalid stat name")).status(HttpStatus.BAD_REQUEST);
            return;
        }

        List<Map.Entry<UUID, Long>> entries = plugin.getDatabase().getLeaderboard(seasonId, stat, 100);
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rank", rank++);
            map.put("playerId", entry.getKey().toString());
            map.put("value", entry.getValue());
            
            plugin.getDatabase().getPlayer(entry.getKey()).ifPresent(p -> map.put("playerName", p.lastName()));
            result.add(map);
        }
        ctx.json(Map.of("seasonId", seasonId, "stat", stat, "leaderboard", result));
    }

    private void handlePlayer(Context ctx) {
        String playerIdStr = ctx.pathParam("playerId");
        UUID playerId;
        try {
            playerId = UUID.fromString(playerIdStr);
        } catch (IllegalArgumentException e) {
            ctx.json(Map.of("error", "Invalid player UUID")).status(HttpStatus.BAD_REQUEST);
            return;
        }

        Optional<TrackedPlayer> opt = plugin.getDatabase().getPlayer(playerId);
        if (opt.isEmpty()) {
            ctx.json(Map.of("error", "Player not found")).status(HttpStatus.NOT_FOUND);
            return;
        }

        TrackedPlayer player = opt.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", player.uuid().toString());
        result.put("name", player.lastName());
        result.put("firstSeen", player.firstSeen());
        result.put("lastSeen", player.lastSeen());
        ctx.json(result);
    }

    private void handlePlayerByName(Context ctx) {
        String playerName = ctx.pathParam("playerName");

        Optional<TrackedPlayer> opt = plugin.getDatabase().getAllPlayers().stream()
            .filter(p -> p.lastName() != null && p.lastName().equalsIgnoreCase(playerName))
            .findFirst();

        if (opt.isEmpty()) {
            ctx.json(Map.of("error", "Player not found")).status(HttpStatus.NOT_FOUND);
            return;
        }

        TrackedPlayer player = opt.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", player.uuid().toString());
        result.put("name", player.lastName());
        result.put("firstSeen", player.firstSeen());
        result.put("lastSeen", player.lastSeen());
        ctx.json(result);
    }

    private void handlePlayerWrapped(Context ctx) {
        String playerIdStr = ctx.pathParam("playerId");
        String seasonId = ctx.pathParam("seasonId");

        UUID playerId;
        try {
            playerId = UUID.fromString(playerIdStr);
        } catch (IllegalArgumentException e) {
            ctx.json(Map.of("error", "Invalid player UUID")).status(HttpStatus.BAD_REQUEST);
            return;
        }

        if (!plugin.getSeasonManager().seasonExists(seasonId)) {
            ctx.json(Map.of("error", "Season not found")).status(HttpStatus.NOT_FOUND);
            return;
        }

        
        Optional<TrackedPlayer> player = plugin.getDatabase().getPlayer(playerId);
        if (player.isEmpty()) {
            ctx.json(Map.of("error", "Player not found")).status(HttpStatus.NOT_FOUND);
            return;
        }
        String playerName = player.get().lastName();

        
        Optional<WrappedSnapshot> cached = plugin.getDatabase().getWrappedSnapshot(playerId, seasonId);
        WrappedSnapshot snapshot;
        boolean refreshed = false;
        if (cached.isPresent()) {
            snapshot = cached.get();
            refreshed = refreshIfStale(playerId, seasonId, snapshot, playerName);
            if (!refreshed) {
                LOG.debug("Served cached Wrapped snapshot", LogContext.of(
                    "player", playerId,
                    "season", seasonId,
                    "generatedAt", snapshot.generatedAt(),
                    "ageSeconds", (System.currentTimeMillis() - snapshot.generatedAt()) / 1000));
            }
        } else {
            snapshot = plugin.getWrappedGenerator().generateAndSave(playerId, seasonId, playerName);
            refreshed = true;
        }

        LOG.debug("Served Wrapped snapshot", LogContext.of(
            "player", playerId,
            "season", seasonId,
            "refreshed", refreshed,
            "achievements", snapshot.achievements().size()));
        ctx.json(snapshot);
    }

    private boolean refreshIfStale(UUID playerId, String seasonId, WrappedSnapshot cached, String playerName) {
        int interval;
        try {
            interval = plugin.getConfigManager().get().webSnapshotRefreshSeconds();
        } catch (Throwable t) {
            LOG.error("Could not read web.snapshot-refresh-seconds", t);
            return false;
        }
        if (interval <= 0) {
            return false;
        }

        PlayerStats current;
        try {
            current = plugin.getDatabase().loadPlayerStats(playerId, seasonId);
        } catch (Throwable t) {
            LOG.error("Could not compare Wrapped stats, serving the cached snapshot", t,
                LogContext.of("player", playerId, "season", seasonId));
            return false;
        }

        if (WrappedGenerator.isSnapshotCurrent(cached, current)) {
            return false;
        }

        long ageSeconds = (System.currentTimeMillis() - cached.generatedAt()) / 1000;
        if (ageSeconds < interval) {
            LOG.trace("Stats changed but the snapshot is still within the refresh interval",
                LogContext.of("player", playerId, "season", seasonId,
                    "ageSeconds", ageSeconds, "intervalSeconds", interval));
            return false;
        }

        String key = playerId + ":" + seasonId;
        if (!refreshingSnapshots.add(key)) {
            LOG.trace("A refresh for this snapshot is already running", LogContext.of(
                "player", playerId, "season", seasonId));
            return false;
        }

        long startedAt = System.currentTimeMillis();
        try {
            plugin.getWrappedGenerator().generateAndSave(playerId, seasonId, playerName);
            LOG.info("Refreshed a stale Wrapped snapshot", LogContext.of(
                "player", playerId,
                "season", seasonId,
                "ageSeconds", ageSeconds,
                "durationMillis", System.currentTimeMillis() - startedAt));
            return true;
        } catch (Throwable t) {
            LOG.error("Could not refresh a stale Wrapped snapshot, serving the cached one", t,
                LogContext.of("player", playerId, "season", seasonId));
            return false;
        } finally {
            refreshingSnapshots.remove(key);
        }
    }

    private void handlePlayerStats(Context ctx) {
        String playerIdStr = ctx.pathParam("playerId");
        String seasonId = ctx.pathParam("seasonId");

        UUID playerId;
        try {
            playerId = UUID.fromString(playerIdStr);
        } catch (IllegalArgumentException e) {
            ctx.json(Map.of("error", "Invalid player UUID")).status(HttpStatus.BAD_REQUEST);
            return;
        }

        if (!plugin.getSeasonManager().seasonExists(seasonId)) {
            ctx.json(Map.of("error", "Season not found")).status(HttpStatus.NOT_FOUND);
            return;
        }

        PlayerStats stats = plugin.getDatabase().loadPlayerStats(playerId, seasonId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("playerId", playerId.toString());
        result.put("seasonId", seasonId);
        result.put("playtimeSeconds", stats.getPlaytimeSeconds());
        result.put("blocksMined", stats.getBlocksMined());
        result.put("blocksPlaced", stats.getBlocksPlaced());
        result.put("distanceTraveled", stats.getDistanceTraveled());
        result.put("mobsKilled", stats.getMobsKilled());
        result.put("deaths", stats.getDeaths());
        result.put("itemsCrafted", stats.getItemsCrafted());
        result.put("fishingCatches", stats.getFishingCatches());
        result.put("loginSessions", stats.getLoginSessions());
        result.put("uniqueDaysPlayed", stats.getUniqueDaysPlayed());
        ctx.json(result);
    }

    

    private void handleAdminCreateSeason(Context ctx) {
        if (!checkAuth(ctx)) return;

        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Throwable t) {
            LOG.error("Could not parse the admin season request body", t, LogContext.of(
                "path", ctx.path(), "ip", ctx.ip()));
            ctx.json(Map.of("error", "Malformed JSON body")).status(HttpStatus.BAD_REQUEST);
            return;
        }
        if (body == null) {
            ctx.json(Map.of("error", "A JSON body is required")).status(HttpStatus.BAD_REQUEST);
            return;
        }
        String name = (String) body.get("name");
        String id = (String) body.get("id");

        if (name == null || name.isBlank()) {
            ctx.json(Map.of("error", "Season name is required")).status(HttpStatus.BAD_REQUEST);
            return;
        }

        if (id == null || id.isBlank()) {
            id = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        }

        if (plugin.getSeasonManager().seasonExists(id)) {
            ctx.json(Map.of("error", "Season already exists")).status(HttpStatus.CONFLICT);
            return;
        }

        Season season;
        try {
            season = plugin.getSeasonManager().createSeason(id, name);
        } catch (RuntimeException e) {
            LOG.error("Could not create a season", e, LogContext.of("season", id, "name", name, "ip", ctx.ip()));
            ctx.json(Map.of("error", "Could not create the season")).status(HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }
        if (season == null) {
            ctx.json(Map.of("error", "Could not create the season")).status(HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }
        ctx.json(Map.of(
            "id", season.id(),
            "name", season.name(),
            "state", season.state().name(),
            "current", season.id().equals(plugin.getSeasonManager().currentSeasonId())));
    }

    private void handleAdminEndSeason(Context ctx) {
        if (!checkAuth(ctx)) return;
        String seasonId = ctx.pathParam("seasonId");
        if (plugin.getSeasonManager().endSeason(seasonId)) {
            ctx.json(Map.of("message", "Season ended", "seasonId", seasonId));
        } else {
            ctx.json(Map.of("error", "Failed to end season")).status(HttpStatus.BAD_REQUEST);
        }
    }

    private void handleAdminPublishSeason(Context ctx) {
        if (!checkAuth(ctx)) return;
        String seasonId = ctx.pathParam("seasonId");
        if (plugin.getSeasonManager().publishSeason(seasonId)) {
            ctx.json(Map.of("message", "Season published", "seasonId", seasonId));
        } else {
            ctx.json(Map.of("error", "Failed to publish season")).status(HttpStatus.BAD_REQUEST);
        }
    }

    private void handleAdminUnpublishSeason(Context ctx) {
        if (!checkAuth(ctx)) return;
        String seasonId = ctx.pathParam("seasonId");
        if (plugin.getSeasonManager().unpublishSeason(seasonId)) {
            ctx.json(Map.of("message", "Season unpublished", "seasonId", seasonId));
        } else {
            ctx.json(Map.of("error", "Failed to unpublish season")).status(HttpStatus.BAD_REQUEST);
        }
    }

    private void handleAdminSwitchSeason(Context ctx) {
        if (!checkAuth(ctx)) return;
        String seasonId = ctx.pathParam("seasonId");
        if (plugin.getSeasonManager().switchSeason(seasonId)) {
            ctx.json(Map.of("message", "Now recording into season", "seasonId", seasonId));
        } else {
            ctx.json(Map.of("error", "Failed to switch season")).status(HttpStatus.BAD_REQUEST);
        }
    }

    private void handleAdminBackup(Context ctx) {
        if (!checkAuth(ctx)) return;
        File backup = plugin.getDatabase().createBackup();
        if (backup != null) {
            ctx.json(Map.of("message", "Backup created", "file", backup.getName()));
        } else {
            ctx.json(Map.of("error", "Backup failed")).status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void handleAdminRebuildSnapshot(Context ctx) {
        if (!checkAuth(ctx)) return;
        String playerIdStr = ctx.pathParam("playerId");
        String seasonId = ctx.pathParam("seasonId");

        UUID playerId;
        try {
            playerId = UUID.fromString(playerIdStr);
        } catch (IllegalArgumentException e) {
            ctx.json(Map.of("error", "Invalid player UUID")).status(HttpStatus.BAD_REQUEST);
            return;
        }

        Optional<TrackedPlayer> player = plugin.getDatabase().getPlayer(playerId);
        if (player.isEmpty()) {
            ctx.json(Map.of("error", "Player not found")).status(HttpStatus.NOT_FOUND);
            return;
        }

        WrappedSnapshot snapshot = plugin.getWrappedGenerator().generateAndSave(playerId, seasonId, player.get().lastName());
        ctx.json(Map.of("message", "Snapshot rebuilt", "playerId", playerId.toString(), "seasonId", seasonId));
    }

    

    private static boolean hasNoBody(Context ctx) {
        String body = ctx.result();
        return body == null || body.isBlank();
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        int guard = 0;
        while (current != null && guard++ < 20) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean checkAuth(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            LOG.warn("Rejected admin request without a bearer token", LogContext.of(
                "path", ctx.path(), "method", String.valueOf(ctx.method()), "ip", ctx.ip()));
            ctx.json(Map.of("error", "Unauthorized")).status(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String token = authHeader.substring(7);
        if (!adminAuth.validateToken(token)) {
            LOG.warn("Rejected admin request with an invalid token", LogContext.of(
                "path", ctx.path(), "method", String.valueOf(ctx.method()), "ip", ctx.ip()));
            ctx.json(Map.of("error", "Invalid token")).status(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    private void handleRoot(Context ctx) {
        
        ctx.html(getIndexHtml());
    }

    
    private String getIndexHtml() {
        String cached = indexHtmlCache;
        if (cached != null) {
            return cached;
        }
        try (InputStream in = getClass().getResourceAsStream("/webapp/index.html")) {
            if (in != null) {
                cached = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.error("Failed to read webapp/index.html from the plugin jar", e);
        } catch (Throwable t) {
            LOG.error("Unexpected failure while reading webapp/index.html", t);
        }
        if (cached == null) {
            LOG.warn("webapp/index.html not found in the plugin jar; serving placeholder",
                LogContext.of("hint", "Run the frontend build (npm run build) and rebuild the plugin jar"));
            cached = "<!DOCTYPE html><html><head><title>SMPWrapped</title></head>" +
                     "<body><h1>SMPWrapped</h1><p>Frontend not built yet. Run the frontend build.</p></body></html>";
        }
        indexHtmlCache = cached;
        return cached;
    }

    public void stop() {
        Javalin current = app;
        if (current == null) {
            return;
        }
        app = null;
        try {
            current.stop();
            LOG.info("Web server stopped");
        } catch (Throwable t) {
            LOG.error("Web server did not shut down cleanly", t);
        }
    }
}
