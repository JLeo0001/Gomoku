package com.rikka.gomoku.arena;

import com.rikka.gomoku.game.Game;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents a physical arena where a Gomoku game takes place.
 * Board and all spawn points are auto-generated from config.
 */
public class Arena {
    private final String id;
    private World world;
    private ArenaState state = ArenaState.IDLE;

    // Board origin (min corner) — auto-calculated
    private Location boardOrigin;
    private int boardSize;
    private int boardY;

    // Auto-generated spawn points
    private Location lobbySpawn;
    private Location player1Spawn;
    private Location player2Spawn;
    private Location spectatorSpawn;

    private int maxSpectators = 10;
    private Game currentGame;

    public Arena(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }

    public ArenaState getState() { return state; }
    public void setState(ArenaState state) { this.state = state; }

    // ─── Board corner compat (for old API) ────────────────────────

    public Location getBoardCorner1() { return boardOrigin; }
    public void setBoardCorner1(Location loc) { this.boardOrigin = loc; }

    public Location getBoardCorner2() {
        if (boardOrigin == null) return null;
        return new Location(world,
            boardOrigin.getBlockX() + boardSize - 1,
            boardOrigin.getBlockY(),
            boardOrigin.getBlockZ() + boardSize - 1);
    }
    public void setBoardCorner2(Location loc) { /* no-op, auto-generated */ }

    public Location getBoardOrigin() { return boardOrigin; }

    // ─── Spawn points ─────────────────────────────────────────────

    public Location getLobbySpawn() { return lobbySpawn; }
    public void setLobbySpawn(Location loc) { this.lobbySpawn = loc; }

    public Location getPlayer1Spawn() { return player1Spawn; }
    public void setPlayer1Spawn(Location loc) { this.player1Spawn = loc; }

    public Location getPlayer2Spawn() { return player2Spawn; }
    public void setPlayer2Spawn(Location loc) { this.player2Spawn = loc; }

    public Location getSpectatorSpawn() { return spectatorSpawn; }
    public void setSpectatorSpawn(Location loc) { this.spectatorSpawn = loc; }

    public int getMaxSpectators() { return maxSpectators; }
    public void setMaxSpectators(int max) { this.maxSpectators = Math.max(0, max); }

    public Game getCurrentGame() { return currentGame; }
    public void setCurrentGame(Game game) { this.currentGame = game; }

    public int getBoardSize() { return boardSize; }

    // ─── Auto-generation ──────────────────────────────────────────

    /**
     * Auto-generate all positions from board size and Y level.
     * Board spans from (0, y, 0) to (size-1, y, size-1).
     * Call this after world is created and every reload.
     */
    public void autoGeneratePositions(int size, int yLevel) {
        if (world == null) return;
        this.boardSize = size;
        this.boardY = yLevel;

        int half = size / 2;

        // Board: (0, y, 0) → (size-1, y, size-1)
        this.boardOrigin = new Location(world, 0, yLevel, 0);

        // Lobby: 5 blocks south of board center
        this.lobbySpawn = new Location(world, half + 0.5, yLevel + 1, -4.5);

        // Player1 (White): west side
        this.player1Spawn = new Location(world, -2.5, yLevel + 1, half + 0.5);

        // Player2 (Black): east side
        this.player2Spawn = new Location(world, size + 1.5, yLevel + 1, half + 0.5);

        // Spectator: elevated center
        this.spectatorSpawn = new Location(world, half + 0.5, yLevel + 10, half + 0.5);
    }

    public boolean isReady() {
        return world != null && boardOrigin != null;
    }
}
