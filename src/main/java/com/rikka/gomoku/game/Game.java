package com.rikka.gomoku.game;

import com.rikka.gomoku.GomokuPlugin;
import com.rikka.gomoku.ai.GomokuAI;
import com.rikka.gomoku.arena.Arena;
import com.rikka.gomoku.arena.ArenaState;
import com.rikka.gomoku.arena.BoardRenderer;
import com.rikka.gomoku.config.ConfigManager;
import com.rikka.gomoku.config.LanguageManager;
import com.rikka.gomoku.sound.SoundManager;
import com.rikka.gomoku.spectator.SpectatorManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Core game state machine.
 *
 * Design:
 *  - whitePlayerId / blackPlayerId: who plays each color.
 *    null means AI (PvE); non-null means a human UUID.
 *  - currentPlayerColor: WHITE or BLACK.  Always valid.
 *  - advanceTurn(): the single place that flips colour and starts the next turn.
 *  - AI computation runs ASYNC; only the result-apply step touches the main thread.
 */
public class Game {
    private final GomokuPlugin plugin;
    private final Arena arena;
    private final ConfigManager config;
    private final LanguageManager lang;
    private final SpectatorManager spectatorManager;
    private final SoundManager sounds;
    private final BoardRenderer renderer;

    private final Board board;
    private GameState state = GameState.WAITING;

    // ── Player slots (direct colour assignment) ───────────────────
    private UUID whitePlayerId;   // null = AI
    private UUID blackPlayerId;   // null = AI

    private boolean isPvE;

    // ── Turn state ────────────────────────────────────────────────
    private int currentPlayerColor = Board.BLACK; // black always moves first
    private boolean aiThinking;
    private final List<Move> moveHistory = new ArrayList<>();
    private final List<UUID> queuedPlayers = new ArrayList<>();

    // ── Timers ────────────────────────────────────────────────────
    private BukkitTask turnTimer;
    private BukkitTask countdownTask;
    private BukkitTask gameDurationTask;
    private int turnSecondsLeft;

    // ── AI ────────────────────────────────────────────────────────
    private final GomokuAI ai = new GomokuAI();

    public Game(GomokuPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.config = plugin.getConfigManager();
        this.lang = plugin.getLanguageManager();
        this.spectatorManager = plugin.getSpectatorManager();
        this.sounds = plugin.getSoundManager();
        this.renderer = new BoardRenderer(config.getSurfaceBlock(), config.getGridBlock());
        this.board = new Board(config.getBoardSize());
    }

    // ═══════════════════════════════════════════════════════════════
    // Player Management
    // ═══════════════════════════════════════════════════════════════

    /** PvP: add a player to the queue. */
    public void addPlayer(Player player) {
        if (queuedPlayers.contains(player.getUniqueId())) return;
        queuedPlayers.add(player.getUniqueId());
        plugin.getGameManager().savePlayerData(player, player.getLocation());
        player.teleport(arena.getLobbySpawn());

        if (queuedPlayers.size() == 1) {
            whitePlayerId = player.getUniqueId();
            player.sendMessage(lang.format("joined-pvp", Map.of()));
        } else if (queuedPlayers.size() == 2) {
            blackPlayerId = player.getUniqueId();
            // Randomize colour assignment
            if (new Random().nextBoolean()) {
                UUID tmp = whitePlayerId;
                whitePlayerId = blackPlayerId;
                blackPlayerId = tmp;
            }
            startCountdown();
        }
    }

    /** PvE: start immediately with AI. */
    public void startPvE(Player player) {
        this.isPvE = true;
        // Randomize which colour the human gets
        if (new Random().nextBoolean()) {
            whitePlayerId = player.getUniqueId();
            blackPlayerId = null; // AI
        } else {
            whitePlayerId = null; // AI
            blackPlayerId = player.getUniqueId();
        }
        plugin.getGameManager().savePlayerData(player, player.getLocation());
        player.teleport(arena.getLobbySpawn());
        queuedPlayers.add(player.getUniqueId());
        startCountdown();
    }

