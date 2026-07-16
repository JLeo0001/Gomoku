package com.rikka.gomoku.game;

public enum GameState {
    WAITING,      // Waiting for players to join
    COUNTDOWN,    // Lobby countdown in progress
    PLAYING,      // Game is active
    ENDING,       // Game ended, announcing results
    CLEANING      // Cleaning up
}
