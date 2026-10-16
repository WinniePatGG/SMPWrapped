import { Pickaxe } from 'lucide-react'

export default function Footer() {
  return (
    <footer className="border-t border-surface-700/50 bg-surface-950/50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2 text-surface-400 text-sm">
            <Pickaxe className="w-4 h-4" />
            <span>SMPWrapped — Your Minecraft season, beautifully recapped</span>
          </div>
          <div className="text-surface-500 text-sm">
            Built with React, Tailwind CSS, and the Paper API
          </div>
        </div>
      </div>
    </footer>
  )
}
