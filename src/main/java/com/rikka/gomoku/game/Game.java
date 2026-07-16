package com.rikka.gomoku.game;

import com.rikka.gomoku.GomokuPlugin;
import com.rikka.gomoku.ai.GomokuAI;
import com.rikka.gomoku.arena.Arena;
import com.rikka.gomoku.arena.ArenaState;
import com.rikka.gomoku.arena.BoardRenderer;
import com.rikka.gomoku.config.ConfigManager;
import com.rikka.gomoku.config.LanguageManager;
import com.rikka.gomoku.spectator.SpectatorManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class Game {
    private final GomokuPlugin plugin;
    private final Arena arena;
    private final ConfigManager config;
    private final LanguageManager lang;
    private final SpectatorManager spectatorManager;
    private final BoardRenderer renderer;

    private final Board board;
    private GameState state = GameState.WAITING;

    private UUID player1;
    private UUID player2;
    private boolean isPvE;

    // Per-game color assignment (randomized, stable for the game)
    private int player1Color = Board.WHITE;
    private int player2Color = Board.BLACK;

    private int currentPlayer = Board.WHITE;
    private final List<Move> moveHistory = new ArrayList<>();
    private final List<UUID> queuedPlayers = new ArrayList<>();

    private BukkitTask turnTimer;
    private BukkitTask countdownTask;
    private BukkitTask gameDurationTask;
    private int turnSecondsLeft;
    private final GomokuAI ai = new GomokuAI();
    private boolean aiThinking = false;

    public Game(GomokuPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.config = plugin.getConfigManager();
        this.lang = plugin.getLanguageManager();
        this.spectatorManager = plugin.getSpectatorManager();
        this.renderer = new BoardRenderer(config.getSurfaceBlock(), config.getGridBlock());
        this.board = new Board(config.getBoardSize());
    }

    // ═══ Player Management ══════════════════════════════════════════

    public void addPlayer(Player player) {
        if (queuedPlayers.contains(player.getUniqueId())) return;
        queuedPlayers.add(player.getUniqueId());

        if (queuedPlayers.size() == 1) {
            player1 = player.getUniqueId();
            plugin.getGameManager().savePlayerData(player, player.getLocation());
            player.teleport(arena.getLobbySpawn());
            if (!isPvE) player.sendMessage(lang.format("joined-pvp", Map.of()));
        } else if (queuedPlayers.size() == 2) {
            player2 = player.getUniqueId();
            plugin.getGameManager().savePlayerData(player, player.getLocation());
            player.teleport(arena.getLobbySpawn());
            startCountdown();
        }
    }

    public void startPvE(Player player) {
        this.isPvE = true;
        player1 = player.getUniqueId();
        player2 = null;
        plugin.getGameManager().savePlayerData(player, player.getLocation());
        player.teleport(arena.getLobbySpawn());
        queuedPlayers.add(player.getUniqueId());
        startCountdown();
    }

    // ═══ Player Leave (handles ALL states) ══════════════════════════

    public void playerLeave(Player player) {
        UUID uuid = player.getUniqueId();
        cancelAllTasks();

        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            restorePlayerNow(uuid);
            UUID other = uuid.equals(player1) ? player2 : player1;
            if (other != null) restorePlayerNow(other);
            fullCleanup();
            return;
        }

        // PLAYING → forfeit
        if (state == GameState.PLAYING) {
            state = GameState.ENDING;
            UUID other = uuid.equals(player1) ? player2 : player1;
            String winnerName = isPvE ? "AI" : (other != null && Bukkit.getPlayer(other) != null
                ? Bukkit.getPlayer(other).getName() : "?");
            broadcastToPlayers(lang.format("win-by-forfeit", Map.of("player", winnerName)));

            restorePlayerNow(uuid);
            if (other != null && !uuid.equals(other)) restorePlayerNow(other);

            new BukkitRunnable() {
                @Override public void run() { fullCleanup(); }
            }.runTaskLater(plugin, 60L);
        }
    }

    // ═══ Countdown ══════════════════════════════════════════════════

    private void startCountdown() {
        state = GameState.COUNTDOWN;
        arena.setState(ArenaState.IN_USE);
        int[] sec = {config.getLobbyCountdown()};
        countdownTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.COUNTDOWN) { cancel(); return; }
                if (sec[0] <= 0) { cancel(); beginGame(); return; }
                if (sec[0] <= 10 || sec[0] % 10 == 0)
                    broadcastToPlayers(lang.format("countdown-start", Map.of("seconds", String.valueOf(sec[0]))));
                sec[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ═══ Game Start ═════════════════════════════════════════════════

    private void beginGame() {
        state = GameState.PLAYING;
        renderer.renderBoard(arena.getBoardOrigin(), board.getSize());

        // Randomly assign colors: player1/player2 get WHITE or BLACK; stable for the game.
        Random rng = new Random();
        if (isPvE) {
            player1Color = rng.nextBoolean() ? Board.WHITE : Board.BLACK;
            player2Color = (player1Color == Board.WHITE) ? Board.BLACK : Board.WHITE;
        } else {
            if (rng.nextBoolean()) {
                player1Color = Board.WHITE;
                player2Color = Board.BLACK;
            } else {
                player1Color = Board.BLACK;
                player2Color = Board.WHITE;
            }
        }
        // Black always moves first.
        currentPlayer = Board.BLACK;

        // White spawn = west side (player1Spawn), Black spawn = east side (player2Spawn)
        Location whiteSpawn = arena.getPlayer1Spawn();
        Location blackSpawn = arena.getPlayer2Spawn();

        Player p1 = Bukkit.getPlayer(player1);
        if (p1 != null && p1.isOnline()) {
            p1.teleport(player1Color == Board.WHITE ? whiteSpawn : blackSpawn);
            p1.setGameMode(GameMode.ADVENTURE);
            p1.setAllowFlight(true); p1.setFlying(true);
            p1.getInventory().clear();
            p1.sendMessage(lang.get(player1Color == Board.WHITE
                ? "color-assigned-white" : "color-assigned-black"));
        }

        if (!isPvE && player2 != null) {
            Player p2 = Bukkit.getPlayer(player2);
            if (p2 != null && p2.isOnline()) {
                p2.teleport(player2Color == Board.WHITE ? whiteSpawn : blackSpawn);
                p2.setGameMode(GameMode.ADVENTURE);
                p2.setAllowFlight(true); p2.setFlying(true);
                p2.getInventory().clear();
                p2.sendMessage(lang.get(player2Color == Board.WHITE
                    ? "color-assigned-white" : "color-assigned-black"));
            }
        }

        String p1Name = p1 != null ? p1.getName() : "?";
        String p2Name = isPvE ? "AI" : (player2 != null && Bukkit.getPlayer(player2) != null
            ? Bukkit.getPlayer(player2).getName() : "?");
        String msg = lang.format(isPvE ? "game-started-pve" : "game-started",
            Map.of("player", p1Name, "player1", p1Name, "player2", p2Name));
        broadcastToPlayers(msg);
        spectatorManager.broadcastToArenaSpectators(arena.getId(), msg);

        startGameDurationTimer();
        startTurn();
    }

    // ═══ Turn ═══════════════════════════════════════════════════════

    private void startTurn() {
        if (state != GameState.PLAYING) return;
        turnSecondsLeft = config.getTurnTimeout();
        if (turnTimer != null) turnTimer.cancel();

        Player cp = getCurrentPlayerId() != null ? Bukkit.getPlayer(getCurrentPlayerId()) : null;
        if (cp != null && cp.isOnline()) cp.sendMessage(lang.format("your-turn", Map.of()));

        if (isPvE && currentPlayer == player2Color && !aiThinking) { doAIMove(); return; }

        Player op = getOpponentId() != null ? Bukkit.getPlayer(getOpponentId()) : null;
        if (op != null && op.isOnline()) op.sendMessage(lang.format("opponent-turn", Map.of()));

        turnTimer = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                turnSecondsLeft--;
                if (turnSecondsLeft <= 0) { cancel(); handleTimeout(); return; }
                if (turnSecondsLeft <= 10) {
                    Player p = getCurrentPlayerId() != null ? Bukkit.getPlayer(getCurrentPlayerId()) : null;
                    if (p != null) p.sendMessage(lang.format("turn-timeout-warning",
                        Map.of("seconds", String.valueOf(turnSecondsLeft))));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void doAIMove() {
        aiThinking = true;
        Player p1 = Bukkit.getPlayer(player1);
        if (p1 != null) p1.sendMessage(lang.format("ai-thinking", Map.of()));
        final int aiColor = player2Color;
        new BukkitRunnable() {
            @Override public void run() {
                aiThinking = false;
                if (state != GameState.PLAYING) return;
                int[] move = ai.findBestMove(board, aiColor);
                board.place(move[0], move[1], aiColor);
                moveHistory.add(new Move(move[0], move[1], aiColor));
                renderer.placePiece(arena.getBoardOrigin(), move[0], move[1], aiColor);
                if (board.checkWin(move[0], move[1], aiColor)) { endGame(aiColor); return; }
                if (board.isFull()) { endGame(0); return; }
                currentPlayer = (aiColor == Board.WHITE) ? Board.BLACK : Board.WHITE;
                startTurn();
            }
        }.runTask(plugin);
    }

    public void placePiece(Player player, int row, int col) {
        if (state != GameState.PLAYING) return;
        if (aiThinking) { player.sendMessage(lang.format("opponent-turn", Map.of())); return; }

        int piece;
        if (player.getUniqueId().equals(player1)) piece = player1Color;
        else if (!isPvE && player.getUniqueId().equals(player2)) piece = player2Color;
        else { player.sendMessage(lang.format("not-your-turn", Map.of())); return; }

        if (piece != currentPlayer) { player.sendMessage(lang.format("not-your-turn", Map.of())); return; }
        if (!board.place(row, col, piece)) { player.sendMessage(lang.format("invalid-move", Map.of())); return; }

        moveHistory.add(new Move(row, col, piece));
        renderer.placePiece(arena.getBoardOrigin(), row, col, piece);

        if (board.checkWin(row, col, piece)) { endGame(piece); return; }
        if (board.isFull()) { endGame(0); return; }

        currentPlayer = (currentPlayer == Board.WHITE) ? Board.BLACK : Board.WHITE;
        startTurn();
    }

    private void handleTimeout() {
        int winner = (currentPlayer == Board.WHITE) ? Board.BLACK : Board.WHITE;
        Player loser = getCurrentPlayerId() != null ? Bukkit.getPlayer(getCurrentPlayerId()) : null;
        if (loser != null) loser.sendMessage(lang.format("turn-timeout", Map.of("player", loser.getName())));
        endGame(winner);
    }

    // ═══ Game End ═══════════════════════════════════════════════════

    private void endGame(int winner) {
        state = GameState.ENDING;
        cancelAllTasks();

        String winnerName = null;
        if (winner == player1Color) {
            Player p = Bukkit.getPlayer(player1);
            winnerName = (p != null) ? p.getName() : "White";
        } else if (winner == player2Color) {
            winnerName = isPvE ? "AI"
                : (player2 != null && Bukkit.getPlayer(player2) != null
                    ? Bukkit.getPlayer(player2).getName() : "Black");
        }

        String msg = winnerName != null
            ? lang.format("win", Map.of("player", winnerName))
            : lang.format("draw", Map.of());
        broadcastToPlayers(msg);
        spectatorManager.broadcastToArenaSpectators(arena.getId(), msg);

        new BukkitRunnable() {
            @Override public void run() {
                restorePlayerNow(player1);
                if (!isPvE && player2 != null) restorePlayerNow(player2);
                fullCleanup();
            }
        }.runTaskLater(plugin, 100L);
    }

    // ═══ Cleanup ════════════════════════════════════════════════════

    private void fullCleanup() {
        cancelAllTasks();

        Location origin = arena.getBoardOrigin();
        if (origin != null) renderer.clearPieces(origin, board.getSize());

        spectatorManager.clearArena(arena.getId());
        plugin.getGameManager().removeGame(arena.getId());

        board.clear();
        moveHistory.clear();
        queuedPlayers.clear();
        player1 = null;
        player2 = null;
        currentPlayer = Board.WHITE;
        player1Color = Board.WHITE;
        player2Color = Board.BLACK;
        isPvE = false;

        arena.setState(ArenaState.IDLE);
        arena.setCurrentGame(null);
        state = GameState.WAITING;
    }

    // ═══ Force End ══════════════════════════════════════════════════

    public void forceEnd() {
        cancelAllTasks();
        if (state == GameState.WAITING) {
            restorePlayerNow(player1); restorePlayerNow(player2);
            fullCleanup(); return;
        }
        state = GameState.ENDING;
        broadcastToPlayers(lang.format("win-by-forfeit", Map.of("player", "N/A")));
        restorePlayerNow(player1);
        if (!isPvE && player2 != null) restorePlayerNow(player2);
        new BukkitRunnable() { @Override public void run() { fullCleanup(); } }.runTaskLater(plugin, 40L);
    }

    public void forceEndWithWinner(UUID winnerId) {
        cancelAllTasks();
        if (state == GameState.WAITING) {
            restorePlayerNow(player1); restorePlayerNow(player2);
            fullCleanup(); return;
        }
        state = GameState.ENDING;
        Player winner = Bukkit.getPlayer(winnerId);
        broadcastToPlayers(lang.format("win-by-forfeit",
            Map.of("player", winner != null ? winner.getName() : "?")));
        restorePlayerNow(player1);
        if (!isPvE && player2 != null) restorePlayerNow(player2);
        new BukkitRunnable() { @Override public void run() { fullCleanup(); } }.runTaskLater(plugin, 40L);
    }

    // ═══ Helpers ════════════════════════════════════════════════════

    private void restorePlayerNow(UUID pid) {
        if (pid == null) return;
        Player p = Bukkit.getPlayer(pid);
        if (p != null && p.isOnline()) plugin.getGameManager().restorePlayerData(p);
    }

    private void cancelAllTasks() {
        if (turnTimer != null) { turnTimer.cancel(); turnTimer = null; }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (gameDurationTask != null) { gameDurationTask.cancel(); gameDurationTask = null; }
    }

    private void startGameDurationTimer() {
        gameDurationTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                broadcastToPlayers(lang.format("draw", Map.of()));
                endGame(0);
            }
        }.runTaskLater(plugin, config.getGameMaxDuration() * 20L);
    }

    private UUID getCurrentPlayerId() {
        if (currentPlayer == player1Color) return player1;
        return player2;
    }

    private UUID getOpponentId() {
        if (currentPlayer == player1Color) return player2;
        return player1;
    }

    private void broadcastToPlayers(String msg) {
        if (player1 != null) { Player p = Bukkit.getPlayer(player1); if (p != null && p.isOnline()) p.sendMessage(msg); }
        if (!isPvE && player2 != null) { Player p = Bukkit.getPlayer(player2); if (p != null && p.isOnline()) p.sendMessage(msg); }
    }

    public boolean hasPlayer(UUID pid) { return pid.equals(player1) || pid.equals(player2); }
    public boolean isPlaying(UUID pid) {
        return (state == GameState.PLAYING || state == GameState.COUNTDOWN) && hasPlayer(pid);
    }

    public GameState getState() { return state; }
    public Board getBoard() { return board; }
    public Arena getArena() { return arena; }
    public UUID getPlayer1() { return player1; }
    public UUID getPlayer2() { return player2; }
    public boolean isPvE() { return isPvE; }
    public int getCurrentPlayer() { return currentPlayer; }
    public int getSpectatorCount() { return spectatorManager.getSpectatorCount(arena.getId()); }
    public int getMaxSpectators() { return arena.getMaxSpectators(); }
    public List<Move> getMoveHistory() { return moveHistory; }
    public int getTurnSecondsLeft() { return turnSecondsLeft; }
}
