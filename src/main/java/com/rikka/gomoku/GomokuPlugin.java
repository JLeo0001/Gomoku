package com.rikka.gomoku;

import com.rikka.gomoku.arena.Arena;
import com.rikka.gomoku.arena.ArenaManager;
import com.rikka.gomoku.arena.BoardRenderer;
import com.rikka.gomoku.command.GomokuAdminCommand;
import com.rikka.gomoku.command.GomokuCommand;
import com.rikka.gomoku.config.ConfigManager;
import com.rikka.gomoku.config.LanguageManager;
import com.rikka.gomoku.game.GameManager;
import com.rikka.gomoku.listener.GameListener;
import com.rikka.gomoku.spectator.SpectatorManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class GomokuPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private GameManager gameManager;
    private ArenaManager arenaManager;
    private SpectatorManager spectatorManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);

        this.gameManager = new GameManager(this);
        this.spectatorManager = new SpectatorManager(languageManager);
        this.arenaManager = new ArenaManager(this, configManager, languageManager, gameManager);

        // Re-detect existing arena worlds and regenerate boards
        recoverExistingArenas();

        Objects.requireNonNull(getCommand("gomoku")).setExecutor(new GomokuCommand(this));
        Objects.requireNonNull(getCommand("gomokuadmin")).setExecutor(new GomokuAdminCommand(this));

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        getLogger().info("========================================");
        getLogger().info("  Gomoku v" + getDescription().getVersion());
        getLogger().info("  Board: " + configManager.getBoardSize() + "×" + configManager.getBoardSize()
            + "  Y=" + configManager.getBoardY());
        getLogger().info("  Arenas: " + arenaManager.getArenaMap().size());
        getLogger().info("========================================");
    }

    /**
     * Scan for existing arena worlds and re-register them.
     * Regenerates the board for each based on current config.
     */
    private void recoverExistingArenas() {
        String prefix = configManager.getWorldPrefix();
        int size = configManager.getBoardSize();
        int y = configManager.getBoardY();
        BoardRenderer renderer = new BoardRenderer(configManager.getSurfaceBlock(), configManager.getGridBlock());

        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (name.startsWith(prefix)) {
                String arenaId = name.substring(prefix.length());
                if (arenaManager.getArena(arenaId) != null) continue; // already registered

                Arena arena = new Arena(arenaId);
                arena.setWorld(world);
                arena.setMaxSpectators(configManager.getMaxSpectators());
                arena.autoGeneratePositions(size, y);
                arenaManager.registerArena(arenaId, arena);

                renderer.renderFullArena(size, y, world);
                getLogger().info("Recovered arena '" + arenaId + "' from world '" + name + "'");
            }
        }
    }

    /**
     * Full reload: config, language, and regenerate all arena boards.
     */
    public void fullReload() {
        configManager.reload();
        languageManager.reload();
        arenaManager.regenerateAllBoards();
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.endAllGames();
        if (spectatorManager != null) spectatorManager.clearAll();
        getLogger().info("Gomoku disabled.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public GameManager getGameManager() { return gameManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public SpectatorManager getSpectatorManager() { return spectatorManager; }
}
