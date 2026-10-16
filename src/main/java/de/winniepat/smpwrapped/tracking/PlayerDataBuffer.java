package de.winniepat.smpwrapped.tracking;

import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


public class PlayerDataBuffer {
    private static final SMPLogger LOG = SMPLogger.get("buffer");

    private final ConcurrentHashMap<UUID, PlayerStats> buffers = new ConcurrentHashMap<>();
    private volatile String seasonId;
    private final Function<UUID, PlayerStats> loader;

    public PlayerDataBuffer(String seasonId, Function<UUID, PlayerStats> loader) {
        this.seasonId = seasonId;
        this.loader = loader;
    }

    public PlayerStats getBuffer(UUID playerId) {
        if (playerId == null) {
            LOG.warn("Refusing to create a stat buffer without a player id");
            return new PlayerStats(new UUID(0L, 0L), seasonId);
        }
        PlayerStats existing = buffers.get(playerId);
        if (existing != null) {
            return existing;
        }
        try {
            return buffers.computeIfAbsent(playerId, id -> {
                PlayerStats loaded = null;
                if (loader != null) {
                    try {
                        loaded = loader.apply(id);
                    } catch (Throwable t) {
                        LOG.error("Could not hydrate the stat buffer from the database", t, LogContext.of(
                            "player", id, "season", seasonId));
                    }
                }
                if (loaded != null) {
                    LOG.trace("Hydrated stat buffer", LogContext.of("player", id, "season", seasonId));
                    return loaded;
                }
                LOG.warn("Started an empty stat buffer, any stored stats for this player could not be loaded",
                    LogContext.of("player", id, "season", seasonId));
                return new PlayerStats(id, seasonId);
            });
        } catch (Throwable t) {
            LOG.error("Could not create a stat buffer", t, LogContext.of("player", playerId, "season", seasonId));
            return new PlayerStats(playerId, seasonId);
        }
    }

    
    public boolean hasBuffer(UUID playerId) {
        return buffers.containsKey(playerId);
    }

    public Optional<PlayerStats> peekBuffer(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(buffers.get(playerId));
    }

    public void removeBuffer(UUID playerId) {
        PlayerStats removed = buffers.remove(playerId);
        if (removed != null) {
            LOG.trace("Removed stat buffer", LogContext.of("player", playerId, "season", seasonId));
        }
    }

    public ConcurrentHashMap<UUID, PlayerStats> getAllBuffers() {
        return buffers;
    }

    public String getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(String seasonId) {
        if (seasonId == null || seasonId.isBlank()) {
            throw new IllegalArgumentException("seasonId must not be blank");
        }
        if (seasonId.equals(this.seasonId)) {
            return;
        }
        this.seasonId = seasonId;
        LOG.info("Stat buffer season changed", LogContext.of("season", seasonId));
    }

    public void clear() {
        int size = buffers.size();
        buffers.clear();
        LOG.trace("Cleared stat buffers", LogContext.of("cleared", size));
    }
}