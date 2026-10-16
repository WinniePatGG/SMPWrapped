import { useState, useEffect, useCallback, useMemo } from 'react'
import type { ReactNode } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronLeft, ChevronRight, Pause, Play, X, ArrowRight, Sparkles, Pickaxe } from 'lucide-react'
import { api, WrappedSnapshot } from '../api/client'
import AnimatedCounter from '../components/AnimatedCounter'
import PlayerAvatar from '../components/PlayerAvatar'
import WrappedDashboard from '../components/WrappedDashboard'
import {
  bestPercentile,
  formatDay,
  formatDuration,
  formatHour,
  formatHours,
  formatNumber,
} from '../lib/wrapped'

const AUTO_MS = 7000

interface Slide {
  id: string
  bg: string
  content: ReactNode
}

export default function WrappedPage() {
  const { playerName } = useParams<{ playerName: string }>()
  const navigate = useNavigate()

  const [wrapped, setWrapped] = useState<WrappedSnapshot | null>(null)
  const [seasonName, setSeasonName] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [view, setView] = useState<'story' | 'dashboard'>('story')
  const [currentSlide, setCurrentSlide] = useState(0)
  const [direction, setDirection] = useState(1)
  const [paused, setPaused] = useState(false)
  const [progress, setProgress] = useState(0)

  // Load season -> player -> wrapped snapshot.
  useEffect(() => {
    if (!playerName) return
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const seasons = await api.getSeasons()
        const season = seasons.seasons.find(s => s.active) || seasons.seasons[0]
        if (!season) throw new Error('No season is available yet')

        const player = await api.getPlayerByName(playerName!)
        const snapshot = await api.getPlayerWrapped(player.uuid, season.id)

        if (cancelled) return
        setWrapped(snapshot)
        setSeasonName(season.name)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load your Wrapped')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [playerName])

  const slides = useMemo<Slide[]>(
    () => (wrapped ? buildSlides(wrapped, seasonName, () => setView('dashboard')) : []),
    [wrapped, seasonName]
  )

  const goNext = useCallback(() => {
    if (slides.length === 0) return
    setDirection(1)
    setProgress(0)
    setCurrentSlide(s => Math.min(s + 1, slides.length - 1))
  }, [slides.length])

  const goPrev = useCallback(() => {
    if (slides.length === 0) return
    setDirection(-1)
    setProgress(0)
    setCurrentSlide(s => Math.max(s - 1, 0))
  }, [slides.length])

  const goToDashboard = useCallback(() => setView('dashboard'), [])

  // Auto-advance through the story like Spotify Wrapped.
  useEffect(() => {
    if (view !== 'story' || paused) return
    if (currentSlide >= slides.length - 1) return

    const interval = window.setInterval(() => {
      setProgress(p => Math.min(p + (50 / AUTO_MS) * 100, 100))
    }, 50)

    return () => window.clearInterval(interval)
  }, [view, paused, currentSlide, slides.length])

  useEffect(() => {
    if (progress >= 100 && currentSlide < slides.length - 1) {
      goNext()
    }
  }, [progress, currentSlide, slides.length, goNext])

  // Keyboard navigation.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (view !== 'story') return
      if (e.key === 'ArrowRight' || e.key === ' ') {
        e.preventDefault()
        if (currentSlide >= slides.length - 1) goToDashboard()
        else goNext()
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault()
        goPrev()
      } else if (e.key === 'Escape') {
        navigate('/')
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [view, currentSlide, slides.length, goNext, goPrev, goToDashboard, navigate])

  // Swipe navigation.
  useEffect(() => {
    let startX = 0
    const onStart = (e: TouchEvent) => {
      startX = e.touches[0].clientX
    }
    const onEnd = (e: TouchEvent) => {
      const diff = startX - e.changedTouches[0].clientX
      if (Math.abs(diff) > 50) {
        if (diff > 0) {
          if (currentSlide >= slides.length - 1) goToDashboard()
          else goNext()
        } else goPrev()
      }
    }
    window.addEventListener('touchstart', onStart)
    window.addEventListener('touchend', onEnd)
    return () => {
      window.removeEventListener('touchstart', onStart)
      window.removeEventListener('touchend', onEnd)
    }
  }, [currentSlide, slides.length, goNext, goPrev, goToDashboard])

  if (loading) return <StoryLoader />
  if (error || !wrapped || slides.length === 0) {
    return <StoryError message={error || 'Wrapped not found'} onBack={() => navigate('/')} />
  }

  if (view === 'dashboard') {
    return (
      <WrappedDashboard
        wrapped={wrapped}
        seasonName={seasonName}
        onReplay={() => {
          setCurrentSlide(0)
          setProgress(0)
          setView('story')
        }}
      />
    )
  }

  const slide = slides[Math.min(currentSlide, slides.length - 1)]
  const isLast = currentSlide >= slides.length - 1

  const handleSurfaceClick = (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).closest('button, a')) return
    if (isLast) goToDashboard()
    else goNext()
  }

  return (
    <div className="fixed inset-0 overflow-hidden bg-[#05060a] select-none">
      <AnimatePresence mode="wait" custom={direction}>
        <motion.div
          key={slide.id}
          custom={direction}
          initial={{ opacity: 0, x: direction > 0 ? 80 : -80 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: direction > 0 ? -80 : 80 }}
          transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
          className="absolute inset-0"
          style={{ background: slide.bg }}
          onClick={handleSurfaceClick}
        >
          <div className="h-full w-full flex flex-col items-center justify-center text-center px-6 sm:px-10 pt-20 pb-24">
            <div className="w-full max-w-2xl">{slide.content}</div>
          </div>
        </motion.div>
      </AnimatePresence>

      {/* Top bar: progress segments + controls */}
      <div className="absolute top-0 inset-x-0 z-30 px-4 pt-4 pb-8 bg-gradient-to-b from-black/50 to-transparent pointer-events-none">
        <div className="max-w-3xl mx-auto flex items-center gap-3 pointer-events-auto">
          <button
            onClick={() => navigate('/')}
            aria-label="Close"
            className="p-2 -ml-2 text-white/80 hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="flex-1 flex items-center gap-1.5">
            {slides.map((s, i) => (
              <div key={s.id} className="h-1 flex-1 rounded-full bg-white/25 overflow-hidden">
                <div
                  className="h-full bg-white rounded-full"
                  style={{ width: i < currentSlide ? '100%' : i === currentSlide ? `${progress}%` : '0%' }}
                />
              </div>
            ))}
          </div>

          <button
            onClick={() => setPaused(p => !p)}
            aria-label={paused ? 'Play' : 'Pause'}
            className="p-2 -mr-2 text-white/80 hover:text-white transition-colors"
          >
            {paused ? <Play className="w-5 h-5" /> : <Pause className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Side navigation */}
      {currentSlide > 0 && (
        <button
          onClick={goPrev}
          aria-label="Previous"
          className="absolute left-2 sm:left-4 top-1/2 -translate-y-1/2 z-20 p-2 rounded-full bg-black/20 text-white/70 hover:text-white hover:bg-black/40 transition-colors"
        >
          <ChevronLeft className="w-6 h-6" />
        </button>
      )}
      {!isLast && (
        <button
          onClick={goNext}
          aria-label="Next"
          className="absolute right-2 sm:right-4 top-1/2 -translate-y-1/2 z-20 p-2 rounded-full bg-black/20 text-white/70 hover:text-white hover:bg-black/40 transition-colors"
        >
          <ChevronRight className="w-6 h-6" />
        </button>
      )}

      {/* Bottom hint */}
      <div className="absolute bottom-0 inset-x-0 z-30 py-5 text-center pointer-events-none">
        <p className="text-white/50 text-xs uppercase tracking-[0.3em]">
          {isLast ? 'Tap to open your dashboard' : 'Tap to continue'}
        </p>
      </div>
    </div>
  )
}

