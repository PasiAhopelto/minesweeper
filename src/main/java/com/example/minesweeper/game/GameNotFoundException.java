package com.example.minesweeper.game;

import java.util.UUID;

public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(UUID id) {
        super("Game " + id + " not found (it may have expired)");
    }
}
