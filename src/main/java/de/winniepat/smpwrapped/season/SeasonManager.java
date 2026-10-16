package de.winniepat.smpwrapped.season;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.model.Season;
import de.winniepat.smpwrapped.model.PlayerStats;

import java.util.*;


public class SeasonManager {

    private final SMPWrapped plugin;

    public SeasonManager(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    
    public Season createSeason(String id, String name) {
        Season season = new Season(id, name, System.currentTimeMillis(), -1, Season.SeasonState.ACTIVE);
        plugin.getDatabase().createSeason(season);

        
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

    
    public boolean publishSeason(String seasonId) {
        Optional<Season> opt = plugin.getDatabase().getSeason(seasonId);
        if (opt.isEmpty()) return false;

        plugin.getDatabase().updateSeasonState(seasonId, Season.SeasonState.ARCHIVED);
        plugin.getLogger().info("Published season: " + seasonId);
        return true;
    }

    
    public boolean unpublishSeason(String seasonId) {
        Optional<Season> opt = plugin.getDatabase().getSeason(seasonId);
        if (opt.isEmpty()) return false;

        plugin.getDatabase().updateSeasonState(seasonId, Season.SeasonState.ENDED);
        plugin.getLogger().info("Unpublished season: " + seasonId);
        return true;
    }

    
    public Optional<Season> getCurrentSeason() {
        String currentId = plugin.getConfigManager().get().currentSeasonId();
        return plugin.getDatabase().getSeason(currentId);
    }

    
    public List<Season> getAllSeasons() {
        return plugin.getDatabase().getAllSeasons();
    }

    
    public boolean seasonExists(String seasonId) {
        return plugin.getDatabase().getSeason(seasonId).isPresent();
    }

    
    public boolean switchSeason(String seasonId) {
        if (!seasonExists(seasonId)) return false;
        plugin.getConfigManager().reload();
        
        plugin.getLogger().info("Switched current season to: " + seasonId);
        return true;
    }
}
