package de.winniepat.smpwrapped.tracking;

import de.winniepat.smpwrapped.model.PlayerStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerDataBufferTest {

    @Test
    @DisplayName("New buffers start empty for the given season")
    void newBuffersUseTheSeason() {
        PlayerDataBuffer buffer = new PlayerDataBuffer("season-1", id -> null);

        UUID player = UUID.randomUUID();
        PlayerStats stats = buffer.getBuffer(player);

        assertThat(stats.getSeasonId()).isEqualTo("season-1");
        assertThat(stats.getPlaytimeSeconds()).isZero();
        assertThat(buffer.getSeasonId()).isEqualTo("season-1");
    }

    @Test
    @DisplayName("The loader hydrates buffers so restarts do not lose stats")
    void loaderHydratesBuffers() {
        UUID player = UUID.randomUUID();
        PlayerStats stored = new PlayerStats(player, "season-1");
        stored.addPlaytime(3600);

        PlayerDataBuffer buffer = new PlayerDataBuffer("season-1", id -> stored);

        assertThat(buffer.getBuffer(player).getPlaytimeSeconds()).isEqualTo(3600);
        assertThat(buffer.getBuffer(player)).isSameAs(stored);
    }

    @Test
    @DisplayName("A failing loader yields a usable empty buffer instead of throwing")
    void failingLoaderFallsBackToEmptyBuffer() {
        UUID player = UUID.randomUUID();
        PlayerDataBuffer buffer = new PlayerDataBuffer("season-1", id -> {
            throw new IllegalStateException("database is down");
        });

        PlayerStats stats = buffer.getBuffer(player);

        assertThat(stats).isNotNull();
        assertThat(stats.getPlayerId()).isEqualTo(player);
        assertThat(stats.getPlaytimeSeconds()).isZero();
    }

    @Test
    @DisplayName("Switching the season clears buffers so stats never leak between seasons")
    void switchingSeasonClearsBuffers() {
        UUID player = UUID.randomUUID();
        Map<String, PlayerStats> store = new HashMap<>();
        PlayerDataBuffer buffer = new PlayerDataBuffer("season-1",
            id -> store.get(id + ":season-1"));

        buffer.getBuffer(player).addPlaytime(7200);
        assertThat(buffer.getBuffer(player).getPlaytimeSeconds()).isEqualTo(7200);

        store.put(player + ":season-2", new PlayerStats(player, "season-2"));
        buffer.clear();
        buffer.setSeasonId("season-2");

        PlayerStats reloaded = buffer.getBuffer(player);
        assertThat(reloaded.getSeasonId()).isEqualTo("season-2");
        assertThat(reloaded.getPlaytimeSeconds()).isZero();
    }

    @Test
    @DisplayName("Setting the same season again is a no-op and keeps the buffers")
    void settingSameSeasonKeepsBuffers() {
        UUID player = UUID.randomUUID();
        PlayerDataBuffer buffer = new PlayerDataBuffer("season-1", id -> null);
        PlayerStats stats = buffer.getBuffer(player);
        stats.addPlaytime(100);

        buffer.setSeasonId("season-1");

        assertThat(buffer.getBuffer(player)).isSameAs(stats);
        assertThat(buffer.getBuffer(player).getPlaytimeSeconds()).isEqualTo(100);
    }

    @Test
    @DisplayName("Blank season ids are rejected")
    void blankSeasonIsRejected() {
        PlayerDataBuffer buffer = new PlayerDataBuffer("season-1", id -> null);

        assertThatThrownBy(() -> buffer.setSeasonId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> buffer.setSeasonId("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(buffer.getSeasonId()).isEqualTo("season-1");
    }

    @Test
    @DisplayName("A null player id does not throw")
    void nullPlayerIdIsTolerated() {
        PlayerDataBuffer buffer = new PlayerDataBuffer("season-1", id -> null);

        PlayerStats stats = buffer.getBuffer(null);

        assertThat(stats).isNotNull();
        assertThat(stats.getSeasonId()).isEqualTo("season-1");
    }

    @Test
    @DisplayName("removeBuffer drops only the requested player")
    void removeBufferIsSelective() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PlayerDataBuffer buffer = new PlayerDataBuffer("season-1", id -> null);
        buffer.getBuffer(first);
        buffer.getBuffer(second);

        buffer.removeBuffer(first);

        assertThat(buffer.hasBuffer(first)).isFalse();
        assertThat(buffer.hasBuffer(second)).isTrue();
    }
}