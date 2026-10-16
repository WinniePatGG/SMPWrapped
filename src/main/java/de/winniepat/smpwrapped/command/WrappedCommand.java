package de.winniepat.smpwrapped.command;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.model.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;


public class WrappedCommand implements CommandExecutor, TabCompleter {

    private final SMPWrapped plugin;

    public WrappedCommand(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            sender.sendMessage(Component.text("/wrapped admin create <name> - Create a season", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin end <season> - End a season", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin publish <season> - Publish a season", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/wrapped admin backup - Create database backup", NamedTextColor.YELLOW));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("smpwrapped.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return;
        }
        plugin.getConfigManager().reload();
        plugin.getStatTracker().flushAll();
        sender.sendMessage(Component.text("Configuration reloaded and stats flushed.", NamedTextColor.GREEN));
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
                plugin.getSeasonManager().createSeason(id, name);
                sender.sendMessage(Component.text("Created season: " + name + " (" + id + ")", NamedTextColor.GREEN));
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
            completions.addAll(List.of("create", "end", "publish", "stats", "backup"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            if (args[1].equalsIgnoreCase("end") || args[1].equalsIgnoreCase("publish")) {
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