    // ═══════════════════════════════════════════════════════════════
    // Player Leave (handles ALL states)
    // ═══════════════════════════════════════════════════════════════

    public void playerLeave(Player player) {
        UUID uuid = player.getUniqueId();
        cancelAllTimers();

        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            restorePlayerNow(uuid);
            UUID other = uuid.equals(whitePlayerId) ? blackPlayerId : whitePlayerId;
            if (other != null) restorePlayerNow(other);
            fullCleanup();
            return;
        }

        // PLAYING → forfeit
        if (state == GameState.PLAYING) {
            state = GameState.ENDING;
            UUID other = uuid.equals(whitePlayerId) ? blackPlayerId : whitePlayerId;
            String winnerName;
            if (other == null) {
                winnerName = "AI";
            } else {
                Player wp = Bukkit.getPlayer(other);
                winnerName = (wp != null) ? wp.getName() : "?";
            }
            broadcastToPlayers(lang.format("win-by-forfeit", Map.of("player", winnerName)));

            restorePlayerNow(uuid);
            if (other != null && !uuid.equals(other)) restorePlayerNow(other);

            new BukkitRunnable() {
                @Override public void run() { fullCleanup(); }
            }.runTaskLater(plugin, 60L);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Countdown
    // ═══════════════════════════════════════════════════════════════

    private void startCountdown() {
        state = GameState.COUNTDOWN;
        arena.setState(ArenaState.IN_USE);
        int[] sec = {config.getLobbyCountdown()};
        countdownTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.COUNTDOWN) { cancel(); return; }
                if (sec[0] <= 0) { cancel(); beginGame(); return; }
                if (sec[0] <= 10 || sec[0] % 10 == 0)
                    broadcastToPlayers(lang.format("countdown-start",
                        Map.of("seconds", String.valueOf(sec[0]))));
                // Play tick sound to both queued players
                sounds.playCountdownTick(whitePlayerId);
                sounds.playCountdownTick(blackPlayerId);
                sec[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ═══════════════════════════════════════════════════════════════
    // Game Start
    // ═══════════════════════════════════════════════════════════════

    private void beginGame() {
        state = GameState.PLAYING;

        // Fully reset board (in-memory + world)
        board.clear();
        Location origin = arena.getBoardOrigin();
        if (origin != null) {
            renderer.clearPieces(origin, board.getSize());
            renderer.renderBoard(origin, board.getSize());
        }

        // Black always moves first
        currentPlayerColor = Board.BLACK;

        // Spawn points
        Location whiteSpawn = arena.getPlayer1Spawn();
        Location blackSpawn = arena.getPlayer2Spawn();

        // Set up human player(s)
        setupPlayer(whitePlayerId, Board.WHITE, whiteSpawn);
        setupPlayer(blackPlayerId, Board.BLACK, blackSpawn);

        // Announce
        String p1Name = playerName(whitePlayerId);
        String p2Name = playerName(blackPlayerId);
        String msg = lang.format(isPvE ? "game-started-pve" : "game-started",
            Map.of("player", p1Name, "player1", p1Name, "player2", p2Name));
        broadcastToPlayers(msg);
        spectatorManager.broadcastToArenaSpectators(arena.getId(), msg);

        startGameDurationTimer();
        startTurn();
    }

    private void setupPlayer(UUID pid, int color, Location spawn) {
        if (pid == null) return; // AI
        Player p = Bukkit.getPlayer(pid);
        if (p == null || !p.isOnline()) return;
        p.teleport(spawn);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.getInventory().clear();
        p.sendMessage(lang.get(color == Board.WHITE
            ? "color-assigned-white" : "color-assigned-black"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Turn State Machine
    // ═══════════════════════════════════════════════════════════════

    /** Entry point for every turn. */
    private void startTurn() {
        if (state != GameState.PLAYING) return;
        cancelTurnTimer();
        turnSecondsLeft = config.getTurnTimeout();

        if (isCurrentPlayerAI()) {
            doAIMove();
        } else {
            // Notify human player
            Player cp = getCurrentPlayerOrNull();
            if (cp != null && cp.isOnline()) {
                cp.sendMessage(lang.format("your-turn", Map.of()));
            }
            // Notify opponent
            UUID oppId = getOpponentPlayerId();
            if (oppId != null) {
                Player op = Bukkit.getPlayer(oppId);
                if (op != null && op.isOnline()) {
                    op.sendMessage(lang.format("opponent-turn", Map.of()));
                }
            }
            startTurnTimer();
        }
    }

    /** Advance to the next player's turn.  Single point of turn transition. */
    private void advanceTurn() {
        currentPlayerColor = (currentPlayerColor == Board.WHITE) ? Board.BLACK : Board.WHITE;
        startTurn();
    }

    // ═══════════════════════════════════════════════════════════════
    // Human Move (called from listener)
    // ═══════════════════════════════════════════════════════════════

    public void placePiece(Player player, int row, int col) {
        if (state != GameState.PLAYING) return;
        if (aiThinking) {
            player.sendMessage(lang.format("opponent-turn", Map.of()));
            return;
        }

        UUID pid = player.getUniqueId();
        int piece;
        if (pid.equals(whitePlayerId)) {
            piece = Board.WHITE;
        } else if (pid.equals(blackPlayerId)) {
            piece = Board.BLACK;
        } else {
            player.sendMessage(lang.format("not-your-turn", Map.of()));
            return;
        }

        if (piece != currentPlayerColor) {
            player.sendMessage(lang.format("not-your-turn", Map.of()));
            return;
        }

        if (!board.place(row, col, piece)) {
            player.sendMessage(lang.format("invalid-move", Map.of()));
            return;
        }

        moveHistory.add(new Move(row, col, piece));
        renderer.placePiece(arena.getBoardOrigin(), row, col, piece);

        // Play placement sound
        sounds.playPiecePlace(whitePlayerId, blackPlayerId);

        if (checkGameEnd(row, col, piece)) return;
        advanceTurn();
    }

    // ═══════════════════════════════════════════════════════════════
    // AI Move (async: compute off-thread, apply on-thread)
    // ═══════════════════════════════════════════════════════════════

    private void doAIMove() {
        aiThinking = true;
        final int aiColor = currentPlayerColor;

        // Copy board on main thread (safe snapshot)
        final Board searchBoard = board.copy();

        // Notify human that AI is thinking
        UUID humanId = (aiColor == Board.WHITE) ? blackPlayerId : whitePlayerId;
        if (humanId != null) {
            Player human = Bukkit.getPlayer(humanId);
            if (human != null && human.isOnline()) {
                human.sendMessage(lang.format("ai-thinking", Map.of()));
            }
        }

        // Compute off the main thread so we never freeze the server
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final int[] move = ai.findBestMove(searchBoard, aiColor);

            // Apply result back on the main thread
            new BukkitRunnable() {
                @Override public void run() {
                    aiThinking = false;
                    if (state != GameState.PLAYING) return;

                    board.place(move[0], move[1], aiColor);
                    moveHistory.add(new Move(move[0], move[1], aiColor));
                    renderer.placePiece(arena.getBoardOrigin(), move[0], move[1], aiColor);

                    // Play placement sound
                    sounds.playPiecePlace(whitePlayerId, blackPlayerId);

                    if (checkGameEnd(move[0], move[1], aiColor)) return;
                    advanceTurn();
                }
            }.runTask(plugin);
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // Game End
    // ═══════════════════════════════════════════════════════════════

    private boolean checkGameEnd(int row, int col, int piece) {
        if (board.checkWin(row, col, piece)) {
            endGame(piece);
            return true;
        }
        if (board.isFull()) {
            endGame(0);
            return true;
        }
        return false;
    }

    private void endGame(int winner) {
        state = GameState.ENDING;
        cancelAllTimers();

        String winnerName = null;
        UUID winnerId = null;
        UUID loserId = null;

        if (winner == Board.WHITE) {
            winnerName = playerName(whitePlayerId);
            winnerId = whitePlayerId;
            loserId = blackPlayerId;
        } else if (winner == Board.BLACK) {
            winnerName = playerName(blackPlayerId);
            winnerId = blackPlayerId;
            loserId = whitePlayerId;
        }

        String msg = winnerName != null
            ? lang.format("win", Map.of("player", winnerName))
            : lang.format("draw", Map.of());
        broadcastToPlayers(msg);
        spectatorManager.broadcastToArenaSpectators(arena.getId(), msg);

        // Play win/lose/draw sounds
        if (winnerId != null) {
            sounds.playWin(winnerId);
        }
        if (loserId != null) {
            sounds.playLose(loserId);
        }
        if (winner == 0) { // draw
            sounds.playDraw(whitePlayerId, blackPlayerId);
        }

        // Notify loser
        if (loserId != null) {
            Player loser = Bukkit.getPlayer(loserId);
            if (loser != null && loser.isOnline()) {
                loser.sendMessage(lang.format("lose", Map.of()));
            }
        }

        new BukkitRunnable() {
            @Override public void run() {
                restorePlayerNow(whitePlayerId);
                restorePlayerNow(blackPlayerId);
                fullCleanup();
            }
        }.runTaskLater(plugin, 100L);
    }

    // ═══════════════════════════════════════════════════════════════
    // Timeout
    // ═══════════════════════════════════════════════════════════════

    private void handleTimeout() {
        int winner = (currentPlayerColor == Board.WHITE) ? Board.BLACK : Board.WHITE;
        Player loser = getCurrentPlayerOrNull();
        if (loser != null && loser.isOnline()) {
            loser.sendMessage(lang.format("turn-timeout", Map.of("player", loser.getName())));
        }
        endGame(winner);
    }

    // ═══════════════════════════════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════════════════════════════

    private void fullCleanup() {
        cancelAllTimers();

        Location origin = arena.getBoardOrigin();
        if (origin != null) renderer.clearPieces(origin, board.getSize());

        spectatorManager.clearArena(arena.getId());
        plugin.getGameManager().removeGame(arena.getId());

        board.clear();
        moveHistory.clear();
        queuedPlayers.clear();
        whitePlayerId = null;
        blackPlayerId = null;
        currentPlayerColor = Board.BLACK;
        isPvE = false;
        aiThinking = false;

        arena.setState(ArenaState.IDLE);
        arena.setCurrentGame(null);
        state = GameState.WAITING;
    }

    // ═══════════════════════════════════════════════════════════════
    // Force End
    // ═══════════════════════════════════════════════════════════════

    public void forceEnd() {
        cancelAllTimers();
        if (state == GameState.WAITING) {
            restorePlayerNow(whitePlayerId);
            restorePlayerNow(blackPlayerId);
            fullCleanup();
            return;
        }
        state = GameState.ENDING;
        broadcastToPlayers(lang.format("win-by-forfeit", Map.of("player", "N/A")));
        restorePlayerNow(whitePlayerId);
        restorePlayerNow(blackPlayerId);
        new BukkitRunnable() {
            @Override public void run() { fullCleanup(); }
        }.runTaskLater(plugin, 40L);
    }

    public void forceEndWithWinner(UUID winnerId) {
        cancelAllTimers();
        if (state == GameState.WAITING) {
            restorePlayerNow(whitePlayerId);
            restorePlayerNow(blackPlayerId);
            fullCleanup();
            return;
        }
        state = GameState.ENDING;
        String winnerName;
        if (winnerId == null) {
            winnerName = isPvE ? "AI" : "?";
        } else {
            Player winner = Bukkit.getPlayer(winnerId);
            winnerName = winner != null ? winner.getName() : "?";
        }
        broadcastToPlayers(lang.format("win-by-forfeit",
            Map.of("player", winnerName)));
        restorePlayerNow(whitePlayerId);
        restorePlayerNow(blackPlayerId);
        new BukkitRunnable() {
            @Override public void run() { fullCleanup(); }
        }.runTaskLater(plugin, 40L);
    }

    // ═══════════════════════════════════════════════════════════════
    // Timer helpers
    // ═══════════════════════════════════════════════════════════════

    private void startTurnTimer() {
        cancelTurnTimer();
        turnTimer = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                turnSecondsLeft--;
                if (turnSecondsLeft <= 0) { cancel(); handleTimeout(); return; }
                if (turnSecondsLeft <= 10) {
                    Player p = getCurrentPlayerOrNull();
                    if (p != null && p.isOnline()) {
                        p.sendMessage(lang.format("turn-timeout-warning",
                            Map.of("seconds", String.valueOf(turnSecondsLeft))));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
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

    private void cancelTurnTimer() {
        if (turnTimer != null) { turnTimer.cancel(); turnTimer = null; }
    }

    private void cancelAllTimers() {
        cancelTurnTimer();
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (gameDurationTask != null) { gameDurationTask.cancel(); gameDurationTask = null; }
    }

    // ═══════════════════════════════════════════════════════════════
    // Player identity helpers
    // ═══════════════════════════════════════════════════════════════

    private boolean isCurrentPlayerAI() {
        return getCurrentPlayerId() == null;
    }

    private UUID getCurrentPlayerId() {
        return (currentPlayerColor == Board.WHITE) ? whitePlayerId : blackPlayerId;
    }

    private Player getCurrentPlayerOrNull() {
        UUID id = getCurrentPlayerId();
        return id != null ? Bukkit.getPlayer(id) : null;
    }

    private UUID getOpponentPlayerId() {
        return (currentPlayerColor == Board.WHITE) ? blackPlayerId : whitePlayerId;
    }

    private String playerName(UUID pid) {
        if (pid == null) return isPvE ? "AI" : "?";
        Player p = Bukkit.getPlayer(pid);
        return (p != null) ? p.getName() : "?";
    }

    private void restorePlayerNow(UUID pid) {
        if (pid == null) return;
        Player p = Bukkit.getPlayer(pid);
        if (p != null && p.isOnline()) plugin.getGameManager().restorePlayerData(p);
    }

    private void broadcastToPlayers(String msg) {
        if (whitePlayerId != null) {
            Player p = Bukkit.getPlayer(whitePlayerId);
            if (p != null && p.isOnline()) p.sendMessage(msg);
        }
        if (blackPlayerId != null) {
            Player p = Bukkit.getPlayer(blackPlayerId);
            if (p != null && p.isOnline()) p.sendMessage(msg);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Public accessors
    // ═══════════════════════════════════════════════════════════════

    public boolean hasPlayer(UUID pid) {
        return pid.equals(whitePlayerId) || pid.equals(blackPlayerId);
    }

    public boolean isPlaying(UUID pid) {
        return (state == GameState.PLAYING || state == GameState.COUNTDOWN) && hasPlayer(pid);
    }

    public GameState getState() { return state; }
    public Board getBoard() { return board; }
    public Arena getArena() { return arena; }

    /** @return the first human player (always non-null). */
    public UUID getPlayer1() {
        return isPvE ? (whitePlayerId != null ? whitePlayerId : blackPlayerId) : whitePlayerId;
    }

    /** @return the second player, or null for PvE. */
    public UUID getPlayer2() {
        return isPvE ? null : blackPlayerId;
    }

    public boolean isPvE() { return isPvE; }
    public int getCurrentPlayer() { return currentPlayerColor; }
    public int getSpectatorCount() { return spectatorManager.getSpectatorCount(arena.getId()); }
    public int getMaxSpectators() { return arena.getMaxSpectators(); }
    public List<Move> getMoveHistory() { return moveHistory; }
    public int getTurnSecondsLeft() { return turnSecondsLeft; }
}
