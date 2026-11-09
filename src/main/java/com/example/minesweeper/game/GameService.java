package com.example.minesweeper.game;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Keeps games in memory and evicts the ones nobody has touched for a while. */
@Service
public class GameService {

    private final Map<UUID, Game> games = new ConcurrentHashMap<>();
    private final Duration ttl;

    public GameService(@Value("${minesweeper.game-ttl:PT2H}") Duration ttl) {
        this.ttl = ttl;
    }

    public GameView create(Difficulty difficulty) {
        return register(Game.of(difficulty));
    }

    public GameView create(int rows, int cols, int mines) {
        return register(new Game(rows, cols, mines));
    }

    public GameView get(UUID id) { return find(id).view(); }

    public GameView reveal(UUID id, int row, int col) { return find(id).reveal(row, col); }

    public GameView toggleFlag(UUID id, int row, int col) { return find(id).toggleFlag(row, col); }

    public GameView chord(UUID id, int row, int col) { return find(id).chord(row, col); }

    public void delete(UUID id) { games.remove(id); }

    @Scheduled(fixedDelayString = "PT5M")
    void evictIdleGames() {
        Instant cutoff = Instant.now().minus(ttl);
        games.values().removeIf(g -> g.lastAccess().isBefore(cutoff));
    }

    int size() { return games.size(); }

    private GameView register(Game game) {
        games.put(game.id(), game);
        return game.view();
    }

    private Game find(UUID id) {
        Game game = games.get(id);
        if (game == null) throw new GameNotFoundException(id);
        return game;
    }
}