/* ---------------------------------- slides --------------------------------- */

function Slide({ children }: { children: ReactNode }) {
  return <div className="flex flex-col items-center">{children}</div>
}

function Kicker({ children }: { children: ReactNode }) {
  return (
    <p className="text-[0.7rem] sm:text-sm font-semibold uppercase tracking-[0.35em] text-white/70 mb-5">
      {children}
    </p>
  )
}

const BIG = 'font-display font-bold text-7xl sm:text-9xl tracking-tighter leading-none'

function buildSlides(wrapped: WrappedSnapshot, seasonName: string, onFinish: () => void): Slide[] {
  const s = wrapped.stats
  const best = bestPercentile(wrapped.percentiles)

  const slides: Slide[] = [
    {
      id: 'intro',
      bg: 'linear-gradient(160deg,#1DB954 0%,#0f7a37 45%,#062d16 100%)',
      content: (
        <Slide>
          <PlayerAvatar
            uuid={wrapped.playerId}
            name={wrapped.playerName}
            size={112}
            className="mx-auto mb-8 rounded-3xl shadow-2xl shadow-black/40"
          />
          <Kicker>SMP Wrapped</Kicker>
          <h1 className="font-display font-bold text-5xl sm:text-7xl tracking-tight leading-none mb-4">
            {wrapped.playerName}
          </h1>
          <p className="text-white/70 text-lg">Season {seasonName}</p>
        </Slide>
      ),
    },
    {
      id: 'playtime',
      bg: 'linear-gradient(160deg,#8E2DE2 0%,#4A00E0 55%,#160033 100%)',
      content: (
        <Slide>
          <Kicker>You played for</Kicker>
          <div className={`${BIG} mb-4`}>
            <AnimatedCounter value={formatHours(s.playtimeSeconds)} format={v => v.toFixed(1)} />
          </div>
          <p className="text-3xl sm:text-4xl font-display font-semibold text-white/90">hours</p>
          <p className="text-white/60 mt-8 text-lg">
            {formatDuration(s.playtimeSeconds)} across {formatNumber(s.uniqueDaysPlayed)} days
          </p>
        </Slide>
      ),
    },
    {
      id: 'prime',
      bg: 'linear-gradient(160deg,#FF8008 0%,#FF3D00 55%,#3a0d00 100%)',
      content: (
        <Slide>
          <Kicker>You were most active</Kicker>
          <h2 className="font-display font-bold text-4xl sm:text-6xl leading-tight mb-6">
            {formatDay(s.topDay) || 'on every single day'}
          </h2>
          {s.topHour != null && (
            <p className="text-2xl sm:text-3xl font-display font-semibold text-white/90">
              usually around {formatHour(s.topHour)}
            </p>
          )}
          <p className="text-white/60 mt-8 text-lg">That's your prime time</p>
        </Slide>
      ),
    },
    best
      ? {
          id: 'percentile',
          bg: 'linear-gradient(160deg,#FF2D95 0%,#C2006B 55%,#3a0022 100%)',
          content: (
            <Slide>
              <Kicker>Your strongest stat</Kicker>
              <p className="text-xl sm:text-2xl text-white/80 mb-3">You're in the</p>
              <div className={`${BIG} mb-5`}>TOP {best.top}%</div>
              <p className="text-2xl sm:text-3xl font-display font-semibold text-white/90">of all players</p>
              <p className="text-white/60 mt-8 text-lg">{best.label}</p>
            </Slide>
          ),
        }
      : {
          id: 'percentile',
          bg: 'linear-gradient(160deg,#FF2D95 0%,#C2006B 55%,#3a0022 100%)',
          content: (
            <Slide>
              <Kicker>Leaderboards</Kicker>
              <h2 className="font-display font-bold text-4xl sm:text-5xl leading-tight mb-6">
                Not enough players to rank you yet
              </h2>
              <p className="text-white/60 text-lg">Bring a few friends along!</p>
            </Slide>
          ),
        },
    {
      id: 'grind',
      bg: 'linear-gradient(160deg,#00E0D1 0%,#0091A8 55%,#00323a 100%)',
      content: (
        <Slide>
          <Kicker>You mined</Kicker>
          <div className={`${BIG} mb-4`}>
            <AnimatedCounter value={s.blocksMined || 0} />
          </div>
          <p className="text-3xl sm:text-4xl font-display font-semibold text-white/90">blocks</p>
          {s.topMinedBlock && (
            <p className="text-white/60 mt-8 text-lg">
              Most of all: {String(s.topMinedBlock).replace(/_/g, ' ')}
            </p>
          )}
        </Slide>
      ),
    },
    {
      id: 'hunter',
      bg: 'linear-gradient(160deg,#FF416C 0%,#B3001B 55%,#3a0007 100%)',
      content: (
        <Slide>
          <Kicker>You defeated</Kicker>
          <div className={`${BIG} mb-4`}>
            <AnimatedCounter value={s.mobsKilled || 0} />
          </div>
          <p className="text-3xl sm:text-4xl font-display font-semibold text-white/90">mobs</p>
          {s.topMob && (
            <p className="text-white/60 mt-8 text-lg">Mostly {String(s.topMob).replace(/_/g, ' ')}</p>
          )}
        </Slide>
      ),
    },
    {
      id: 'explorer',
      bg: 'linear-gradient(160deg,#3A7BFF 0%,#143CB0 55%,#050B33 100%)',
      content: (
        <Slide>
          <Kicker>You traveled</Kicker>
          <div className={`${BIG} mb-4`}>
            <AnimatedCounter value={s.distanceTraveled || 0} />
          </div>
          <p className="text-3xl sm:text-4xl font-display font-semibold text-white/90">blocks</p>
          {s.topDimension && (
            <p className="text-white/60 mt-8 text-lg">
              Favourite dimension: {String(s.topDimension).replace(/_/g, ' ')}
            </p>
          )}
        </Slide>
      ),
    },
    {
      id: 'survival',
      bg: 'linear-gradient(160deg,#5B6478 0%,#2A3040 55%,#0A0D14 100%)',
      content: (
        <Slide>
          <Kicker>You died</Kicker>
          <div className={`${BIG} mb-4`}>
            <AnimatedCounter value={s.deaths || 0} />
          </div>
          <p className="text-3xl sm:text-4xl font-display font-semibold text-white/90">
            {(s.deaths || 0) === 1 ? 'time' : 'times'}
          </p>
          <p className="text-white/60 mt-8 text-lg">
            {(s.deaths || 0) === 0
              ? 'Flawless season. Untouchable.'
              : (s.deaths || 0) < 5
              ? 'Pretty solid survival skills.'
              : 'Death is just part of the adventure.'}
          </p>
        </Slide>
      ),
    },
    {
      id: 'archetype',
      bg: 'linear-gradient(160deg,#1B2A6B 0%,#0A1130 55%,#04060F 100%)',
      content: (
        <Slide>
          <Kicker>Your archetype</Kicker>
          <h2 className="font-display font-bold text-5xl sm:text-7xl gradient-text-gold leading-tight mb-8">
            {wrapped.archetype}
          </h2>
          <p className="text-white/80 text-lg sm:text-xl max-w-xl mx-auto leading-relaxed">
            {wrapped.archetypeDescription}
          </p>
        </Slide>
      ),
    },
    {
      id: 'achievements',
      bg: 'linear-gradient(160deg,#12C2A0 0%,#0A6E5A 55%,#02241D 100%)',
      content: (
        <Slide>
          <Kicker>You unlocked</Kicker>
          <h2 className="font-display font-bold text-4xl sm:text-5xl mb-8">
            {wrapped.achievements.length} achievements
          </h2>
          {wrapped.achievements.length > 0 ? (
            <div className="flex flex-wrap justify-center gap-3">
              {wrapped.achievements.map((a, i) => (
                <motion.span
                  key={a}
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: 0.2 + i * 0.08 }}
                  className="px-4 py-2 rounded-full bg-white/15 border border-white/25 text-white text-sm font-medium backdrop-blur-sm"
                >
                  {a}
                </motion.span>
              ))}
            </div>
          ) : (
            <p className="text-white/70 text-lg">Your first achievement is just around the corner.</p>
          )}
        </Slide>
      ),
    },
    {
      id: 'finale',
      bg: 'linear-gradient(150deg,#1DB954 0%,#4A00E0 45%,#FF2D95 100%)',
      content: (
        <Slide>
          <Sparkles className="w-12 h-12 mx-auto mb-6 text-white/80" />
          <Kicker>That's a wrap</Kicker>
          <h2 className="font-display font-bold text-4xl sm:text-6xl leading-tight mb-5">
            Nice season, {wrapped.playerName}.
          </h2>
          <p className="text-white/75 text-lg mb-10">Here's everything you did, all in one place.</p>
          <button
            onClick={onFinish}
            className="inline-flex items-center gap-2 px-8 py-4 rounded-full bg-white text-black font-semibold text-lg hover:scale-105 active:scale-95 transition-transform shadow-xl shadow-black/30"
          >
            Open your dashboard
            <ArrowRight className="w-5 h-5" />
          </button>
        </Slide>
      ),
    },
  ]

  return slides
}

