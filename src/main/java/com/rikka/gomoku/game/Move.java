package com.rikka.gomoku.game;

public record Move(int row, int col, int player) {
    /** player: 1 = White, 2 = Black */
}
