package de.winniepat.smpwrapped.web;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.config.PluginConfig;
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
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Manages the embedded Javalin web server and REST API.
 */
public class WebServerManager {

    private final SMPWrapped plugin;
    private Javalin app;
    private RateLimiter rateLimiter;
    private AdminAuth adminAuth;
    private volatile String indexHtmlCache;

    public WebServerManager(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    public void start() {
        PluginConfig config = plugin.getConfigManager().get();
        if (!config.webEnabled()) {
            plugin.getLogger().info("Web server is disabled in config.");
            return;
        }

        rateLimiter = new RateLimiter(config.rateLimitRequestsPerMinute());
        adminAuth = new AdminAuth(plugin);

        // Javalin detects an SLF4J provider by scanning META-INF/services through
        // the thread context classloader. During plugin enable the TCCL is the
        // server classloader, which cannot see this plugin's shaded service files,
        // so Javalin prints a misleading "no logger" banner and trips Paper's
        // stdout nag. Point the TCCL at the plugin classloader during startup.
        ClassLoader previousTccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            app = Javalin.create(javalinConfig -> {
                javalinConfig.jsonMapper(new JavalinJackson());
                javalinConfig.showJavalinBanner = false;

                // Serve static frontend assets from the JAR
                File webappDir = new File(plugin.getDataFolder(), "webapp");
                if (webappDir.exists()) {
                    javalinConfig.staticFiles.add(webappDir.getAbsolutePath(), Location.EXTERNAL);
                } else {
                    // Try to extract from JAR
                    javalinConfig.staticFiles.add("/webapp", Location.CLASSPATH);
                }

                // SPA fallback: serve index.html for client-side routes. Javalin's
                // spaRoot runs AFTER endpoint matching and static file handling,
                // so real assets are served normally and only genuine 404s fall
                // through to the SPA shell.
                javalinConfig.spaRoot.addFile("/", "/webapp/index.html", Location.CLASSPATH);

                // CORS - enable for all origins (restricted by reverse proxy in production)
                javalinConfig.bundledPlugins.enableCors(cors -> {
                    cors.addRule(rule -> {
                        rule.anyHost();
                    });
                });
            });

            setupRoutes();
            setupErrorHandling();

            app.start(config.webHost(), config.webPort());
        } finally {
            Thread.currentThread().setContextClassLoader(previousTccl);
        }

        plugin.getLogger().info("Web server started on " + config.webHost() + ":" + config.webPort());
    }

    private void setupRoutes() {
        // Health check
        app.get("/api/v1/health", this::handleHealth);

        // Seasons
        app.get("/api/v1/seasons", this::handleSeasons);
        app.get("/api/v1/seasons/current", this::handleCurrentSeason);
        app.get("/api/v1/seasons/{seasonId}/stats", this::handleSeasonStats);
        app.get("/api/v1/seasons/{seasonId}/leaderboards/{stat}", this::handleLeaderboard);

        // Players
        app.get("/api/v1/players/{playerId}", this::handlePlayer);
        app.get("/api/v1/players/{playerId}/wrapped/{seasonId}", this::handlePlayerWrapped);
        app.get("/api/v1/players/{playerId}/stats/{seasonId}", this::handlePlayerStats);

        // Admin endpoints (require auth)
        app.post("/api/v1/admin/seasons", this::handleAdminCreateSeason);
        app.post("/api/v1/admin/seasons/{seasonId}/end", this::handleAdminEndSeason);
        app.post("/api/v1/admin/seasons/{seasonId}/publish", this::handleAdminPublishSeason);
        app.post("/api/v1/admin/seasons/{seasonId}/unpublish", this::handleAdminUnpublishSeason);
        app.post("/api/v1/admin/backup", this::handleAdminBackup);
        app.post("/api/v1/admin/rebuild/{playerId}/{seasonId}", this::handleAdminRebuildSnapshot);

        // Unmatched API routes must stay JSON (spaRoot would otherwise serve HTML).
        app.get("/api/<path>", ctx -> ctx.json(Map.of("error", "Not found")).status(HttpStatus.NOT_FOUND));

        // Root serves the SPA shell directly (with a graceful placeholder fallback).
        // Client-side deep links (e.g. /wrapped/season-1) are handled by spaRoot.
        app.get("/", this::handleRoot);
    }

