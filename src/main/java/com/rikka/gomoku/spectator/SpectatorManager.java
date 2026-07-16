package com.rikka.gomoku.spectator;

import com.rikka.gomoku.arena.Arena;
import com.rikka.gomoku.config.LanguageManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages spectators for Gomoku games.
 * Provides chat isolation and spectator limits.
 */
public class SpectatorManager {
    private final LanguageManager lang;
    // Maps arena id -> set of spectator UUIDs
    private final Map<String, Set<UUID>> spectators = new HashMap<>();
    // Maps spectator UUID -> original game mode
    private final Map<UUID, GameMode> originalGameModes = new HashMap<>();
    // Maps spectator UUID -> original location
    private final Map<UUID, Location> originalLocations = new HashMap<>();
    // Chat mode: maps spectator UUID -> arena id (only spectators in same arena see chat)
    private final Map<UUID, String> chatIsolation = new HashMap<>();

    public SpectatorManager(LanguageManager lang) {
        this.lang = lang;
    }

    public boolean addSpectator(Player player, Arena arena) {
        String arenaId = arena.getId();
        Set<UUID> set = spectators.computeIfAbsent(arenaId, k -> new HashSet<>());

        if (set.size() >= arena.getMaxSpectators()) {
            player.sendMessage(lang.format("spectate-full", Map.of()));
            return false;
        }

        // Save original state
        originalGameModes.put(player.getUniqueId(), player.getGameMode());
        originalLocations.put(player.getUniqueId(), player.getLocation());

        // Set spectator mode
        player.setGameMode(GameMode.SPECTATOR);
        if (arena.getSpectatorSpawn() != null) {
            player.teleport(arena.getSpectatorSpawn());
        }

        set.add(player.getUniqueId());
        chatIsolation.put(player.getUniqueId(), arenaId);

        return true;
    }

    public void removeSpectator(Player player) {
        UUID uuid = player.getUniqueId();

        // Remove from arena set
        for (Set<UUID> set : spectators.values()) {
            set.remove(uuid);
        }

        chatIsolation.remove(uuid);

        // Restore original state
        GameMode gm = originalGameModes.remove(uuid);
        if (gm != null) {
            player.setGameMode(gm);
        }
        Location loc = originalLocations.remove(uuid);
        if (loc != null) {
            player.teleport(loc);
        }

        player.sendMessage(lang.format("spectate-leave", Map.of()));
    }

    public boolean isSpectating(Player player) {
        return chatIsolation.containsKey(player.getUniqueId());
    }

    public String getSpectatingArena(Player player) {
        return chatIsolation.get(player.getUniqueId());
    }

    public int getSpectatorCount(String arenaId) {
        Set<UUID> set = spectators.get(arenaId);
        return set == null ? 0 : set.size();
    }

    /**
     * Send a message to all spectators in the given arena.
     */
    public void broadcastToArenaSpectators(String arenaId, String message) {
        Set<UUID> set = spectators.get(arenaId);
        if (set == null) return;

        String prefix = lang.getPrefix() + lang.get("spectator-chat-prefix") + " ";
        for (UUID uuid : set) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(prefix + message);
            }
        }
    }

    /**
     * Send a message only to spectators in the same arena as the sender.
     */
    public void handleSpectatorChat(Player sender, String message) {
        String arenaId = chatIsolation.get(sender.getUniqueId());
        if (arenaId == null) return;

        String prefix = lang.getPrefix() + lang.get("spectator-chat-prefix") + " ";
        String formatted = prefix + "&f" + sender.getName() + "&7: &f" + message;
        formatted = org.bukkit.ChatColor.translateAlternateColorCodes('&', formatted);

        Set<UUID> set = spectators.get(arenaId);
        if (set != null) {
            for (UUID uuid : set) {
                Player p = org.bukkit.Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(formatted);
                }
            }
        }
    }

    /**
     * Remove all spectators from an arena (e.g., when game ends).
     */
    public void clearArena(String arenaId) {
        Set<UUID> set = spectators.remove(arenaId);
        if (set != null) {
            for (UUID uuid : new HashSet<>(set)) {
                Player p = org.bukkit.Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    removeSpectator(p);
                }
            }
        }
    }

    public void clearAll() {
        for (UUID uuid : new HashSet<>(chatIsolation.keySet())) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                removeSpectator(p);
            }
        }
        spectators.clear();
        originalGameModes.clear();
        originalLocations.clear();
        chatIsolation.clear();
    }
}
