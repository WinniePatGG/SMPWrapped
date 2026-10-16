package de.winniepat.smpwrapped.tracking;

import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory buffer for accumulating player statistics.
 * Periodically flushed to the database.
 */
public class PlayerDataBuffer {
    private final ConcurrentHashMap<UUID, PlayerStats> buffers = new ConcurrentHashMap<>();
    private final String seasonId;

    public PlayerDataBuffer(String seasonId) {
        this.seasonId = seasonId;
    }

    public PlayerStats getBuffer(UUID playerId) {
        return buffers.computeIfAbsent(playerId, id -> new PlayerStats(id, seasonId));
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
