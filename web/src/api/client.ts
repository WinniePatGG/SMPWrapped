const API_BASE = '/api/v1'

async function fetchApi<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: 'Request failed' }))
    throw new Error(error.error || `HTTP ${response.status}`)
  }

  return response.json()
}

export interface Season {
  id: string
  name: string
  startTime: number
  endTime: number
  state: 'ACTIVE' | 'ENDED' | 'ARCHIVED' | 'DRAFT'
  active: boolean
  current?: boolean
}

export interface Player {
  uuid: string
  name: string
  firstSeen: number
  lastSeen: number
}

export interface PlayerStats {
  playerId: string
  seasonId: string
  playtimeSeconds: number
  blocksMined: number
  blocksPlaced: number
  distanceTraveled: number
  mobsKilled: number
  deaths: number
  itemsCrafted: number
  fishingCatches: number
  loginSessions: number
  uniqueDaysPlayed: number
}

export interface WrappedStats {
  playtimeSeconds?: number
  playtimeHours?: number
  blocksMined?: number
  blocksPlaced?: number
  distanceTraveled?: number
  mobsKilled?: number
  deaths?: number
  damageDealt?: number
  damageReceived?: number
  itemsCrafted?: number
  itemsCollected?: number
  fishingCatches?: number
  loginSessions?: number
  uniqueDaysPlayed?: number
  topMinedBlock?: string
  topMinedBlockCount?: number
  topMob?: string
  topMobCount?: number
  topDimension?: string
  topDay?: string
  topHour?: number
  [key: string]: any
}

export interface WrappedSnapshot {
  playerId: string
  seasonId: string
  playerName: string
  generatedAt: number
  archetype: string
  archetypeDescription: string
  stats: WrappedStats
  achievements: string[]
  percentiles: Record<string, number>
  milestones: string[]
}

export interface LeaderboardEntry {
  rank: number
  playerId: string
  playerName?: string
  value: number
}

export interface ServerStats {
  seasonId: string
  stats: Record<string, number>
}

export function pickSeason(seasons: Season[] | undefined): Season | undefined {
  if (!seasons || seasons.length === 0) return undefined
  return seasons.find((s) => s.current) ?? seasons.find((s) => s.active) ?? seasons[0]
}

export const api = {
  
  health: () => fetchApi<{ status: string; version: string }>('/health'),

  
  getSeasons: () => fetchApi<{ seasons: Season[] }>('/seasons'),
  getCurrentSeason: () => fetchApi<Season>('/seasons/current'),
  getSeasonStats: (seasonId: string) => fetchApi<ServerStats>(`/seasons/${seasonId}/stats`),

  
  getLeaderboard: (seasonId: string, stat: string) =>
    fetchApi<{ seasonId: string; stat: string; leaderboard: LeaderboardEntry[] }>(
      `/seasons/${seasonId}/leaderboards/${stat}`
    ),

  
  getPlayer: (playerId: string) => fetchApi<Player>(`/players/${playerId}`),
  getPlayerByName: (playerName: string) => fetchApi<Player>(`/players/by-name/${encodeURIComponent(playerName)}`),
  getPlayerStats: (playerId: string, seasonId: string) =>
    fetchApi<PlayerStats>(`/players/${playerId}/stats/${seasonId}`),
  getPlayerWrapped: (playerId: string, seasonId: string) =>
    fetchApi<WrappedSnapshot>(`/players/${playerId}/wrapped/${seasonId}`),
}
