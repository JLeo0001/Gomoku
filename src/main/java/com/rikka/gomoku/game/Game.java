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

/**
 * Represents a single Gomoku game instance.
 */
public class Game {
    private final GomokuPlugin plugin;
    private final Arena arena;
    private final ConfigManager config;
    private final LanguageManager lang;
    private final SpectatorManager spectatorManager;
    private final BoardRenderer renderer;

    private final Board board;
    private GameState state = GameState.WAITING;

    private UUID player1; // White (skeleton skull)
    private UUID player2; // Black (wither skeleton skull), null if PvE
    private boolean isPvE;

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

    // ─── Player Management ────────────────────────────────────────

    public void addPlayer(Player player) {
        if (queuedPlayers.contains(player.getUniqueId())) return;

        queuedPlayers.add(player.getUniqueId());

        if (queuedPlayers.size() == 1) {
            player1 = player.getUniqueId();
            // Save return location (where player was before joining)
            plugin.getGameManager().savePlayerData(player, player.getLocation());
            player.teleport(arena.getLobbySpawn());

            if (!isPvE) {
                player.sendMessage(lang.format("joined-pvp", Map.of()));
            }
        } else if (queuedPlayers.size() == 2) {
            player2 = player.getUniqueId();
            plugin.getGameManager().savePlayerData(player, player.getLocation());
            player.teleport(arena.getLobbySpawn());

            startCountdown();
        }
    }

    /**
     * Set this as PvE mode and start immediately with one player.
     */
    public void startPvE(Player player) {
        this.isPvE = true;
        player1 = player.getUniqueId();
        player2 = null; // AI
        plugin.getGameManager().savePlayerData(player, player.getLocation());
        player.teleport(arena.getLobbySpawn());
        queuedPlayers.add(player.getUniqueId());

        // Short countdown then start
        startCountdown();
    }

    // ─── Countdown ────────────────────────────────────────────────

    private void startCountdown() {
        state = GameState.COUNTDOWN;
        arena.setState(ArenaState.IN_USE);

        int[] seconds = {config.getLobbyCountdown()};

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.COUNTDOWN) {
                    cancel();
                    return;
                }

                if (seconds[0] <= 0) {
                    cancel();
                    beginGame();
                    return;
                }

                if (seconds[0] <= 10 || seconds[0] % 10 == 0) {
                    broadcastToPlayers(lang.format("countdown-start", Map.of("seconds", String.valueOf(seconds[0]))));
                }

