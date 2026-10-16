package de.winniepat.smpwrapped.model;


public record Season(
    String id,
    String name,
    long startTime,
    long endTime,       
    SeasonState state
) {
    public enum SeasonState {
        ACTIVE,     
        ENDED,      
        ARCHIVED,   
        DRAFT       
    }

    public boolean isActive() {
        return state == SeasonState.ACTIVE;
    }

    public boolean isArchived() {
        return state == SeasonState.ARCHIVED;
    }
}
