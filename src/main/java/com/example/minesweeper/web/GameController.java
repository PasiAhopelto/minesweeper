package com.example.minesweeper.web;

import java.util.UUID;

import com.example.minesweeper.game.Difficulty;
import com.example.minesweeper.game.GameService;
import com.example.minesweeper.game.GameView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
public class GameController {

    /** Either a difficulty, or a custom size (rows, cols, mines). */
    public record NewGameRequest(Difficulty difficulty, Integer rows, Integer cols, Integer mines) {
    }

    public record CellRequest(@NotNull @Min(0) Integer row, @NotNull @Min(0) Integer col) {
    }

    private final GameService service;

    public GameController(GameService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameView create(@RequestBody(required = false) NewGameRequest request) {
        if (request == null || request.difficulty() != null) {
            return service.create(request == null ? Difficulty.BEGINNER : request.difficulty());
        }
        if (request.rows() == null || request.cols() == null || request.mines() == null) {
            throw new IllegalArgumentException("Provide either 'difficulty' or 'rows', 'cols' and 'mines'");
        }
        return service.create(request.rows(), request.cols(), request.mines());
    }

    @GetMapping("/{id}")
    public GameView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/reveal")
    public GameView reveal(@PathVariable UUID id, @Valid @RequestBody CellRequest cell) {
        return service.reveal(id, cell.row(), cell.col());
    }

    @PostMapping("/{id}/flag")
    public GameView flag(@PathVariable UUID id, @Valid @RequestBody CellRequest cell) {
        return service.toggleFlag(id, cell.row(), cell.col());
    }

    @PostMapping("/{id}/chord")
    public GameView chord(@PathVariable UUID id, @Valid @RequestBody CellRequest cell) {
        return service.chord(id, cell.row(), cell.col());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
