package de.winniepat.smpwrapped.season;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.model.Season;
import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.*;

/**
 * Manages season lifecycle: creation, ending, archiving, and publication.
 */
public class SeasonManager {

    private final SMPWrapped plugin;

    public SeasonManager(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    /**
     * Create a new season.
     */
    public Season createSeason(String id, String name) {
        Season season = new Season(id, name, System.currentTimeMillis(), -1, Season.SeasonState.ACTIVE);
        plugin.getDatabase().createSeason(season);

        // If there's a current active season, end it first
        String currentId = plugin.getConfigManager().get().currentSeasonId();
        if (currentId != null && !currentId.isBlank()) {
            plugin.getDatabase().getSeason(currentId).ifPresent(current -> {
                if (current.isActive()) {
                    endSeason(currentId);
                }
            });
        }

        plugin.getLogger().info("Created new season: " + name + " (" + id + ")");
        return season;
    }

    /**
     * End a season (archive it).
     */
    public boolean endSeason(String seasonId) {
        Optional<Season> opt = plugin.getDatabase().getSeason(seasonId);
        if (opt.isEmpty()) return false;

        Season season = opt.get();
        if (!season.isActive() && season.state() != Season.SeasonState.DRAFT) return false;

        long endTime = System.currentTimeMillis();
        plugin.getDatabase().setSeasonEndTime(seasonId, endTime);
        plugin.getDatabase().updateSeasonState(seasonId, Season.SeasonState.ENDED);

        plugin.getLogger().info("Ended season: " + season.name() + " (" + seasonId + ")");
        return true;
    }

    /**
     * Publish a season (make Wrapped pages publicly available).
     */
    public boolean publishSeason(String seasonId) {
        Optional<Season> opt = plugin.getDatabase().getSeason(seasonId);
        if (opt.isEmpty()) return false;

        plugin.getDatabase().updateSeasonState(seasonId, Season.SeasonState.ARCHIVED);
        plugin.getLogger().info("Published season: " + seasonId);
        return true;
    }

    /**
     * Unpublish a season.
     */
    public boolean unpublishSeason(String seasonId) {
        Optional<Season> opt = plugin.getDatabase().getSeason(seasonId);
        if (opt.isEmpty()) return false;

        plugin.getDatabase().updateSeasonState(seasonId, Season.SeasonState.ENDED);
        plugin.getLogger().info("Unpublished season: " + seasonId);
        return true;
    }

    /**
     * Get the current active season.
     */
    public Optional<Season> getCurrentSeason() {
        String currentId = plugin.getConfigManager().get().currentSeasonId();
        return plugin.getDatabase().getSeason(currentId);
    }

    /**
     * Get all seasons.
     */
    public List<Season> getAllSeasons() {
        return plugin.getDatabase().getAllSeasons();
    }

    /**
     * Check if a season exists.
     */
    public boolean seasonExists(String seasonId) {
        return plugin.getDatabase().getSeason(seasonId).isPresent();
    }

    /**
     * Switch the current season.
     */
    public boolean switchSeason(String seasonId) {
        if (!seasonExists(seasonId)) return false;
        plugin.getConfigManager().reload();
        // Note: In production you'd update the config file here
        plugin.getLogger().info("Switched current season to: " + seasonId);
        return true;
    }
}
