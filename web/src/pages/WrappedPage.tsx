import { useState, useEffect, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronLeft, ChevronRight, Share2, Copy, Check, ArrowLeft } from 'lucide-react'
import { useApi } from '../hooks/useApi'
import { api, WrappedSnapshot, Season } from '../api/client'
import { PageLoader, ErrorState } from '../components/LoadingSkeleton'
import PlayerAvatar from '../components/PlayerAvatar'
import AnimatedCounter from '../components/AnimatedCounter'

export default function WrappedPage() {
  const { playerName } = useParams<{ playerName: string }>()
  const [currentSlide, setCurrentSlide] = useState(0)
  const [direction, setDirection] = useState(0)
  const [copied, setCopied] = useState(false)

  // First, we need to find the player by name to get their UUID
  // For now, we'll use a placeholder approach - in production you'd have a search endpoint
  const { data: seasonData } = useApi<{ seasons: Season[] }>(() => api.getSeasons())
  const currentSeason = seasonData?.seasons?.find(s => s.active) || seasonData?.seasons?.[0]

  // Mock: In production, you'd look up the player by name first
  // For demo purposes, we'll use a default UUID
  const playerId = '00000000-0000-0000-0000-000000000000'

  const { data: wrapped, loading, error, refetch } = useApi<WrappedSnapshot>(
    () => api.getPlayerWrapped(playerId, currentSeason?.id || 'season-1'),
    [currentSeason?.id]
  )

  const nextSlide = useCallback(() => {
    if (!wrapped) return
    const maxSlides = getSlideCount(wrapped)
    if (currentSlide < maxSlides - 1) {
      setDirection(1)
      setCurrentSlide(prev => prev + 1)
    }
  }, [currentSlide, wrapped])

  const prevSlide = useCallback(() => {
    if (currentSlide > 0) {
      setDirection(-1)
      setCurrentSlide(prev => prev - 1)
    }
  }, [currentSlide])

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight' || e.key === ' ') {
        e.preventDefault()
        nextSlide()
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault()
        prevSlide()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [nextSlide, prevSlide])

  // Touch/swipe support
  useEffect(() => {
    let touchStartX = 0
    const handleTouchStart = (e: TouchEvent) => {
      touchStartX = e.touches[0].clientX
    }
    const handleTouchEnd = (e: TouchEvent) => {
      const diff = touchStartX - e.changedTouches[0].clientX
      if (Math.abs(diff) > 50) {
        if (diff > 0) nextSlide()
        else prevSlide()
      }
    }
    window.addEventListener('touchstart', handleTouchStart)
    window.addEventListener('touchend', handleTouchEnd)
    return () => {
      window.removeEventListener('touchstart', handleTouchStart)
      window.removeEventListener('touchend', handleTouchEnd)
    }
  }, [nextSlide, prevSlide])

  const handleCopyLink = () => {
    navigator.clipboard.writeText(window.location.href)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleShare = async () => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: `${wrapped?.playerName}'s Minecraft Wrapped`,
          text: `Check out my Minecraft Wrapped! I'm ${wrapped?.archetype}!`,
          url: window.location.href,
        })
      } catch (err) {
        // User cancelled or share failed
      }
    } else {
      handleCopyLink()
    }
  }

  if (loading) return <PageLoader />
  if (error) return <ErrorState message={error} onRetry={refetch} />
  if (!wrapped) return <ErrorState message="Wrapped not found" />

  const slides = buildSlides(wrapped)
  const slide = slides[currentSlide]

  return (
    <div className="min-h-screen flex flex-col">
      {/* Progress bar */}
      <div className="fixed top-0 left-0 right-0 z-50 h-1 bg-surface-800">
        <motion.div
          className="h-full bg-gradient-to-r from-emerald-500 to-cyan-500"
          initial={{ width: 0 }}
          animate={{ width: `${((currentSlide + 1) / slides.length) * 100}%` }}
          transition={{ duration: 0.3 }}
        />
      </div>

      {/* Navigation */}
      <div className="fixed bottom-0 left-0 right-0 z-50 bg-surface-950/80 backdrop-blur-xl border-t border-surface-700/50">
        <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2 text-surface-400 hover:text-white transition-colors">
            <ArrowLeft className="w-4 h-4" />
            <span className="text-sm">Back</span>
          </Link>

          <div className="flex items-center gap-4">
            <button
              onClick={prevSlide}
              disabled={currentSlide === 0}
              className="p-2 rounded-lg bg-surface-700/50 text-white disabled:opacity-30 disabled:cursor-not-allowed hover:bg-surface-700 transition-colors"
            >
              <ChevronLeft className="w-5 h-5" />
            </button>

            <span className="text-surface-400 text-sm font-mono">
              {currentSlide + 1} / {slides.length}
            </span>

            <button
              onClick={nextSlide}
              disabled={currentSlide === slides.length - 1}
              className="p-2 rounded-lg bg-surface-700/50 text-white disabled:opacity-30 disabled:cursor-not-allowed hover:bg-surface-700 transition-colors"
            >
              <ChevronRight className="w-5 h-5" />
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button onClick={handleCopyLink} className="p-2 rounded-lg bg-surface-700/50 text-white hover:bg-surface-700 transition-colors">
              {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
            </button>
            <button onClick={handleShare} className="p-2 rounded-lg bg-surface-700/50 text-white hover:bg-surface-700 transition-colors">
              <Share2 className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      {/* Slide content */}
      <div className="flex-1 flex items-center justify-center p-4 pt-8 pb-20">
        <AnimatePresence mode="wait" custom={direction}>
          <motion.div
            key={currentSlide}
            custom={direction}
            initial={{ opacity: 0, x: direction > 0 ? 100 : -100 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: direction > 0 ? -100 : 100 }}
            transition={{ duration: 0.4, ease: 'easeInOut' }}
            className="w-full max-w-2xl"
          >
            {slide}
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  )
}

