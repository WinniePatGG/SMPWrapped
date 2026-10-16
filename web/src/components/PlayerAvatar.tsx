import { useState } from 'react'
import { motion } from 'framer-motion'

interface PlayerAvatarProps {
  uuid: string
  name: string
  size?: number
  className?: string
}

export default function PlayerAvatar({ uuid, name, size = 48, className = '' }: PlayerAvatarProps) {
  const [imageError, setImageError] = useState(false)
  const avatarUrl = `https://mc-heads.net/avatar/${uuid}/${size}`

  if (imageError) {
    return (
      <div
        className={`rounded-lg bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center font-bold text-white ${className}`}
        style={{ width: size, height: size, fontSize: size * 0.4 }}
      >
        {name.charAt(0).toUpperCase()}
      </div>
    )
  }

  return (
    <motion.img
      src={avatarUrl}
      alt={name}
      className={`rounded-lg ${className}`}
      style={{ width: size, height: size }}
      onError={() => setImageError(true)}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
    />
  )
}
