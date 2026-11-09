import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError, type Difficulty, type GameView, type NewGame } from './api'
import { Board } from './Board'

const STORAGE_KEY = 'minesweeper.gameId'
const PRESETS: { id: Difficulty; name: string; rows: number; cols: number; mines: number }[] = [
  { id: 'BEGINNER', name: 'Beginner', rows: 9, cols: 9, mines: 10 },
  { id: 'INTERMEDIATE', name: 'Intermediate', rows: 16, cols: 16, mines: 40 },
  { id: 'EXPERT', name: 'Expert', rows: 16, cols: 30, mines: 99 },
]

function save(id: string) {
  try { localStorage.setItem(STORAGE_KEY, id) } catch { /* storage unavailable */ }
}
function load(): string | null {
  try { return localStorage.getItem(STORAGE_KEY) } catch { return null }
}

function pad(n: number) {
  return String(Math.max(-99, Math.min(999, n))).padStart(3, '0')
}

function useTimer(game: GameView | null) {
  const [seconds, setSeconds] = useState(0)
  useEffect(() => {
    if (!game) return
    setSeconds(game.elapsedSeconds)
    if (game.status !== 'PLAYING') return
    const base = game.elapsedSeconds
    const start = Date.now()
    const t = window.setInterval(() => setSeconds(base + Math.floor((Date.now() - start) / 1000)), 250)
    return () => window.clearInterval(t)
  }, [game])
  return seconds
}

export default function App() {
  const [game, setGame] = useState<GameView | null>(null)
  const [settings, setSettings] = useState<NewGame>({ difficulty: 'BEGINNER' })
  const [custom, setCustom] = useState({ rows: 20, cols: 24, mines: 80 })
  const [showCustom, setShowCustom] = useState(false)
  const [flagMode, setFlagMode] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const busy = useRef(false)
  const seconds = useTimer(game)

  const newGame = useCallback(async (next: NewGame) => {
    try {
      setError(null)
      const g = await api.create(next)
      setSettings(next)
      setGame(g)
      save(g.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  // Resume the last game after a reload, otherwise start a fresh one.
  useEffect(() => {
    const id = load()
    const resume = id ? api.get(id) : Promise.reject(new ApiError(404, 'none'))
    resume
      .then((g) => {
        setGame(g)
        const preset = PRESETS.find((p) => p.rows === g.rows && p.cols === g.cols && p.mines === g.mines)
        setSettings(preset ? { difficulty: preset.id } : { rows: g.rows, cols: g.cols, mines: g.mines })
      })
      .catch(() => newGame({ difficulty: 'BEGINNER' }))
  }, [newGame])

  const act = useCallback(
    async (fn: () => Promise<GameView>) => {
      if (busy.current) return
      busy.current = true
      try {
        setGame(await fn())
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) {
          setError('That game expired, a new one was started.')
          await newGame(settings)
        } else {
          setError(e instanceof Error ? e.message : String(e))
        }
      } finally {
        busy.current = false
      }
    },
    [newGame, settings],
  )

  const onReveal = useCallback(
    (r: number, c: number) => game && act(() => api.reveal(game.id, r, c)),
    [game, act],
  )
  const onFlag = useCallback(
    (r: number, c: number) => game && act(() => api.flag(game.id, r, c)),
    [game, act],
  )

  const face = !game ? '🙂' : game.status === 'WON' ? '😎' : game.status === 'LOST' ? '😵' : '🙂'
  const activePreset = 'difficulty' in settings ? settings.difficulty : null

  return (
    <main className="app">
      <header>
        <h1>Minesweeper</h1>
        <nav className="levels" aria-label="Difficulty">
          {PRESETS.map((p) => (
            <button
              key={p.id}
              className={activePreset === p.id && !showCustom ? 'active' : ''}
              onClick={() => {
                setShowCustom(false)
                newGame({ difficulty: p.id })
              }}
            >
              <span>{p.name}</span>
              <small>{p.rows}×{p.cols} · {p.mines}</small>
            </button>
          ))}
          <button
            className={showCustom || (!activePreset && game) ? 'active' : ''}
            onClick={() => setShowCustom((s) => !s)}
          >
            <span>Custom</span>
            <small>your size</small>
          </button>
        </nav>
        {showCustom && (
          <form
            className="custom"
            onSubmit={(e) => {
              e.preventDefault()
              newGame(custom)
            }}
          >
            {(['rows', 'cols', 'mines'] as const).map((k) => (
              <label key={k}>
                {k}
                <input
                  type="number"
                  min={k === 'mines' ? 1 : 5}
                  max={k === 'rows' ? 30 : k === 'cols' ? 50 : custom.rows * custom.cols - 1}
                  value={custom[k]}
                  onChange={(e) => setCustom({ ...custom, [k]: Number(e.target.value) })}
                />
              </label>
            ))}
            <button type="submit">Start</button>
          </form>
        )}
      </header>

      {error && (
        <p className="error" role="alert" onClick={() => setError(null)}>
          {error}
        </p>
      )}

      {game && (
        <section className="game">
          <div className="status-bar">
            <output className="counter" aria-label="Mines left">{pad(game.minesLeft)}</output>
            <button className="face" aria-label="New game" onClick={() => newGame(settings)}>
              {face}
            </button>
            <output className="counter" aria-label="Seconds elapsed">{pad(seconds)}</output>
          </div>

          <div className="board-scroll">
            <Board game={game} flagMode={flagMode} onReveal={onReveal} onFlag={onFlag} />
          </div>

          <div className="toolbar">
            <button
              className={`flag-toggle ${flagMode ? 'active' : ''}`}
              aria-pressed={flagMode}
              onClick={() => setFlagMode((f) => !f)}
            >
              🚩 Flag mode {flagMode ? 'on' : 'off'}
            </button>
          </div>

          <p className={`result ${game.status.toLowerCase()}`} aria-live="polite">
            {game.status === 'WON' && `You cleared the field in ${seconds}s!`}
            {game.status === 'LOST' && 'Boom. Click the face to try again.'}
          </p>
        </section>
      )}

      <footer>
        Left click: reveal · Right click / long press / F key: flag · Click a number: open its neighbours
      </footer>
    </main>
  )
}
