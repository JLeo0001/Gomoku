package com.rikka.gomoku;

import com.rikka.gomoku.arena.ArenaManager;
import com.rikka.gomoku.command.GomokuAdminCommand;
import com.rikka.gomoku.command.GomokuCommand;
import com.rikka.gomoku.config.ConfigManager;
import com.rikka.gomoku.config.LanguageManager;
import com.rikka.gomoku.game.GameManager;
import com.rikka.gomoku.listener.GameListener;
import com.rikka.gomoku.spectator.SpectatorManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Main plugin class for Gomoku (五子棋) - a Minecraft minigame plugin for Purpur.
 * <p>
 * Features:
 * - PvP and PvE (AI) Gomoku gameplay
 * - 19x19 configurable board with skeleton/wither skull pieces
 * - Multi-arena system with independent worlds
 * - Spectator system with chat isolation
 * - Full inventory/state protection
 * - Multi-language support (zh_CN, en_US)
 */
public final class GomokuPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private GameManager gameManager;
    private ArenaManager arenaManager;
    private SpectatorManager spectatorManager;

    @Override
    public void onEnable() {
        // Initialize config and language
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);

        // Initialize managers
        this.gameManager = new GameManager(this);
        this.spectatorManager = new SpectatorManager(languageManager);
        this.arenaManager = new ArenaManager(this, configManager, languageManager, gameManager);

        // Register commands
        Objects.requireNonNull(getCommand("gomoku")).setExecutor(new GomokuCommand(this));
        Objects.requireNonNull(getCommand("gomokuadmin")).setExecutor(new GomokuAdminCommand(this));

        // Register listener
        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        getLogger().info("========================================");
        getLogger().info("  Gomoku v" + getDescription().getVersion());
        getLogger().info("  Board size: " + configManager.getBoardSize() + "x" + configManager.getBoardSize());
        getLogger().info("  Arenas: " + arenaManager.getArenaMap().size());
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        // Clean up all games
        if (gameManager != null) {
            gameManager.endAllGames();
        }

        // Clear spectators
        if (spectatorManager != null) {
            spectatorManager.clearAll();
        }

        getLogger().info("Gomoku disabled. All games cleaned up.");
    }

    // ─── Getters ──────────────────────────────────────────────────

    public ConfigManager getConfigManager() { return configManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public GameManager getGameManager() { return gameManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public SpectatorManager getSpectatorManager() { return spectatorManager; }
}
