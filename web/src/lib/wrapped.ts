import { WrappedSnapshot } from '../api/client'

/** Human-readable labels for the percentile stat keys returned by the API. */
export const STAT_LABELS: Record<string, string> = {
  playtime_seconds: 'Playtime',
  blocks_mined: 'Blocks Mined',
  blocks_placed: 'Blocks Placed',
  distance_traveled: 'Distance Traveled',
  mobs_killed: 'Mobs Killed',
  items_crafted: 'Items Crafted',
  fishing_catches: 'Fishing Catches',
  unique_days: 'Days Played',
}

/**
 * The API reports the percentage of players you beat (0-100). The intuitive
 * "you're in the top X%" phrasing is the inverse, so convert and clamp to a
 * sane 1-99 range.
 */
export function topPercent(percentile: number): number {
  return Math.min(99, Math.max(1, Math.round(100 - percentile)))
}

export function formatNumber(n?: number): string {
  return (n ?? 0).toLocaleString()
}

export function formatHours(seconds?: number): number {
  return Math.round(((seconds ?? 0) / 3600) * 10) / 10
}

export function formatDuration(seconds?: number): string {
  const s = Math.max(0, Math.floor(seconds ?? 0))
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (d > 0) return `${d}d ${h}h ${m}m`
  if (h > 0) return `${h}h ${m}m`
  return `${m}m`
}

/** Turn a 0-23 hour into a friendly label like "9PM". */
export function formatHour(hour?: number): string {
  if (hour == null) return ''
  const h = ((Math.round(hour) % 24) + 24) % 24
  const suffix = h < 12 ? 'AM' : 'PM'
  const twelve = h % 12 === 0 ? 12 : h % 12
  return `${twelve}${suffix}`
}

/** Turn a stored day key into something readable, falling back to the raw value. */
export function formatDay(day?: string): string {
  if (!day) return ''
  const d = new Date(day)
  if (Number.isNaN(d.getTime())) return day
  return d.toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' })
}

/** The single best percentile the player earned, or null when unavailable. */
export function bestPercentile(
  percentiles: Record<string, number>
): { key: string; label: string; percentile: number; top: number } | null {
  const entries = Object.entries(percentiles)
  if (entries.length === 0) return null
  const [key, percentile] = entries.reduce((best, curr) => (curr[1] > best[1] ? curr : best))
  return { key, label: STAT_LABELS[key] ?? key.replace(/_/g, ' '), percentile, top: topPercent(percentile) }
}

export interface StatDef {
  key: string
  label: string
  /** Renders the value as a display string. */
  format: (stats: WrappedSnapshot['stats']) => string
  /** Optional secondary line (context such as the top block or mob). */
  sub?: (stats: WrappedSnapshot['stats']) => string | null
}

/** Every tracked number, in the order we want to present them. */
export const STAT_DEFS: StatDef[] = [
  {
    key: 'playtimeSeconds',
    label: 'Playtime',
    format: (s) => formatDuration(s.playtimeSeconds),
    sub: (s) => `${formatHours(s.playtimeSeconds)} hours total`,
  },
  { key: 'uniqueDaysPlayed', label: 'Days Played', format: (s) => formatNumber(s.uniqueDaysPlayed) },
  { key: 'loginSessions', label: 'Sessions', format: (s) => formatNumber(s.loginSessions) },
  {
    key: 'blocksMined',
    label: 'Blocks Mined',
    format: (s) => formatNumber(s.blocksMined),
    sub: (s) => (s.topMinedBlock ? `mostly ${s.topMinedBlock.replace(/_/g, ' ')}` : null),
  },
  { key: 'blocksPlaced', label: 'Blocks Placed', format: (s) => formatNumber(s.blocksPlaced) },
  {
    key: 'distanceTraveled',
    label: 'Distance',
    format: (s) => `${formatNumber(s.distanceTraveled)}`,
    sub: (s) => (s.distanceTraveled ? `${(s.distanceTraveled / 1000).toFixed(1)} km` : 'blocks'),
  },
  {
    key: 'mobsKilled',
    label: 'Mobs Killed',
    format: (s) => formatNumber(s.mobsKilled),
    sub: (s) => (s.topMob ? `mostly ${s.topMob.replace(/_/g, ' ')}` : null),
  },
  { key: 'deaths', label: 'Deaths', format: (s) => formatNumber(s.deaths) },
  { key: 'itemsCrafted', label: 'Items Crafted', format: (s) => formatNumber(s.itemsCrafted) },
  { key: 'itemsCollected', label: 'Items Collected', format: (s) => formatNumber(s.itemsCollected) },
  { key: 'fishingCatches', label: 'Fish Caught', format: (s) => formatNumber(s.fishingCatches) },
  { key: 'damageDealt', label: 'Damage Dealt', format: (s) => formatNumber(s.damageDealt) },
  { key: 'damageReceived', label: 'Damage Taken', format: (s) => formatNumber(s.damageReceived) },
]