/* -------------------------------- fallbacks -------------------------------- */

function StoryLoader() {
  return (
    <div className="fixed inset-0 bg-[#05060a] flex flex-col items-center justify-center gap-6">
      <motion.div
        animate={{ scale: [1, 1.12, 1], rotate: [0, 6, 0] }}
        transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}
        className="w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-400 to-violet-500 flex items-center justify-center"
      >
        <Pickaxe className="w-8 h-8 text-black/80" />
      </motion.div>
      <p className="text-white/60 text-sm uppercase tracking-[0.3em]">Loading your Wrapped</p>
    </div>
  )
}

function StoryError({ message, onBack }: { message: string; onBack: () => void }) {
  return (
    <div className="fixed inset-0 bg-[#05060a] flex items-center justify-center px-6">
      <div className="text-center max-w-md">
        <div className="w-16 h-16 mx-auto mb-6 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center">
          <span className="text-2xl">🥲</span>
        </div>
        <h1 className="font-display font-bold text-2xl mb-3">No Wrapped here</h1>
        <p className="text-white/60 mb-8">{message}</p>
        <button
          onClick={onBack}
          className="inline-flex items-center gap-2 px-6 py-3 rounded-full bg-white text-black font-semibold hover:scale-105 active:scale-95 transition-transform"
        >
          Back to search
        </button>
      </div>
    </div>
  )
}