                seconds[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ─── Game Start ───────────────────────────────────────────────

    private void beginGame() {
        state = GameState.PLAYING;

        // Render the board
        Location origin = arena.getBoardOrigin();
        renderer.renderBoard(origin, board.getSize());

        // Set up players
        Player p1 = Bukkit.getPlayer(player1);
        if (p1 != null && p1.isOnline()) {
            p1.teleport(arena.getPlayer1Spawn());
            p1.setGameMode(GameMode.ADVENTURE);
            p1.setAllowFlight(true);
            p1.setFlying(true);
            p1.getInventory().clear();
        }

        if (!isPvE && player2 != null) {
            Player p2 = Bukkit.getPlayer(player2);
            if (p2 != null && p2.isOnline()) {
                p2.teleport(arena.getPlayer2Spawn());
                p2.setGameMode(GameMode.ADVENTURE);
                p2.setAllowFlight(true);
                p2.setFlying(true);
                p2.getInventory().clear();
            }
        }

        // Randomly choose first player
        currentPlayer = new Random().nextBoolean() ? Board.WHITE : Board.BLACK;

        String p1Name = p1 != null ? p1.getName() : "?";
        String p2Name = isPvE ? "AI" : (Bukkit.getPlayer(player2) != null ? Bukkit.getPlayer(player2).getName() : "?");

        broadcastToPlayers(lang.format(isPvE ? "game-started-pve" : "game-started",
            Map.of("player", p1Name, "player1", p1Name, "player2", p2Name)));

        // Notify spectators
        spectatorManager.broadcastToArenaSpectators(arena.getId(),
            lang.format(isPvE ? "game-started-pve" : "game-started",
                Map.of("player", p1Name, "player1", p1Name, "player2", p2Name)));

        // Start game duration timer
        startGameDurationTimer();

        // Start first turn
        startTurn();
    }

    // ─── Turn Management ─────────────────────────────────────────

    private void startTurn() {
        if (state != GameState.PLAYING) return;

        turnSecondsLeft = config.getTurnTimeout();

        // Cancel previous timer
        if (turnTimer != null) turnTimer.cancel();

        // Notify current player
        UUID currentId = getCurrentPlayerId();
        Player currentPlayerObj = currentId != null ? Bukkit.getPlayer(currentId) : null;

        if (currentPlayerObj != null && currentPlayerObj.isOnline()) {
            currentPlayerObj.sendMessage(lang.format("your-turn", Map.of()));
        }

        // If AI's turn
        if (isPvE && currentPlayer == Board.BLACK && !aiThinking) {
            doAIMove();
            return;
        }

        // Notify opponent
        UUID opponentId = getOpponentId();
        Player opponentObj = opponentId != null ? Bukkit.getPlayer(opponentId) : null;
        if (opponentObj != null && opponentObj.isOnline()) {
            opponentObj.sendMessage(lang.format("opponent-turn", Map.of()));
        }

        // Start turn timer
        turnTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) {
                    cancel();
                    return;
                }
                turnSecondsLeft--;

                if (turnSecondsLeft <= 0) {
                    cancel();
                    handleTimeout();
                    return;
                }

                if (turnSecondsLeft <= 10) {
                    UUID cid = getCurrentPlayerId();
                    Player cp = cid != null ? Bukkit.getPlayer(cid) : null;
                    if (cp != null) {
                        cp.sendMessage(lang.format("turn-timeout-warning",
                            Map.of("seconds", String.valueOf(turnSecondsLeft))));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void doAIMove() {
        aiThinking = true;
        Player p1 = Bukkit.getPlayer(player1);
        if (p1 != null) {
            p1.sendMessage(lang.format("ai-thinking", Map.of()));
        }

        // Run AI on next tick to avoid blocking
        new BukkitRunnable() {
            @Override
            public void run() {
                int[] move = ai.findBestMove(board, Board.BLACK);
                aiThinking = false;

                if (state != GameState.PLAYING) return;

                board.place(move[0], move[1], Board.BLACK);
                moveHistory.add(new Move(move[0], move[1], Board.BLACK));

                Location origin = arena.getBoardOrigin();
                renderer.placePiece(origin, move[0], move[1], Board.BLACK);

                if (board.checkWin(move[0], move[1], Board.BLACK)) {
                    endGame(Board.BLACK);
                    return;
                }

                if (board.isFull()) {
                    endGame(0); // Draw
                    return;
                }

                // Switch turn back to player
                currentPlayer = Board.WHITE;
                startTurn();
            }
        }.runTask(plugin);
    }

    public void placePiece(Player player, int row, int col) {
        if (state != GameState.PLAYING) return;
        if (aiThinking) {
            player.sendMessage(lang.format("opponent-turn", Map.of()));
            return;
        }

        int playerPiece;
        if (player.getUniqueId().equals(player1)) {
            playerPiece = Board.WHITE;
        } else if (!isPvE && player.getUniqueId().equals(player2)) {
            playerPiece = Board.BLACK;
        } else {
            player.sendMessage(lang.format("not-your-turn", Map.of()));
            return;
        }

        if (playerPiece != currentPlayer) {
            player.sendMessage(lang.format("not-your-turn", Map.of()));
            return;
        }

        if (!board.place(row, col, playerPiece)) {
            player.sendMessage(lang.format("invalid-move", Map.of()));
            return;
        }

        moveHistory.add(new Move(row, col, playerPiece));

        Location origin = arena.getBoardOrigin();
        renderer.placePiece(origin, row, col, playerPiece);

        // Check win
        if (board.checkWin(row, col, playerPiece)) {
            endGame(playerPiece);
            return;
        }

        // Check draw
        if (board.isFull()) {
            endGame(0);
            return;
        }

        // Switch turn
        currentPlayer = (currentPlayer == Board.WHITE) ? Board.BLACK : Board.WHITE;
        startTurn();
    }

    private void handleTimeout() {
        int winner = (currentPlayer == Board.WHITE) ? Board.BLACK : Board.WHITE;
        UUID loserId = getCurrentPlayerId();
        Player loser = loserId != null ? Bukkit.getPlayer(loserId) : null;
        if (loser != null) {
            loser.sendMessage(lang.format("turn-timeout", Map.of("player", loser.getName())));
        }
        endGame(winner);
    }

    // ─── Game End ─────────────────────────────────────────────────

    private void endGame(int winner) {
        state = GameState.ENDING;

        // Cancel timers
        if (turnTimer != null) turnTimer.cancel();
        if (gameDurationTask != null) gameDurationTask.cancel();

        String winnerName;
        if (winner == Board.WHITE) {
            Player p = Bukkit.getPlayer(player1);
            winnerName = (p != null) ? p.getName() : "White";
        } else if (winner == Board.BLACK) {
            if (isPvE) {
                winnerName = "AI";
            } else {
                Player p = Bukkit.getPlayer(player2);
                winnerName = (p != null) ? p.getName() : "Black";
            }
        } else {
            winnerName = null; // Draw
        }

        // Announce
        if (winnerName != null) {
            String msg = lang.format("win", Map.of("player", winnerName));
            broadcastToPlayers(msg);
            spectatorManager.broadcastToArenaSpectators(arena.getId(), msg);
        } else {
            String msg = lang.format("draw", Map.of());
            broadcastToPlayers(msg);
            spectatorManager.broadcastToArenaSpectators(arena.getId(), msg);
        }

        // Schedule cleanup
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanup();
            }
        }.runTaskLater(plugin, 100L); // 5 second delay
    }

    // ─── Cleanup ──────────────────────────────────────────────────

    public void cleanup() {
        state = GameState.CLEANING;

        // Cancel all tasks
        if (turnTimer != null) turnTimer.cancel();
        if (countdownTask != null) countdownTask.cancel();
        if (gameDurationTask != null) gameDurationTask.cancel();

        // Clear board
        Location origin = arena.getBoardOrigin();
        if (origin != null) {
            renderer.clearPieces(origin, board.getSize());
        }

        // Restore players
        restorePlayer(player1);
        if (!isPvE && player2 != null) {
            restorePlayer(player2);
        }

        // Clear spectators
        spectatorManager.clearArena(arena.getId());

        // Clean up game manager state
        plugin.getGameManager().removeGame(arena.getId());

        // Reset game state
        board.clear();
        moveHistory.clear();
        queuedPlayers.clear();
        player1 = null;
        player2 = null;
        currentPlayer = Board.WHITE;
        isPvE = false;

        arena.setState(ArenaState.IDLE);
        arena.setCurrentGame(null);
        state = GameState.WAITING;
    }

    public void forceEnd() {
        if (state == GameState.PLAYING || state == GameState.COUNTDOWN) {
            state = GameState.ENDING;
            broadcastToPlayers(lang.format("win-by-forfeit", Map.of("player", "N/A")));
            cleanup();
        }
    }

    public void forceEndWithWinner(UUID winnerId) {
        if (state == GameState.PLAYING || state == GameState.COUNTDOWN) {
            state = GameState.ENDING;
            Player winner = Bukkit.getPlayer(winnerId);
            String name = winner != null ? winner.getName() : "?";
            broadcastToPlayers(lang.format("win-by-forfeit", Map.of("player", name)));
            cleanup();
        }
    }

    // ─── Player Restoration ───────────────────────────────────────

    private void restorePlayer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        plugin.getGameManager().restorePlayerData(player);
    }

