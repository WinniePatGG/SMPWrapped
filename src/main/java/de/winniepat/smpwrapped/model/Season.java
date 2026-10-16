package de.winniepat.smpwrapped.model;

/**
 * Represents a season in the SMP.
 */
public record Season(
    String id,
    String name,
    long startTime,
    long endTime,       // -1 if still active
    SeasonState state
) {
    public enum SeasonState {
        ACTIVE,     // Currently running
        ENDED,      // Finished but not yet published
        ARCHIVED,   // Finished and published/archived
        DRAFT       // Created but not yet started
    }

    public boolean isActive() {
        return state == SeasonState.ACTIVE;
    }

    public boolean isArchived() {
        return state == SeasonState.ARCHIVED;
    }
}
