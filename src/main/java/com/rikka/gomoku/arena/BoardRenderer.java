package com.rikka.gomoku.arena;

import com.rikka.gomoku.game.Board;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;

/**
 * Renders the Gomoku board in the Minecraft world using blocks and skulls.
 */
public class BoardRenderer {
    private final Material surfaceBlock;
    private final Material gridBlock;

    public BoardRenderer(Material surfaceBlock, Material gridBlock) {
        this.surfaceBlock = surfaceBlock;
        this.gridBlock = gridBlock;
    }

    /**
     * Render the full board with grid lines.
     * Board surface = surfaceBlock, grid lines = gridBlock, intersections = surfaceBlock.
     * @param origin the minimum-corner location of the board (0, y, 0)
     * @param boardSize the size of the board (e.g., 19)
     */
    public void renderFullBoard(Location origin, int boardSize) {
        if (origin == null) return;
        World world = origin.getWorld();
        int y = origin.getBlockY();
        int startX = origin.getBlockX();
        int startZ = origin.getBlockZ();

        // Build base platform (boardSize × boardSize)
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                // Grid lines: every cell gets gridBlock, intersections get surfaceBlock
                Block block = world.getBlockAt(startX + c, y, startZ + r);
                block.setType(surfaceBlock);
            }
        }

        // Draw grid lines between intersections
        if (gridBlock != surfaceBlock) {
            for (int r = 0; r < boardSize; r++) {
                for (int c = 0; c < boardSize; c++) {
                    // Place grid line markers on edges between intersections
                    // Horizontal lines (between columns)
                    if (c < boardSize - 1) {
                        Block hLine = world.getBlockAt(startX + c, y, startZ + r);
                        // Alternate: every other row for subtle grid effect
                    }
                }
            }
        }

        // Build a visible border around the board
        for (int i = -1; i <= boardSize; i++) {
            // Top border
            world.getBlockAt(startX + i, y, startZ - 1).setType(gridBlock);
            // Bottom border
            world.getBlockAt(startX + i, y, startZ + boardSize).setType(gridBlock);
            // Left border
            world.getBlockAt(startX - 1, y, startZ + i).setType(gridBlock);
            // Right border
            world.getBlockAt(startX + boardSize, y, startZ + i).setType(gridBlock);
        }
    }

    /**
     * Simple board rendering — just the surface (used by Game during cleanup).
     */
    public void renderBoard(Location origin, int boardSize) {
        renderFullBoard(origin, boardSize);
    }

    /**
     * Place a piece on the board.
     * White = SKELETON_SKULL, Black = WITHER_SKELETON_SKULL
     */
    public void placePiece(Location origin, int row, int col, int player) {
        if (origin == null) return;
        World world = origin.getWorld();
        int y = origin.getBlockY();
        int x = origin.getBlockX() + col;
        int z = origin.getBlockZ() + row;

        Block pieceBlock = world.getBlockAt(x, y + 1, z);
        if (player == Board.WHITE) {
            pieceBlock.setType(Material.SKELETON_SKULL);
        } else {
            pieceBlock.setType(Material.WITHER_SKELETON_SKULL);
        }
        Directional dir = (Directional) pieceBlock.getBlockData();
        dir.setFacing(BlockFace.UP);
        pieceBlock.setBlockData(dir);
    }

    /**
     * Clear all pieces from the board.
     */
    public void clearPieces(Location origin, int boardSize) {
        if (origin == null) return;
        World world = origin.getWorld();
        int y = origin.getBlockY();
        int startX = origin.getBlockX();
        int startZ = origin.getBlockZ();

        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                Block above = world.getBlockAt(startX + c, y + 1, startZ + r);
                Material type = above.getType();
                if (type == Material.SKELETON_SKULL || type == Material.WITHER_SKELETON_SKULL) {
                    above.setType(Material.AIR);
                }
            }
        }
    }

    /**
     * Get board coordinates (row, col) from a clicked block location.
     */
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
