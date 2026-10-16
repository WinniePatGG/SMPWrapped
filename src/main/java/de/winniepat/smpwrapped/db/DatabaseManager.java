package de.winniepat.smpwrapped.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.model.*;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages SQLite database connections via HikariCP.
 * Provides async query execution and schema migrations.
 */
public class DatabaseManager {

    private final SMPWrapped plugin;
    private HikariDataSource dataSource;
    private ExecutorService asyncExecutor;

    public DatabaseManager(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        String dbPath = plugin.getConfigManager().get().databasePath();
        File dbFile = new File(plugin.getDataFolder(), dbPath);
        dbFile.getParentFile().mkdirs();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("SMPWrapped-Pool");

        // SQLite optimizations
        config.addDataSourceProperty("cache_size", "8000");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "30000");

        dataSource = new HikariDataSource(config);

        asyncExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SMPWrapped-DB");
            t.setDaemon(true);
            return t;
        });

        runMigrations();
    }

    public ExecutorService getAsyncExecutor() {
        return asyncExecutor;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Run a database operation asynchronously.
     */
    public <T> CompletableFuture<T> supplyAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Run a database operation asynchronously (no return).
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    private void runMigrations() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Players table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    last_name TEXT NOT NULL,
                    first_seen INTEGER NOT NULL,
                    last_seen INTEGER NOT NULL,
                    opted_out INTEGER NOT NULL DEFAULT 0
                )
            """);

            // Seasons table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS seasons (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    start_time INTEGER NOT NULL,
                    end_time INTEGER NOT NULL DEFAULT -1,
                    state TEXT NOT NULL DEFAULT 'DRAFT'
                )
            """);

            // Player season stats table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_season_stats (
                    player_uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    stat_key TEXT NOT NULL,
                    stat_value TEXT NOT NULL,
                    stat_type TEXT NOT NULL DEFAULT 'long',
                    PRIMARY KEY (player_uuid, season_id, stat_key)
                )
            """);

            // Sessions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    login_time INTEGER NOT NULL,
                    logout_time INTEGER NOT NULL DEFAULT -1,
                    dimension TEXT
                )
            """);

            // Wrapped snapshots table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS wrapped_snapshots (
                    player_uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    generated_at INTEGER NOT NULL,
                    snapshot_json TEXT NOT NULL,
                    PRIMARY KEY (player_uuid, season_id)
                )
            """);

            // Achievements table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS achievements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    achievement_key TEXT NOT NULL,
                    achieved_at INTEGER NOT NULL,
                    UNIQUE(player_uuid, season_id, achievement_key)
                )
            """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stats_season ON player_season_stats(season_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stats_player ON player_season_stats(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_player ON sessions(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_achievements_player ON achievements(player_uuid, season_id)");

            plugin.getLogger().info("Database initialized and migrations applied.");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    // === Player operations ===

    public void upsertPlayer(TrackedPlayer player) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO players (uuid, last_name, first_seen, last_seen, opted_out) VALUES (?, ?, ?, ?, ?) " +
                     "ON CONFLICT(uuid) DO UPDATE SET last_name = excluded.last_name, last_seen = excluded.last_seen"
                 )) {
                ps.setString(1, player.uuid().toString());
                ps.setString(2, player.lastName());
                ps.setLong(3, player.firstSeen());
                ps.setLong(4, player.lastSeen());
                ps.setInt(5, player.optedOut() ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to upsert player: " + e.getMessage());
            }
        });
    }

    public Optional<TrackedPlayer> getPlayer(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT uuid, last_name, first_seen, last_seen, opted_out FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new TrackedPlayer(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("last_name"),
                        rs.getLong("first_seen"),
                        rs.getLong("last_seen"),
                        rs.getInt("opted_out") == 1
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get player: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<TrackedPlayer> getAllPlayers() {
        List<TrackedPlayer> players = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM players")) {
            while (rs.next()) {
                players.add(new TrackedPlayer(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("last_name"),
                    rs.getLong("first_seen"),
                    rs.getLong("last_seen"),
                    rs.getInt("opted_out") == 1
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get all players: " + e.getMessage());
        }
        return players;
    }

    public void setPlayerOptOut(UUID uuid, boolean optedOut) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE players SET opted_out = ? WHERE uuid = ?")) {
                ps.setInt(1, optedOut ? 1 : 0);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set opt-out: " + e.getMessage());
            }
        });
    }

    // === Season operations ===

    public void createSeason(Season season) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO seasons (id, name, start_time, end_time, state) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, season.id());
                ps.setString(2, season.name());
                ps.setLong(3, season.startTime());
                ps.setLong(4, season.endTime());
                ps.setString(5, season.state().name());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to create season: " + e.getMessage());
            }
        });
    }

    public Optional<Season> getSeason(String seasonId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM seasons WHERE id = ?")) {
            ps.setString(1, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Season(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getLong("start_time"),
                        rs.getLong("end_time"),
                        Season.SeasonState.valueOf(rs.getString("state"))
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get season: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<Season> getAllSeasons() {
        List<Season> seasons = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM seasons ORDER BY start_time DESC")) {
            while (rs.next()) {
                seasons.add(new Season(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getLong("start_time"),
                    rs.getLong("end_time"),
                    Season.SeasonState.valueOf(rs.getString("state"))
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get all seasons: " + e.getMessage());
        }
        return seasons;
    }

    public void updateSeasonState(String seasonId, Season.SeasonState state) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE seasons SET state = ? WHERE id = ?")) {
                ps.setString(1, state.name());
                ps.setString(2, seasonId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update season state: " + e.getMessage());
            }
        });
    }

    public void setSeasonEndTime(String seasonId, long endTime) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE seasons SET end_time = ? WHERE id = ?")) {
                ps.setLong(1, endTime);
                ps.setString(2, seasonId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set season end time: " + e.getMessage());
            }
        });
    }

    // === Stats operations ===

    public void savePlayerStats(PlayerStats stats) {
        runAsync(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "playtime_seconds", stats.getPlaytimeSeconds());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "blocks_mined", stats.getBlocksMined());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "blocks_placed", stats.getBlocksPlaced());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "distance_traveled", stats.getDistanceTraveled());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "mobs_killed", stats.getMobsKilled());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "deaths", stats.getDeaths());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "damage_dealt", stats.getDamageDealt());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "damage_received", stats.getDamageReceived());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "items_crafted", stats.getItemsCrafted());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "items_collected", stats.getItemsCollected());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "fishing_catches", stats.getFishingCatches());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "login_sessions", stats.getLoginSessions());
                    saveStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "unique_days", stats.getUniqueDaysPlayed());

                    // Map-type stats as JSON
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "dimension_time", stats.getDimensionTime());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "blocks_mined_by_type", stats.getBlocksMinedByType());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "blocks_placed_by_type", stats.getBlocksPlacedByType());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "mobs_killed_by_type", stats.getMobsKilledByType());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "daily_playtime", stats.getDailyPlaytime());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "hourly_playtime", toStringKeyed(stats.getHourlyPlaytime()));

                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save player stats: " + e.getMessage());
            }
        });
    }

    private void saveStat(Connection conn, String playerUuid, String seasonId, String key, long value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT OR REPLACE INTO player_season_stats (player_uuid, season_id, stat_key, stat_value, stat_type) VALUES (?, ?, ?, ?, 'long')")) {
            ps.setString(1, playerUuid);
            ps.setString(2, seasonId);
            ps.setString(3, key);
            ps.setString(4, String.valueOf(value));
            ps.executeUpdate();
        }
    }

    private void saveMapStat(Connection conn, String playerUuid, String seasonId, String key, Map<String, Long> map) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT OR REPLACE INTO player_season_stats (player_uuid, season_id, stat_key, stat_value, stat_type) VALUES (?, ?, ?, ?, 'json')")) {
            ps.setString(1, playerUuid);
            ps.setString(2, seasonId);
            ps.setString(3, key);
            ps.setString(4, serializeMap(map));
            ps.executeUpdate();
        }
    }

    /** Convert an integer-keyed map (e.g. hour of day) into the string-keyed form used by JSON storage. */
    private Map<String, Long> toStringKeyed(Map<Integer, Long> map) {
        Map<String, Long> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private String serializeMap(Map<String, Long> map) {        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public PlayerStats loadPlayerStats(UUID playerId, String seasonId) {
        PlayerStats stats = new PlayerStats(playerId, seasonId);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT stat_key, stat_value, stat_type FROM player_season_stats WHERE player_uuid = ? AND season_id = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("stat_key");
                    String value = rs.getString("stat_value");
                    String type = rs.getString("stat_type");
                    try {
                        if ("long".equals(type)) {
                            long v = Long.parseLong(value);
                            applyStat(stats, key, v);
                        } else if ("json".equals(type)) {
                            applyMapStat(stats, key, value);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to parse stat " + key + ": " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load player stats: " + e.getMessage());
        }
        return stats;
    }

    private void applyStat(PlayerStats stats, String key, long value) {
        switch (key) {
            case "playtime_seconds" -> stats.addPlaytime(value);
            case "blocks_mined" -> stats.addBlocksMined(value);
            case "blocks_placed" -> stats.addBlocksPlaced(value);
            case "distance_traveled" -> stats.addDistance(value);
            case "mobs_killed" -> stats.addMobsKilled(value);
            case "deaths" -> { for (long i = 0; i < value; i++) stats.addDeath(); }
            case "damage_dealt" -> stats.addDamageDealt(value);
            case "damage_received" -> stats.addDamageReceived(value);
            case "items_crafted" -> stats.addItemsCrafted(value);
            case "items_collected" -> stats.addItemsCollected(value);
            case "fishing_catches" -> { for (long i = 0; i < value; i++) stats.addFishingCatch(); }
            case "login_sessions" -> { for (long i = 0; i < value; i++) stats.addLoginSession(); }
            case "unique_days" -> { for (long i = 0; i < value; i++) stats.addUniqueDay(); }
        }
    }

    private void applyMapStat(PlayerStats stats, String key, String json) {
        Map<String, Long> map = parseJsonMap(json);
        switch (key) {
            case "dimension_time" -> map.forEach(stats::addDimensionTime);
            case "blocks_mined_by_type" -> map.forEach(stats::addBlockMined);
            case "blocks_placed_by_type" -> map.forEach(stats::addBlockPlaced);
            case "mobs_killed_by_type" -> map.forEach(stats::addMobKilled);
            case "daily_playtime" -> map.forEach(stats::addDailyPlaytime);
            case "hourly_playtime" -> map.forEach((hour, seconds) -> {
                try {
                    stats.addHourlyPlaytime(Integer.parseInt(hour), seconds);
                } catch (NumberFormatException ignored) {
                    // Skip malformed keys
                }
            });
        }
    }

    private Map<String, Long> parseJsonMap(String json) {
        Map<String, Long> map = new HashMap<>();
        if (json == null || json.isBlank() || json.equals("{}")) return map;
        // Simple JSON parser for flat string->long maps
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        // Split on commas not inside quotes
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        List<String> pairs = new ArrayList<>();
        for (char c : trimmed.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            if (c == ',' && !inQuotes) {
                pairs.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) pairs.add(current.toString().trim());

        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx > 0) {
                String k = pair.substring(0, colonIdx).trim().replace("\"", "");
                String v = pair.substring(colonIdx + 1).trim();
                try {
                    map.put(k, Long.parseLong(v));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    // === Leaderboard queries ===

    public List<Map.Entry<UUID, Long>> getLeaderboard(String seasonId, String statKey, int limit) {
        List<Map.Entry<UUID, Long>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT player_uuid, stat_value FROM player_season_stats WHERE season_id = ? AND stat_key = ? AND stat_type = 'long' ORDER BY CAST(stat_value AS INTEGER) DESC LIMIT ?")) {
            ps.setString(1, seasonId);
            ps.setString(2, statKey);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(Map.entry(
                        UUID.fromString(rs.getString("player_uuid")),
                        Long.parseLong(rs.getString("stat_value"))
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get leaderboard: " + e.getMessage());
        }
        return results;
    }

    public Map<UUID, Long> getAllStatsForSeason(String seasonId, String statKey) {
        Map<UUID, Long> results = new HashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT player_uuid, stat_value FROM player_season_stats WHERE season_id = ? AND stat_key = ? AND stat_type = 'long'")) {
            ps.setString(1, seasonId);
            ps.setString(2, statKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.put(UUID.fromString(rs.getString("player_uuid")), Long.parseLong(rs.getString("stat_value")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get all stats: " + e.getMessage());
        }
        return results;
    }

    // === Session operations ===

    public void startSession(Session session) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO sessions (player_uuid, login_time, logout_time, dimension) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, session.playerId().toString());
                ps.setLong(2, session.loginTime());
                ps.setLong(3, session.logoutTime());
                ps.setString(4, session.dimension());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to start session: " + e.getMessage());
            }
        });
    }

    public void endSession(UUID playerId, long logoutTime) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE sessions SET logout_time = ? WHERE player_uuid = ? AND logout_time = -1")) {
                ps.setLong(1, logoutTime);
                ps.setString(2, playerId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to end session: " + e.getMessage());
            }
        });
    }

    // === Wrapped snapshot operations ===

    public void saveWrappedSnapshot(WrappedSnapshot snapshot) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO wrapped_snapshots (player_uuid, season_id, generated_at, snapshot_json) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, snapshot.playerId().toString());
                ps.setString(2, snapshot.seasonId());
                ps.setLong(3, snapshot.generatedAt());
                ps.setString(4, snapshotToJson(snapshot));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save wrapped snapshot: " + e.getMessage());
            }
        });
    }

    public Optional<WrappedSnapshot> getWrappedSnapshot(UUID playerId, String seasonId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT snapshot_json FROM wrapped_snapshots WHERE player_uuid = ? AND season_id = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(jsonToSnapshot(rs.getString("snapshot_json")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get wrapped snapshot: " + e.getMessage());
        }
        return Optional.empty();
    }

    private String snapshotToJson(WrappedSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"playerId\":\"").append(snapshot.playerId()).append("\",");
        sb.append("\"seasonId\":\"").append(snapshot.seasonId()).append("\",");
        sb.append("\"playerName\":\"").append(escapeJson(snapshot.playerName())).append("\",");
        sb.append("\"generatedAt\":").append(snapshot.generatedAt()).append(",");
        sb.append("\"archetype\":\"").append(escapeJson(snapshot.archetype())).append("\",");
        sb.append("\"archetypeDescription\":\"").append(escapeJson(snapshot.archetypeDescription())).append("\",");
        sb.append("\"stats\":{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : snapshot.stats().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object v = entry.getValue();
            if (v instanceof String) sb.append("\"").append(escapeJson((String) v)).append("\"");
            else sb.append(v);
            first = false;
        }
        sb.append("},");
        sb.append("\"achievements\":[");
        first = true;
        for (String a : snapshot.achievements()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(a)).append("\"");
            first = false;
        }
        sb.append("],");
        sb.append("\"percentiles\":{");
        first = true;
        for (Map.Entry<String, Double> entry : snapshot.percentiles().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("},");
        sb.append("\"milestones\":[");
        first = true;
        for (String m : snapshot.milestones()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(m)).append("\"");
            first = false;
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private WrappedSnapshot jsonToSnapshot(String json) {
        // Simple JSON parser for snapshot
        Map<String, Object> map = parseJsonObject(json);
        UUID playerId = UUID.fromString((String) map.get("playerId"));
        String seasonId = (String) map.get("seasonId");
        String playerName = (String) map.get("playerName");
        long generatedAt = ((Number) map.get("generatedAt")).longValue();
        String archetype = (String) map.get("archetype");
        String archetypeDescription = (String) map.get("archetypeDescription");

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) map.getOrDefault("stats", Map.of());
        @SuppressWarnings("unchecked")
        List<String> achievements = (List<String>) map.getOrDefault("achievements", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Double> percentiles = (Map<String, Double>) map.getOrDefault("percentiles", Map.of());
        @SuppressWarnings("unchecked")
        List<String> milestones = (List<String>) map.getOrDefault("milestones", List.of());

        return new WrappedSnapshot(playerId, seasonId, playerName, generatedAt, archetype,
            archetypeDescription, stats, achievements, percentiles, milestones);
    }

    private Map<String, Object> parseJsonObject(String json) {
        // Minimal JSON parser - handles our snapshot format
        Map<String, Object> result = new HashMap<>();
        // For production, use Jackson. This is a simple fallback.
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        int depth = 0;
        StringBuilder current = new StringBuilder();
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        for (char c : json.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            if (!inQuotes) {
                if (c == '{' || c == '[') depth++;
                if (c == '}' || c == ']') depth--;
                if (c == ',' && depth == 0) {
                    tokens.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString().trim());

        for (String token : tokens) {
            int colonIdx = token.indexOf(':');
            if (colonIdx <= 0) continue;
            String key = token.substring(0, colonIdx).trim().replace("\"", "");
            String value = token.substring(colonIdx + 1).trim();
            result.put(key, parseJsonValue(value));
        }
        return result;
    }

    private Object parseJsonValue(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("{")) {
            return parseJsonObject(value);
        }
        if (value.startsWith("[")) {
            return parseJsonArray(value);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e2) {
                return value;
            }
        }
    }

    private List<String> parseJsonArray(String value) {
        List<String> list = new ArrayList<>();
        value = value.trim();
        if (value.startsWith("[")) value = value.substring(1);
        if (value.endsWith("]")) value = value.substring(0, value.length() - 1);
        if (value.isBlank()) return list;
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            if (c == ',' && !inQuotes) {
                list.add(current.toString().trim().replace("\"", ""));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            list.add(current.toString().trim().replace("\"", ""));
        }
        return list;
    }

    // === Achievements ===

    public void saveAchievement(UUID playerId, String seasonId, String achievementKey) {
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO achievements (player_uuid, season_id, achievement_key, achieved_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, playerId.toString());
                ps.setString(2, seasonId);
                ps.setString(3, achievementKey);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save achievement: " + e.getMessage());
            }
        });
    }

    public List<String> getAchievements(UUID playerId, String seasonId) {
        List<String> achievements = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT achievement_key FROM achievements WHERE player_uuid = ? AND season_id = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    achievements.add(rs.getString("achievement_key"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get achievements: " + e.getMessage());
        }
        return achievements;
    }

    // === Server-wide stats ===

    public Map<String, Long> getServerWideStats(String seasonId) {
        Map<String, Long> stats = new HashMap<>();
        String[] keys = {"playtime_seconds", "blocks_mined", "blocks_placed", "distance_traveled",
            "mobs_killed", "deaths", "items_crafted", "fishing_catches", "login_sessions"};

        try (Connection conn = getConnection()) {
            for (String key : keys) {
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT SUM(CAST(stat_value AS INTEGER)) as total FROM player_season_stats WHERE season_id = ? AND stat_key = ?")) {
                    ps.setString(1, seasonId);
                    ps.setString(2, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            stats.put(key, rs.getLong("total"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get server-wide stats: " + e.getMessage());
        }
        return stats;
    }

    // === Backup ===

    public File createBackup() {
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            backupDir.mkdirs();
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new java.util.Date());
            File backupFile = new File(backupDir, "smpwrapped-backup-" + timestamp + ".db");

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("BACKUP TO ?")) {
                ps.setString(1, backupFile.getAbsolutePath());
                ps.execute();
            }
            plugin.getLogger().info("Database backup created: " + backupFile.getAbsolutePath());
            return backupFile;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create backup: " + e.getMessage());
            return null;
        }
    }

    public void shutdown() {
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
