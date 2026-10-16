import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  ArrowLeft,
  RotateCcw,
  Share2,
  Copy,
  Check,
  Trophy,
  Sparkles,
  Compass,
  Clock,
  Swords,
  CalendarDays,
} from 'lucide-react'
import { WrappedSnapshot } from '../api/client'
import PlayerAvatar from './PlayerAvatar'
import { STAT_DEFS, STAT_LABELS, formatDay, formatHour, topPercent } from '../lib/wrapped'

interface Props {
  wrapped: WrappedSnapshot
  seasonName: string
  onReplay: () => void
}

export default function WrappedDashboard({ wrapped, seasonName, onReplay }: Props) {
  const [copied, setCopied] = useState(false)

  const s = wrapped.stats
  const percentiles = Object.entries(wrapped.percentiles)
  const generated = wrapped.generatedAt ? new Date(wrapped.generatedAt).toLocaleDateString() : null

  const handleCopy = () => {
    navigator.clipboard.writeText(window.location.href)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleShare = async () => {
    const url = window.location.href
    if (navigator.share) {
      try {
        await navigator.share({
          title: `${wrapped.playerName}'s SMP Wrapped`,
          text: `I'm ${wrapped.archetype} this season. Check out my SMP Wrapped!`,
          url,
        })
        return
      } catch {
        
      }
    }
    handleCopy()
  }

  const highlights = [
    s.topMinedBlock && {
      icon: Sparkles,
      label: 'Most mined block',
      value: String(s.topMinedBlock).replace(/_/g, ' '),
      sub: s.topMinedBlockCount ? `${s.topMinedBlockCount.toLocaleString()} times` : undefined,
    },
    s.topMob && {
      icon: Swords,
      label: 'Favourite target',
      value: String(s.topMob).replace(/_/g, ' '),
      sub: s.topMobCount ? `${s.topMobCount.toLocaleString()} slain` : undefined,
    },
    s.topDimension && {
      icon: Compass,
      label: 'Home dimension',
      value: String(s.topDimension).replace(/_/g, ' '),
    },
    s.topDay && {
      icon: CalendarDays,
      label: 'Most active day',
      value: formatDay(s.topDay),
    },
    s.topHour != null && {
      icon: Clock,
      label: 'Prime time',
      value: formatHour(s.topHour),
    },
  ].filter(Boolean) as { icon: typeof Clock; label: string; value: string; sub?: string }[]

  return (
    <div className="min-h-screen relative overflow-x-hidden">
      {}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-52 -left-52 w-[40rem] h-[40rem] rounded-full bg-emerald-500/10 blur-[140px]" />
        <div className="absolute top-1/3 -right-52 w-[40rem] h-[40rem] rounded-full bg-violet-500/10 blur-[140px]" />
      </div>

      <div className="relative max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12">
        {}
        <div className="flex items-center justify-between mb-10">
          <Link
            to="/"
            className="inline-flex items-center gap-2 text-surface-300 hover:text-white transition-colors text-sm"
          >
            <ArrowLeft className="w-4 h-4" />
            New search
          </Link>
          <div className="flex items-center gap-2">
            <button
              onClick={onReplay}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-white text-sm font-medium hover:bg-white/10 transition-colors"
            >
              <RotateCcw className="w-4 h-4" />
              Replay
            </button>
            <button
              onClick={handleShare}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-white text-black text-sm font-semibold hover:scale-[1.03] active:scale-95 transition-transform"
            >
              <Share2 className="w-4 h-4" />
              Share
            </button>
            <button
              onClick={handleCopy}
              aria-label="Copy link"
              className="p-2.5 rounded-xl bg-white/5 border border-white/10 text-white hover:bg-white/10 transition-colors"
            >
              {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
            </button>
          </div>
        </div>

        {}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="flex flex-col sm:flex-row items-center sm:items-end gap-6 mb-12"
        >
          <PlayerAvatar
            uuid={wrapped.playerId}
            name={wrapped.playerName}
            size={128}
            className="rounded-3xl shadow-2xl shadow-black/40"
          />
          <div className="text-center sm:text-left">
            <p className="text-xs uppercase tracking-[0.3em] text-surface-400 mb-2">Season {seasonName}</p>
            <h1 className="font-display font-bold text-4xl sm:text-5xl tracking-tight leading-none mb-3">
              {wrapped.playerName}
            </h1>
            <div className="flex flex-wrap items-center justify-center sm:justify-start gap-3">
              <span className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-gradient-to-r from-gold-500/20 to-emerald-500/20 border border-white/10 text-gold-400 text-sm font-semibold">
                <Trophy className="w-4 h-4" />
                {wrapped.archetype}
              </span>
              {generated && <span className="text-surface-400 text-sm">Generated {generated}</span>}
            </div>
          </div>
        </motion.div>

        <p className="text-surface-300 text-lg max-w-2xl leading-relaxed mb-12">{wrapped.archetypeDescription}</p>

        {}
        <Section title="By the numbers" subtitle="Everything you racked up this season">
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {STAT_DEFS.map((def, i) => {
              const sub = def.sub?.(s)
              return (
                <motion.div
                  key={def.key}
                  initial={{ opacity: 0, y: 16 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.4, delay: Math.min(i * 0.04, 0.4) }}
                  className="glass-card p-5"
                >
                  <p className="text-surface-400 text-xs uppercase tracking-wider mb-2">{def.label}</p>
                  <p className="font-display font-bold text-2xl sm:text-3xl text-white leading-tight">
                    {def.format(s)}
                  </p>
                  {sub && <p className="text-surface-500 text-xs mt-1.5">{sub}</p>}
                </motion.div>
              )
            })}
          </div>
        </Section>

        {}
        <Section title="How you rank" subtitle="Compared to every other player this season">
          {percentiles.length > 0 ? (
            <div className="glass-card p-6 space-y-5">
              {percentiles
                .slice()
                .sort((a, b) => b[1] - a[1])
                .map(([key, percentile], i) => (
                  <div key={key}>
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-surface-300 text-sm">{STAT_LABELS[key] ?? key.replace(/_/g, ' ')}</span>
                      <span className="text-white font-semibold text-sm">Top {topPercent(percentile)}%</span>
                    </div>
                    <div className="h-2.5 bg-surface-700/60 rounded-full overflow-hidden">
                      <motion.div
                        className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-cyan-400"
                        initial={{ width: 0 }}
                        whileInView={{ width: `${percentile}%` }}
                        viewport={{ once: true }}
                        transition={{ duration: 1, delay: 0.1 + i * 0.06, ease: 'easeOut' }}
                      />
                    </div>
                  </div>
                ))}
            </div>
          ) : (
            <div className="glass-card p-8 text-center text-surface-400">
              Not enough players have played this season to rank you yet.
            </div>
          )}
        </Section>

        {}
        {highlights.length > 0 && (
          <Section title="Season highlights" subtitle="The moments and habits that stood out">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {highlights.map((h, i) => (
                <motion.div
                  key={h.label}
                  initial={{ opacity: 0, y: 16 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.4, delay: i * 0.05 }}
                  className="glass-card p-5 flex items-start gap-4"
                >
                  <div className="w-10 h-10 shrink-0 rounded-xl bg-gradient-to-br from-emerald-500/80 to-violet-500/80 flex items-center justify-center">
                    <h.icon className="w-5 h-5 text-white" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-surface-400 text-xs uppercase tracking-wider mb-1">{h.label}</p>
                    <p className="text-white font-semibold capitalize truncate">{h.value}</p>
                    {h.sub && <p className="text-surface-500 text-xs mt-1">{h.sub}</p>}
                  </div>
                </motion.div>
              ))}
            </div>
          </Section>
        )}

        {}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-16">
          <div className="glass-card p-6">
            <h3 className="font-display font-bold text-xl mb-5 flex items-center gap-2">
              <Trophy className="w-5 h-5 text-gold-400" />
              Achievements
            </h3>
            {wrapped.achievements.length > 0 ? (
              <div className="flex flex-wrap gap-2.5">
                {wrapped.achievements.map(a => (
                  <span
                    key={a}
                    className="px-3.5 py-1.5 rounded-full  border  ring-1  text-sm font-medium"
                  >
                    {a}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-surface-400 text-sm">No achievements yet this season.</p>
            )}
          </div>

          <div className="glass-card p-6">
            <h3 className="font-display font-bold text-xl mb-5 flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-emerald-400" />
              Milestones
            </h3>
            {wrapped.milestones.length > 0 ? (
              <ol className="relative border-l border-surface-700/60 ml-2 space-y-5">
                {wrapped.milestones.map(m => (
                  <li key={m} className="pl-6">
                    <span className="absolute -left-[5px] mt-1.5 w-2.5 h-2.5 rounded-full bg-emerald-400" />
                    <p className="text-surface-300 text-sm">{m}</p>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="text-surface-400 text-sm">Your journey is just getting started.</p>
            )}
          </div>
        </div>

        <div className="text-center pb-8">
          <button
            onClick={onReplay}
            className="inline-flex items-center gap-2 px-8 py-4 rounded-full bg-white text-black font-semibold hover:scale-105 active:scale-95 transition-transform"
          >
            <RotateCcw className="w-5 h-5" />
            Watch your Wrapped again
          </button>
          <p className="text-surface-500 text-sm mt-6">SMP Wrapped · Season {seasonName}</p>
        </div>
      </div>
    </div>
  )
}

function Section({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <section className="mb-16">
      <div className="mb-6">
        <h2 className="font-display font-bold text-2xl sm:text-3xl tracking-tight">{title}</h2>
        {subtitle && <p className="text-surface-400 mt-1">{subtitle}</p>}
      </div>
      {children}
    </section>
  )
}
