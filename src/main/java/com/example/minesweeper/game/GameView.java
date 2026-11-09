package com.example.minesweeper.game;

import java.util.List;
import java.util.UUID;

/**
 * What the client sees of a game. {@code board} holds one string per row, one character
 * per cell (see {@code Game#cellCode} for the codes).
 */
public record GameView(
        UUID id,
        int rows,
        int cols,
        int mines,
        int minesLeft,
        GameStatus status,
        long elapsedSeconds,
        List<String> board) {
}
