package com.example.minesweeper.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * A single Minesweeper game. All state lives on the server; clients only ever see
 * the masked board produced by {@link #view()}, so mine positions cannot be peeked.
 * <p>
 * Mines are placed lazily on the first reveal so the first click is always safe
 * (and opens an area whenever the board has room for it).
 */
public final class Game {

    public static final int MIN_SIZE = 5;
    public static final int MAX_ROWS = 30;
    public static final int MAX_COLS = 50;

    private final UUID id = UUID.randomUUID();
    private final int rows;
    private final int cols;
    private final int mineCount;
    private final Random random;
    private final Clock clock;

    private final boolean[][] mines;
    private final boolean[][] revealed;
    private final boolean[][] flagged;
    private final int[][] adjacent;

    private GameStatus status = GameStatus.READY;
    private int revealedCount;
    private int flagCount;
    private int explodedRow = -1;
    private int explodedCol = -1;
    private Instant startedAt;
    private Instant finishedAt;
    private volatile Instant lastAccess;

    public Game(int rows, int cols, int mineCount) {
        this(rows, cols, mineCount, new Random(), Clock.systemUTC());
    }

    public Game(int rows, int cols, int mineCount, Random random, Clock clock) {
        if (rows < MIN_SIZE || rows > MAX_ROWS) {
            throw new IllegalArgumentException("rows must be between %d and %d".formatted(MIN_SIZE, MAX_ROWS));
        }
        if (cols < MIN_SIZE || cols > MAX_COLS) {
            throw new IllegalArgumentException("cols must be between %d and %d".formatted(MIN_SIZE, MAX_COLS));
        }
        if (mineCount < 1 || mineCount > rows * cols - 1) {
            throw new IllegalArgumentException("mines must be between 1 and %d".formatted(rows * cols - 1));
        }
        this.rows = rows;
        this.cols = cols;
        this.mineCount = mineCount;
        this.random = random;
        this.clock = clock;
        this.mines = new boolean[rows][cols];
        this.revealed = new boolean[rows][cols];
        this.flagged = new boolean[rows][cols];
        this.adjacent = new int[rows][cols];
        this.lastAccess = clock.instant();
    }

    public static Game of(Difficulty difficulty) {
        return new Game(difficulty.rows(), difficulty.cols(), difficulty.mines());
    }

    public UUID id() { return id; }

    public Instant lastAccess() { return lastAccess; }

    // ------------------------------------------------------------------ actions

    /** Reveals a cell. Revealing an already revealed number acts as a chord. */
    public synchronized GameView reveal(int row, int col) {
        checkBounds(row, col);
        touch();
        if (status.isOver() || flagged[row][col]) {
            return view();
        }
        if (revealed[row][col]) {
            return chord(row, col);
        }
        if (status == GameStatus.READY) {
            placeMines(row, col);
            status = GameStatus.PLAYING;
            startedAt = clock.instant();
        }
        open(row, col);
        checkWin();
        return view();
    }

    /** Toggles a flag on a hidden cell. */
    public synchronized GameView toggleFlag(int row, int col) {
        checkBounds(row, col);
        touch();
        if (!status.isOver() && !revealed[row][col]) {
            flagged[row][col] = !flagged[row][col];
            flagCount += flagged[row][col] ? 1 : -1;
        }
        return view();
    }

    /**
     * Chord: on a revealed number whose adjacent flag count matches the number,
     * reveal all other hidden neighbours.
     */
    public synchronized GameView chord(int row, int col) {
        checkBounds(row, col);
        touch();
        if (status != GameStatus.PLAYING || !revealed[row][col] || adjacent[row][col] == 0) {
            return view();
        }
        int flags = 0;
        for (int[] n : neighbours(row, col)) {
            if (flagged[n[0]][n[1]]) flags++;
        }
        if (flags != adjacent[row][col]) {
            return view();
        }
        for (int[] n : neighbours(row, col)) {
            int r = n[0], c = n[1];
            if (!revealed[r][c] && !flagged[r][c] && status == GameStatus.PLAYING) {
                open(r, c);
            }
        }
        checkWin();
        return view();
    }

    public synchronized GameView view() {
        touch();
        List<String> board = new ArrayList<>(rows);
        boolean lost = status == GameStatus.LOST;
        for (int r = 0; r < rows; r++) {
            StringBuilder sb = new StringBuilder(cols);
            for (int c = 0; c < cols; c++) {
                sb.append(cellCode(r, c, lost));
            }
            board.add(sb.toString());
        }
        return new GameView(id, rows, cols, mineCount, mineCount - flagCount, status,
                elapsedSeconds(), board);
    }

    // ------------------------------------------------------------------ internals

    /**
     * Cell codes sent to the client:
     * {@code H} hidden, {@code F} flag, {@code 0}-{@code 8} revealed number,
     * and only once the game is lost: {@code M} mine, {@code X} exploded mine, {@code W} wrong flag.
     */
    private char cellCode(int r, int c, boolean lost) {
        if (revealed[r][c]) {
            if (mines[r][c]) return (r == explodedRow && c == explodedCol) ? 'X' : 'M';
            return (char) ('0' + adjacent[r][c]);
        }
        if (flagged[r][c]) {
            return (lost && !mines[r][c]) ? 'W' : 'F';
        }
        if (lost && mines[r][c]) return 'M';
        return 'H';
    }

    private void placeMines(int safeRow, int safeCol) {
        // Keep the 3x3 area around the first click free when there is room for it,
        // otherwise just the clicked cell.
        boolean protectArea = rows * cols - mineCount >= 9;
        List<int[]> candidates = new ArrayList<>(rows * cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                boolean safe = protectArea
                        ? Math.abs(r - safeRow) <= 1 && Math.abs(c - safeCol) <= 1
                        : r == safeRow && c == safeCol;
                if (!safe) candidates.add(new int[]{r, c});
            }
        }
        // Partial Fisher-Yates shuffle: pick mineCount distinct cells.
        for (int i = 0; i < mineCount; i++) {
            int j = i + random.nextInt(candidates.size() - i);
            int[] tmp = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, tmp);
            int[] cell = candidates.get(i);
            mines[cell[0]][cell[1]] = true;
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int count = 0;
                for (int[] n : neighbours(r, c)) {
                    if (mines[n[0]][n[1]]) count++;
                }
                adjacent[r][c] = count;
            }
        }
    }

    /** Opens a cell; flood-fills through zero cells. */
    private void open(int row, int col) {
        if (mines[row][col]) {
            revealed[row][col] = true;
            explodedRow = row;
            explodedCol = col;
            finish(GameStatus.LOST);
            return;
        }
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{row, col});
        while (!stack.isEmpty()) {
            int[] cell = stack.pop();
            int r = cell[0], c = cell[1];
            if (revealed[r][c] || flagged[r][c]) continue;
            revealed[r][c] = true;
            revealedCount++;
            if (adjacent[r][c] == 0) {
                for (int[] n : neighbours(r, c)) {
                    if (!revealed[n[0]][n[1]]) stack.push(n);
                }
            }
        }
    }

    private void checkWin() {
        if (status == GameStatus.PLAYING && revealedCount == rows * cols - mineCount) {
            // Flag every remaining mine for a tidy final board.
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (mines[r][c]) flagged[r][c] = true;
                }
            }
            flagCount = mineCount;
            finish(GameStatus.WON);
        }
    }

    private void finish(GameStatus result) {
        status = result;
        finishedAt = clock.instant();
    }

    private long elapsedSeconds() {
        if (startedAt == null) return 0;
        Instant end = finishedAt != null ? finishedAt : clock.instant();
        return Duration.between(startedAt, end).toSeconds();
    }

    private List<int[]> neighbours(int row, int col) {
        List<int[]> result = new ArrayList<>(8);
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr, c = col + dc;
                if (r >= 0 && r < rows && c >= 0 && c < cols) result.add(new int[]{r, c});
            }
        }
        return result;
    }

    private void checkBounds(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new IllegalArgumentException("cell (%d, %d) is outside the board".formatted(row, col));
        }
    }

    private void touch() {
        lastAccess = clock.instant();
    }

    // Test hook: lets unit tests set up a deterministic board.
    synchronized void placeMinesForTest(boolean[][] layout) {
        int count = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                mines[r][c] = layout[r][c];
                if (layout[r][c]) count++;
            }
        }
        if (count != mineCount) throw new IllegalArgumentException("layout has " + count + " mines");
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int n = 0;
                for (int[] nb : neighbours(r, c)) if (mines[nb[0]][nb[1]]) n++;
                adjacent[r][c] = n;
            }
        }
        status = GameStatus.PLAYING;
        startedAt = clock.instant();
    }
}
