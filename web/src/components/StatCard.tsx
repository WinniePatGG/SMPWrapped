import { motion } from 'framer-motion'
import { LucideIcon } from 'lucide-react'
import AnimatedCounter from './AnimatedCounter'

interface StatCardProps {
  icon: LucideIcon
  label: string
  value: number
  suffix?: string
  color?: string
  delay?: number
}

export default function StatCard({
  icon: Icon,
  label,
  value,
  suffix = '',
  color = 'emerald',
  delay = 0,
}: StatCardProps) {
  const colorMap: Record<string, string> = {
    emerald: 'from-emerald-500 to-emerald-600 shadow-emerald-500/20',
    cyan: 'from-cyan-500 to-cyan-600 shadow-cyan-500/20',
    violet: 'from-violet-500 to-violet-600 shadow-violet-500/20',
    gold: 'from-gold-500 to-gold-600 shadow-gold-500/20',
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay }}
      className="glass-card-hover p-6"
    >
      <div className={`w-10 h-10 rounded-lg bg-gradient-to-br ${colorMap[color]} flex items-center justify-center mb-4 shadow-lg`}>
        <Icon className="w-5 h-5 text-white" />
      </div>
      <p className="text-surface-400 text-sm mb-1">{label}</p>
      <p className="text-2xl font-display font-bold text-white">
        <AnimatedCounter value={value} />
        {suffix && <span className="text-surface-400 text-lg ml-1">{suffix}</span>}
      </p>
    </motion.div>
  )
}
