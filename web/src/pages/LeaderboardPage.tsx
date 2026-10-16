import { useState } from 'react'
import { motion } from 'framer-motion'
import { Trophy, Clock, Blocks, Sword, Fish, Calendar } from 'lucide-react'
import { useApi } from '../hooks/useApi'
import { api, Season, LeaderboardEntry } from '../api/client'
import { PageLoader, ErrorState } from '../components/LoadingSkeleton'
import PlayerAvatar from '../components/PlayerAvatar'

const LEADERBOARD_STATS = [
  { key: 'playtime_seconds', label: 'Playtime', icon: Clock, format: (v: number) => `${Math.round(v / 3600)}h` },
  { key: 'blocks_mined', label: 'Blocks Mined', icon: Blocks, format: (v: number) => v.toLocaleString() },
  { key: 'distance_traveled', label: 'Distance', icon: Trophy, format: (v: number) => `${(v / 1000).toFixed(1)}k` },
  { key: 'mobs_killed', label: 'Mobs Killed', icon: Sword, format: (v: number) => v.toLocaleString() },
  { key: 'fishing_catches', label: 'Fish Caught', icon: Fish, format: (v: number) => v.toLocaleString() },
  { key: 'unique_days', label: 'Unique Days', icon: Calendar, format: (v: number) => v.toLocaleString() },
]

export default function LeaderboardPage() {
  const [selectedStat, setSelectedStat] = useState(LEADERBOARD_STATS[0])
  const { data: seasonData } = useApi<{ seasons: Season[] }>(() => api.getSeasons())
  const currentSeason = seasonData?.seasons?.find(s => s.active) || seasonData?.seasons?.[0]

  const { data: leaderboardData, loading, error, refetch } = useApi<{ leaderboard: LeaderboardEntry[] }>(
    () => api.getLeaderboard(currentSeason?.id || 'season-1', selectedStat.key),
    [currentSeason?.id, selectedStat.key]
  )

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <h1 className="section-title mb-2 flex items-center gap-3">
          <Trophy className="w-8 h-8 text-gold-400" />
          Leaderboard
        </h1>
        <p className="section-subtitle mb-8">
          {currentSeason ? `Season: ${currentSeason.name}` : 'Select a season'}
        </p>

        {/* Stat selector */}
        <div className="flex flex-wrap gap-2 mb-8">
          {LEADERBOARD_STATS.map(stat => (
            <button
              key={stat.key}
              onClick={() => setSelectedStat(stat)}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all
                ${selectedStat.key === stat.key
                  ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                  : 'bg-surface-700/30 text-surface-400 border border-surface-700/50 hover:bg-surface-700/50'
                }`}
            >
              <stat.icon className="w-4 h-4" />
              {stat.label}
            </button>
          ))}
        </div>

        {/* Leaderboard */}
        {loading ? (
          <PageLoader />
        ) : error ? (
          <ErrorState message={error} onRetry={refetch} />
        ) : leaderboardData?.leaderboard?.length === 0 ? (
          <div className="text-center py-12 text-surface-400">
            <p>No data available for this stat yet.</p>
          </div>
        ) : (
          <div className="space-y-3">
            {leaderboardData?.leaderboard?.map((entry, index) => (
              <motion.div
                key={entry.playerId}
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: index * 0.05 }}
                className={`glass-card-hover p-4 flex items-center gap-4
                  ${index === 0 ? 'border-gold-500/30 bg-gold-500/5' : ''}
                  ${index === 1 ? 'border-gray-400/30 bg-gray-400/5' : ''}
                  ${index === 2 ? 'border-amber-600/30 bg-amber-600/5' : ''}
                `}
              >
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center font-bold text-sm
                  ${index === 0 ? 'bg-gold-500/20 text-gold-400' :
                    index === 1 ? 'bg-gray-400/20 text-gray-300' :
                    index === 2 ? 'bg-amber-600/20 text-amber-500' :
                    'bg-surface-700/50 text-surface-400'
                  }`}
                >
                  {entry.rank}
                </div>

                <PlayerAvatar uuid={entry.playerId} name={entry.playerName || 'Unknown'} size={40} />

                <div className="flex-1">
                  <p className="font-semibold text-white">{entry.playerName || 'Unknown Player'}</p>
                </div>

                <div className="text-right">
                  <p className="font-display font-bold text-lg text-white">
                    {selectedStat.format(entry.value)}
                  </p>
                </div>
              </motion.div>
            ))}
          </div>
        )}
      </motion.div>
    </div>
  )
}
