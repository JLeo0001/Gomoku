package com.rikka.gomoku.game;

import com.rikka.gomoku.GomokuPlugin;
import com.rikka.gomoku.arena.Arena;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Manages all active Game instances and player data persistence.
 */
public class GameManager {
    private final GomokuPlugin plugin;
    // Maps arena id -> active Game
    private final Map<String, Game> activeGames = new HashMap<>();
    // Maps player UUID -> PlayerGameData
    private final Map<UUID, PlayerGameData> savedPlayerData = new HashMap<>();
    // Maps player UUID -> arena id they are in
    private final Map<UUID, String> playerArenaMap = new HashMap<>();

    public GameManager(GomokuPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Save a player's current state before entering a game.
     */
    public void savePlayerData(Player player, Location returnLocation) {
        PlayerGameData data = new PlayerGameData(
            player.getUniqueId(),
            returnLocation != null ? returnLocation.clone() : player.getLocation().clone(),
            player.getGameMode(),
            player.getInventory().getContents().clone(),
            player.getEnderChest().getContents().clone(),
            player.getHealth(),
            player.getFoodLevel(),
            player.getSaturation(),
            player.getLevel(),
            player.getExp()
        );
        savedPlayerData.put(player.getUniqueId(), data);

        // Clear player for game
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setLevel(0);
        player.setExp(0);
    }

    /**
     * Restore a player's pre-game state.
     */
    public void restorePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerGameData data = savedPlayerData.remove(uuid);
        playerArenaMap.remove(uuid);

        if (data == null) return;

        player.getInventory().setContents(data.getInventoryContents());
        player.getEnderChest().setContents(data.getEnderChestContents());
        player.setGameMode(data.getGameMode());
        player.setHealth(Math.min(data.getHealth(), 20));
        player.setFoodLevel(data.getFoodLevel());
        player.setSaturation(data.getSaturation());
        player.setLevel(data.getXpLevel());
        player.setExp(data.getXpProgress());

        if (data.getReturnLocation() != null && data.getReturnLocation().getWorld() != null) {
            player.teleport(data.getReturnLocation());
        }
    }

    /**
     * Create a new game on the given arena.
     */
    public Game createGame(Arena arena) {
        Game game = new Game(plugin, arena);
        activeGames.put(arena.getId(), game);
        arena.setCurrentGame(game);
        return game;
    }

    /**
     * Get the active game on an arena, or null.
     */
    public Game getGame(String arenaId) {
        return activeGames.get(arenaId);
    }

    /**
     * Get the game a player is currently in.
     */
    public Game getPlayerGame(UUID playerId) {
        String arenaId = playerArenaMap.get(playerId);
        if (arenaId == null) return null;
        return activeGames.get(arenaId);
    }

    /**
     * Register a player as being in an arena.
     */
    public void setPlayerArena(UUID playerId, String arenaId) {
        playerArenaMap.put(playerId, arenaId);
    }

    /**
     * Remove a player from their current game (on quit/disconnect).
     */
    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Game game = getPlayerGame(uuid);
        if (game != null) {
            game.forceEndWithWinner(game.getPlayer1().equals(uuid) ? game.getPlayer2() : game.getPlayer1());
            restorePlayerData(player);
        } else {
            // Player might be in queue
            restorePlayerData(player);
        }

        // Also handle spectator
        plugin.getSpectatorManager().removeSpectator(player);
    }

    /**
     * Force end all active games.
     */
    public void endAllGames() {
        for (Game game : new ArrayList<>(activeGames.values())) {
            game.forceEnd();
        }
    }

    /**
     * Remove a game (called by Game.cleanup).
     */
    public void removeGame(String arenaId) {
        activeGames.remove(arenaId);
    }

    public Map<String, Game> getActiveGames() {
        return Collections.unmodifiableMap(activeGames);
    }

    public boolean isInGame(UUID playerId) {
        return playerArenaMap.containsKey(playerId);
    }

    public PlayerGameData getPlayerData(UUID playerId) {
        return savedPlayerData.get(playerId);
    }
}
