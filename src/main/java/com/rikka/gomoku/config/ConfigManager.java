package com.rikka.gomoku.config;

import com.rikka.gomoku.GomokuPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final GomokuPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(GomokuPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public int getBoardSize() {
        int size = config.getInt("board.size", 19);
        return Math.max(9, Math.min(25, size)); // clamp 9-25
    }

    public Material getSurfaceBlock() {
        String name = config.getString("board.surface-block", "OAK_PLANKS");
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : Material.OAK_PLANKS;
    }

    public Material getGridBlock() {
        String name = config.getString("board.grid-block", "DARK_OAK_PLANKS");
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : Material.DARK_OAK_PLANKS;
    }

    public int getLobbyCountdown() {
        return Math.max(5, config.getInt("timing.lobby-countdown", 30));
    }

    public int getTurnTimeout() {
        return Math.max(10, config.getInt("timing.turn-timeout", 60));
    }

    public int getGameMaxDuration() {
        return Math.max(60, config.getInt("timing.game-max-duration", 1800));
    }

    public int getMaxSpectators() {
        return Math.max(0, config.getInt("spectator.max-per-arena", 10));
    }

    public boolean isMultiverseEnabled() {
        return config.getBoolean("multiverse.enabled", true);
    }

    public String getWorldPrefix() {
        return config.getString("multiverse.world-prefix", "gomoku_arena_");
    }
}
