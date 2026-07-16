package com.rikka.gomoku.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Stores a player's pre-game state so it can be restored after the game.
 */
public class PlayerGameData {
    private final UUID playerId;
    private final Location returnLocation;
    private final GameMode gameMode;
    private final ItemStack[] inventoryContents;
    private final ItemStack[] enderChestContents;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final int xpLevel;
    private final float xpProgress;

    public PlayerGameData(UUID playerId, Location returnLocation, GameMode gameMode,
                          ItemStack[] inventoryContents, ItemStack[] enderChestContents,
                          double health, int foodLevel, float saturation,
                          int xpLevel, float xpProgress) {
        this.playerId = playerId;
        this.returnLocation = returnLocation;
        this.gameMode = gameMode;
        this.inventoryContents = inventoryContents;
        this.enderChestContents = enderChestContents;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.xpLevel = xpLevel;
        this.xpProgress = xpProgress;
    }

    public UUID getPlayerId() { return playerId; }
    public Location getReturnLocation() { return returnLocation; }
    public GameMode getGameMode() { return gameMode; }
    public ItemStack[] getInventoryContents() { return inventoryContents; }
    public ItemStack[] getEnderChestContents() { return enderChestContents; }
    public double getHealth() { return health; }
    public int getFoodLevel() { return foodLevel; }
    public float getSaturation() { return saturation; }
    public int getXpLevel() { return xpLevel; }
    public float getXpProgress() { return xpProgress; }
}
