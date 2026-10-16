import { useState, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Search, ArrowRight } from 'lucide-react'
import { useApi } from '../hooks/useApi'
import { api, Season } from '../api/client'

export default function LandingPage() {
  const [query, setQuery] = useState('')
  const navigate = useNavigate()

  const { data: seasonData } = useApi<{ seasons: Season[] }>(() => api.getSeasons())
  const currentSeason = seasonData?.seasons?.find(s => s.active) || seasonData?.seasons?.[0]

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const name = query.trim()
    if (name) navigate(`/w/${encodeURIComponent(name)}`)
  }

  return (
    <div className="min-h-screen relative overflow-hidden flex items-center justify-center px-6">
      {/* Ambient glow */}
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute -top-40 -left-40 w-[36rem] h-[36rem] rounded-full bg-emerald-500/20 blur-[120px]" />
        <div className="absolute -bottom-40 -right-40 w-[36rem] h-[36rem] rounded-full bg-violet-500/20 blur-[120px]" />
      </div>

      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: 'easeOut' }}
        className="relative w-full max-w-md text-center"
      >
        {currentSeason && (
          <span className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-white/5 border border-white/10 text-surface-300 text-xs font-medium uppercase tracking-[0.2em] mb-8">
            Season {currentSeason.name}
          </span>
        )}

        <h1 className="font-display font-bold text-5xl sm:text-6xl tracking-tight leading-[0.95] mb-4">
          SMP
          <br />
          <span className="gradient-text">Wrapped</span>
        </h1>
        <p className="text-surface-300 mb-10 text-balance">
          Your season, recapped. Enter your Minecraft username to see your story.
        </p>

        <form onSubmit={handleSubmit} className="relative">
          <Search className="absolute left-5 top-1/2 -translate-y-1/2 w-5 h-5 text-surface-400" />
          <input
            autoFocus
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Minecraft username"
            className="w-full pl-14 pr-32 py-4 bg-white/5 border border-white/10 rounded-2xl text-white placeholder-surface-500 text-lg focus:outline-none focus:border-emerald-400/50 focus:bg-white/10 transition-colors"
          />
          <button
            type="submit"
            disabled={!query.trim()}
            className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-1.5 px-5 py-2.5 rounded-xl bg-white text-black font-semibold text-sm disabled:opacity-30 hover:bg-white/90 transition-colors"
          >
            Go
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>
      </motion.div>
    </div>
  )
}
