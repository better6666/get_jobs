"use client"
import { usePathname } from 'next/navigation'
import { ReactNode } from 'react'
import { motion } from 'framer-motion'

export default function ContentArea({ children }: { children: ReactNode }) {
  const pathname = usePathname()

  return (
    <main className="flex-1 ml-64 min-w-0 overflow-x-hidden bg-[#f7f8fa] dark:bg-zinc-950 min-h-screen text-slate-900 dark:text-slate-100">
      <motion.div
        key={pathname}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -10 }}
        transition={{ duration: 0.3, ease: "easeOut" }}
        className="w-full max-w-7xl mx-auto py-6 px-6 lg:px-10 min-w-0"
      >
        {children}
      </motion.div>
    </main>
  )
}
