package com.rikka.gomoku.game;

public class Board {
    public static final int EMPTY = 0;
    public static final int WHITE = 1;
    public static final int BLACK = 2;

    private final int size;
    private final int[][] grid;
    private int lastRow = -1;
    private int lastCol = -1;

    public Board(int size) {
        this.size = size;
        this.grid = new int[size][size];
    }

    public int getSize() { return size; }

    public int get(int row, int col) {
        if (row < 0 || row >= size || col < 0 || col >= size) return -1;
        return grid[row][col];
    }

    /**
     * Place a piece. Returns true if the move was valid.
     */
    public boolean place(int row, int col, int player) {
        if (row < 0 || row >= size || col < 0 || col >= size) return false;
        if (grid[row][col] != EMPTY) return false;
        grid[row][col] = player;
        lastRow = row;
        lastCol = col;
        return true;
    }

    /**
     * Remove a piece (for undo during AI search).
     */
    public void undo(int row, int col) {
        if (row >= 0 && row < size && col >= 0 && col < size) {
            grid[row][col] = EMPTY;
        }
    }

    /**
     * Check if placing at (row, col) results in a win for the given player.
     * Only checks around the most recently placed piece.
     */
    public boolean checkWin(int row, int col, int player) {
        if (grid[row][col] != player) return false;

        // Directions: horizontal, vertical, diagonal-down-right, diagonal-down-left
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

        for (int[] dir : dirs) {
            int count = 1;
            // Positive direction
            for (int i = 1; i < 5; i++) {
                int r = row + dir[0] * i;
                int c = col + dir[1] * i;
                if (r >= 0 && r < size && c >= 0 && c < size && grid[r][c] == player) count++;
                else break;
            }
            // Negative direction
            for (int i = 1; i < 5; i++) {
                int r = row - dir[0] * i;
                int c = col - dir[1] * i;
                if (r >= 0 && r < size && c >= 0 && c < size && grid[r][c] == player) count++;
                else break;
            }
            if (count >= 5) return true;
        }
        return false;
    }

    public int getLastRow() { return lastRow; }
    public int getLastCol() { return lastCol; }

    public boolean isFull() {
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (grid[r][c] == EMPTY) return false;
        return true;
    }

    public void clear() {
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                grid[r][c] = EMPTY;
        lastRow = -1;
        lastCol = -1;
    }

    /**
     * Deep copy for AI search.
     */
    public Board copy() {
        Board b = new Board(size);
        for (int r = 0; r < size; r++)
            System.arraycopy(this.grid[r], 0, b.grid[r], 0, size);
        b.lastRow = this.lastRow;
        b.lastCol = this.lastCol;
        return b;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                sb.append(grid[r][c] == EMPTY ? "." : (grid[r][c] == WHITE ? "O" : "X"));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
