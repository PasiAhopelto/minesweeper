package com.example.minesweeper.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class GameTest {

    /** 5x5 board with mines at (0,0) and (4,4). */
    private static Game fixedGame() {
        Game game = new Game(5, 5, 2, new Random(1), Clock.systemUTC());
        boolean[][] layout = new boolean[5][5];
        layout[0][0] = true;
        layout[4][4] = true;
        game.placeMinesForTest(layout);
        return game;
    }

    @RepeatedTest(20)
    void firstClickIsAlwaysSafeAndOpensArea() {
        Game game = new Game(9, 9, 10);
        GameView view = game.reveal(4, 4);
        assertThat(view.status()).isIn(GameStatus.PLAYING, GameStatus.WON);
        assertThat(view.board().get(4).charAt(4)).isEqualTo('0');
    }

    @Test
    void firstClickSafeEvenOnNearlyFullBoard() {
        Game game = new Game(5, 5, 24);
        GameView view = game.reveal(2, 2);
        assertThat(view.status()).isEqualTo(GameStatus.WON);
        assertThat(view.board().get(2).charAt(2)).isEqualTo('8');
    }

    @Test
    void floodFillRevealsZeroRegion() {
        GameView view = fixedGame().reveal(2, 2);
        // Everything except the two mines is revealed -> won.
        assertThat(view.status()).isEqualTo(GameStatus.WON);
        assertThat(view.board()).containsExactly("F1000", "11000", "00000", "00011", "0001F");
        assertThat(view.minesLeft()).isZero();
    }

    @Test
    void hittingMineLosesAndExposesBoard() {
        Game game = fixedGame();
        game.toggleFlag(1, 1); // wrong flag
        GameView view = game.reveal(0, 0);
        assertThat(view.status()).isEqualTo(GameStatus.LOST);
        assertThat(view.board().get(0).charAt(0)).isEqualTo('X');
        assertThat(view.board().get(1).charAt(1)).isEqualTo('W');
        assertThat(view.board().get(4).charAt(4)).isEqualTo('M');
    }

    @Test
    void hiddenMinesAreNotLeakedWhilePlaying() {
        Game game = fixedGame();
        GameView view = game.reveal(0, 1);
        assertThat(view.status()).isEqualTo(GameStatus.PLAYING);
        assertThat(String.join("", view.board())).doesNotContain("M", "X");
    }

    @Test
    void flagsBlockRevealAndUpdateCounter() {
        Game game = fixedGame();
        GameView view = game.toggleFlag(0, 0);
        assertThat(view.minesLeft()).isEqualTo(1);
        view = game.reveal(0, 0);
        assertThat(view.status()).isEqualTo(GameStatus.PLAYING);
        assertThat(game.toggleFlag(0, 0).minesLeft()).isEqualTo(2);
    }

    @Test
    void chordRevealsNeighboursWhenFlagsMatch() {
        Game game = fixedGame();
        game.reveal(1, 1);
        game.toggleFlag(0, 0);
        GameView view = game.chord(1, 1);
        assertThat(view.board().get(0)).startsWith("F1");
        assertThat(view.board().get(2).charAt(2)).isEqualTo('0');
    }

    @Test
    void chordDoesNothingWithoutEnoughFlags() {
        Game game = fixedGame();
        game.reveal(1, 1);
        GameView view = game.chord(1, 1);
        assertThat(view.board().get(0).charAt(1)).isEqualTo('H');
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> new Game(2, 9, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Game(9, 9, 81)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Game(9, 9, 10).reveal(9, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
