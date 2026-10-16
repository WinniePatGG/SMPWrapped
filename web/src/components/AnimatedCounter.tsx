import { useEffect, useState } from 'react'
import { motion, useSpring, useTransform } from 'framer-motion'

interface AnimatedCounterProps {
  value: number
  duration?: number
  className?: string
  format?: (value: number) => string
}

export default function AnimatedCounter({
  value,
  duration = 2,
  className = '',
  format = (v) => Math.round(v).toLocaleString(),
}: AnimatedCounterProps) {
  const spring = useSpring(0, { duration: duration * 1000, bounce: 0 })
  const display = useTransform(spring, (v) => format(v))

  useEffect(() => {
    spring.set(value)
  }, [value, spring])

  return (
    <motion.span className={className}>
      {display}
    </motion.span>
  )
}
