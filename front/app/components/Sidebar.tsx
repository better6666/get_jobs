'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useState } from 'react'
import {
  BiEnvelope,
  BiBriefcase,
  BiSearch,
  BiTask,
  BiUserCircle,
  BiBrain,
  BiMoon,
  BiSun,
  BiBarChartAlt2,
  BiCheckShield,
  BiHistory,
  BiUserCheck,
  BiDetail,
  BiGridAlt,
  BiSliderAlt
} from 'react-icons/bi'
import { motion } from 'framer-motion'
import { useTheme } from 'next-themes'
import { API_BASE } from '@/lib/api'

export default function Sidebar() {
  const pathname = usePathname()
  const { theme, setTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  // 健康检查状态：up / degraded / down / unknown
  const [health, setHealth] = useState<'up' | 'degraded' | 'down' | 'unknown'>('unknown')
  const [checking, setChecking] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  useEffect(() => {
    let interval: NodeJS.Timeout | null = null

    const check = async () => {
      if (checking) return
      setChecking(true)
      const baseUrl = process.env.API_BASE_URL || `${API_BASE}`

      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), 3000)
      try {
        let res = await fetch(`${baseUrl}/api/health`, { signal: controller.signal })
        if (res.status === 404) {
          res = await fetch(`${baseUrl}/actuator/health`, { signal: controller.signal })
        }
        if (!res.ok) throw new Error(`status ${res.status}`)
        const data = await res.json()
        const statusRaw = (data.status || data.state || '').toString().toUpperCase()
        if (statusRaw === 'UP' || statusRaw === 'HEALTHY') {
          setHealth('up')
        } else if (statusRaw === 'DEGRADED' || statusRaw === 'WARN') {
          setHealth('degraded')
        } else {
          setHealth('down')
        }
      } catch (e) {
        setHealth('unknown')
      } finally {
        clearTimeout(timeout)
        setChecking(false)
      }
    }

    check()
    interval = setInterval(check, 30000)
    return () => {
      if (interval) clearInterval(interval)
    }
  }, [checking])

  const coreGroup = [
    { href: '/', icon: BiBarChartAlt2, label: '漏斗看板' },
    { href: '/review-queue', icon: BiCheckShield, label: '人工复核中心' },
    { href: '/applications', icon: BiHistory, label: '投递全流程' },
  ]

  const assetGroup = [
    { href: '/profile', icon: BiUserCheck, label: '候选人画像' },
    { href: '/resumes', icon: BiDetail, label: '多版本简历' },
    { href: '/keywords', icon: BiGridAlt, label: '关键词矩阵' },
  ]

  const strategyGroup = [
    { href: '/strategy', icon: BiSliderAlt, label: '投递策略' },
    { href: '/ai-config', icon: BiBrain, label: 'AI配置' },
    { href: '/env-config', icon: BiEnvelope, label: '环境配置' },
  ]

  const platformGroup = [
    { href: '/boss', icon: BiBriefcase, label: 'Boss直聘' },
    { href: '/liepin', icon: BiSearch, label: '猎聘' },
    { href: '/51job', icon: BiTask, label: '51job' },
    { href: '/zhilian', icon: BiUserCircle, label: '智联招聘' },
  ]

  const renderNavSection = (title: string, items: typeof coreGroup) => (
    <div className="mb-4">
      <div className="px-3 mb-2 text-[10px] font-bold text-slate-400/80 dark:text-neutral-500 uppercase tracking-[0.08em] flex items-center gap-2 before:content-[''] before:w-3 before:h-px before:bg-slate-300 dark:before:bg-neutral-700">
        {title}
      </div>
      <div className="space-y-0.5">
        {items.map((item) => {
          const Icon = item.icon
          const isActive = pathname === item.href
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`
                group flex items-center gap-3 px-3 py-2 rounded-lg transition-all duration-200
                ${isActive
                  ? 'bg-blue-50/80 dark:bg-blue-900/15 text-blue-600 dark:text-blue-400 font-medium relative before:absolute before:left-0 before:top-1 before:bottom-1 before:w-[3px] before:rounded-full before:bg-blue-600 dark:before:bg-blue-400'
                  : 'text-slate-600 dark:text-neutral-400 hover:bg-slate-50 dark:hover:bg-neutral-800 hover:text-slate-900 dark:hover:text-white'
                }
              `}
            >
              <Icon className={`text-lg ${isActive ? 'text-blue-600 dark:text-blue-400' : 'text-slate-400 dark:text-neutral-500 group-hover:text-slate-600 dark:group-hover:text-neutral-300'}`} />
              <span className="text-sm">{item.label}</span>
            </Link>
          )
        })}
      </div>
    </div>
  )

  return (
    <motion.div
      initial={{ x: -100, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      transition={{ duration: 0.5, ease: "easeOut" }}
      className="fixed left-0 top-0 h-full w-64 bg-white dark:bg-neutral-900 border-r border-slate-200 dark:border-neutral-800 z-50 flex flex-col"
    >
      {/* 侧边栏头部 */}
      <motion.div
        initial={{ y: -20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ delay: 0.2, duration: 0.5 }}
        className="p-5 border-b border-slate-200 dark:border-neutral-800"
      >
        <div className="flex items-center gap-3 mb-1">
          <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-blue-600 via-blue-600 to-indigo-500 flex items-center justify-center text-white font-bold text-xl shadow-sm">
            GJ
          </div>
          <div>
            <h1 className="text-lg font-bold text-slate-900 dark:text-white">Get Jobs</h1>
            <p className="text-blue-600 dark:text-blue-400 text-xs font-medium">AI 智能求职 Agent</p>
          </div>
        </div>

        {/* 状态指示器 */}
        <div className="mt-3 flex items-center gap-2 text-xs px-3 py-1.5 rounded-full bg-slate-100/80 dark:bg-neutral-800/60 text-slate-600 dark:text-neutral-400 border border-slate-200/60 dark:border-neutral-700/60">
          <div
            className={`w-2 h-2 rounded-full ${
              health === 'up'
                ? 'bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.6)]'
                : health === 'degraded'
                ? 'bg-yellow-500 shadow-[0_0_8px_rgba(234,179,8,0.6)]'
                : health === 'down'
                ? 'bg-red-500 shadow-[0_0_8px_rgba(239,68,68,0.6)]'
                : 'bg-slate-400'
            }`}
          ></div>
          <span className="font-medium">
            {health === 'up'
              ? 'Agent 引擎就绪'
              : health === 'degraded'
              ? '服务部分降级'
              : health === 'down'
              ? '服务连接异常'
              : '正在连接服务...'}
          </span>
        </div>

        {/* 主题切换器 */}
        {mounted && (
          <button
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className="mt-3 w-full flex items-center justify-center gap-2 px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 dark:bg-neutral-800 dark:hover:bg-neutral-700 text-slate-700 dark:text-neutral-300 text-xs font-medium transition-colors duration-200"
          >
            {theme === 'dark' ? (
              <>
                <BiSun className="text-sm text-amber-500" />
                <span>浅色模式</span>
              </>
            ) : (
              <>
                <BiMoon className="text-sm text-indigo-500" />
                <span>深色模式</span>
              </>
            )}
          </button>
        )}
      </motion.div>

      {/* 导航菜单 */}
      <nav className="flex-1 p-3 overflow-y-auto no-scrollbar">
        {renderNavSection('核心工作台', coreGroup)}
        {renderNavSection('资产与画像', assetGroup)}
        {renderNavSection('策略与引擎', strategyGroup)}
        {renderNavSection('招聘平台', platformGroup)}
      </nav>

      {/* 底部信息 */}
      <div className="p-3.5 border-t border-slate-200/80 dark:border-neutral-800/80 flex items-center justify-between text-[11px] text-slate-400 dark:text-neutral-500 bg-slate-50/50 dark:bg-neutral-900/50">
        <span>Get Jobs v2.0</span>
        <span className="text-green-600 dark:text-green-400 font-mono">Agent Active</span>
      </div>
    </motion.div>
  )
}
