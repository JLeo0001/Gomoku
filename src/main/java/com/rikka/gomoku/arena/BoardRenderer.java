package com.rikka.gomoku.arena;

import com.rikka.gomoku.game.Board;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Renders the Gomoku board and all arena structures in the Minecraft world.
 */
public class BoardRenderer {
    private final Material surfaceBlock;
    private final Material gridBlock;

    // Structure blocks
    private static final Material LOBBY_FLOOR = Material.STONE_BRICKS;
    private static final Material SPAWN_PAD = Material.SMOOTH_STONE_SLAB;

    public BoardRenderer(Material surfaceBlock, Material gridBlock) {
        this.surfaceBlock = surfaceBlock;
        this.gridBlock = gridBlock;
    }

    // ═══════════════════════════════════════════════════════════════
    // Full arena build
    // ═══════════════════════════════════════════════════════════════

    /**
     * Clear all arena structures and pieces for regeneration.
     */
    public void clearAll(World world, int boardSize, int yLevel) {
        int margin = 8;

        // Clear board surface + pieces
        for (int x = -margin; x <= boardSize + margin; x++) {
            for (int z = -margin; z <= boardSize + margin; z++) {
                world.getBlockAt(x, yLevel, z).setType(Material.AIR);
                world.getBlockAt(x, yLevel + 1, z).setType(Material.AIR);
            }
        }
    }

    /**
     * Build everything: board + lobby platform + player platforms.
     */
    public void renderFullArena(int boardSize, int yLevel, World world) {
        Location origin = new Location(world, 0, yLevel, 0);
        renderFullBoard(origin, boardSize);
        renderLobby(boardSize, yLevel, world);
        renderPlayerPlatforms(boardSize, yLevel, world);
    }

    /**
     * Clear only pieces (skulls), keep structures intact.
     */
    public void clearPiecesOnly(Location origin, int boardSize) {
        clearPieces(origin, boardSize);
    }

    // ═══════════════════════════════════════════════════════════════
    // Board
    // ═══════════════════════════════════════════════════════════════

    public void renderFullBoard(Location origin, int boardSize) {
        if (origin == null) return;
        World world = origin.getWorld();
        int y = origin.getBlockY();
        int sx = origin.getBlockX();
        int sz = origin.getBlockZ();

        // Surface
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                world.getBlockAt(sx + c, y, sz + r).setType(surfaceBlock);
            }
        }

        // Border
        for (int i = -1; i <= boardSize; i++) {
            world.getBlockAt(sx + i, y, sz - 1).setType(gridBlock);
            world.getBlockAt(sx + i, y, sz + boardSize).setType(gridBlock);
            world.getBlockAt(sx - 1, y, sz + i).setType(gridBlock);
            world.getBlockAt(sx + boardSize, y, sz + i).setType(gridBlock);
        }
    }

    public void renderBoard(Location origin, int boardSize) {
        renderFullBoard(origin, boardSize);
    }

    // ═══════════════════════════════════════════════════════════════
    // Lobby / waiting area (south of board)
    // ═══════════════════════════════════════════════════════════════

    private void renderLobby(int boardSize, int y, World world) {
        int half = boardSize / 2;

        // 5×3 platform centered behind the board (north side)
        for (int x = -2; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                world.getBlockAt(half + x, y, boardSize + 4 + z).setType(LOBBY_FLOOR);
            }
        }

        // Beacon marker at center (sits flush on the platform)
        world.getBlockAt(half, y, boardSize + 5).setType(Material.BEACON);
    }

    // ═══════════════════════════════════════════════════════════════
    // Player spawn platforms (west & east of board)
    // ═══════════════════════════════════════════════════════════════

    private void renderPlayerPlatforms(int boardSize, int y, World world) {
        int half = boardSize / 2;

        // White player platform (west side)
        int wx = -4;
        for (int z = -1; z <= 1; z++) {
            world.getBlockAt(wx, y, half + z).setType(SPAWN_PAD);
        }

        // Black player platform (east side)
        int bx = boardSize + 3;
        for (int z = -1; z <= 1; z++) {
            world.getBlockAt(bx, y, half + z).setType(SPAWN_PAD);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Pieces
    // ═══════════════════════════════════════════════════════════════

    public void placePiece(Location origin, int row, int col, int player) {
        if (origin == null) return;
        World world = origin.getWorld();
        int y = origin.getBlockY();
        int x = origin.getBlockX() + col;
        int z = origin.getBlockZ() + row;

        Block pieceBlock = world.getBlockAt(x, y + 1, z);
        Material skullType = (player == Board.WHITE) ? Material.SKELETON_SKULL : Material.WITHER_SKELETON_SKULL;
        pieceBlock.setType(skullType);
        // Floor-standing skulls use Rotatable in 1.21+, not Directional.
        // Default rotation (0 = south) is fine for pieces.
    }

    public void clearPieces(Location origin, int boardSize) {
        if (origin == null) return;
        World world = origin.getWorld();
        int y = origin.getBlockY();
        int sx = origin.getBlockX();
        int sz = origin.getBlockZ();

        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                Block above = world.getBlockAt(sx + c, y + 1, sz + r);
                Material type = above.getType();
                if (type == Material.SKELETON_SKULL || type == Material.WITHER_SKELETON_SKULL) {
                    above.setType(Material.AIR);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Interaction
    // ═══════════════════════════════════════════════════════════════

    public int[] getBoardCoords(Location origin, int boardSize, Location clicked) {
        if (origin == null || clicked == null) return null;
        if (!clicked.getWorld().equals(origin.getWorld())) return null;

        int blockY = clicked.getBlockY();
        int originY = origin.getBlockY();

        if (blockY != originY && blockY != originY + 1) return null;

        int col = clicked.getBlockX() - origin.getBlockX();
        int row = clicked.getBlockZ() - origin.getBlockZ();

        if (row < 0 || row >= boardSize || col < 0 || col >= boardSize) return null;
        return new int[]{row, col};
    }
}
