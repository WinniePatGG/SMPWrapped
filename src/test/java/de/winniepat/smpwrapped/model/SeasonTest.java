package de.winniepat.smpwrapped.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonTest {

    @Test
    @DisplayName("A season is only active in the ACTIVE state")
    void activeOnlyInActiveState() {
        assertThat(season(Season.SeasonState.ACTIVE).isActive()).isTrue();
        assertThat(season(Season.SeasonState.DRAFT).isActive()).isFalse();
        assertThat(season(Season.SeasonState.ENDED).isActive()).isFalse();
        assertThat(season(Season.SeasonState.ARCHIVED).isActive()).isFalse();
    }

    @Test
    @DisplayName("A season is only archived in the ARCHIVED state")
    void archivedOnlyInArchivedState() {
        assertThat(season(Season.SeasonState.ARCHIVED).isArchived()).isTrue();
        assertThat(season(Season.SeasonState.ACTIVE).isArchived()).isFalse();
        assertThat(season(Season.SeasonState.ENDED).isArchived()).isFalse();
    }

    @Test
    @DisplayName("The lifecycle create to end to publish walks the state chain")
    void lifecycleWalk() {
        Season created = season(Season.SeasonState.ACTIVE);
        assertThat(created.isActive()).isTrue();

        Season ended = new Season(created.id(), created.name(), created.startTime(), 1L, Season.SeasonState.ENDED);
        assertThat(ended.isActive()).isFalse();
        assertThat(ended.isArchived()).isFalse();

        Season published = new Season(ended.id(), ended.name(), ended.startTime(), ended.endTime(), Season.SeasonState.ARCHIVED);
        assertThat(published.isArchived()).isTrue();
        assertThat(published.isActive()).isFalse();
    }

    @Test
    @DisplayName("Only one state can be active at a time")
    void statesAreMutuallyExclusive() {
        for (Season.SeasonState state : Season.SeasonState.values()) {
            Season season = season(state);
            assertThat(season.isActive() && season.isArchived()).isFalse();
        }
    }

    @Test
    @DisplayName("An open season carries an end time of -1")
    void openSeasonHasNegativeEndTime() {
        assertThat(season(Season.SeasonState.ACTIVE).endTime()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("Seasons are compared by all their fields")
    void equality() {
        assertThat(season(Season.SeasonState.ACTIVE)).isEqualTo(season(Season.SeasonState.ACTIVE));
        assertThat(season(Season.SeasonState.ACTIVE)).isNotEqualTo(season(Season.SeasonState.ENDED));
        assertThatThrownBy(() -> Season.SeasonState.valueOf("FINISHED"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Season season(Season.SeasonState state) {
        return new Season("season-1", "Season 1", 1_000L, -1L, state);
    }
}