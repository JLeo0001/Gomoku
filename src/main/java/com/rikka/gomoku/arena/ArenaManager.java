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
 * Boards and spawn points are auto-generated from config on create and reload.
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
     * Create a new arena with a dedicated world and auto-generated board.
     */
    public boolean createArena(String id, Player creator) {
        if (arenas.containsKey(id)) {
            if (creator != null) creator.sendMessage(lang.format("arena-exists", Map.of("arena", id)));
            return false;
        }

        String worldName = config.getWorldPrefix() + id;

        World world = createArenaWorld(worldName);
        if (world == null) {
            if (creator != null) creator.sendMessage(lang.getPrefix() + " §cFailed to create world for arena.");
            return false;
        }

        Arena arena = new Arena(id);
        arena.setWorld(world);
        arena.setMaxSpectators(config.getMaxSpectators());
        arena.autoGeneratePositions(config.getBoardSize(), config.getBoardY());
        arenas.put(id, arena);

        // Set world spawn to lobby for safety
        world.setSpawnLocation(arena.getLobbySpawn());

        // Render the entire arena (board + lobby + spawn pads + spectator deck)
        BoardRenderer renderer = new BoardRenderer(config.getSurfaceBlock(), config.getGridBlock());
        renderer.renderFullArena(config.getBoardSize(), config.getBoardY(), world);

        if (creator != null) creator.sendMessage(lang.format("arena-created", Map.of("arena", id)));
        plugin.getLogger().info("Arena '" + id + "' created — board " + config.getBoardSize()
            + "×" + config.getBoardSize() + " at Y=" + config.getBoardY());
        return true;
    }

    /**
     * Regenerate all arena boards. Called on reload.
     */
    public void regenerateAllBoards() {
        int size = config.getBoardSize();
        int y = config.getBoardY();
        BoardRenderer renderer = new BoardRenderer(config.getSurfaceBlock(), config.getGridBlock());

        for (Arena arena : arenas.values()) {
            if (arena.getState() == ArenaState.IN_USE) continue;

            arena.autoGeneratePositions(size, y);
            renderer.renderFullArena(size, y, arena.getWorld());
            plugin.getLogger().info("Regenerated arena '" + arena.getId() + "'");
        }
    }

    private World createArenaWorld(String worldName) {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) return existing;

        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.generatorSettings("{\"layers\":[{\"block\":\"bedrock\",\"height\":1},{\"block\":\"stone\",\"height\":127}],\"biome\":\"minecraft:plains\"}");

        World world = creator.createWorld();
        if (world != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setTime(6000);
        }
        return world;
    }

    /**
     * Delete an arena and unload its world.
     */
    public boolean deleteArena(String id, Player admin) {
        Arena arena = arenas.get(id);
        if (arena == null) {
            if (admin != null) admin.sendMessage(lang.format("arena-not-found", Map.of("arena", id)));
            return false;
        }

        if (arena.getState() != ArenaState.IDLE) {
            if (admin != null) admin.sendMessage(lang.format("arena-in-use", Map.of("arena", id)));
            return false;
        }

        if (arena.getCurrentGame() != null) {
            arena.getCurrentGame().forceEnd();
        }

        World world = arena.getWorld();
        if (world != null) {
            for (Player p : world.getPlayers()) {
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            Bukkit.unloadWorld(world, false);
        }

        arenas.remove(id);
        if (admin != null) admin.sendMessage(lang.format("arena-deleted", Map.of("arena", id)));
        return true;
    }

    /**
     * Set a functional point for an arena (manual override for auto-generated points).
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
            case "lobby" -> arena.setLobbySpawn(loc);
            case "spawn1" -> arena.setPlayer1Spawn(loc);
            case "spawn2" -> arena.setPlayer2Spawn(loc);
            case "spectator", "spec" -> arena.setSpectatorSpawn(loc);
            default -> {
                admin.sendMessage(lang.getPrefix() + " §cUnknown point. Use: lobby, spawn1, spawn2, spectator");
                return false;
            }
        }

        admin.sendMessage(lang.format("point-set", Map.of("arena", id, "point", point)));
        return true;
    }

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