    private void setupErrorHandling() {
        app.error(404, ctx -> ctx.json(Map.of("error", "Not found")).status(HttpStatus.NOT_FOUND));
        app.error(500, ctx -> {
            plugin.getLogger().severe("Internal server error: " + ctx.result());
            ctx.json(Map.of("error", "Internal server error")).status(HttpStatus.INTERNAL_SERVER_ERROR);
        });
    }

    // === Public API handlers ===

    private void handleHealth(Context ctx) {
        ctx.json(Map.of(
            "status", "ok",
            "version", plugin.getDescription().getVersion(),
            "timestamp", System.currentTimeMillis()
        ));
    }

    private void handleSeasons(Context ctx) {
        List<Season> seasons = plugin.getSeasonManager().getAllSeasons();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Season s : seasons) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.id());
            map.put("name", s.name());
            map.put("startTime", s.startTime());
            map.put("endTime", s.endTime());
            map.put("state", s.state().name());
            map.put("active", s.isActive());
            result.add(map);
        }
        ctx.json(Map.of("seasons", result));
    }

    private void handleCurrentSeason(Context ctx) {
        plugin.getSeasonManager().getCurrentSeason().ifPresentOrElse(
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

        // Validate stat name
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
            // Add player name if available
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

        // Check if snapshot exists, generate if not
        Optional<WrappedSnapshot> snapshot = plugin.getDatabase().getWrappedSnapshot(playerId, seasonId);
        if (snapshot.isEmpty()) {
            Optional<TrackedPlayer> player = plugin.getDatabase().getPlayer(playerId);
            if (player.isEmpty()) {
                ctx.json(Map.of("error", "Player not found")).status(HttpStatus.NOT_FOUND);
                return;
            }
            snapshot = Optional.of(plugin.getWrappedGenerator().generateAndSave(playerId, seasonId, player.get().lastName()));
        }

        ctx.json(snapshot.get());
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

    // === Admin API handlers ===

    private void handleAdminCreateSeason(Context ctx) {
        if (!checkAuth(ctx)) return;

        Map<String, Object> body = ctx.bodyAsClass(Map.class);
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

        Season season = plugin.getSeasonManager().createSeason(id, name);
        ctx.json(Map.of("id", season.id(), "name", season.name(), "state", season.state().name()));
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

    // === Helper methods ===

    private boolean checkAuth(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.json(Map.of("error", "Unauthorized")).status(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String token = authHeader.substring(7);
        if (!adminAuth.validateToken(token)) {
            ctx.json(Map.of("error", "Invalid token")).status(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    private void handleRoot(Context ctx) {
        // Serve index.html for the root; deep links are handled by spaRoot.
        ctx.html(getIndexHtml());
    }

    /**
     * Loads the built frontend's index.html from the plugin classpath
     * (/webapp/index.html), caching it. Falls back to a minimal placeholder if the
     * frontend was not built into the JAR.
     */
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
            plugin.getLogger().warning("Failed to read webapp/index.html: " + e.getMessage());
        }
        if (cached == null) {
            plugin.getLogger().warning("webapp/index.html not found in the plugin JAR; serving placeholder.");
            cached = "<!DOCTYPE html><html><head><title>SMPWrapped</title></head>" +
                     "<body><h1>SMPWrapped</h1><p>Frontend not built yet. Run the frontend build.</p></body></html>";
        }
        indexHtmlCache = cached;
        return cached;
    }

    public void stop() {
        if (app != null) {
            app.stop();
            plugin.getLogger().info("Web server stopped.");
        }
    }
}