function getSlideCount(wrapped: WrappedSnapshot): number {
  return buildSlides(wrapped).length
}

function buildSlides(wrapped: WrappedSnapshot): JSX.Element[] {
  const slides: JSX.Element[] = []

  // Slide 1: Introduction
  slides.push(
    <div key="intro" className="text-center">
      <PlayerAvatar uuid={wrapped.playerId} name={wrapped.playerName} size={96} className="mx-auto mb-6" />
      <h1 className="text-4xl md:text-5xl font-display font-bold mb-2">{wrapped.playerName}</h1>
      <p className="text-surface-400 text-lg">Minecraft Wrapped</p>
      <p className="text-surface-500 text-sm mt-2">Season {wrapped.seasonId}</p>
    </div>
  )

  // Slide 2: Time played
  slides.push(
    <div key="time" className="text-center">
      <p className="text-surface-400 text-sm uppercase tracking-wider mb-2">Time Played</p>
      <p className="text-6xl md:text-7xl font-display font-bold gradient-text mb-4">
        <AnimatedCounter value={wrapped.stats.playtimeHours || 0} duration={2.5} />
      </p>
      <p className="text-surface-400 text-lg">hours this season</p>
    </div>
  )

  // Slide 3: The Grind
  slides.push(
    <div key="grind" className="text-center">
      <h2 className="text-2xl font-display font-bold mb-6">The Grind</h2>
      <div className="grid grid-cols-3 gap-4">
        <div className="glass-card p-4">
          <p className="text-3xl font-bold text-emerald-400">
            <AnimatedCounter value={wrapped.stats.blocksMined || 0} />
          </p>
          <p className="text-surface-400 text-sm">Blocks Mined</p>
        </div>
        <div className="glass-card p-4">
          <p className="text-3xl font-bold text-cyan-400">
            <AnimatedCounter value={wrapped.stats.blocksPlaced || 0} />
          </p>
          <p className="text-surface-400 text-sm">Blocks Placed</p>
        </div>
        <div className="glass-card p-4">
          <p className="text-3xl font-bold text-violet-400">
            <AnimatedCounter value={wrapped.stats.itemsCrafted || 0} />
          </p>
          <p className="text-surface-400 text-sm">Items Crafted</p>
        </div>
      </div>
    </div>
  )

  // Slide 4: Explorer
  slides.push(
    <div key="explorer" className="text-center">
      <h2 className="text-2xl font-display font-bold mb-6">The Explorer</h2>
      <p className="text-5xl font-display font-bold gradient-text mb-4">
        <AnimatedCounter value={wrapped.stats.distanceTraveled || 0} />
      </p>
      <p className="text-surface-400 text-lg">blocks traveled</p>
      {wrapped.stats.topDimension && (
        <p className="text-surface-500 text-sm mt-4">
          Favorite dimension: <span className="text-emerald-400">{wrapped.stats.topDimension}</span>
        </p>
      )}
    </div>
  )

  // Slide 5: Hunter
  slides.push(
    <div key="hunter" className="text-center">
      <h2 className="text-2xl font-display font-bold mb-6">The Hunter</h2>
      <p className="text-5xl font-display font-bold text-red-400 mb-4">
        <AnimatedCounter value={wrapped.stats.mobsKilled || 0} />
      </p>
      <p className="text-surface-400 text-lg">mobs defeated</p>
      {wrapped.stats.topMob && (
        <p className="text-surface-500 text-sm mt-4">
          Most defeated: <span className="text-red-400">{wrapped.stats.topMob}</span> ({wrapped.stats.topMobCount})
        </p>
      )}
    </div>
  )

  // Slide 6: Survival
  slides.push(
    <div key="survival" className="text-center">
      <h2 className="text-2xl font-display font-bold mb-6">The Survival Story</h2>
      <p className="text-5xl font-display font-bold text-gold-400 mb-4">
        <AnimatedCounter value={wrapped.stats.deaths || 0} />
      </p>
      <p className="text-surface-400 text-lg">deaths this season</p>
      <p className="text-surface-500 text-sm mt-4">
        {wrapped.stats.deaths === 0
          ? "Flawless! Not a single death."
          : wrapped.stats.deaths < 5
          ? "Pretty good survival skills!"
          : "Death is just part of the adventure!"}
      </p>
    </div>
  )

  // Slide 7: Archetype
  slides.push(
    <div key="archetype" className="text-center">
      <p className="text-surface-400 text-sm uppercase tracking-wider mb-2">Your Archetype</p>
      <h2 className="text-4xl md:text-5xl font-display font-bold gradient-text-gold mb-4">
        {wrapped.archetype}
      </h2>
      <p className="text-surface-400 text-lg max-w-md mx-auto">{wrapped.archetypeDescription}</p>
    </div>
  )

  // Slide 8: Achievements
  slides.push(
    <div key="achievements" className="text-center">
      <h2 className="text-2xl font-display font-bold mb-6">Achievements</h2>
      <div className="flex flex-wrap justify-center gap-3">
        {wrapped.achievements.map((achievement, i) => (
          <motion.span
            key={achievement}
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: i * 0.1 }}
            className="px-4 py-2 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm font-medium"
          >
            {achievement}
          </motion.span>
        ))}
      </div>
    </div>
  )

  // Slide 9: Comparison
  slides.push(
    <div key="comparison" className="text-center">
      <h2 className="text-2xl font-display font-bold mb-6">How You Compare</h2>
      <div className="space-y-4">
        {Object.entries(wrapped.percentiles).slice(0, 4).map(([stat, percentile]) => (
          <div key={stat} className="flex items-center justify-between glass-card p-4">
            <span className="text-surface-400 text-sm capitalize">{stat.replace(/_/g, ' ')}</span>
            <div className="flex items-center gap-3">
              <div className="w-32 h-2 bg-surface-700 rounded-full overflow-hidden">
                <motion.div
                  className="h-full bg-gradient-to-r from-emerald-500 to-cyan-500"
                  initial={{ width: 0 }}
                  animate={{ width: `${percentile}%` }}
                  transition={{ duration: 1, delay: 0.2 }}
                />
              </div>
              <span className="text-emerald-400 font-mono text-sm w-12 text-right">{percentile}%</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )

  // Slide 10: Finale
  slides.push(
    <div key="finale" className="text-center">
      <h2 className="text-3xl font-display font-bold mb-4">That's a Wrap!</h2>
      <p className="text-surface-400 text-lg mb-6">
        Thanks for playing this season, {wrapped.playerName}!
      </p>
      <div className="glass-card p-6 inline-block">
        <p className="text-surface-500 text-sm mb-2">Share your Wrapped</p>
        <p className="text-emerald-400 font-mono text-sm">{window.location.href}</p>
      </div>
    </div>
  )

  return slides
}
