package com.rikka.gomoku.arena;

import com.rikka.gomoku.game.Game;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Represents a physical arena where a Gomoku game takes place.
 */
public class Arena {
    private final String id;
    private World world;
    private ArenaState state = ArenaState.IDLE;

    // Corner locations of the board (two opposite corners)
    private Location boardCorner1;
    private Location boardCorner2;

    // Spawn points
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

    public Location getBoardCorner1() { return boardCorner1; }
    public void setBoardCorner1(Location loc) { this.boardCorner1 = loc; }

    public Location getBoardCorner2() { return boardCorner2; }
    public void setBoardCorner2(Location loc) { this.boardCorner2 = loc; }

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

    public boolean isReady() {
        return world != null
            && boardCorner1 != null
            && boardCorner2 != null
            && lobbySpawn != null
            && player1Spawn != null
            && player2Spawn != null
            && spectatorSpawn != null;
    }

    /**
     * Get the origin (min x, min z) of the board.
     */
    public Location getBoardOrigin() {
        if (boardCorner1 == null || boardCorner2 == null) return null;
        double minX = Math.min(boardCorner1.getX(), boardCorner2.getX());
        double minZ = Math.min(boardCorner1.getZ(), boardCorner2.getZ());
        double y = boardCorner1.getY();
        return new Location(world, minX, y, minZ);
    }
}
