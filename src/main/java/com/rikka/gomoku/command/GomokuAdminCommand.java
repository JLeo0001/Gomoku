package com.rikka.gomoku.command;

import com.rikka.gomoku.GomokuPlugin;
import com.rikka.gomoku.arena.Arena;
import com.rikka.gomoku.arena.ArenaManager;
import com.rikka.gomoku.config.LanguageManager;
import com.rikka.gomoku.game.Game;
import com.rikka.gomoku.game.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Admin commands: /gomokuadmin <create|delete|set|status|end|reload|list>
 */
public class GomokuAdminCommand implements CommandExecutor, TabCompleter {
    private final GomokuPlugin plugin;
    private final ArenaManager arenaManager;
    private final GameManager gameManager;
    private final LanguageManager lang;

    public GomokuAdminCommand(GomokuPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.gameManager = plugin.getGameManager();
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("gomoku.admin")) {
            sender.sendMessage(lang.format("no-permission", Map.of()));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(sender, args);
            case "delete", "remove" -> handleDelete(sender, args);
            case "set" -> handleSet(sender, args);
            case "status", "info" -> handleStatus(sender, args);
            case "end", "stop" -> handleEnd(sender, args);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "maxspectators", "maxspec" -> handleMaxSpectators(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(lang.getPrefix() + " §cUsage: /gka create <arena_id>");
            return true;
        }

        String id = args[1];
        Player creator = (sender instanceof Player p) ? p : null;
        arenaManager.createArena(id, creator);
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(lang.getPrefix() + " §cUsage: /gka delete <arena_id>");
            return true;
        }

        Player admin = (sender instanceof Player p) ? p : null;
        arenaManager.deleteArena(args[1], admin);
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.format("player-only", Map.of()));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(lang.getPrefix() + " §cUsage: /gka set <arena_id> <board1|board2|lobby|spawn1|spawn2|spectator>");
            return true;
        }

        arenaManager.setPoint(args[1], args[2], player);
        return true;
    }

    private boolean handleStatus(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Arena arena = arenaManager.getArena(args[1]);
            if (arena == null) {
                sender.sendMessage(lang.format("arena-not-found", Map.of("arena", args[1])));
            } else {
                sender.sendMessage(arenaManager.getArenaStatus(arena));
            }
        } else {
            sender.sendMessage(lang.format("arena-list-header", Map.of()));
            for (Arena arena : arenaManager.getArenas()) {
                sender.sendMessage(arenaManager.getArenaStatus(arena));
            }
        }
        return true;
    }

    private boolean handleEnd(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("all")) {
                gameManager.endAllGames();
                sender.sendMessage(lang.format("all-games-ended", Map.of()));
            } else {
                Arena arena = arenaManager.getArena(args[1]);
                if (arena == null) {
                    sender.sendMessage(lang.format("arena-not-found", Map.of("arena", args[1])));
                    return true;
                }
                Game game = arena.getCurrentGame();
                if (game != null) {
                    game.forceEnd();
                    sender.sendMessage(lang.format("game-ended", Map.of("arena", args[1])));
                } else {
                    sender.sendMessage(lang.format("no-active-game", Map.of("arena", args[1])));
                }
            }
        } else {
            sender.sendMessage(lang.getPrefix() + " §cUsage: /gka end <arena_id|all>");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getLanguageManager().reload();
        sender.sendMessage(lang.format("config-reloaded", Map.of()));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Collection<Arena> arenas = arenaManager.getArenas();
        if (arenas.isEmpty()) {
            sender.sendMessage(lang.getPrefix() + " §7No arenas configured.");
            return true;
        }

        sender.sendMessage(lang.format("arena-list-header", Map.of()));
        for (Arena arena : arenas) {
            String stateStr = arena.getState().name();
            String mapEntry = lang.get("arena-list-entry")
                .replace("{name}", arena.getId())
                .replace("{state}", stateStr);
            sender.sendMessage(mapEntry);
        }
        return true;
    }

    private boolean handleMaxSpectators(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.getPrefix() + " §cUsage: /gka maxspec <arena_id> <number>");
            return true;
        }

        Arena arena = arenaManager.getArena(args[1]);
        if (arena == null) {
            sender.sendMessage(lang.format("arena-not-found", Map.of("arena", args[1])));
            return true;
        }

        try {
            int max = Integer.parseInt(args[2]);
            arena.setMaxSpectators(max);
            sender.sendMessage(lang.format("point-set", Map.of("arena", args[1], "point", "maxSpectators=" + max)));
        } catch (NumberFormatException e) {
            sender.sendMessage(lang.getPrefix() + " §cInvalid number.");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(lang.getPrefix() + " §6Admin Commands:");
        sender.sendMessage("  §e/gka create <id> §7- Create a new arena");
        sender.sendMessage("  §e/gka delete <id> §7- Delete an arena");
        sender.sendMessage("  §e/gka set <id> <point> §7- Set arena point (board1/board2/lobby/spawn1/spawn2/spectator)");
        sender.sendMessage("  §e/gka maxspec <id> <n> §7- Set max spectators for arena");
        sender.sendMessage("  §e/gka status [id] §7- Show arena status");
        sender.sendMessage("  §e/gka list §7- List all arenas");
        sender.sendMessage("  §e/gka end <id|all> §7- Force end game(s)");
        sender.sendMessage("  §e/gka reload §7- Reload configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = List.of("create", "delete", "set", "status", "end", "reload", "list", "maxspectators");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (List.of("delete", "remove", "set", "status", "info", "maxspectators", "maxspec").contains(sub)) {
                for (Arena a : arenaManager.getArenas()) {
                    if (a.getId().startsWith(args[1].toLowerCase())) completions.add(a.getId());
                }
            } else if (sub.equals("end") || sub.equals("stop")) {
                for (Arena a : arenaManager.getArenas()) {
                    if (a.getId().startsWith(args[1].toLowerCase())) completions.add(a.getId());
                }
                if ("all".startsWith(args[1].toLowerCase())) completions.add("all");
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                for (String point : List.of("board1", "board2", "lobby", "spawn1", "spawn2", "spectator")) {
                    if (point.startsWith(args[2].toLowerCase())) completions.add(point);
                }
            }
        }

        return completions;
    }
}
