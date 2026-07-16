package com.rikka.gomoku.command;

import com.rikka.gomoku.GomokuPlugin;
import com.rikka.gomoku.arena.Arena;
import com.rikka.gomoku.arena.ArenaState;
import com.rikka.gomoku.config.LanguageManager;
import com.rikka.gomoku.game.Game;
import com.rikka.gomoku.game.GameManager;
import com.rikka.gomoku.spectator.SpectatorManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Player commands: /gomoku <join|leave|spectate|version>
 */
public class GomokuCommand implements CommandExecutor, TabCompleter {
    private final GomokuPlugin plugin;
    private final GameManager gameManager;
    private final LanguageManager lang;
    private final SpectatorManager spectatorManager;

    public GomokuCommand(GomokuPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.lang = plugin.getLanguageManager();
        this.spectatorManager = plugin.getSpectatorManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.format("player-only", Map.of()));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "join" -> handleJoin(player, args);
            case "leave", "quit" -> handleLeave(player);
            case "spectate", "spec", "watch" -> handleSpectate(player, args);
            case "version", "ver" -> handleVersion(player);
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }

    private boolean handleJoin(Player player, String[] args) {
        if (!player.hasPermission("gomoku.player")) {
            player.sendMessage(lang.format("no-permission", Map.of()));
            return true;
        }

        if (gameManager.isInGame(player.getUniqueId())) {
            player.sendMessage(lang.format("already-in-game", Map.of()));
            return true;
        }

        // Determine PvP or PvE
        String mode = args.length >= 2 ? args[1].toLowerCase() : "pvp";
        String arenaId = args.length >= 3 ? args[2] : null;

        Arena arena;
        if (arenaId != null) {
            arena = plugin.getArenaManager().getArena(arenaId);
            if (arena == null) {
                player.sendMessage(lang.format("arena-not-found", Map.of("arena", arenaId)));
                return true;
            }
            if (arena.getState() != ArenaState.IDLE) {
                player.sendMessage(lang.format("arena-in-use", Map.of("arena", arenaId)));
                return true;
            }
        } else {
            arena = plugin.getArenaManager().getAvailableArena();
        }

        if (arena == null) {
            player.sendMessage(lang.format("no-arena-available", Map.of()));
            return true;
        }

        if (mode.equals("pve") || mode.equals("ai")) {
            // PvE: start immediately with AI
            if (arena.getCurrentGame() == null) {
                Game game = gameManager.createGame(arena);
                game.startPvE(player);
                gameManager.setPlayerArena(player.getUniqueId(), arena.getId());
                player.sendMessage(lang.format("joined-pve", Map.of()));
            } else {
                player.sendMessage(lang.format("arena-in-use", Map.of("arena", arena.getId())));
            }
        } else {
            // PvP: join queue
            Game game = arena.getCurrentGame();
            if (game == null) {
                game = gameManager.createGame(arena);
            }

            if (game.getState() == null || game.getState().name().equals("WAITING")) {
                game.addPlayer(player);
                gameManager.setPlayerArena(player.getUniqueId(), arena.getId());
            } else {
                player.sendMessage(lang.format("arena-in-use", Map.of("arena", arena.getId())));
            }
        }

        return true;
    }

    private boolean handleLeave(Player player) {
        Game game = gameManager.getPlayerGame(player.getUniqueId());
        if (game != null) {
            game.forceEndWithWinner(
                game.getPlayer1() != null && game.getPlayer1().equals(player.getUniqueId())
                    ? game.getPlayer2() : game.getPlayer1()
            );
            player.sendMessage(lang.format("left-game", Map.of()));
        } else if (spectatorManager.isSpectating(player)) {
            spectatorManager.removeSpectator(player);
        } else {
            player.sendMessage(lang.format("not-in-game", Map.of()));
        }
        return true;
    }

    private boolean handleSpectate(Player player, String[] args) {
        if (!player.hasPermission("gomoku.spectate")) {
            player.sendMessage(lang.format("no-permission", Map.of()));
            return true;
        }

        if (gameManager.isInGame(player.getUniqueId())) {
            player.sendMessage(lang.format("already-in-game", Map.of()));
            return true;
        }

        if (spectatorManager.isSpectating(player)) {
            spectatorManager.removeSpectator(player);
            return true;
        }

        String arenaId = args.length >= 2 ? args[1] : null;
        Arena arena;
        if (arenaId != null) {
            arena = plugin.getArenaManager().getArena(arenaId);
        } else {
            // Find first active game
            arena = plugin.getArenaManager().getArenas().stream()
                .filter(a -> a.getCurrentGame() != null)
                .findFirst().orElse(null);
        }

        if (arena == null || arena.getCurrentGame() == null) {
            player.sendMessage(lang.format("no-active-game", Map.of("arena", arenaId != null ? arenaId : "?")));
            return true;
        }

        Game game = arena.getCurrentGame();
        spectatorManager.addSpectator(player, arena);

        player.sendMessage(lang.format("spectate-join", Map.of(
            "player1", game.getPlayer1() != null ?
                (org.bukkit.Bukkit.getPlayer(game.getPlayer1()) != null ?
                    org.bukkit.Bukkit.getPlayer(game.getPlayer1()).getName() : "?") : "?",
            "player2", game.isPvE() ? "AI" :
                (game.getPlayer2() != null ?
                    (org.bukkit.Bukkit.getPlayer(game.getPlayer2()) != null ?
                        org.bukkit.Bukkit.getPlayer(game.getPlayer2()).getName() : "?") : "?")
        )));

        return true;
    }

    private boolean handleVersion(Player player) {
        player.sendMessage(lang.format("version", Map.of("version", plugin.getDescription().getVersion())));
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(lang.getPrefix() + " §6Commands:");
        player.sendMessage("  §e/gomoku join [pvp|pve] [arena] §7- Join a game");
        player.sendMessage("  §e/gomoku leave §7- Leave current game");
        player.sendMessage("  §e/gomoku spectate [arena] §7- Spectate a game");
        player.sendMessage("  §e/gomoku version §7- Show plugin version");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("join", "leave", "spectate", "version"));
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("join")) {
                for (String s : List.of("pvp", "pve")) {
                    if (s.startsWith(args[1].toLowerCase())) completions.add(s);
                }
            } else if (args[0].equalsIgnoreCase("spectate") || args[0].equalsIgnoreCase("spec")) {
                for (Arena a : plugin.getArenaManager().getArenas()) {
                    if (a.getId().startsWith(args[1].toLowerCase())) completions.add(a.getId());
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("join")) {
                for (Arena a : plugin.getArenaManager().getArenas()) {
                    if (a.getId().startsWith(args[2].toLowerCase())) completions.add(a.getId());
                }
            }
        }

        return completions;
    }
}
