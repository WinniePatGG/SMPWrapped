import { WrappedSnapshot } from '../api/client'
import type { LucideIcon } from 'lucide-react'
import { Trophy, Pickaxe, Hammer, Sword, Heart, Compass, HammerIcon, Fish, Package, Users, Server, Sparkles, Target } from 'lucide-react'

export type AchievementTier = 'BRONZE' | 'SILVER' | 'GOLD' | 'DIAMOND' | 'LEGENDARY'

export interface Achievement {
  id: string
  name: string
  description: string
  tier: AchievementTier
  category: string
}

export function parseAchievement(raw: string): Achievement {
  if (!raw) {
    return { id: 'unknown', name: raw, description: '', tier: 'BRONZE', category: 'FUN' }
  }
  const parts = raw.split('|')
  if (parts.length >= 5) {
    const tier = (parts[3] as AchievementTier) || 'BRONZE'
    return {
      id: parts[0] || raw,
      name: parts[1] || raw,
      description: parts[2] || '',
      tier,
      category: parts[4] || 'FUN',
    }
  }
  return { id: raw, name: raw, description: '', tier: 'BRONZE', category: 'FUN' }
}

export function achievementIcon(category: string): LucideIcon {
  switch (category) {
    case 'MINING':
      return Pickaxe
    case 'BUILDING':
      return Hammer
    case 'COMBAT':
      return Sword
    case 'SURVIVAL':
      return Heart
    case 'EXPLORATION':
      return Compass
    case 'CRAFTING':
      return HammerIcon
    case 'FISHING':
      return Fish
    case 'COLLECTION':
      return Package
    case 'SOCIAL':
      return Users
    case 'SERVER':
      return Server
    case 'FUN':
      return Sparkles
    case 'ACTIVITY':
    default:
      return Trophy
  }
}

export const TIER_STYLES: Record<AchievementTier, { label: string; classes: string; border: string; ring: string }> = {
  BRONZE: { label: 'Bronze', classes: 'bg-amber-800/30 text-amber-300', border: 'border-amber-700/40', ring: 'ring-amber-600/20' },
  SILVER: { label: 'Silver', classes: 'bg-slate-500/30 text-slate-200', border: 'border-slate-400/40', ring: 'ring-slate-400/20' },
  GOLD: { label: 'Gold', classes: 'bg-yellow-500/30 text-yellow-200', border: 'border-yellow-400/40', ring: 'ring-yellow-400/20' },
  DIAMOND: { label: 'Diamond', classes: 'bg-cyan-400/30 text-cyan-200', border: 'border-cyan-400/40', ring: 'ring-cyan-400/20' },
  LEGENDARY: { label: 'Legendary', classes: 'bg-purple-500/30 text-purple-200', border: 'border-purple-400/40', ring: 'ring-purple-400/20' },
}


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


export function formatHour(hour?: number): string {
  if (hour == null) return ''
  const h = ((Math.round(hour) % 24) + 24) % 24
  const suffix = h < 12 ? 'AM' : 'PM'
  const twelve = h % 12 === 0 ? 12 : h % 12
  return `${twelve}${suffix}`
}


export function formatDay(day?: string): string {
  if (!day) return ''
  const d = new Date(day)
  if (Number.isNaN(d.getTime())) return day
  return d.toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' })
}


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
  
  format: (stats: WrappedSnapshot['stats']) => string
  
  sub?: (stats: WrappedSnapshot['stats']) => string | null
}


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
