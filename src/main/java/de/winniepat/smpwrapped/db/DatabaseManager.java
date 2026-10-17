package de.winniepat.smpwrapped.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.*;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class DatabaseManager {

    private static final SMPLogger LOG = SMPLogger.get("database");

    private final SMPWrapped plugin;
    private HikariDataSource dataSource;
    private ExecutorService asyncExecutor;

    public DatabaseManager(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        String dbPath = plugin.getConfigManager().get().databasePath();
        File dbFile = new File(plugin.getDataFolder(), dbPath);

        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IllegalStateException("Could not create database directory: " + parent.getAbsolutePath());
        }

        LOG.info("Connecting to SQLite database", LogContext.of(
            "path", dbFile.getAbsolutePath(),
            "parent", parent == null ? "<none>" : parent.getAbsolutePath()));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("SMPWrapped-Pool");

        config.addDataSourceProperty("cache_size", "8000");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "30000");

        try {
            dataSource = new HikariDataSource(config);
        } catch (Throwable t) {
            LOG.fatal("Could not open the database connection pool", t, LogContext.of(
                "path", dbFile.getAbsolutePath()));
            throw new IllegalStateException("Database initialization failed", t);
        }

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
        HikariDataSource current = dataSource;
        if (current == null) {
            throw new SQLException("Connection pool is not initialised");
        }
        return current.getConnection();
    }

    
    public <T> CompletableFuture<T> supplyAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor).whenComplete((result, throwable) -> {
            if (throwable != null) {
                LOG.error("Unhandled failure in an async database task", unwrap(throwable));
            }
        });
    }

    
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor).whenComplete((result, throwable) -> {
            if (throwable != null) {
                LOG.error("Unhandled failure in an async database task", unwrap(throwable));
            }
        });
    }

    static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private static final String[] MIGRATIONS = {
        "CREATE TABLE IF NOT EXISTS players ("
            + " uuid TEXT PRIMARY KEY,"
            + " last_name TEXT NOT NULL,"
            + " first_seen INTEGER NOT NULL,"
            + " last_seen INTEGER NOT NULL,"
            + " opted_out INTEGER NOT NULL DEFAULT 0"
            + ")",

        "CREATE TABLE IF NOT EXISTS seasons ("
            + " id TEXT PRIMARY KEY,"
            + " name TEXT NOT NULL,"
            + " start_time INTEGER NOT NULL,"
            + " end_time INTEGER NOT NULL DEFAULT -1,"
            + " state TEXT NOT NULL DEFAULT 'DRAFT'"
            + ")",

        "CREATE TABLE IF NOT EXISTS player_season_stats ("
            + " player_uuid TEXT NOT NULL,"
            + " season_id TEXT NOT NULL,"
            + " stat_key TEXT NOT NULL,"
            + " stat_value TEXT NOT NULL,"
            + " stat_type TEXT NOT NULL DEFAULT 'long',"
            + " PRIMARY KEY (player_uuid, season_id, stat_key)"
            + ")",

        "CREATE TABLE IF NOT EXISTS sessions ("
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " player_uuid TEXT NOT NULL,"
            + " login_time INTEGER NOT NULL,"
            + " logout_time INTEGER NOT NULL DEFAULT -1,"
            + " dimension TEXT"
            + ")",

        "CREATE TABLE IF NOT EXISTS wrapped_snapshots ("
            + " player_uuid TEXT NOT NULL,"
            + " season_id TEXT NOT NULL,"
            + " generated_at INTEGER NOT NULL,"
            + " snapshot_json TEXT NOT NULL,"
            + " PRIMARY KEY (player_uuid, season_id)"
            + ")",

        "CREATE TABLE IF NOT EXISTS achievements ("
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " player_uuid TEXT NOT NULL,"
            + " season_id TEXT NOT NULL,"
            + " achievement_key TEXT NOT NULL,"
            + " achieved_at INTEGER NOT NULL,"
            + " UNIQUE(player_uuid, season_id, achievement_key)"
            + ")",

        "CREATE INDEX IF NOT EXISTS idx_stats_season ON player_season_stats(season_id)",
        "CREATE INDEX IF NOT EXISTS idx_stats_player ON player_season_stats(player_uuid)",
        "CREATE INDEX IF NOT EXISTS idx_sessions_player ON sessions(player_uuid)",
        "CREATE INDEX IF NOT EXISTS idx_achievements_player ON achievements(player_uuid, season_id)"
    };

    private void runMigrations() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String migration : MIGRATIONS) {
                stmt.execute(migration);
            }

            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_seasons_single_active ON seasons(state) WHERE state = 'ACTIVE'");
            } catch (SQLException e) {
                LOG.error("Could not enforce a single active season, existing data violates the rule", e,
                    LogContext.of("hint", "End or unpublish every extra ACTIVE season, then restart the server"));
            }

            LOG.info("Database initialized and migrations applied", LogContext.of(
                "tables", 6, "indexes", 5, "statements", MIGRATIONS.length));

        } catch (SQLException e) {
            LOG.fatal("Failed to apply database migrations", e);
            throw new RuntimeException("Database initialization failed", e);
        } catch (Throwable t) {
            LOG.fatal("Unexpected failure while applying database migrations", t);
            throw new RuntimeException("Database initialization failed", t);
        }
    }

    

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
                LOG.error("Failed to upsert player", e, LogContext.of(
                    "operation", "upsertPlayer",
                    "player", player.uuid(),
                    "name", player.lastName()));
            }
        });
    }

    public Optional<TrackedPlayer> getPlayer(UUID uuid) {
        if (uuid == null) {
            LOG.warn("Refusing to look up a player without a UUID");
            return Optional.empty();
        }
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
            LOG.error("Failed to load player", e, LogContext.of("operation", "getPlayer", "player", uuid));
        } catch (IllegalArgumentException e) {
            LOG.error("Stored player row contains an invalid UUID", e, LogContext.of(
                "operation", "getPlayer", "player", uuid));
        }
        return Optional.empty();
    }

    public List<TrackedPlayer> getAllPlayers() {
        List<TrackedPlayer> players = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM players")) {
            while (rs.next()) {
                String rawUuid = rs.getString("uuid");
                try {
                    players.add(new TrackedPlayer(
                        UUID.fromString(rawUuid),
                        rs.getString("last_name"),
                        rs.getLong("first_seen"),
                        rs.getLong("last_seen"),
                        rs.getInt("opted_out") == 1
                    ));
                } catch (IllegalArgumentException e) {
                    LOG.error("Skipping player row with an invalid UUID", e, LogContext.of(
                        "operation", "getAllPlayers", "row", rawUuid));
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to load all players", e, LogContext.of("operation", "getAllPlayers"));
        }
        return players;
    }

    public void setPlayerOptOut(UUID uuid, boolean optedOut) {
        if (uuid == null) {
            LOG.warn("Refusing to change the opt-out flag of a player without a UUID");
            return;
        }
        runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE players SET opted_out = ? WHERE uuid = ?")) {
                ps.setInt(1, optedOut ? 1 : 0);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.error("Failed to update the player opt-out flag", e, LogContext.of(
                    "operation", "setPlayerOptOut", "player", uuid, "optedOut", optedOut));
            }
        });
    }

    

    public boolean createSeason(Season season) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO seasons (id, name, start_time, end_time, state) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, season.id());
            ps.setString(2, season.name());
            ps.setLong(3, season.startTime());
            ps.setLong(4, season.endTime());
            ps.setString(5, season.state().name());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOG.error("Failed to store season", e, LogContext.of(
                "operation", "createSeason", "season", season.id(), "name", season.name(),
                "hint", "Another season may still be ACTIVE, end it and retry"));
            return false;
        }
    }

    public Optional<Season> getSeason(String seasonId) {
        if (seasonId == null || seasonId.isBlank()) {
            LOG.warn("Refusing to load a season without an id");
            return Optional.empty();
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM seasons WHERE id = ?")) {
            ps.setString(1, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readSeason(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to load season", e, LogContext.of("operation", "getSeason", "season", seasonId));
        } catch (IllegalArgumentException e) {
            LOG.error("Stored season row contains an unknown state", e, LogContext.of(
                "operation", "getSeason", "season", seasonId));
        }
        return Optional.empty();
    }

    private Season readSeason(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String state = rs.getString("state");
        long start = rs.getLong("start_time");
        long end = rs.getLong("end_time");
        try {
            return new Season(id, name, start, end, Season.SeasonState.valueOf(state));
        } catch (IllegalArgumentException | NullPointerException e) {
            LOG.error("Season row has an invalid state, defaulting to ENDED", e, LogContext.of(
                "season", id, "storedState", state));
            return new Season(id, name, start, end, Season.SeasonState.ENDED);
        }
    }

    public List<Season> getAllSeasons() {
        List<Season> seasons = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM seasons ORDER BY start_time DESC")) {
            while (rs.next()) {
                seasons.add(readSeason(rs));
            }
        } catch (SQLException e) {
            LOG.error("Failed to load all seasons", e, LogContext.of("operation", "getAllSeasons"));
        }
        return seasons;
    }

    public void updateSeasonState(String seasonId, Season.SeasonState state) {
        if (seasonId == null || state == null) {
            LOG.warn("Refusing to update a season without an id or state",
                LogContext.of("season", seasonId, "state", state));
            return;
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE seasons SET state = ? WHERE id = ?")) {
            ps.setString(1, state.name());
            ps.setString(2, seasonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to update season state", e, LogContext.of(
                "operation", "updateSeasonState", "season", seasonId, "state", state));
        }
    }

    public void setSeasonEndTime(String seasonId, long endTime) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE seasons SET end_time = ? WHERE id = ?")) {
            ps.setLong(1, endTime);
            ps.setString(2, seasonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to update season end time", e, LogContext.of(
                "operation", "setSeasonEndTime", "season", seasonId, "endTime", endTime));
        }
    }

    

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

                    
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "dimension_time", stats.getDimensionTime());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "blocks_mined_by_type", stats.getBlocksMinedByType());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "blocks_placed_by_type", stats.getBlocksPlacedByType());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "mobs_killed_by_type", stats.getMobsKilledByType());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "daily_playtime", stats.getDailyPlaytime());
                    saveMapStat(conn, stats.getPlayerId().toString(), stats.getSeasonId(), "hourly_playtime", toStringKeyed(stats.getHourlyPlaytime()));

                    conn.commit();
                } catch (SQLException e) {
                    try {
                        conn.rollback();
                        LOG.warn("Rolled back player stats transaction", LogContext.of(
                            "operation", "savePlayerStats",
                            "player", stats.getPlayerId(),
                            "season", stats.getSeasonId()));
                    } catch (SQLException rollbackError) {
                        LOG.error("Rollback of the player stats transaction failed", rollbackError, LogContext.of(
                            "operation", "savePlayerStats",
                            "player", stats.getPlayerId(),
                            "season", stats.getSeasonId()));
                    }
                    throw e;
                }
            } catch (SQLException e) {
                LOG.error("Failed to save player stats", e, LogContext.of(
                    "operation", "savePlayerStats",
                    "player", stats.getPlayerId(),
                    "season", stats.getSeasonId()));
            } catch (Throwable t) {
                LOG.error("Unexpected failure while saving player stats", t, LogContext.of(
                    "operation", "savePlayerStats",
                    "player", stats.getPlayerId(),
                    "season", stats.getSeasonId()));
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
        if (playerId == null || seasonId == null) {
            LOG.warn("Refusing to load stats without a player id and season id",
                LogContext.of("player", playerId, "season", seasonId));
            return stats;
        }
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
                        } else {
                            LOG.warn("Ignoring stat row with an unknown type", LogContext.of(
                                "operation", "loadPlayerStats",
                                "player", playerId,
                                "season", seasonId,
                                "statKey", key,
                                "statType", type));
                        }
                    } catch (Exception e) {
                        LOG.error("Failed to parse stored stat", e, LogContext.of(
                            "operation", "loadPlayerStats",
                            "player", playerId,
                            "season", seasonId,
                            "statKey", key,
                            "statType", type,
                            "value", value));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to load player stats", e, LogContext.of(
                "operation", "loadPlayerStats", "player", playerId, "season", seasonId));
        } catch (Throwable t) {
            LOG.error("Unexpected failure while loading player stats", t, LogContext.of(
                "operation", "loadPlayerStats", "player", playerId, "season", seasonId));
        }
        return stats;
    }

    private void applyStat(PlayerStats stats, String key, long value) {
        if (key == null) {
            return;
        }
        switch (key) {
            case "playtime_seconds":
                stats.addPlaytime(value);
                break;
            case "blocks_mined":
                stats.addBlocksMined(value);
                break;
            case "blocks_placed":
                stats.addBlocksPlaced(value);
                break;
            case "distance_traveled":
                stats.addDistance(value);
                break;
            case "mobs_killed":
                stats.addMobsKilled(value);
                break;
            case "deaths":
                repeat(key, value, stats::addDeath);
                break;
            case "damage_dealt":
                stats.addDamageDealt(value);
                break;
            case "damage_received":
                stats.addDamageReceived(value);
                break;
            case "items_crafted":
                stats.addItemsCrafted(value);
                break;
            case "items_collected":
                stats.addItemsCollected(value);
                break;
            case "fishing_catches":
                repeat(key, value, stats::addFishingCatch);
                break;
            case "login_sessions":
                repeat(key, value, stats::addLoginSession);
                break;
            case "unique_days":
                repeat(key, value, stats::addUniqueDay);
                break;
            default:
                LOG.trace("Ignoring unknown scalar stat key",
                    LogContext.of("statKey", key, "value", value));
        }
    }

    private static final long MAX_REPEAT_ITERATIONS = 1_000_000L;

    private void repeat(String statKey, long value, Runnable increment) {
        long iterations = Math.max(0L, Math.min(value, MAX_REPEAT_ITERATIONS));
        if (iterations != value) {
            LOG.warn("Stored stat exceeds the safe replay limit and was clamped", LogContext.of(
                "statKey", statKey,
                "storedValue", value,
                "appliedValue", iterations));
        }
        for (long i = 0; i < iterations; i++) {
            increment.run();
        }
    }

    private void applyMapStat(PlayerStats stats, String key, String json) {
        Map<String, Long> map = parseJsonMap(json);
        if (key == null) {
            return;
        }
        switch (key) {
            case "dimension_time":
                map.forEach(stats::addDimensionTime);
                break;
            case "blocks_mined_by_type":
                map.forEach(stats::addBlockMined);
                break;
            case "blocks_placed_by_type":
                map.forEach(stats::addBlockPlaced);
                break;
            case "mobs_killed_by_type":
                map.forEach(stats::addMobKilled);
                break;
            case "daily_playtime":
                map.forEach(stats::addDailyPlaytime);
                break;
            case "hourly_playtime":
                map.forEach((hour, seconds) -> {
                    try {
                        stats.addHourlyPlaytime(Integer.parseInt(hour), seconds);
                    } catch (NumberFormatException e) {
                        LOG.warn("Ignoring hourly playtime entry with a non-numeric hour",
                            LogContext.of("hour", hour, "seconds", seconds));
                    }
                });
                break;
            default:
                LOG.trace("Ignoring unknown map stat key", LogContext.of("statKey", key));
        }
    }

    private Map<String, Long> parseJsonMap(String json) {
        Map<String, Long> map = new HashMap<>();
        if (json == null || json.isBlank() || json.equals("{}")) return map;
        
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        
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
                } catch (NumberFormatException e) {
                    LOG.trace("Skipping malformed map stat entry", LogContext.of("key", k, "value", v));
                }
            }
        }
        return map;
    }

    

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
                    String rawUuid = rs.getString("player_uuid");
                    String rawValue = rs.getString("stat_value");
                    try {
                        results.add(Map.entry(UUID.fromString(rawUuid), Long.parseLong(rawValue)));
                    } catch (IllegalArgumentException e) {
                        LOG.error("Skipping leaderboard row with invalid data", e, LogContext.of(
                            "operation", "getLeaderboard",
                            "season", seasonId,
                            "stat", statKey,
                            "playerUuid", rawUuid,
                            "value", rawValue));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to load leaderboard", e, LogContext.of(
                "operation", "getLeaderboard", "season", seasonId, "stat", statKey, "limit", limit));
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
                    String rawUuid = rs.getString("player_uuid");
                    String rawValue = rs.getString("stat_value");
                    try {
                        results.put(UUID.fromString(rawUuid), Long.parseLong(rawValue));
                    } catch (IllegalArgumentException e) {
                        LOG.error("Skipping stats row with invalid data", e, LogContext.of(
                            "operation", "getAllStatsForSeason",
                            "season", seasonId,
                            "stat", statKey,
                            "playerUuid", rawUuid,
                            "value", rawValue));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to load stats", e, LogContext.of(
                "operation", "getAllStatsForSeason", "season", seasonId, "stat", statKey));
        }
        return results;
    }

    

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
                LOG.error("Failed to store session start", e, LogContext.of(
                    "operation", "startSession", "player", session.playerId()));
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
                LOG.error("Failed to store session end", e, LogContext.of(
                    "operation", "endSession", "player", playerId, "logoutTime", logoutTime));
            }
        });
    }

    

    public void saveWrappedSnapshot(WrappedSnapshot snapshot) {
        if (snapshot == null) {
            LOG.warn("Refusing to save a null Wrapped snapshot");
            return;
        }
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
                LOG.error("Failed to save Wrapped snapshot", e, LogContext.of(
                    "operation", "saveWrappedSnapshot",
                    "player", snapshot.playerId(),
                    "season", snapshot.seasonId()));
            }
        });
    }

    public Optional<WrappedSnapshot> getWrappedSnapshot(UUID playerId, String seasonId) {
        if (playerId == null || seasonId == null) {
            LOG.warn("Refusing to load a Wrapped snapshot without a player id and season id",
                LogContext.of("player", playerId, "season", seasonId));
            return Optional.empty();
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT snapshot_json FROM wrapped_snapshots WHERE player_uuid = ? AND season_id = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(jsonToSnapshot(rs.getString("snapshot_json"), playerId, seasonId));
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to load Wrapped snapshot", e, LogContext.of(
                "operation", "getWrappedSnapshot", "player", playerId, "season", seasonId));
        } catch (RuntimeException e) {
            LOG.error("Stored Wrapped snapshot is corrupted", e, LogContext.of(
                "operation", "getWrappedSnapshot", "player", playerId, "season", seasonId));
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

    private WrappedSnapshot jsonToSnapshot(String json, UUID fallbackPlayerId, String fallbackSeasonId) {
        
        Map<String, Object> map;
        try {
            map = parseJsonObject(json);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Snapshot JSON is not parsable", e);
        }

        UUID playerId = readUuid(map.get("playerId"), fallbackPlayerId, "playerId");
        String seasonId = readString(map.get("seasonId"), fallbackSeasonId, "seasonId");
        String playerName = readString(map.get("playerName"), "unknown", "playerName");
        long generatedAt = readLong(map.get("generatedAt"), 0L, "generatedAt");
        String archetype = readString(map.get("archetype"), "unknown", "archetype");
        String archetypeDescription = readString(map.get("archetypeDescription"), "", "archetypeDescription");

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

    private UUID readUuid(Object raw, UUID fallback, String field) {
        if (raw instanceof String && !((String) raw).isBlank()) {
            String text = (String) raw;
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException e) {
                LOG.warn("Snapshot field is not a valid UUID, using fallback", LogContext.of(
                    "field", field, "value", text, "fallback", fallback));
            }
        }
        return fallback;
    }

    private String readString(Object raw, String fallback, String field) {
        return (raw instanceof String) ? (String) raw : fallback;
    }

    private long readLong(Object raw, long fallback, String field) {
        return (raw instanceof Number) ? ((Number) raw).longValue() : fallback;
    }

    private Map<String, Object> parseJsonObject(String json) {
        
        Map<String, Object> result = new HashMap<>();
        
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
                LOG.error("Failed to save achievement", e, LogContext.of(
                    "operation", "saveAchievement",
                    "player", playerId,
                    "season", seasonId,
                    "achievement", achievementKey));
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
            LOG.error("Failed to load achievements", e, LogContext.of(
                "operation", "getAchievements", "player", playerId, "season", seasonId));
        }
        return achievements;
    }

    

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
                } catch (SQLException e) {
                    LOG.error("Failed to aggregate a server-wide stat", e, LogContext.of(
                        "operation", "getServerWideStats", "season", seasonId, "stat", key));
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to load server-wide stats", e, LogContext.of(
                "operation", "getServerWideStats", "season", seasonId));
        }
        return stats;
    }

    

    public File createBackup() {
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists() && !backupDir.mkdirs() && !backupDir.exists()) {
                LOG.error("Could not create the backup directory", null, LogContext.of(
                    "directory", backupDir.getAbsolutePath()));
                return null;
            }
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new java.util.Date());
            File backupFile = new File(backupDir, "smpwrapped-backup-" + timestamp + ".db");

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("BACKUP TO ?")) {
                ps.setString(1, backupFile.getAbsolutePath());
                ps.execute();
            }
            LOG.info("Database backup created", LogContext.of(
                "file", backupFile.getName(),
                "path", backupFile.getAbsolutePath(),
                "sizeBytes", backupFile.exists() ? backupFile.length() : -1L));
            return backupFile;
        } catch (Throwable t) {
            LOG.error("Failed to create database backup", t);
            return null;
        }
    }

    public void shutdown() {
        LOG.info("Shutting down the database");
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOG.warn("Database tasks did not finish in time, forcing shutdown",
                        LogContext.of("pendingThreads", 2));
                    asyncExecutor.shutdownNow();
                } else {
                    LOG.debug("All database tasks completed");
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while waiting for database tasks to finish", e);
            }
        }
        if (dataSource != null) {
            try {
                dataSource.close();
                LOG.debug("Connection pool closed");
            } catch (Throwable t) {
                LOG.error("Failed to close the connection pool", t);
            }
        }
    }
}
