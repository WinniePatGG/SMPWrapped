package de.winniepat.smpwrapped.season;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.Season;
import de.winniepat.smpwrapped.tracking.StatTracker;

import java.util.*;


public class SeasonManager {

    private static final SMPLogger LOG = SMPLogger.get("seasons");

    private final SMPWrapped plugin;

    public SeasonManager(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    
    public Season createSeason(String id, String name) {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            LOG.warn("Refusing to create a season without an id or a name",
                LogContext.of("season", id, "name", name));
            throw new IllegalArgumentException("A season needs both an id and a name");
        }

        List<String> ended = endEveryOtherActiveSeason(id);

        Season season = new Season(id, name, System.currentTimeMillis(), -1, Season.SeasonState.ACTIVE);
        if (!plugin.getDatabase().createSeason(season)) {
            throw new IllegalStateException("Could not store the new season " + id);
        }
        LOG.info("Created a new season", LogContext.of(
            "season", id, "name", name, "endedBefore", ended));

        makeCurrent(season.id(), false);
        return season;
    }

    private List<String> endEveryOtherActiveSeason(String keepSeasonId) {
        List<String> ended = new ArrayList<>();
        List<Season> seasons;
        try {
            seasons = plugin.getDatabase().getAllSeasons();
        } catch (Throwable t) {
            LOG.error("Could not list seasons, cannot guarantee a single active season", t,
                LogContext.of("season", keepSeasonId));
            return ended;
        }
        for (Season season : seasons) {
            if (!season.isActive() || season.id().equals(keepSeasonId)) {
                continue;
            }
            if (endSeasonInternal(season.id())) {
                ended.add(season.id());
            }
        }
        if (!ended.isEmpty()) {
            LOG.info("Ended previously active seasons", LogContext.of("seasons", ended));
        }
        return ended;
    }

    
    public boolean endSeason(String seasonId) {
        Optional<Season> opt = plugin.getDatabase().getSeason(seasonId);
        if (opt.isEmpty()) {
            LOG.warn("Cannot end a season that does not exist", LogContext.of("season", seasonId));
            return false;
        }

        Season season = opt.get();
        if (!season.isActive() && season.state() != Season.SeasonState.DRAFT) {
            LOG.warn("Season cannot be ended in its current state", LogContext.of(
                "season", seasonId, "state", season.state()));
            return false;
        }

        if (!endSeasonInternal(seasonId)) {
            return false;
        }

        LOG.info("Ended season", LogContext.of("season", seasonId, "name", season.name()));

        if (seasonId.equals(currentSeasonId())) {
            LOG.warn("The current season was just ended, stats keep recording into it until you switch seasons",
                LogContext.of("season", seasonId,
                    "hint", "Run /wrapped admin create <name> or /wrapped admin switch <season>"));
        }

        if (autoPublishOnEnd()) {
            boolean published = publishSeason(seasonId);
            LOG.info(published ? "Auto-published the season because publication.auto-publish-on-end is on"
                    : "Auto-publish was requested but failed",
                LogContext.of("season", seasonId));
        }
        return true;
    }

    private boolean endSeasonInternal(String seasonId) {
        long endTime = System.currentTimeMillis();
        plugin.getDatabase().setSeasonEndTime(seasonId, endTime);
        plugin.getDatabase().updateSeasonState(seasonId, Season.SeasonState.ENDED);
        return true;
    }

    private boolean autoPublishOnEnd() {
        try {
            return plugin.getConfigManager().get().autoPublishOnEnd();
        } catch (Throwable t) {
            LOG.error("Could not read publication.auto-publish-on-end, defaulting to off", t);
            return false;
        }
    }

    
    public boolean publishSeason(String seasonId) {
        Optional<Season> opt = plugin.getDatabase().getSeason(seasonId);
        if (opt.isEmpty()) {
            LOG.warn("Cannot publish a season that does not exist", LogContext.of("season", seasonId));
            return false;
        }

        plugin.getDatabase().updateSeasonState(seasonId, Season.SeasonState.ARCHIVED);
        LOG.info("Published season", LogContext.of(
            "season", seasonId, "name", opt.get().name(), "previousState", opt.get().state()));
        return true;
    }

    
    public boolean unpublishSeason(String seasonId) {
        Optional<Season> opt = plugin.getDatabase().getSeason(seasonId);
        if (opt.isEmpty()) {
            LOG.warn("Cannot unpublish a season that does not exist", LogContext.of("season", seasonId));
            return false;
        }

        plugin.getDatabase().updateSeasonState(seasonId, Season.SeasonState.ENDED);
        LOG.info("Unpublished season", LogContext.of(
            "season", seasonId, "name", opt.get().name(), "previousState", opt.get().state()));
        return true;
    }

    
    public Optional<Season> getCurrentSeason() {
        String currentId = currentSeasonId();
        return currentId == null ? Optional.empty() : plugin.getDatabase().getSeason(currentId);
    }

    public String currentSeasonId() {
        try {
            return plugin.getConfigManager().get().currentSeasonId();
        } catch (Throwable t) {
            LOG.error("Could not read the current season id", t);
            return null;
        }
    }

    
    public List<Season> getAllSeasons() {
        return plugin.getDatabase().getAllSeasons();
    }

    
    public boolean seasonExists(String seasonId) {
        if (seasonId == null || seasonId.isBlank()) {
            return false;
        }
        return plugin.getDatabase().getSeason(seasonId).isPresent();
    }

    
    public boolean switchSeason(String seasonId) {
        if (!seasonExists(seasonId)) {
            LOG.warn("Cannot switch to a season that does not exist", LogContext.of("season", seasonId));
            return false;
        }
        if (seasonId.equals(currentSeasonId())) {
            LOG.debug("Already the current season", LogContext.of("season", seasonId));
            return true;
        }
        if (!makeCurrent(seasonId, true)) {
            return false;
        }
        LOG.info("Switched the current season", LogContext.of("season", seasonId));
        return true;
    }

    private boolean makeCurrent(String seasonId, boolean failIfAlreadyCurrent) {
        if (failIfAlreadyCurrent && seasonId.equals(currentSeasonId())) {
            return true;
        }
        StatTracker tracker = plugin.getStatTracker();
        if (tracker != null) {
            tracker.switchSeason(seasonId);
        } else {
            LOG.warn("No stat tracker available, recording will only pick up the new season after a restart",
                LogContext.of("season", seasonId));
        }
        try {
            plugin.getConfigManager().setCurrentSeasonId(seasonId);
            return true;
        } catch (RuntimeException e) {
            LOG.error("Could not persist the new current season", e, LogContext.of("season", seasonId));
            if (tracker != null) {
                tracker.switchSeason(currentSeasonId());
            }
            return false;
        }
    }
}