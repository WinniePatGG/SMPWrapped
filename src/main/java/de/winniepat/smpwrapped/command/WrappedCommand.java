package de.winniepat.smpwrapped.command;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

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

            switch (args[0].toLowerCase()) {
                case "stats" -> handleStats(sender);
                case "top" -> handleTop(sender);
                case "help" -> handleHelp(sender);
                case "reload" -> handleReload(sender);
                case "admin" -> handleAdmin(sender, args);
                default -> sender.sendMessage(Component.text("Unknown subcommand. Use /wrapped help for help.", NamedTextColor.RED));
            }
        } catch (Throwable t) {
            LOG.error("Command handler failed", t, LogContext.of(
                "sender", sender.getName(),
                "label", label,
                "args", String.join(" ", args)));
            sender.sendMessage(Component.text("Something went wrong while running that command. Check the server log.", NamedTextColor.RED));
        }
        return true;
    }

    private void sendWrappedLink(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        String baseUrl = plugin.getConfigManager().get().publicBaseUrl();
        String url = baseUrl + "/w/" + player.getName();

        player.sendMessage(Component.text("Click here to open your Wrapped!", NamedTextColor.GREEN)
            .clickEvent(ClickEvent.openUrl(url)));
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        String seasonId = plugin.getConfigManager().get().currentSeasonId();
        PlayerStats stats = plugin.getDatabase().loadPlayerStats(player.getUniqueId(), seasonId);

        sender.sendMessage(Component.text("=== Your Stats ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Playtime: " + formatDuration(stats.getPlaytimeSeconds()), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Blocks Mined: " + stats.getBlocksMined(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Blocks Placed: " + stats.getBlocksPlaced(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Distance Traveled: " + stats.getDistanceTraveled() + " blocks", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Mobs Killed: " + stats.getMobsKilled(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Deaths: " + stats.getDeaths(), NamedTextColor.YELLOW));
    }

    private void handleTop(CommandSender sender) {
        String seasonId = plugin.getConfigManager().get().currentSeasonId();
        List<Map.Entry<UUID, Long>> top = plugin.getDatabase().getLeaderboard(seasonId, "playtime_seconds", 5);

        sender.sendMessage(Component.text("=== Top Players (Playtime) ===", NamedTextColor.GOLD));
        int[] rank = {1};
        for (Map.Entry<UUID, Long> entry : top) {
            plugin.getDatabase().getPlayer(entry.getKey()).ifPresent(p -> {
                sender.sendMessage(Component.text(rank[0] + ". " + p.lastName() + " - " + formatDuration(entry.getValue()), NamedTextColor.YELLOW));
            });
            rank[0]++;
        }
    }

    private void handleHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== SMPWrapped Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/wrapped - Open your Wrapped page", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/wrapped stats - View your stats", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/wrapped top - View top players", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/wrapped reload - Reload configuration", NamedTextColor.YELLOW));
        if (sender.hasPermission("smpwrapped.admin")) {
            sender.sendMessage(Component.text("=== Admin Commands ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("/wrapped admin create <name> - Create and activate a season", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin seasons - List all seasons", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin switch <season> - Record stats into another season", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin end <season> - End a season", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin publish <season> - Publish (archive) a season", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin unpublish <season> - Bring a season back to ENDED", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin stats <player> - Show a player's stats", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin backup - Create database backup", NamedTextColor.YELLOW));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("smpwrapped.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return;
        }
        try {
            plugin.getConfigManager().reload();
            plugin.getStatTracker().flushAll();
            sender.sendMessage(Component.text("Configuration reloaded and stats flushed.", NamedTextColor.GREEN));
            LOG.info("Configuration reloaded from chat", LogContext.of("sender", sender.getName()));
        } catch (Throwable t) {
            LOG.error("Reload failed", t, LogContext.of("sender", sender.getName()));
            sender.sendMessage(Component.text("Reload failed, check the server log for details.", NamedTextColor.RED));
        }
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smpwrapped.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /wrapped admin <create|end|publish|stats|backup>", NamedTextColor.RED));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /wrapped admin create <name>", NamedTextColor.RED));
                    return;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                String id = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
                try {
                    Season season = plugin.getSeasonManager().createSeason(id, name);
                    if (season == null) {
                        sender.sendMessage(Component.text("Could not create the season.", NamedTextColor.RED));
                        return;
                    }
                    sender.sendMessage(Component.text("Created and activated season: " + name + " (" + id + ")", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text("Stats are now recorded into " + id + ".", NamedTextColor.YELLOW));
                } catch (RuntimeException e) {
                    LOG.error("Could not create a season", e, LogContext.of(
                        "sender", sender.getName(), "season", id, "name", name));
                    sender.sendMessage(Component.text("Could not create the season. Check the server log.", NamedTextColor.RED));
                }
            }
            case "end" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /wrapped admin end <season>", NamedTextColor.RED));
                    return;
                }
                String seasonId = args[2];
                if (plugin.getSeasonManager().endSeason(seasonId)) {
                    sender.sendMessage(Component.text("Ended season: " + seasonId, NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Failed to end season.", NamedTextColor.RED));
                }
            }
            case "publish" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /wrapped admin publish <season>", NamedTextColor.RED));
                    return;
                }
                String seasonId = args[2];
                if (plugin.getSeasonManager().publishSeason(seasonId)) {
                    sender.sendMessage(Component.text("Published season: " + seasonId, NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Failed to publish season.", NamedTextColor.RED));
                }
            }
            case "unpublish" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /wrapped admin unpublish <season>", NamedTextColor.RED));
                    return;
                }
                String seasonId = args[2];
                if (plugin.getSeasonManager().unpublishSeason(seasonId)) {
                    sender.sendMessage(Component.text("Unpublished season: " + seasonId, NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Failed to unpublish season.", NamedTextColor.RED));
                }
            }
            case "switch" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /wrapped admin switch <season>", NamedTextColor.RED));
                    return;
                }
                String seasonId = args[2];
                if (plugin.getSeasonManager().switchSeason(seasonId)) {
                    sender.sendMessage(Component.text("Now recording into season: " + seasonId, NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Failed to switch season. Check the server log.", NamedTextColor.RED));
                }
            }
            case "seasons" -> {
                sender.sendMessage(Component.text("=== Seasons ===", NamedTextColor.GOLD));
                String current = plugin.getSeasonManager().currentSeasonId();
                for (Season season : plugin.getSeasonManager().getAllSeasons()) {
                    sender.sendMessage(Component.text(
                        (season.id().equals(current) ? "* " : "  ") + season.id() + " [" + season.state() + "] " + season.name(),
                        season.isActive() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                }
            }
            case "stats" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /wrapped admin stats <player>", NamedTextColor.RED));
                    return;
                }
                String playerName = args[2];
                
                plugin.getDatabase().getAllPlayers().stream()
                    .filter(p -> p.lastName().equalsIgnoreCase(playerName))
                    .findFirst()
                    .ifPresentOrElse(p -> {
                        PlayerStats stats = plugin.getDatabase().loadPlayerStats(p.uuid(), plugin.getConfigManager().get().currentSeasonId());
                        sender.sendMessage(Component.text("=== Stats for " + p.lastName() + " ===", NamedTextColor.GOLD));
                        sender.sendMessage(Component.text("Playtime: " + formatDuration(stats.getPlaytimeSeconds()), NamedTextColor.YELLOW));
                        sender.sendMessage(Component.text("Blocks Mined: " + stats.getBlocksMined(), NamedTextColor.YELLOW));
                        sender.sendMessage(Component.text("Mobs Killed: " + stats.getMobsKilled(), NamedTextColor.YELLOW));
                    }, () -> sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)));
            }
            case "backup" -> {
                var backup = plugin.getDatabase().createBackup();
                if (backup != null) {
                    sender.sendMessage(Component.text("Backup created: " + backup.getName(), NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Backup failed.", NamedTextColor.RED));
                }
            }
            default -> sender.sendMessage(Component.text("Unknown admin subcommand.", NamedTextColor.RED));
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
            completions.addAll(List.of("stats", "top", "help", "reload"));
            if (sender.hasPermission("smpwrapped.admin")) {
                completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            completions.addAll(List.of("create", "seasons", "switch", "end", "publish", "unpublish", "stats", "backup"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            if (args[1].equalsIgnoreCase("switch") || args[1].equalsIgnoreCase("end")
                || args[1].equalsIgnoreCase("publish") || args[1].equalsIgnoreCase("unpublish")) {
                completions.addAll(plugin.getSeasonManager().getAllSeasons().stream()
                    .map(Season::id)
                    .toList());
            } else if (args[1].equalsIgnoreCase("stats")) {
                completions.addAll(plugin.getDatabase().getAllPlayers().stream()
                    .map(TrackedPlayer::lastName)
                    .toList());
            }
        }

        return completions;
    }
}
