package de.winniepat.smpwrapped.model;

import java.util.UUID;
import java.util.Objects;


public final class Season {
    private final String id;
    private final String name;
    private final long startTime;
    private final long endTime;
    private final SeasonState state;

    public Season(String id, String name, long startTime, long endTime, SeasonState state) {
        this.id = id;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.state = state;
    }

    public String id() { return id; }
    public String name() { return name; }
    public long startTime() { return startTime; }
    public long endTime() { return endTime; }
    public SeasonState state() { return state; }

    public boolean isActive() {
        return state == SeasonState.ACTIVE;
    }

    public boolean isArchived() {
        return state == SeasonState.ARCHIVED;
    }

    public enum SeasonState {
        ACTIVE,
        ENDED,
        ARCHIVED,
        DRAFT
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Season)) return false;
        Season that = (Season) other;
        return startTime == that.startTime
            && endTime == that.endTime
            && Objects.equals(id, that.id)
            && Objects.equals(name, that.name)
            && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, startTime, endTime, state);
    }

    @Override
    public String toString() {
        return "Season[id=" + id + ", name=" + name + ", startTime=" + startTime
            + ", endTime=" + endTime + ", state=" + state + "]";
    }
}