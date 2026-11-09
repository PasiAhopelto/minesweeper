package com.example.minesweeper.game;

public enum GameStatus {
    /** Board created, no cell revealed yet (mines not placed). */
    READY,
    PLAYING,
    WON,
    LOST;

    public boolean isOver() {
        return this == WON || this == LOST;
    }
}
