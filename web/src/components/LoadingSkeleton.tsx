import { motion } from 'framer-motion'

export default function LoadingSkeleton({ className = '' }: { className?: string }) {
  return (
    <div className={`shimmer rounded-xl bg-surface-800/50 ${className}`} />
  )
}

export function PageLoader() {
  return (
    <div className="min-h-[60vh] flex items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-emerald-500 to-cyan-500 animate-pulse" />
        <p className="text-surface-400 text-sm">Loading...</p>
      </div>
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="min-h-[40vh] flex items-center justify-center">
      <div className="text-center glass-card p-8 max-w-md">
        <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-red-500/10 flex items-center justify-center">
          <span className="text-2xl">!</span>
        </div>
        <h3 className="text-lg font-semibold text-white mb-2">Something went wrong</h3>
        <p className="text-surface-400 text-sm mb-4">{message}</p>
        {onRetry && (
          <button onClick={onRetry} className="btn-secondary text-sm">
            Try Again
          </button>
        )}
      </div>
    </div>
  )
}

export function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="min-h-[40vh] flex items-center justify-center">
      <div className="text-center glass-card p-8 max-w-md">
        <h3 className="text-lg font-semibold text-white mb-2">{title}</h3>
        <p className="text-surface-400 text-sm">{description}</p>
      </div>
    </div>
  )
}
