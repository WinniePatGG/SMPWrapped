package de.winniepat.smpwrapped.command;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.*;
import de.winniepat.smpwrapped.util.Chat;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;


public class WrappedCommand implements CommandExecutor, TabCompleter {

    private static final SMPLogger LOG = SMPLogger.get("command");

    private final SMPWrapped plugin;

    public WrappedCommand(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            LOG.trace("Command executed", LogContext.of(
                "sender", sender.getName(),
                "label", label,
                "args", String.join(" ", args)));
            if (args.length == 0) {
                sendWrappedLink(sender);
                return true;
            }

            String sub = args[0].toLowerCase();
            if (sub.equals("stats")) {
                handleStats(sender);
            } else if (sub.equals("top")) {
                handleTop(sender);
            } else if (sub.equals("help")) {
                handleHelp(sender);
            } else if (sub.equals("reload")) {
                handleReload(sender);
            } else if (sub.equals("admin")) {
                handleAdmin(sender, args);
            } else {
                sender.sendMessage(Chat.of(Chat.RED, "Unknown subcommand. Use /wrapped help for help."));
            }
        } catch (Throwable t) {
            LOG.error("Command handler failed", t, LogContext.of(
                "sender", sender.getName(),
                "label", label,
                "args", String.join(" ", args)));
            sender.sendMessage(Chat.of(Chat.RED, "Something went wrong while running that command. Check the server log."));
        }
        return true;
    }

    private void sendWrappedLink(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Chat.of(Chat.RED, "Only players can use this command."));
            return;
        }
        Player player = (Player) sender;

        String baseUrl = plugin.getConfigManager().get().publicBaseUrl();
        String url = baseUrl + "/w/" + player.getName();

        sender.sendMessage(Chat.of(Chat.GREEN, "Click here to open your Wrapped!"));
        sender.sendMessage(Chat.link("", url));
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Chat.of(Chat.RED, "Only players can use this command."));
            return;
        }
        Player player = (Player) sender;

        String seasonId = plugin.getConfigManager().get().currentSeasonId();
        PlayerStats stats = plugin.getDatabase().loadPlayerStats(player.getUniqueId(), seasonId);

        sender.sendMessage(Chat.title("=== Your Stats ==="));
        sender.sendMessage(Chat.line(Chat.YELLOW, "Playtime", formatDuration(stats.getPlaytimeSeconds())));
        sender.sendMessage(Chat.line(Chat.YELLOW, "Blocks Mined", String.valueOf(stats.getBlocksMined())));
        sender.sendMessage(Chat.line(Chat.YELLOW, "Blocks Placed", String.valueOf(stats.getBlocksPlaced())));
        sender.sendMessage(Chat.line(Chat.YELLOW, "Distance Traveled", stats.getDistanceTraveled() + " blocks"));
        sender.sendMessage(Chat.line(Chat.YELLOW, "Mobs Killed", String.valueOf(stats.getMobsKilled())));
        sender.sendMessage(Chat.line(Chat.YELLOW, "Deaths", String.valueOf(stats.getDeaths())));
    }

    private void handleTop(CommandSender sender) {
        String seasonId = plugin.getConfigManager().get().currentSeasonId();
        List<Map.Entry<UUID, Long>> top = plugin.getDatabase().getLeaderboard(seasonId, "playtime_seconds", 5);

        sender.sendMessage(Chat.title("=== Top Players (Playtime) ==="));
        int[] rank = {1};
        for (Map.Entry<UUID, Long> entry : top) {
            final int place = rank[0];
            plugin.getDatabase().getPlayer(entry.getKey()).ifPresent(p ->
                sender.sendMessage(Chat.line(Chat.YELLOW, place + ". " + p.lastName(), formatDuration(entry.getValue()))));
            rank[0]++;
        }
    }

    private void handleHelp(CommandSender sender) {
        sender.sendMessage(Chat.title("=== SMPWrapped Commands ==="));
        sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped", "Open your Wrapped page"));
        sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped stats", "View your stats"));
        sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped top", "View top players"));
        sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped reload", "Reload configuration"));
        if (sender.hasPermission("smpwrapped.admin")) {
            sender.sendMessage(Chat.title("=== Admin Commands ==="));
            sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped admin create <name>", "Create and activate a season"));
            sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped admin seasons", "List all seasons"));
            sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped admin switch <season>", "Record stats into another season"));
            sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped admin end <season>", "End a season"));
            sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped admin publish <season>", "Publish (archive) a season"));
            sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped admin unpublish <season>", "Bring a season back to ENDED"));
            sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped admin stats <player>", "Show a player's stats"));
            sender.sendMessage(Chat.line(Chat.YELLOW, "/wrapped admin backup", "Create database backup"));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("smpwrapped.admin")) {
            sender.sendMessage(Chat.of(Chat.RED, "You don't have permission to use this command."));
            return;
        }
        try {
            plugin.getConfigManager().reload();
            plugin.getStatTracker().flushAll();
            sender.sendMessage(Chat.of(Chat.GREEN, "Configuration reloaded and stats flushed."));
            LOG.info("Configuration reloaded from chat", LogContext.of("sender", sender.getName()));
        } catch (Throwable t) {
            LOG.error("Reload failed", t, LogContext.of("sender", sender.getName()));
            sender.sendMessage(Chat.of(Chat.RED, "Reload failed, check the server log for details."));
        }
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smpwrapped.admin")) {
            sender.sendMessage(Chat.of(Chat.RED, "You don't have permission to use this command."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Chat.of(Chat.RED, "Usage: /wrapped admin <create|seasons|switch|end|publish|unpublish|stats|backup>"));
            return;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("create")) {
            handleAdminCreate(sender, args);
        } else if (sub.equals("end")) {
            handleAdminSeasonAction(sender, args, "end", "Ended season");
        } else if (sub.equals("publish")) {
            handleAdminSeasonAction(sender, args, "publish", "Published season");
        } else if (sub.equals("unpublish")) {
            handleAdminSeasonAction(sender, args, "unpublish", "Unpublished season");
        } else if (sub.equals("switch")) {
            handleAdminSwitch(sender, args);
        } else if (sub.equals("seasons")) {
            handleAdminSeasons(sender);
        } else if (sub.equals("stats")) {
            handleAdminStats(sender, args);
        } else if (sub.equals("backup")) {
            handleAdminBackup(sender);
        } else {
            sender.sendMessage(Chat.of(Chat.RED, "Unknown admin subcommand."));
        }
    }

    private void handleAdminCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Chat.of(Chat.RED, "Usage: /wrapped admin create <name>"));
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String id = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        try {
            Season season = plugin.getSeasonManager().createSeason(id, name);
            if (season == null) {
                sender.sendMessage(Chat.of(Chat.RED, "Could not create the season."));
                return;
            }
            sender.sendMessage(Chat.of(Chat.GREEN, "Created and activated season: " + name + " (" + id + ")"));
            sender.sendMessage(Chat.of(Chat.YELLOW, "Stats are now recorded into " + id + "."));
        } catch (RuntimeException e) {
            LOG.error("Could not create a season", e, LogContext.of(
                "sender", sender.getName(), "season", id, "name", name));
            sender.sendMessage(Chat.of(Chat.RED, "Could not create the season. Check the server log."));
        }
    }

    private void handleAdminSeasonAction(CommandSender sender, String[] args, String action, String successMessage) {
        if (args.length < 3) {
            sender.sendMessage(Chat.of(Chat.RED, "Usage: /wrapped admin " + action + " <season>"));
            return;
        }
        String seasonId = args[2];
        boolean ok;
        if (action.equals("end")) {
            ok = plugin.getSeasonManager().endSeason(seasonId);
        } else if (action.equals("publish")) {
            ok = plugin.getSeasonManager().publishSeason(seasonId);
        } else {
            ok = plugin.getSeasonManager().unpublishSeason(seasonId);
        }
        if (ok) {
            sender.sendMessage(Chat.of(Chat.GREEN, successMessage + ": " + seasonId));
        } else {
            sender.sendMessage(Chat.of(Chat.RED, "Failed to " + action + " season."));
        }
    }

    private void handleAdminSwitch(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Chat.of(Chat.RED, "Usage: /wrapped admin switch <season>"));
            return;
        }
        String seasonId = args[2];
        if (plugin.getSeasonManager().switchSeason(seasonId)) {
            sender.sendMessage(Chat.of(Chat.GREEN, "Now recording into season: " + seasonId));
        } else {
            sender.sendMessage(Chat.of(Chat.RED, "Failed to switch season. Check the server log."));
        }
    }

    private void handleAdminSeasons(CommandSender sender) {
        sender.sendMessage(Chat.title("=== Seasons ==="));
        String current = plugin.getSeasonManager().currentSeasonId();
        for (Season season : plugin.getSeasonManager().getAllSeasons()) {
            String marker = season.id().equals(current) ? "* " : "  ";
            sender.sendMessage(Chat.of(season.isActive() ? Chat.GREEN : Chat.YELLOW,
                marker + season.id() + " [" + season.state() + "] " + season.name()));
        }
    }

    private void handleAdminStats(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Chat.of(Chat.RED, "Usage: /wrapped admin stats <player>"));
            return;
        }
        final String playerName = args[2];
        plugin.getDatabase().getAllPlayers().stream()
            .filter(p -> p.lastName().equalsIgnoreCase(playerName))
            .findFirst()
            .ifPresentOrElse(p -> {
                PlayerStats stats = plugin.getDatabase().loadPlayerStats(
                    p.uuid(), plugin.getConfigManager().get().currentSeasonId());
                sender.sendMessage(Chat.title("=== Stats for " + p.lastName() + " ==="));
                sender.sendMessage(Chat.line(Chat.YELLOW, "Playtime", formatDuration(stats.getPlaytimeSeconds())));
                sender.sendMessage(Chat.line(Chat.YELLOW, "Blocks Mined", String.valueOf(stats.getBlocksMined())));
                sender.sendMessage(Chat.line(Chat.YELLOW, "Mobs Killed", String.valueOf(stats.getMobsKilled())));
            }, () -> sender.sendMessage(Chat.of(Chat.RED, "Player not found.")));
    }

    private void handleAdminBackup(CommandSender sender) {
        File backup = plugin.getDatabase().createBackup();
        if (backup != null) {
            sender.sendMessage(Chat.of(Chat.GREEN, "Backup created: " + backup.getName()));
        } else {
            sender.sendMessage(Chat.of(Chat.RED, "Backup failed."));
        }
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("stats", "top", "help", "reload"));
            if (sender.hasPermission("smpwrapped.admin")) {
                completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            completions.addAll(Arrays.asList(
                "create", "seasons", "switch", "end", "publish", "unpublish", "stats", "backup"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String action = args[1].toLowerCase();
            if (action.equals("switch") || action.equals("end")
                || action.equals("publish") || action.equals("unpublish")) {
                for (Season season : plugin.getSeasonManager().getAllSeasons()) {
                    completions.add(season.id());
                }
            } else if (action.equals("stats")) {
                for (TrackedPlayer tracked : plugin.getDatabase().getAllPlayers()) {
                    completions.add(tracked.lastName());
                }
            }
        }

        return completions;
    }
}