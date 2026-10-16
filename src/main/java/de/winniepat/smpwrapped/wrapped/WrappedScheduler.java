package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.config.PluginConfig;
import de.winniepat.smpwrapped.model.TrackedPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
            plugin.getLogger().info("Scheduled Wrapped generation is disabled.");
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
            executor.shutdownNow();
            executor = null;
        }
    }

    private void logSchedule(PluginConfig config) {
        if (config.weeklyWrappedEnabled()) {
            plugin.getLogger().info("Weekly Wrapped generation scheduled for "
                + DayOfWeek.of(config.weeklyWrappedDayOfWeek()) + " at "
                + formatTime(config.weeklyWrappedHour(), config.weeklyWrappedMinute()) + " (server time).");
        }
        if (config.monthlyWrappedEnabled()) {
            plugin.getLogger().info("Monthly Wrapped generation scheduled for day "
                + config.monthlyWrappedDay() + " at "
                + formatTime(config.monthlyWrappedHour(), config.monthlyWrappedMinute()) + " (server time).");
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
                    generateAll("weekly");
                }
            }

            if (config.monthlyWrappedEnabled()) {
                long target = monthlyTarget(now, config).atZone(zone).toInstant().toEpochMilli();
                if (nowMillis >= target && lastMonthlyRun < target) {
                    lastMonthlyRun = nowMillis;
                    saveState();
                    generateAll("monthly");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Wrapped scheduler error: " + e.getMessage());
        }
    }

    
    private void generateAll(String reason) {
        String seasonId = plugin.getConfigManager().get().currentSeasonId();
        List<TrackedPlayer> players = plugin.getDatabase().getAllPlayers();
        int generated = 0;
        for (TrackedPlayer player : players) {
            if (player.optedOut()) {
                continue;
            }
            try {
                plugin.getWrappedGenerator().generateAndSave(player.uuid(), seasonId, player.lastName());
                generated++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to generate " + reason + " Wrapped for "
                    + player.lastName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Generated " + generated + "/" + players.size()
            + " " + reason + " Wrapped snapshots.");
        notifyPlayers(reason, generated);
    }

    private void notifyPlayers(String reason, int generated) {
        if (generated <= 0) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            String base = plugin.getConfigManager().get().publicBaseUrl();
            if (base != null && base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                String url = base + "/w/" + player.getName();
                player.sendMessage(Component.text("[SMPWrapped] ", NamedTextColor.GREEN)
                    .append(Component.text("Your " + reason + " Wrapped is ready! ", NamedTextColor.WHITE))
                    .append(Component.text("Click to open", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(url))));
            }
        });
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
            return;
        }
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
            lastWeeklyRun = parseLong(props.getProperty("weekly"));
            lastMonthlyRun = parseLong(props.getProperty("monthly"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load schedule state: " + e.getMessage());
        }
    }

    private void saveState() {
        Properties props = new Properties();
        props.setProperty("weekly", Long.toString(lastWeeklyRun));
        props.setProperty("monthly", Long.toString(lastMonthlyRun));
        try (OutputStream out = new FileOutputStream(stateFile())) {
            props.store(out, "SMPWrapped schedule last-run timestamps");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save schedule state: " + e.getMessage());
        }
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
