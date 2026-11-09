export type GameStatus = 'READY' | 'PLAYING' | 'WON' | 'LOST'
export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'EXPERT'

export interface GameView {
  id: string
  rows: number
  cols: number
  mines: number
  minesLeft: number
  status: GameStatus
  elapsedSeconds: number
  /** One string per row; cell codes: H hidden, F flag, 0-8 number, M mine, X exploded, W wrong flag. */
  board: string[]
}

export type NewGame = { difficulty: Difficulty } | { rows: number; cols: number; mines: number }

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

async function request(path: string, init?: RequestInit): Promise<GameView> {
  const res = await fetch(`/api/games${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
  })
  if (!res.ok) {
    let message = res.statusText
    try {
      const problem = await res.json()
      message = problem.detail ?? message
    } catch {
      /* not JSON */
    }
    throw new ApiError(res.status, message)
  }
  return res.json()
}

const cell = (row: number, col: number) => JSON.stringify({ row, col })

export const api = {
  create: (game: NewGame) => request('', { method: 'POST', body: JSON.stringify(game) }),
  get: (id: string) => request(`/${id}`),
  reveal: (id: string, row: number, col: number) =>
    request(`/${id}/reveal`, { method: 'POST', body: cell(row, col) }),
  flag: (id: string, row: number, col: number) =>
    request(`/${id}/flag`, { method: 'POST', body: cell(row, col) }),
}
