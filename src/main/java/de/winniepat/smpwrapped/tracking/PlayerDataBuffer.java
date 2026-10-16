package de.winniepat.smpwrapped.tracking;

import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Thread-safe in-memory buffer for accumulating player statistics.
 * Periodically flushed to the database.
 *
 * <p>When a player is seen for the first time in a session, their previously
 * persisted stats are loaded via the supplied loader so counters accumulate
 * across server restarts instead of resetting.</p>
 */
public class PlayerDataBuffer {
    private final ConcurrentHashMap<UUID, PlayerStats> buffers = new ConcurrentHashMap<>();
    private final String seasonId;
    private final Function<UUID, PlayerStats> loader;

    public PlayerDataBuffer(String seasonId, Function<UUID, PlayerStats> loader) {
        this.seasonId = seasonId;
        this.loader = loader;
    }

    public PlayerStats getBuffer(UUID playerId) {
        return buffers.computeIfAbsent(playerId, id -> {
            PlayerStats loaded = loader == null ? null : loader.apply(id);
            return loaded != null ? loaded : new PlayerStats(id, seasonId);
        });
    }

    /** True if a buffer already exists for this player (avoids a DB load). */
    public boolean hasBuffer(UUID playerId) {
        return buffers.containsKey(playerId);
    }

    public void removeBuffer(UUID playerId) {
        buffers.remove(playerId);
    }

    public ConcurrentHashMap<UUID, PlayerStats> getAllBuffers() {
        return buffers;
    }

    public String getSeasonId() {
        return seasonId;
    }

    public void clear() {
        buffers.clear();
    }
}
