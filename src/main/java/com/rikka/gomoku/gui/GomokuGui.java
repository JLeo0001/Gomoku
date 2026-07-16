package com.rikka.gomoku.gui;

import com.rikka.gomoku.GomokuPlugin;
import com.rikka.gomoku.arena.Arena;
import com.rikka.gomoku.arena.ArenaState;
import com.rikka.gomoku.config.LanguageManager;
import com.rikka.gomoku.game.Game;
import com.rikka.gomoku.game.GameManager;
import com.rikka.gomoku.game.GameState;
import com.rikka.gomoku.spectator.SpectatorManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Chest GUI for all player-facing Gomoku operations.
 *
 * Flow:
 *   Main Menu (27) → Quick PvP / Quick PvE / Spectate / Arena List
 *   Arena List (54) → Arena Action picker
 *   Arena Action (9) → Join PvP / Join PvE / Spectate
 */
public final class GomokuGui implements Listener {

    private final GomokuPlugin plugin;
    private final GameManager gameManager;
    private final SpectatorManager spectatorManager;
    private final LanguageManager lang;
    private final NamespacedKey actionKey;
    private final NamespacedKey arenaKey;
    private final NamespacedKey modeKey;

    public GomokuGui(GomokuPlugin plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.spectatorManager = plugin.getSpectatorManager();
        this.lang = plugin.getLanguageManager();
        this.actionKey = new NamespacedKey(plugin, "gomoku_gui_action");
        this.arenaKey = new NamespacedKey(plugin, "gomoku_gui_arena");
        this.modeKey = new NamespacedKey(plugin, "gomoku_gui_mode");
    }

    // ═══════════════════════════════════════════════════════════════
    // Public openers
    // ═══════════════════════════════════════════════════════════════

    public void openMain(Player player) {
        Inventory inv = createInventory(27, lang.get("gui.main.title"));

        // Decorative border
        fillBorder(inv);

        // ── Row 2 (slots 10–16): Quick actions ──────────────────

        // PvP Quick Match
        inv.setItem(10, actionItem(
            Material.DIAMOND_SWORD,
            lang.get("gui.main.pvp-name"),
            lang.getList("gui.main.pvp-lore"),
            "pvp_quick"
        ));

        // Arena Browser
        inv.setItem(11, actionItem(
            Material.COMPASS,
            lang.get("gui.main.arena-list-name"),
            lang.getList("gui.main.arena-list-lore"),
            "arena_list"
        ));

        // Status / Info
        inv.setItem(13, statusItem(player));

        // PvE Quick Match
        inv.setItem(14, actionItem(
            Material.WITHER_SKELETON_SKULL,
            lang.get("gui.main.pve-name"),
            lang.getList("gui.main.pve-lore"),
            "pve_quick"
        ));

        // Spectate active game
        inv.setItem(15, actionItem(
            Material.ENDER_EYE,
            lang.get("gui.main.spectate-name"),
            lang.getList("gui.main.spectate-lore"),
            "spectate_list"
        ));

        // Leave
        inv.setItem(16, actionItem(
            Material.BARRIER,
            lang.get("gui.main.leave-name"),
            lang.getList("gui.main.leave-lore"),
            "leave"
        ));

        player.openInventory(inv);
    }

    public void openArenaList(Player player) {
        Collection<Arena> arenas = plugin.getArenaManager().getArenas();
        int size = Math.min(54, ((Math.max(arenas.size(), 1) + 8) / 9) * 9 + 18);
        size = Math.max(27, size);

        String title = lang.get("gui.arena-list.title");
        Inventory inv = createInventory(size, title);
        fillBorder(inv);

        if (arenas.isEmpty()) {
            inv.setItem(size / 2, plainItem(Material.BARRIER,
                lang.get("gui.arena-list.empty-name"),
                lang.getList("gui.arena-list.empty-lore")));
        } else {
            int slot = 10;
            for (Arena arena : arenas) {
                if (slot >= size - 9) break; // reserve bottom row
                inv.setItem(slot, arenaItem(arena));
                slot++;
                // skip right border
                if (slot % 9 == 8) slot += 2;
            }
        }

        // Back button
        inv.setItem(size - 5, actionItem(Material.ARROW,
            lang.get("gui.common.back-name"), Collections.emptyList(), "main"));

        player.openInventory(inv);
    }

    public void openArenaAction(Player player, String arenaId) {
        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null) {
            openArenaList(player);
            return;
        }

