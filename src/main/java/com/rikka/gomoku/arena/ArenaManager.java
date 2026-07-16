package com.rikka.gomoku.arena;

import com.rikka.gomoku.GomokuPlugin;
import com.rikka.gomoku.config.ConfigManager;
import com.rikka.gomoku.config.LanguageManager;
import com.rikka.gomoku.game.GameManager;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

/**
 * Manages all arena instances and their worlds.
 */
public class ArenaManager {
    private final GomokuPlugin plugin;
    private final ConfigManager config;
    private final LanguageManager lang;
    private final GameManager gameManager;

    // arena id -> Arena
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(GomokuPlugin plugin, ConfigManager config, LanguageManager lang, GameManager gameManager) {
        this.plugin = plugin;
        this.config = config;
        this.lang = lang;
        this.gameManager = gameManager;
    }

    /**
     * Create a new arena with a dedicated world.
     */
    public boolean createArena(String id, Player creator) {
        if (arenas.containsKey(id)) {
            creator.sendMessage(lang.format("arena-exists", Map.of("arena", id)));
            return false;
        }

        String worldName = config.getWorldPrefix() + id;

        // Create the world
        World world = createArenaWorld(worldName);
        if (world == null) {
            creator.sendMessage(lang.getPrefix() + " §cFailed to create world for arena.");
            return false;
        }

        Arena arena = new Arena(id);
        arena.setWorld(world);
        arenas.put(id, arena);

        creator.sendMessage(lang.format("arena-created", Map.of("arena", id)));
        plugin.getLogger().info("Arena '" + id + "' created in world '" + worldName + "'.");
        return true;
    }

    private World createArenaWorld(String worldName) {
        // Check if world already exists
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) return existing;

        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.generatorSettings("{\"layers\":[{\"block\":\"air\",\"height\":1}],\"biome\":\"minecraft:plains\"}");

        World world = creator.createWorld();
        if (world != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setTime(6000); // Noon
        }
        return world;
    }

    /**
     * Delete an arena and unload its world.
     */
    public boolean deleteArena(String id, Player admin) {
        Arena arena = arenas.get(id);
        if (arena == null) {
            admin.sendMessage(lang.format("arena-not-found", Map.of("arena", id)));
            return false;
        }

        if (arena.getState() != ArenaState.IDLE) {
            admin.sendMessage(lang.format("arena-in-use", Map.of("arena", id)));
            return false;
        }

        // End any game
        if (arena.getCurrentGame() != null) {
            arena.getCurrentGame().forceEnd();
        }

        // Unload world
        World world = arena.getWorld();
        if (world != null) {
            // Kick players from world
            for (Player p : world.getPlayers()) {
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            Bukkit.unloadWorld(world, false);
        }

        arenas.remove(id);
        admin.sendMessage(lang.format("arena-deleted", Map.of("arena", id)));
        return true;
    }

    /**
     * Set a functional point for an arena.
     * Points: board1, board2, lobby, spawn1, spawn2, spectator
     */
    public boolean setPoint(String id, String point, Player admin) {
        Arena arena = arenas.get(id);
        if (arena == null) {
            admin.sendMessage(lang.format("arena-not-found", Map.of("arena", id)));
            return false;
        }

        if (!admin.getWorld().equals(arena.getWorld())) {
            admin.sendMessage(lang.format("not-in-arena-world", Map.of()));
            return false;
        }

        Location loc = admin.getLocation();

        switch (point.toLowerCase()) {
            case "board1", "corner1" -> arena.setBoardCorner1(loc);
            case "board2", "corner2" -> arena.setBoardCorner2(loc);
            case "lobby" -> arena.setLobbySpawn(loc);
            case "spawn1" -> arena.setPlayer1Spawn(loc);
            case "spawn2" -> arena.setPlayer2Spawn(loc);
            case "spectator", "spec" -> arena.setSpectatorSpawn(loc);
            default -> {
                admin.sendMessage(lang.getPrefix() + " §cUnknown point. Use: board1, board2, lobby, spawn1, spawn2, spectator");
                return false;
            }
        }

        admin.sendMessage(lang.format("point-set", Map.of("arena", id, "point", point)));
        return true;
    }

    /**
     * Get an available arena (IDLE state).
     */
    public Arena getAvailableArena() {
        for (Arena arena : arenas.values()) {
            if (arena.getState() == ArenaState.IDLE && arena.isReady()) {
                return arena;
            }
        }
        return null;
    }

    public Arena getArena(String id) {
        return arenas.get(id);
    }

    public Collection<Arena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public Map<String, Arena> getArenaMap() {
        return Collections.unmodifiableMap(arenas);
    }

    /**
     * Get arena status info string.
     */
    public String getArenaStatus(Arena arena) {
        return lang.format("arena-status", Map.of(
            "name", arena.getId(),
            "state", arena.getState().name(),
            "game", arena.getCurrentGame() != null ? arena.getCurrentGame().getState().name() : "None",
            "spectators", String.valueOf(arena.getCurrentGame() != null ? arena.getCurrentGame().getSpectatorCount() : 0),
            "max", String.valueOf(arena.getMaxSpectators())
        ));
    }
}
