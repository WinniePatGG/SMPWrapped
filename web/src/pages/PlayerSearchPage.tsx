import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Search, ArrowRight } from 'lucide-react'
import { useApi } from '../hooks/useApi'
import { api, Season, Player } from '../api/client'
import PlayerAvatar from '../components/PlayerAvatar'

export default function PlayerSearchPage() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Player[]>([])
  const [searching, setSearching] = useState(false)
  const navigate = useNavigate()

  const { data: seasonData } = useApi<{ seasons: Season[] }>(() => api.getSeasons())
  const currentSeason = seasonData?.seasons?.find(s => s.active) || seasonData?.seasons?.[0]

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!query.trim()) return

    setSearching(true)
    try {
      // In production, you'd have a search endpoint
      // For now, we'll simulate with a mock search
      const allPlayers = await api.getSeasons() // This would be a player search endpoint
      // Mock results for demo
      setResults([])
    } catch (err) {
      setResults([])
    } finally {
      setSearching(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <h1 className="section-title mb-2">Find Your Wrapped</h1>
        <p className="section-subtitle mb-8">Enter your Minecraft username to view your personalized Wrapped</p>

        <form onSubmit={handleSearch} className="mb-8">
          <div className="flex gap-3">
            <div className="flex-1 relative">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-surface-400" />
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Enter Minecraft username..."
                className="w-full pl-12 pr-4 py-3 bg-surface-800/50 border border-surface-700/50 rounded-xl text-white placeholder-surface-500 focus:outline-none focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/50"
              />
            </div>
            <button type="submit" className="btn-primary flex items-center gap-2">
              Search
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </form>

        {searching && (
          <div className="text-center py-8">
            <div className="w-8 h-8 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
            <p className="text-surface-400">Searching...</p>
          </div>
        )}

        {results.length > 0 && (
          <div className="space-y-3">
            {results.map((player) => (
              <motion.button
                key={player.uuid}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                onClick={() => navigate(`/w/${player.name}`)}
                className="w-full glass-card-hover p-4 flex items-center gap-4 text-left"
              >
                <PlayerAvatar uuid={player.uuid} name={player.name} size={48} />
                <div className="flex-1">
                  <p className="font-semibold text-white">{player.name}</p>
                  <p className="text-surface-400 text-sm">
                    Last seen: {new Date(player.lastSeen).toLocaleDateString()}
                  </p>
                </div>
                <ArrowRight className="w-5 h-5 text-surface-400" />
              </motion.button>
            ))}
          </div>
        )}

        {!searching && results.length === 0 && query && (
          <div className="text-center py-8 text-surface-400">
            <p>No players found matching "{query}"</p>
            <p className="text-sm mt-2">Make sure the player has joined the server.</p>
          </div>
        )}

        {!query && (
          <div className="text-center py-12">
            <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-surface-800/50 flex items-center justify-center">
              <Search className="w-8 h-8 text-surface-500" />
            </div>
            <p className="text-surface-400">Enter a username above to find your Wrapped</p>
          </div>
        )}
      </motion.div>
    </div>
  )
}
