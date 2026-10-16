import { useState } from 'react'
import { motion } from 'framer-motion'
import { BarChart3, Clock, Blocks, Sword, Trophy } from 'lucide-react'
import { useApi } from '../hooks/useApi'
import { api, Season, ServerStats } from '../api/client'
import { PageLoader, ErrorState } from '../components/LoadingSkeleton'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'

export default function StatsPage() {
  const { data: seasonData } = useApi<{ seasons: Season[] }>(() => api.getSeasons())
  const currentSeason = seasonData?.seasons?.find(s => s.active) || seasonData?.seasons?.[0]

  const { data: statsData, loading, error, refetch } = useApi<ServerStats>(
    () => api.getSeasonStats(currentSeason?.id || 'season-1'),
    [currentSeason?.id]
  )

  if (loading) return <PageLoader />
  if (error) return <ErrorState message={error} onRetry={refetch} />
  if (!statsData) return <PageLoader />

  const stats = statsData.stats

  const playtimeData = [
    { name: 'Hours', value: Math.round((stats.playtime_seconds || 0) / 3600) },
    { name: 'Days', value: Math.round((stats.playtime_seconds || 0) / 86400) },
  ]

  const blockData = [
    { name: 'Mined', value: stats.blocks_mined || 0 },
    { name: 'Placed', value: stats.blocks_placed || 0 },
  ]

  const COLORS = ['#10b981', '#06b6d4', '#8b5cf6', '#f59e0b']

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <h1 className="section-title mb-2 flex items-center gap-3">
          <BarChart3 className="w-8 h-8 text-violet-400" />
          Server Statistics
        </h1>
        <p className="section-subtitle mb-10">
          {currentSeason ? `Season: ${currentSeason.name}` : 'No active season'}
        </p>

        {/* Summary cards */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-12">
          <StatSummary icon={Clock} label="Total Playtime" value={`${Math.round((stats.playtime_seconds || 0) / 3600)}h`} color="emerald" />
          <StatSummary icon={Blocks} label="Blocks Mined" value={(stats.blocks_mined || 0).toLocaleString()} color="cyan" />
          <StatSummary icon={Sword} label="Mobs Killed" value={(stats.mobs_killed || 0).toLocaleString()} color="violet" />
          <StatSummary icon={Trophy} label="Total Deaths" value={(stats.deaths || 0).toLocaleString()} color="gold" />
        </div>

        {/* Charts */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="glass-card p-6">
            <h3 className="text-lg font-semibold mb-4">Playtime Overview</h3>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={playtimeData}>
                <XAxis dataKey="name" stroke="#94a3b8" />
                <YAxis stroke="#94a3b8" />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1a2332', border: '1px solid #324159', borderRadius: '8px' }}
                />
                <Bar dataKey="value" fill="#10b981" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="glass-card p-6">
            <h3 className="text-lg font-semibold mb-4">Blocks Mined vs Placed</h3>
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie
                  data={blockData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={80}
                  paddingAngle={5}
                  dataKey="value"
                >
                  {blockData.map((_, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ backgroundColor: '#1a2332', border: '1px solid #324159', borderRadius: '8px' }}
                />
              </PieChart>
            </ResponsiveContainer>
            <div className="flex justify-center gap-4 mt-2">
              {blockData.map((entry, index) => (
                <div key={entry.name} className="flex items-center gap-2 text-sm">
                  <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS[index] }} />
                  <span className="text-surface-400">{entry.name}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </motion.div>
    </div>
  )
}

function StatSummary({ icon: Icon, label, value, color }: { icon: typeof Clock; label: string; value: string; color: string }) {
  const colorMap: Record<string, string> = {
    emerald: 'from-emerald-500 to-emerald-600',
    cyan: 'from-cyan-500 to-cyan-600',
    violet: 'from-violet-500 to-violet-600',
    gold: 'from-gold-500 to-gold-600',
  }

  return (
    <motion.div
      whileHover={{ scale: 1.02 }}
      className="glass-card p-4"
    >
      <div className={`w-8 h-8 rounded-lg bg-gradient-to-br ${colorMap[color]} flex items-center justify-center mb-3`}>
        <Icon className="w-4 h-4 text-white" />
      </div>
      <p className="text-surface-400 text-xs mb-1">{label}</p>
      <p className="text-xl font-display font-bold text-white">{value}</p>
    </motion.div>
  )
}
