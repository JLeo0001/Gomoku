package com.rikka.gomoku.arena;

import com.rikka.gomoku.game.Board;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
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
     * Render the initial empty board.
     * @param origin the minimum-corner location of the board
     * @param boardSize the size of the board (e.g., 19)
     */
    public void renderBoard(Location origin, int boardSize) {
        World world = origin.getWorld();
        int y = origin.getBlockY();
        int startX = origin.getBlockX();
        int startZ = origin.getBlockZ();

        // Place surface blocks
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                Block block = world.getBlockAt(startX + c, y, startZ + r);
                if (r == 0 || r == boardSize - 1 || c == 0 || c == boardSize - 1) {
                    block.setType(gridBlock);
                } else {
                    block.setType(surfaceBlock);
                }
            }
        }
    }

    /**
     * Place a piece on the board.
     * @param origin board origin
     * @param row board row
     * @param col board col
     * @param player 1 = White (skeleton skull), 2 = Black (wither skeleton skull)
     */
    public void placePiece(Location origin, int row, int col, int player) {
        World world = origin.getWorld();
        int y = origin.getBlockY();
        int x = origin.getBlockX() + col;
        int z = origin.getBlockZ() + row;

        // Place skull on top of the board block
        Block pieceBlock = world.getBlockAt(x, y + 1, z);
        if (player == Board.WHITE) {
            pieceBlock.setType(Material.SKELETON_SKULL);
            // Set rotation to face up
            Directional dir = (Directional) pieceBlock.getBlockData();
            dir.setFacing(BlockFace.UP);
            pieceBlock.setBlockData(dir);
        } else {
            pieceBlock.setType(Material.WITHER_SKELETON_SKULL);
            Directional dir = (Directional) pieceBlock.getBlockData();
            dir.setFacing(BlockFace.UP);
            pieceBlock.setBlockData(dir);
        }
    }

    /**
     * Clear all pieces from the board (remove skull blocks above the board).
     */
    public void clearPieces(Location origin, int boardSize) {
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
     * Returns null if the click is not on a valid board position.
     */
    public int[] getBoardCoords(Location origin, int boardSize, Location clicked) {
        if (!clicked.getWorld().equals(origin.getWorld())) return null;

        int blockY = clicked.getBlockY();
        int originY = origin.getBlockY();

        // Accept clicks on the board surface or one block above (piece area)
        if (blockY != originY && blockY != originY + 1) return null;

        int col = clicked.getBlockX() - origin.getBlockX();
        int row = clicked.getBlockZ() - origin.getBlockZ();

        if (row < 0 || row >= boardSize || col < 0 || col >= boardSize) return null;
        return new int[]{row, col};
    }
}