        Inventory inv = createInventory(9, lang.get("gui.arena-action.title",
            Map.of("arena", arenaId)));
        fillBorder(inv);

        boolean isIdle = arena.getState() == ArenaState.IDLE;
        boolean hasActiveGame = arena.getCurrentGame() != null
            && arena.getCurrentGame().getState() == GameState.PLAYING;

        if (isIdle) {
            // Join PvP
            inv.setItem(2, arenaActionItem(Material.DIAMOND_SWORD,
                lang.get("gui.arena-action.pvp-name"),
                lang.getList("gui.arena-action.pvp-lore",
                    Map.of("arena", arenaId)),
                arenaId, "pvp"));

            // Join PvE
            inv.setItem(4, arenaActionItem(Material.WITHER_SKELETON_SKULL,
                lang.get("gui.arena-action.pve-name"),
                lang.getList("gui.arena-action.pve-lore",
                    Map.of("arena", arenaId)),
                arenaId, "pve"));
        } else {
            // Arena in use — show status
            inv.setItem(4, plainItem(Material.RED_CONCRETE,
                lang.get("gui.arena-action.inuse-name"),
                lang.getList("gui.arena-action.inuse-lore",
                    Map.of("arena", arenaId, "state", arena.getState().name()))));
        }

        // Spectate (only if game active)
        if (hasActiveGame) {
            inv.setItem(6, arenaActionItem(Material.ENDER_EYE,
                lang.get("gui.arena-action.spectate-name"),
                lang.getList("gui.arena-action.spectate-lore",
                    Map.of("arena", arenaId)),
                arenaId, "spectate"));
        }

        // Back
        inv.setItem(7, actionItem(Material.ARROW,
            lang.get("gui.common.back-name"), Collections.emptyList(), "arena_list"));

