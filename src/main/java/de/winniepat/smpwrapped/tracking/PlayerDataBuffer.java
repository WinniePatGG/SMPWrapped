package de.winniepat.smpwrapped.tracking;

import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


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
