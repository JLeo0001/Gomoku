package com.rikka.gomoku.sound;

import com.rikka.gomoku.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Plays game sound effects to players.
 * All sound names are read from config and resolved via {@link Sound#valueOf(String)}.
 * If a sound name is invalid or sounds are disabled in config, playback is silently skipped.
 */
public class SoundManager {

    private final ConfigManager config;

    public SoundManager(ConfigManager config) {
        this.config = config;
    }

    /** Play the countdown tick sound to a player. */
    public void playCountdownTick(UUID playerId) {
        playTo(playerId, config.getCountdownSound(), 0.6f, 1.4f);
    }

    /** Play the piece-place sound to everyone in the game. */
    public void playPiecePlace(UUID whiteId, UUID blackId) {
        playTo(whiteId, config.getPiecePlaceSound(), 0.8f, 1.0f);
        playTo(blackId, config.getPiecePlaceSound(), 0.8f, 1.0f);
    }

    /** Play the victory sound to the winner. */
    public void playWin(UUID winnerId) {
        playTo(winnerId, config.getWinSound(), 1.0f, 1.0f);
    }

    /** Play the defeat sound to the loser. */
    public void playLose(UUID loserId) {
        playTo(loserId, config.getLoseSound(), 1.0f, 0.8f);
    }

    /** Play a draw sound to both players. */
    public void playDraw(UUID whiteId, UUID blackId) {
        playTo(whiteId, config.getLoseSound(), 0.8f, 0.9f);
        playTo(blackId, config.getLoseSound(), 0.8f, 0.9f);
    }

    // ── internal ────────────────────────────────────────────────

    private void playTo(UUID playerId, String soundName, float volume, float pitch) {
        if (!config.isSoundsEnabled()) return;
        if (playerId == null) return; // AI slot
        if (soundName == null || soundName.isEmpty()) return;

        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            // Unknown / version-mismatched sound — skip
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