        player.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════════
    // Click handler
    // ═══════════════════════════════════════════════════════════════

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GomokuInventoryHolder)) return;

        event.setCancelled(true);

        ItemStack stack = event.getCurrentItem();
        if (stack == null || !stack.hasItemMeta()) return;

        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        String action = data.get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        String arena = data.getOrDefault(arenaKey, PersistentDataType.STRING, "");
        String mode = data.getOrDefault(modeKey, PersistentDataType.STRING, "");

        switch (action) {
            case "pvp_quick" -> joinGame(player, "pvp", null);
            case "pve_quick" -> joinGame(player, "pve", null);
            case "arena_list" -> openArenaList(player);
            case "spectate_list" -> openSpectateList(player);
            case "leave" -> handleLeave(player);
            case "main" -> openMain(player);
            case "arena_action" -> openArenaAction(player, arena);
            case "join" -> joinGame(player, mode, arena);
            case "spectate" -> handleSpectate(player, arena);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Business logic
    // ═══════════════════════════════════════════════════════════════

    private void joinGame(Player player, String mode, String arenaId) {
        if (!player.hasPermission("gomoku.player")) {
            player.sendMessage(lang.format("no-permission", Map.of()));
            player.closeInventory();
            return;
        }

        if (gameManager.isInGame(player.getUniqueId())) {
            player.sendMessage(lang.format("already-in-game", Map.of()));
            player.closeInventory();
            return;
        }

        // Resolve arena
        Arena arena;
        if (arenaId != null && !arenaId.isEmpty()) {
            arena = plugin.getArenaManager().getArena(arenaId);
            if (arena == null) {
                player.sendMessage(lang.format("arena-not-found", Map.of("arena", arenaId)));
                return;
            }
            if (arena.getState() != ArenaState.IDLE) {
                player.sendMessage(lang.format("arena-in-use", Map.of("arena", arenaId)));
                return;
            }
        } else {
            arena = plugin.getArenaManager().getAvailableArena();
            if (arena == null) {
                player.sendMessage(lang.format("no-arena-available", Map.of()));
                player.closeInventory();
                return;
            }
        }

        if (mode.equals("pve") || mode.equals("ai")) {
            if (arena.getCurrentGame() == null) {
                Game game = gameManager.createGame(arena);
                game.startPvE(player);
                gameManager.setPlayerArena(player.getUniqueId(), arena.getId());
                player.sendMessage(lang.format("joined-pve", Map.of()));
            } else {
                player.sendMessage(lang.format("arena-in-use", Map.of("arena", arena.getId())));
            }
        } else {
            Game game = arena.getCurrentGame();
            if (game == null) {
                game = gameManager.createGame(arena);
            }
            if (game.getState() == GameState.WAITING) {
                game.addPlayer(player);
                gameManager.setPlayerArena(player.getUniqueId(), arena.getId());
            } else {
                player.sendMessage(lang.format("arena-in-use", Map.of("arena", arena.getId())));
            }
        }

        player.closeInventory();
    }

    private void handleLeave(Player player) {
        Game game = gameManager.getPlayerGame(player.getUniqueId());
        if (game != null) {
            game.playerLeave(player);
            player.sendMessage(lang.format("left-game", Map.of()));
            player.closeInventory();
            return;
        }
        if (spectatorManager.isSpectating(player)) {
            spectatorManager.removeSpectator(player);
            player.closeInventory();
            return;
        }
        player.sendMessage(lang.format("not-in-game", Map.of()));
    }

    private void handleSpectate(Player player, String arenaId) {
        if (!player.hasPermission("gomoku.spectate")) {
            player.sendMessage(lang.format("no-permission", Map.of()));
            return;
        }

        if (gameManager.isInGame(player.getUniqueId())) {
            player.sendMessage(lang.format("already-in-game", Map.of()));
            return;
        }

        if (spectatorManager.isSpectating(player)) {
            spectatorManager.removeSpectator(player);
            player.closeInventory();
            return;
        }

        Arena arena = plugin.getArenaManager().getArena(arenaId);
        if (arena == null || arena.getCurrentGame() == null) {
            player.sendMessage(lang.format("no-active-game", Map.of(
                "arena", arenaId != null ? arenaId : "?")));
            return;
        }

        Game game = arena.getCurrentGame();
        spectatorManager.addSpectator(player, arena);

        String p1Name = game.getPlayer1() != null ?
            (Bukkit.getPlayer(game.getPlayer1()) != null ?
                Bukkit.getPlayer(game.getPlayer1()).getName() : "?") : "?";
        String p2Name = game.isPvE() ? "AI" :
            (game.getPlayer2() != null ?
                (Bukkit.getPlayer(game.getPlayer2()) != null ?
                    Bukkit.getPlayer(game.getPlayer2()).getName() : "?") : "?");

        player.sendMessage(lang.format("spectate-join", Map.of(
            "player1", p1Name, "player2", p2Name)));
        player.closeInventory();
    }

    private void openSpectateList(Player player) {
        Collection<Arena> arenas = plugin.getArenaManager().getArenas();
        List<Arena> activeArenas = arenas.stream()
            .filter(a -> a.getCurrentGame() != null
                && a.getCurrentGame().getState() == GameState.PLAYING)
            .toList();

        int size = 27;
        String title = lang.get("gui.spectate-list.title");
        Inventory inv = createInventory(size, title);
        fillBorder(inv);

        if (activeArenas.isEmpty()) {
            inv.setItem(13, plainItem(Material.BARRIER,
                lang.get("gui.spectate-list.empty-name"),
                lang.getList("gui.spectate-list.empty-lore")));
        } else {
            int slot = 10;
            for (Arena arena : activeArenas) {
                if (slot > 16) break;
                inv.setItem(slot++, spectateArenaItem(arena));
            }
        }

        inv.setItem(22, actionItem(Material.ARROW,
            lang.get("gui.common.back-name"), Collections.emptyList(), "main"));

        player.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════════
    // Item factories
    // ═══════════════════════════════════════════════════════════════

    private ItemStack actionItem(Material material, String name, List<String> lore, String action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(LanguageManager.color(name));
        meta.setLore(lore.stream().map(LanguageManager::color).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack arenaActionItem(Material material, String name, List<String> lore,
                                       String arenaId, String mode) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(LanguageManager.color(name));
        meta.setLore(lore.stream().map(LanguageManager::color).toList());
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(actionKey, PersistentDataType.STRING, "join");
        data.set(arenaKey, PersistentDataType.STRING, arenaId);
        data.set(modeKey, PersistentDataType.STRING, mode);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack arenaItem(Arena arena) {
        boolean inUse = arena.getState() != ArenaState.IDLE;
        Material mat = inUse ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
        if (arena.getState() == ArenaState.MAINTENANCE) mat = Material.GRAY_CONCRETE;

        String nameKey = inUse ? "gui.arena-list.item-inuse-name"
            : "gui.arena-list.item-idle-name";
        String loreKey = inUse ? "gui.arena-list.item-inuse-lore"
            : "gui.arena-list.item-idle-lore";

        String name = lang.get(nameKey, Map.of("arena", arena.getId()));

        Game game = arena.getCurrentGame();
        String gameInfo = game != null ? game.getState().name() : "None";
        String spectators = game != null ? String.valueOf(game.getSpectatorCount()) : "0";

        List<String> lore = lang.getList(loreKey, Map.of(
            "state", arena.getState().name(),
            "game", gameInfo,
            "spectators", spectators,
            "max", String.valueOf(arena.getMaxSpectators())
        ));

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(LanguageManager.color(name));
        meta.setLore(lore.stream().map(LanguageManager::color).toList());
        PersistentDataContainer data = meta.getPersistentDataContainer();

        if (inUse) {
            // Clicking an in-use arena opens spectate mode directly
            data.set(actionKey, PersistentDataType.STRING, "arena_action");
        } else {
            data.set(actionKey, PersistentDataType.STRING, "arena_action");
        }
        data.set(arenaKey, PersistentDataType.STRING, arena.getId());

        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack spectateArenaItem(Arena arena) {
        Game game = arena.getCurrentGame();
        String p1Name = "?";
        String p2Name = "?";
        if (game != null) {
            if (game.getPlayer1() != null) {
                Player p = Bukkit.getPlayer(game.getPlayer1());
                p1Name = p != null ? p.getName() : "?";
            }
            if (game.isPvE()) {
                p2Name = "AI";
            } else if (game.getPlayer2() != null) {
                Player p = Bukkit.getPlayer(game.getPlayer2());
                p2Name = p != null ? p.getName() : "?";
            }
        }

        String name = lang.get("gui.spectate-list.item-name",
            Map.of("arena", arena.getId()));
        List<String> lore = lang.getList("gui.spectate-list.item-lore", Map.of(
            "player1", p1Name,
            "player2", p2Name,
            "spectators", String.valueOf(arena.getCurrentGame() != null ?
                arena.getCurrentGame().getSpectatorCount() : 0)
        ));

        ItemStack stack = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(LanguageManager.color(name));
        meta.setLore(lore.stream().map(LanguageManager::color).toList());
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(actionKey, PersistentDataType.STRING, "spectate");
        data.set(arenaKey, PersistentDataType.STRING, arena.getId());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack statusItem(Player player) {
        Game game = gameManager.getPlayerGame(player.getUniqueId());
        boolean spectating = spectatorManager.isSpectating(player);

        String name;
        List<String> lore;

        if (game != null) {
            Arena arena = game.getArena();
            String opponent;
            if (game.isPvE()) {
                opponent = "AI";
            } else {
                UUID oppId = game.getPlayer1() != null
                    && game.getPlayer1().equals(player.getUniqueId())
                    ? game.getPlayer2() : game.getPlayer1();
                opponent = oppId != null ?
                    (Bukkit.getPlayer(oppId) != null ?
                        Bukkit.getPlayer(oppId).getName() : "?") : "?";
            }

            name = lang.get("gui.main.status-in-game-name");
            lore = lang.getList("gui.main.status-in-game-lore", Map.of(
                "arena", arena.getId(),
                "opponent", opponent,
                "state", game.getState().name(),
                "mode", game.isPvE() ? "PvE" : "PvP"
            ));
        } else if (spectating) {
            name = lang.get("gui.main.status-spectating-name");
            String specArena = spectatorManager.getSpectatingArena(player);
            lore = lang.getList("gui.main.status-spectating-lore",
                Map.of("arena", specArena != null ? specArena : "?"));
        } else {
            name = lang.get("gui.main.status-idle-name");
            lore = lang.getList("gui.main.status-idle-lore", Map.of(
                "arenas", String.valueOf(plugin.getArenaManager().getArenas().size()),
                "version", plugin.getDescription().getVersion()
            ));
        }

        Material mat = game != null ? Material.EXPERIENCE_BOTTLE
            : spectating ? Material.ENDER_PEARL : Material.BOOK;

        return plainItem(mat, name, lore);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private void fillBorder(Inventory inv) {
        ItemStack filler = plainItem(Material.GRAY_STAINED_GLASS_PANE,
            lang.get("gui.common.filler-name"),
            Collections.emptyList());

        int size = inv.getSize();
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(i, filler);
            }
        }
    }

    private Inventory createInventory(int size, String title) {
        GomokuInventoryHolder holder = new GomokuInventoryHolder();
        Inventory inv = Bukkit.createInventory(holder, size,
            LanguageManager.color(title));
        holder.inventory(inv);
        return inv;
    }

    private ItemStack plainItem(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(LanguageManager.color(name));
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream().map(LanguageManager::color).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
