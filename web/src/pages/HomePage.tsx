import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Pickaxe, Trophy, BarChart3, Search, ArrowRight, Clock, Users, Blocks } from 'lucide-react'
import { useApi } from '../hooks/useApi'
import { api, Season, ServerStats } from '../api/client'
import StatCard from '../components/StatCard'
import { PageLoader, ErrorState } from '../components/LoadingSkeleton'

export default function HomePage() {
  const [searchQuery, setSearchQuery] = useState('')
  const { data: seasonData, loading: seasonLoading, error: seasonError } = useApi<{ seasons: Season[] }>(
    () => api.getSeasons()
  )
  const { data: statsData, loading: statsLoading } = useApi<ServerStats>(() =>
    api.getSeasonStats(seasonData?.seasons?.[0]?.id || 'season-1')
  )

  const currentSeason = seasonData?.seasons?.find(s => s.active) || seasonData?.seasons?.[0]

  return (
    <div>
      {/* Hero Section */}
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-b from-emerald-500/5 via-transparent to-transparent" />
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20 md:py-32">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
            className="text-center max-w-3xl mx-auto"
          >
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', bounce: 0.5, delay: 0.2 }}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm font-medium mb-6"
            >
              <Pickaxe className="w-4 h-4" />
              Season {currentSeason?.name || '1'} is live
            </motion.div>

            <h1 className="text-5xl md:text-7xl font-display font-bold tracking-tight mb-6">
              Your Minecraft
              <br />
              <span className="gradient-text">Wrapped</span>
            </h1>

            <p className="text-xl text-surface-400 mb-10 max-w-2xl mx-auto leading-relaxed">
              Relive your season. See your stats, discover your archetype,
              and share your journey with friends.
            </p>

            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Link to="/search" className="btn-primary flex items-center gap-2 text-lg px-8 py-4">
                <Search className="w-5 h-5" />
                Find Your Wrapped
              </Link>
              <Link to="/leaderboard" className="btn-secondary flex items-center gap-2 text-lg px-8 py-4">
                <Trophy className="w-5 h-5" />
                Leaderboard
              </Link>
            </div>
          </motion.div>
        </div>
      </section>

      {/* Server Stats Section */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.6 }}
        >
          <h2 className="section-title text-center mb-2">Server Overview</h2>
          <p className="section-subtitle text-center mb-10">
            {currentSeason ? `Season: ${currentSeason.name}` : 'No active season'}
          </p>

          {statsLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {[1, 2, 3].map(i => (
                <div key={i} className="h-32 shimmer rounded-2xl bg-surface-800/50" />
              ))}
            </div>
          ) : statsData ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <StatCard
                icon={Clock}
                label="Total Playtime"
                value={Math.round((statsData.stats.playtime_seconds || 0) / 3600)}
                suffix="hours"
                color="emerald"
                delay={0}
              />
              <StatCard
                icon={Blocks}
                label="Blocks Mined"
                value={statsData.stats.blocks_mined || 0}
                color="cyan"
                delay={0.1}
              />
              <StatCard
                icon={Users}
                label="Players Tracked"
                value={statsData.stats.login_sessions || 0}
                suffix="sessions"
                color="violet"
                delay={0.2}
              />
            </div>
          ) : (
            <EmptyState title="No data yet" description="Server statistics will appear here once players start playing." />
          )}
        </motion.div>
      </section>

      {/* Quick Actions */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <QuickAction
            icon={Search}
            title="Player Search"
            description="Find any player and view their Wrapped"
            to="/search"
            color="emerald"
          />
          <QuickAction
            icon={Trophy}
            title="Leaderboards"
            description="See who topped the charts this season"
            to="/leaderboard"
            color="gold"
          />
          <QuickAction
            icon={BarChart3}
            title="Statistics"
            description="Explore detailed server statistics"
            to="/stats"
            color="violet"
          />
        </div>
      </section>
    </div>
  )
}

function QuickAction({
  icon: Icon,
  title,
  description,
  to,
  color,
}: {
  icon: typeof Search
  title: string
  description: string
  to: string
  color: string
}) {
  const colorMap: Record<string, string> = {
    emerald: 'from-emerald-500/10 to-emerald-500/5 border-emerald-500/20 hover:border-emerald-500/40',
    gold: 'from-gold-500/10 to-gold-500/5 border-gold-500/20 hover:border-gold-500/40',
    violet: 'from-violet-500/10 to-violet-500/5 border-violet-500/20 hover:border-violet-500/40',
  }

  return (
    <Link to={to}>
      <motion.div
        whileHover={{ scale: 1.02 }}
        className={`glass-card p-6 bg-gradient-to-br ${colorMap[color]} transition-all duration-300 cursor-pointer group`}
      >
        <Icon className="w-8 h-8 text-white mb-4 group-hover:scale-110 transition-transform" />
        <h3 className="text-lg font-semibold text-white mb-2">{title}</h3>
        <p className="text-surface-400 text-sm">{description}</p>
        <ArrowRight className="w-4 h-4 text-surface-400 mt-4 group-hover:translate-x-1 transition-transform" />
      </motion.div>
    </Link>
  )
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="text-center py-12">
      <h3 className="text-lg font-semibold text-white mb-2">{title}</h3>
      <p className="text-surface-400 text-sm">{description}</p>
    </div>
  )
}
