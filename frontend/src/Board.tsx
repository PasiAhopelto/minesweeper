import { memo, useRef } from 'react'
import type { GameView } from './api'

interface Props {
  game: GameView
  flagMode: boolean
  onReveal: (row: number, col: number) => void
  onFlag: (row: number, col: number) => void
}

const LONG_PRESS_MS = 400

function label(code: string): string {
  switch (code) {
    case 'H': return 'hidden'
    case 'F': return 'flagged'
    case 'M': return 'mine'
    case 'X': return 'exploded mine'
    case 'W': return 'wrong flag'
    case '0': return 'empty'
    default: return `${code} adjacent mines`
  }
}

function content(code: string): string {
  switch (code) {
    case 'F': return '🚩'
    case 'M':
    case 'X': return '💣'
    case 'W': return '❌'
    case 'H':
    case '0': return ''
    default: return code
  }
}

export const Board = memo(function Board({ game, flagMode, onReveal, onFlag }: Props) {
  const pressTimer = useRef<number | undefined>(undefined)
  const longPressed = useRef(false)
  const over = game.status === 'WON' || game.status === 'LOST'

  const primary = (r: number, c: number) => {
    if (longPressed.current) {
      longPressed.current = false
      return
    }
    const code = game.board[r][c]
    if (flagMode && (code === 'H' || code === 'F')) onFlag(r, c)
    else onReveal(r, c)
  }

  const startPress = (e: React.PointerEvent, r: number, c: number) => {
    if (e.pointerType !== 'touch') return
    longPressed.current = false
    window.clearTimeout(pressTimer.current)
    pressTimer.current = window.setTimeout(() => {
      longPressed.current = true
      navigator.vibrate?.(30)
      onFlag(r, c)
    }, LONG_PRESS_MS)
  }
  const cancelPress = () => window.clearTimeout(pressTimer.current)

  return (
    <div
      className={`board ${over ? 'over' : ''}`}
      role="grid"
      aria-label="Minefield"
      style={{ ['--cols' as string]: game.cols }}
      onContextMenu={(e) => e.preventDefault()}
    >
      {game.board.map((row, r) => (
        <div className="row" role="row" key={r}>
          {[...row].map((code, c) => {
            const revealed = /[0-8MX]/.test(code)
            return (
              <button
                key={c}
                role="gridcell"
                className={`cell c-${code} ${revealed ? 'open' : ''}`}
                aria-label={`Row ${r + 1}, column ${c + 1}: ${label(code)}`}
                disabled={over}
                onClick={() => primary(r, c)}
                onContextMenu={(e) => {
                  e.preventDefault()
                  onFlag(r, c)
                }}
                onKeyDown={(e) => {
                  if (e.key === 'f' || e.key === 'F') {
                    e.preventDefault()
                    onFlag(r, c)
                  }
                }}
                onPointerDown={(e) => startPress(e, r, c)}
                onPointerUp={cancelPress}
                onPointerLeave={cancelPress}
                onPointerCancel={cancelPress}
              >
                {content(code)}
              </button>
            )
          })}
        </div>
      ))}
    </div>
  )
})
