# Minesweeper

A Minesweeper web app: Java / Spring Boot backend, React frontend, built with Maven into one runnable jar.

| Part     | Stack |
|----------|-------|
| Backend  | Java 25, Spring Boot 4.1.2 (Spring MVC, Bean Validation) |
| Frontend | React 19.3, TypeScript 7, Vite 8 |
| Build    | Maven 3.9, frontend-maven-plugin 2.0.2 (downloads Node 26.13.0 LTS locally) |

## Build and run

Requires JDK 25 and Maven. Node is **not** required; Maven downloads it into `frontend/node`.

```bash
mvn clean package
java -jar target/minesweeper-1.0.0-SNAPSHOT.jar
```

Open http://localhost:8080.

## Development

Run the backend and the Vite dev server (hot reload, proxies `/api` to port 8080) side by side:

```bash
mvn spring-boot:run -Dskip.frontend
```

```bash
cd frontend && npm install && npm run dev
```

Then open http://localhost:5173.

## How to play

- **Left click**: reveal a cell. The first click is always safe.
- **Right click**, **long press** (touch), or the **F** key: place or remove a flag.
- **Click a revealed number** when the right number of flags surround it: opens all its other neighbours (chord).
- **Flag mode** button: makes taps place flags (handy on phones).
- Click the face to start a new game. Your current game resumes after a page reload.

## Architecture

All game state is kept on the server. The client only ever gets a masked board, so mine
positions can't be read from the browser. Mines are placed on the first reveal.

```
src/main/java/com/example/minesweeper/
  game/Game.java          game rules: mine placement, flood fill, flags, chord, win/loss
  game/GameService.java   in-memory game store; evicts games idle longer than minesweeper.game-ttl
  web/GameController.java REST API
frontend/src/
  App.tsx                 difficulty picker, counters, timer
  Board.tsx               the grid and its mouse/touch/keyboard input
```

### REST API

| Method | Path                        | Body                                   |
|--------|-----------------------------|----------------------------------------|
| POST   | `/api/games`                | `{"difficulty":"BEGINNER"}` or `{"rows":20,"cols":24,"mines":80}` |
| GET    | `/api/games/{id}`           |                                        |
| POST   | `/api/games/{id}/reveal`    | `{"row":0,"col":0}`                    |
| POST   | `/api/games/{id}/flag`      | `{"row":0,"col":0}`                    |
| POST   | `/api/games/{id}/chord`     | `{"row":0,"col":0}`                    |
| DELETE | `/api/games/{id}`           |                                        |

Each call returns the game view. `board` holds one string per row, one character per cell:
`H` hidden, `F` flag, `0`–`8` revealed number. After a loss you also get `M` mine, `X` the mine that exploded, and `W` a wrong flag.

Custom boards: 5–30 rows, 5–50 columns, 1 to (cells − 1) mines.
