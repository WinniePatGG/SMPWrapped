package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.config.PluginConfig;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.TrackedPlayer;
import de.winniepat.smpwrapped.util.Chat;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public final class WrappedScheduler {

    private static final SMPLogger LOG = SMPLogger.get("scheduler");

    private static final long CHECK_INTERVAL_SECONDS = 30L;

    private final SMPWrapped plugin;
    private ScheduledExecutorService executor;

    private volatile long lastWeeklyRun = 0L;   
    private volatile long lastMonthlyRun = 0L;  

    public WrappedScheduler(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    public void start() {
        PluginConfig config = plugin.getConfigManager().get();
        if (!config.weeklyWrappedEnabled() && !config.monthlyWrappedEnabled()) {
            LOG.info("Scheduled Wrapped generation is disabled");
            return;
        }

        loadState();

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SMPWrapped-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        executor.scheduleAtFixedRate(this::tick, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);

        logSchedule(config);
    }

    public void stop() {
        if (executor != null) {
            LOG.info("Stopping the Wrapped scheduler");
            executor.shutdownNow();
            executor = null;
        }
    }

    private void logSchedule(PluginConfig config) {
        if (config.weeklyWrappedEnabled()) {
            LOG.info("Weekly Wrapped generation scheduled", LogContext.of(
                "day", DayOfWeek.of(config.weeklyWrappedDayOfWeek()).name(),
                "time", formatTime(config.weeklyWrappedHour(), config.weeklyWrappedMinute()),
                "checkIntervalSeconds", CHECK_INTERVAL_SECONDS));
        }
        if (config.monthlyWrappedEnabled()) {
            LOG.info("Monthly Wrapped generation scheduled", LogContext.of(
                "dayOfMonth", config.monthlyWrappedDay(),
                "time", formatTime(config.monthlyWrappedHour(), config.monthlyWrappedMinute()),
                "checkIntervalSeconds", CHECK_INTERVAL_SECONDS));
        }
    }

    private void tick() {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            PluginConfig config = plugin.getConfigManager().get();
            LocalDateTime now = LocalDateTime.now();
            ZoneId zone = ZoneId.systemDefault();
            long nowMillis = now.atZone(zone).toInstant().toEpochMilli();

            if (config.weeklyWrappedEnabled()) {
                long target = weeklyTarget(now, config).atZone(zone).toInstant().toEpochMilli();
                if (nowMillis >= target && lastWeeklyRun < target) {
                    
                    lastWeeklyRun = nowMillis;
                    saveState();
                    LOG.info("Running the scheduled weekly Wrapped generation", LogContext.of(
                        "targetMillis", target, "missedByMillis", nowMillis - target));
                    generateAll("weekly");
                }
            }

            if (config.monthlyWrappedEnabled()) {
                long target = monthlyTarget(now, config).atZone(zone).toInstant().toEpochMilli();
                if (nowMillis >= target && lastMonthlyRun < target) {
                    lastMonthlyRun = nowMillis;
                    saveState();
                    LOG.info("Running the scheduled monthly Wrapped generation", LogContext.of(
                        "targetMillis", target, "missedByMillis", nowMillis - target));
                    generateAll("monthly");
                }
            }
        } catch (Throwable t) {
            LOG.error("Wrapped scheduler tick failed", t, LogContext.of(
                "weeklyEnabled", safeWeeklyEnabled(),
                "monthlyEnabled", safeMonthlyEnabled()));
        }
    }

    private boolean safeWeeklyEnabled() {
        try {
            return plugin.getConfigManager().get().weeklyWrappedEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean safeMonthlyEnabled() {
        try {
            return plugin.getConfigManager().get().monthlyWrappedEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    
    private void generateAll(String reason) {
        String seasonId = plugin.getConfigManager().get().currentSeasonId();
        List<TrackedPlayer> players;
        try {
            players = plugin.getDatabase().getAllPlayers();
        } catch (Throwable t) {
            LOG.error("Could not load players for the scheduled Wrapped generation", t, LogContext.of(
                "reason", reason, "season", seasonId));
            return;
        }

        int generated = 0;
        int failed = 0;
        int skipped = 0;
        for (TrackedPlayer player : players) {
            if (player.optedOut()) {
                skipped++;
                continue;
            }
            try {
                plugin.getWrappedGenerator().generateAndSave(player.uuid(), seasonId, player.lastName());
                generated++;
            } catch (Throwable t) {
                failed++;
                LOG.error("Failed to generate " + reason + " Wrapped for a player", t, LogContext.of(
                    "reason", reason, "season", seasonId,
                    "player", player.lastName(), "uuid", player.uuid()));
            }
        }

        if (failed > 0) {
            LOG.error("Scheduled Wrapped generation finished with failures", null, LogContext.of(
                "reason", reason, "season", seasonId,
                "players", players.size(), "generated", generated,
                "failed", failed, "skippedOptedOut", skipped));
        } else {
            LOG.info("Scheduled Wrapped generation finished", LogContext.of(
                "reason", reason, "season", seasonId,
                "players", players.size(), "generated", generated,
                "skippedOptedOut", skipped));
        }
        notifyPlayers(reason, generated);
    }

    private void notifyPlayers(String reason, int generated) {
        if (generated <= 0) {
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        String base = plugin.getConfigManager().get().publicBaseUrl();
                        if (base != null && base.endsWith("/")) {
                            base = base.substring(0, base.length() - 1);
                        }
                        int notified = 0;
                        for (Player player : plugin.getServer().getOnlinePlayers()) {
                            String url = base + "/w/" + player.getName();
                            player.sendMessage(Chat.prefix("Your " + reason + " Wrapped is ready!"));
                            player.sendMessage(Chat.link("Click to open:", url));
                            notified++;
                        }
                        LOG.debug("Notified online players", LogContext.of(
                            "reason", reason, "notified", notified));
                    } catch (Throwable t) {
                        LOG.error("Could not notify players about their new Wrapped", t,
                            LogContext.of("reason", reason));
                    }
                }
            });
        } catch (Throwable t) {
            LOG.error("Could not schedule the Wrapped notification", t,
                LogContext.of("reason", reason));
        }
    }

    private LocalDateTime weeklyTarget(LocalDateTime now, PluginConfig config) {
        LocalDate monday = now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate target = monday.plusDays(config.weeklyWrappedDayOfWeek() - 1L);
        return target.atTime(config.weeklyWrappedHour(), config.weeklyWrappedMinute());
    }

    private LocalDateTime monthlyTarget(LocalDateTime now, PluginConfig config) {
        LocalDate first = now.toLocalDate().withDayOfMonth(1);
        int day = Math.min(config.monthlyWrappedDay(), first.lengthOfMonth());
        return first.withDayOfMonth(day).atTime(config.monthlyWrappedHour(), config.monthlyWrappedMinute());
    }

    private static String formatTime(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    

    private File stateFile() {
        return new File(plugin.getDataFolder(), "wrapped-schedule.state");
    }

    private void loadState() {
        File file = stateFile();
        if (!file.exists()) {
            LOG.debug("No schedule state file yet", LogContext.of("file", file.getName()));
            return;
        }
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
            lastWeeklyRun = parseLong(props.getProperty("weekly"));
            lastMonthlyRun = parseLong(props.getProperty("monthly"));
            LOG.debug("Schedule state loaded", LogContext.of(
                "lastWeeklyRun", lastWeeklyRun, "lastMonthlyRun", lastMonthlyRun));
        } catch (Throwable t) {
            LOG.error("Failed to load the schedule state file; schedules may run twice after a restart", t,
                LogContext.of("file", file.getAbsolutePath()));
        }
    }

    private void saveState() {
        Properties props = new Properties();
        props.setProperty("weekly", Long.toString(lastWeeklyRun));
        props.setProperty("monthly", Long.toString(lastMonthlyRun));
        File file = stateFile();
        try (OutputStream out = new FileOutputStream(file)) {
            props.store(out, "SMPWrapped schedule last-run timestamps");
        } catch (Throwable t) {
            LOG.error("Failed to save the schedule state file", t,
                LogContext.of("file", file.getAbsolutePath()));
        }
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Ignoring an unparsable schedule timestamp", LogContext.of("value", value));
            return 0L;
        }
    }
}
