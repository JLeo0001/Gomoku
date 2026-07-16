package com.rikka.gomoku.listener;

import com.rikka.gomoku.GomokuPlugin;
import com.rikka.gomoku.arena.Arena;
import com.rikka.gomoku.arena.BoardRenderer;
import com.rikka.gomoku.game.Game;
import com.rikka.gomoku.game.GameManager;
import com.rikka.gomoku.game.GameState;
import com.rikka.gomoku.spectator.SpectatorManager;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerLoginEvent.Result;

/**
 * Main event listener for Gomoku plugin.
 */
public class GameListener implements Listener {
    private final GomokuPlugin plugin;
    private final GameManager gameManager;
    private final SpectatorManager spectatorManager;

    public GameListener(GomokuPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.spectatorManager = plugin.getSpectatorManager();
    }

    // ─── Board Interaction ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player.getUniqueId());

        if (game == null || game.getState() != GameState.PLAYING) return;

        // Only handle right-click
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        Arena arena = game.getArena();
        Location origin = arena.getBoardOrigin();
        if (origin == null) return;

        BoardRenderer renderer = new BoardRenderer(
            plugin.getConfigManager().getSurfaceBlock(),
            plugin.getConfigManager().getGridBlock()
        );

        int boardSize = game.getBoard().getSize();
        int[] coords = renderer.getBoardCoords(origin, boardSize, event.getClickedBlock().getLocation());

        if (coords != null) {
            game.placePiece(player, coords[0], coords[1]);
        }
    }

    // ─── Block Protection ─────────────────────────────────────────

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isInGameOrSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isInGameOrSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ─── Damage Protection ────────────────────────────────────────

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isInGameOrSpectating(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager) {
            if (isInGameOrSpectating(damager)) {
                event.setCancelled(true);
            }
        }
        if (event.getEntity() instanceof Player victim) {
            if (isInGameOrSpectating(victim)) {
                event.setCancelled(true);
            }
        }
    }

    // ─── Drop / Pickup Protection ─────────────────────────────────

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isInGameOrSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isInGameOrSpectating(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (isInGameOrSpectating(player)) {
                event.setCancelled(true);
            }
        }
    }

    // ─── Hunger Protection ────────────────────────────────────────

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isInGameOrSpectating(player)) {
                event.setCancelled(true);
            }
        }
    }

    // ─── Player Disconnect ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        gameManager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKick(PlayerKickEvent event) {
        if (!event.isCancelled()) {
            gameManager.handlePlayerQuit(event.getPlayer());
        }
    }

    // ─── World Change Protection ──────────────────────────────────

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player.getUniqueId());

        if (game != null) {
            Arena arena = game.getArena();
            Location origin = arena.getBoardOrigin();

            // If player left the arena world, treat as forfeit
            if (origin != null && !player.getWorld().equals(origin.getWorld())) {
                UUID player1 = game.getPlayer1();
                UUID player2 = game.getPlayer2();
                UUID winner;
                UUID pid = player.getUniqueId();
                if (pid.equals(player1)) {
                    winner = player2; // may be null (PvE)
                } else if (pid.equals(player2)) {
                    winner = player1;
                } else {
                    winner = null;
                }
                game.forceEndWithWinner(winner);
            }
        }
    }

    // ─── Spectator Chat ───────────────────────────────────────────

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (spectatorManager.isSpectating(player)) {
            event.setCancelled(true);
            spectatorManager.handleSpectatorChat(player, event.getMessage());
        }
    }

    // ─── Command Protection ───────────────────────────────────────

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (spectatorManager.isSpectating(player)) {
            String cmd = event.getMessage().toLowerCase().split(" ")[0];
            // Allow gomoku commands and essential commands
            if (!cmd.startsWith("/gomoku") && !cmd.startsWith("/gk") && !cmd.startsWith("/go")
                && !cmd.startsWith("/gka") && !cmd.startsWith("/gomokuadmin")) {
                player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    " §cYou can only use Gomoku commands while spectating.");
                event.setCancelled(true);
            }
        }
    }

    // ─── Utility ──────────────────────────────────────────────────

    private boolean isInGameOrSpectating(Player player) {
        return gameManager.isInGame(player.getUniqueId()) || spectatorManager.isSpectating(player);
    }
}