    // ─── Game Duration Timer ──────────────────────────────────────

    private void startGameDurationTimer() {
        gameDurationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PLAYING) {
                    cancel();
                    return;
                }
                // Game time limit reached - draw
                broadcastToPlayers(lang.format("draw", Map.of()));
                endGame(0);
            }
        }.runTaskLater(plugin, config.getGameMaxDuration() * 20L);
    }

    // ─── Utility ──────────────────────────────────────────────────

    public UUID getCurrentPlayerId() {
        if (currentPlayer == Board.WHITE) return player1;
        return player2;
    }

    public UUID getOpponentId() {
        if (currentPlayer == Board.WHITE) return player2;
        return player1;
    }

    public void broadcastToPlayers(String message) {
        if (player1 != null) {
            Player p = Bukkit.getPlayer(player1);
            if (p != null) p.sendMessage(message);
        }
        if (!isPvE && player2 != null) {
            Player p = Bukkit.getPlayer(player2);
            if (p != null) p.sendMessage(message);
        }
    }

    public boolean hasPlayer(UUID playerId) {
        return playerId.equals(player1) || playerId.equals(player2);
    }

    public boolean isPlaying(UUID playerId) {
        return (state == GameState.PLAYING || state == GameState.COUNTDOWN)
            && hasPlayer(playerId);
    }

    // ─── Getters ──────────────────────────────────────────────────

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
