'use client'

import { useEffect, useState } from 'react'
import {
  BiSliderAlt,
  BiShieldQuarter,
  BiCheckShield,
  BiRocket,
  BiSave,
  BiRefresh,
  BiCheck,
  BiInfoCircle
} from 'react-icons/bi'
import { motion } from 'framer-motion'
import PageHeader from '../components/PageHeader'
import { apiUrl } from '@/lib/api'

interface StrategyConfig {
  id?: number
  runMode: string
  autoApplyThreshold: number
  reviewMinThreshold: number
  maxJobsPerCompanyPerDay: number
  dedupDays: number
  enableAiGreeting: number
  enableRiskBlock: number
}

export default function StrategyPage() {
  const [config, setConfig] = useState<StrategyConfig>({
    runMode: 'BALANCED',
    autoApplyThreshold: 78,
    reviewMinThreshold: 60,
    maxJobsPerCompanyPerDay: 2,
    dedupDays: 30,
    enableAiGreeting: 1,
    enableRiskBlock: 1,
  })

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [savedMsg, setSavedMsg] = useState(false)

  const loadStrategy = async () => {
    setLoading(true)
    try {
      const res = await fetch(apiUrl('/api/agent/strategy')).then(r => r.json())
      if (res.success && res.data) {
        setConfig(res.data)
      }
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadStrategy()
  }, [])

  const handleSave = async () => {
    setSaving(true)
    try {
      const res = await fetch(apiUrl('/api/agent/strategy'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      }).then(r => r.json())

      if (res.success) {
        setSavedMsg(true)
        setTimeout(() => setSavedMsg(false), 3000)
      }
    } catch (e) {
      console.error(e)
    } finally {
      setSaving(false)
    }
  }

  const handleSelectPreset = (mode: 'SAFE' | 'BALANCED' | 'AGGRESSIVE') => {
    if (mode === 'SAFE') {
      setConfig({
        ...config,
        runMode: 'SAFE',
        autoApplyThreshold: 85,
        reviewMinThreshold: 65,
        maxJobsPerCompanyPerDay: 1,
      })
    } else if (mode === 'AGGRESSIVE') {
      setConfig({
        ...config,
        runMode: 'AGGRESSIVE',
        autoApplyThreshold: 70,
        reviewMinThreshold: 50,
        maxJobsPerCompanyPerDay: 3,
      })
    } else {
      setConfig({
        ...config,
        runMode: 'BALANCED',
        autoApplyThreshold: 78,
        reviewMinThreshold: 60,
        maxJobsPerCompanyPerDay: 2,
      })
    }
  }

  return (
    <div className="max-w-5xl mx-auto pb-12">
      <PageHeader
        icon={<BiSliderAlt />}
        title="投递策略引擎与风控配置"
        subtitle="智能调度求职模式（保守、稳健、积极），精准控制打分阈值、企业频次及查重周期"
        actions={
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold shadow-sm"
          >
            {savedMsg ? <BiCheck className="text-base" /> : <BiSave className="text-base" />}
            {savedMsg ? '策略已生效' : saving ? '正在保存...' : '保存策略配置'}
          </button>
        }
      />

      {/* 预设模式选择卡片 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5 mb-8">
        {[
          {
            mode: 'SAFE',
            title: '保守质量模式',
            sub: '宁缺毋滥，精准点对点',
            icon: BiShieldQuarter,
            color: 'text-indigo-600',
            bg: 'bg-indigo-50/50 dark:bg-indigo-950/20',
            border: 'border-indigo-200 dark:border-indigo-800/40',
            desc: '只投 85 分以上的极高匹配度岗位，单日同企业上限 1 次，最大限度保护账号安全并追求极高回复质量。',
          },
          {
            mode: 'BALANCED',
            title: '稳健平衡模式 (推荐)',
            sub: '兼顾数量与优质转化',
            icon: BiCheckShield,
            color: 'text-blue-600',
            bg: 'bg-blue-50/50 dark:bg-blue-950/20',
            border: 'border-blue-200 dark:border-blue-800/40',
            desc: '自动投递 78 分及以上岗位；60-77 分进入人工复核专区；单日同企业上限 2 次，行业最佳实践。',
          },
          {
            mode: 'AGGRESSIVE',
            title: '积极突破模式',
            sub: '快速起量，快速拿面试',
            icon: BiRocket,
            color: 'text-purple-600',
            bg: 'bg-purple-50/50 dark:bg-purple-950/20',
            border: 'border-purple-200 dark:border-purple-800/40',
            desc: '自动投递门槛下探至 70 分，单日同企业上限 3 次，适合急需快速面试反馈、广泛建立联系的求职阶段。',
          },
        ].map((m) => {
          const isSelected = config.runMode === m.mode
          const Icon = m.icon
          return (
            <div
              key={m.mode}
              onClick={() => handleSelectPreset(m.mode as any)}
              className={`p-5 rounded-xl border-2 cursor-pointer transition-all ${
                isSelected
                  ? 'border-blue-600 shadow-sm bg-white dark:bg-neutral-900 ring-2 ring-blue-500/20'
                  : 'border-slate-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 opacity-80 hover:opacity-100 hover:border-slate-300'
              }`}
            >
              <div className="flex items-center gap-3 mb-2">
                <div className={`p-2 rounded-xl ${m.bg} ${m.color}`}>
                  <Icon className="text-2xl" />
                </div>
                <div>
                  <h3 className="font-bold text-sm text-slate-900 dark:text-white">{m.title}</h3>
                  <p className="text-[11px] text-slate-400">{m.sub}</p>
                </div>
              </div>
              <p className="text-xs text-slate-600 dark:text-neutral-400 leading-relaxed mt-2">
                {m.desc}
              </p>
            </div>
          )
        })}
      </div>

      {/* 详细微调表单 */}
      <div className="p-6 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs space-y-6">
        <h3 className="font-bold text-sm text-slate-900 dark:text-white border-b border-slate-100 dark:border-neutral-800 pb-3 flex items-center gap-2">
          <BiSliderAlt className="text-blue-600" />
          策略参数深度调优
        </h3>

        {/* 自动投递阈值 */}
        <div>
          <div className="flex justify-between text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">
            <span>自动投递评分阈值 (分)</span>
            <span className="text-blue-600 font-bold">{config.autoApplyThreshold} 分</span>
          </div>
          <input
            type="range"
            min={50}
            max={95}
            value={config.autoApplyThreshold}
            onChange={(e) => setConfig({ ...config, autoApplyThreshold: parseInt(e.target.value) })}
            className="w-full accent-blue-600 cursor-pointer"
          />
          <p className="text-[11px] text-slate-400 mt-1">
            当岗位 10 维可解释评分达到或超过此分值时，系统判定为高度契合，自动发起投递与沟通。
          </p>
        </div>

        {/* 人工复核最低阈值 */}
        <div>
          <div className="flex justify-between text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">
            <span>人工复核最低阈值 (分)</span>
            <span className="text-amber-600 font-bold">{config.reviewMinThreshold} 分</span>
          </div>
          <input
            type="range"
            min={40}
            max={85}
            value={config.reviewMinThreshold}
            onChange={(e) => setConfig({ ...config, reviewMinThreshold: parseInt(e.target.value) })}
            className="w-full accent-amber-600 cursor-pointer"
          />
          <p className="text-[11px] text-slate-400 mt-1">
            介于【{config.reviewMinThreshold} 分】与【{config.autoApplyThreshold - 1} 分】之间的边缘岗位，将自动转入「人工复核中心」，由您一键审核。
          </p>
        </div>

        {/* 同企业单日频次限制 */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5 text-xs">
          <div>
            <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">
              同一企业单日投递上限 (次/天)
            </label>
            <input
              type="number"
              min={1}
              max={10}
              value={config.maxJobsPerCompanyPerDay}
              onChange={(e) => setConfig({ ...config, maxJobsPerCompanyPerDay: parseInt(e.target.value) || 1 })}
              className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
            />
            <p className="text-[11px] text-slate-400 mt-1">
              超过单日上限的同公司岗位自动流入待复核队列，彻底杜绝短时间内向同一家公司连发多份简历的刷屏被拒风险。
            </p>
          </div>

          <div>
            <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">
              岗位查重防撞车时间窗 (天)
            </label>
            <input
              type="number"
              min={7}
              max={180}
              value={config.dedupDays}
              onChange={(e) => setConfig({ ...config, dedupDays: parseInt(e.target.value) || 30 })}
              className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
            />
            <p className="text-[11px] text-slate-400 mt-1">
              同一平台同一职位在此时间窗内绝不重复打扰或重复投递。
            </p>
          </div>
        </div>

        {/* 功能开关 */}
        <div className="pt-3 border-t border-slate-100 dark:border-neutral-800 space-y-3">
          <label className="flex items-start gap-3 p-3 rounded-xl bg-slate-50 dark:bg-neutral-800/40 cursor-pointer">
            <input
              type="checkbox"
              checked={config.enableAiGreeting === 1}
              onChange={(e) => setConfig({ ...config, enableAiGreeting: e.target.checked ? 1 : 0 })}
              className="mt-0.5 rounded text-blue-600"
            />
            <div>
              <div className="text-xs font-bold text-slate-800 dark:text-neutral-200">
                启用 AI 专属定制打招呼语 (20-80字精准出击)
              </div>
              <div className="text-[11px] text-slate-400 mt-0.5">
                严格基于求职者真实画像和 JD 痛点提炼针对性优势，拒绝套话；若关闭则使用通用问候模板。
              </div>
            </div>
          </label>

          <label className="flex items-start gap-3 p-3 rounded-xl bg-slate-50 dark:bg-neutral-800/40 cursor-pointer">
            <input
              type="checkbox"
              checked={config.enableRiskBlock === 1}
              onChange={(e) => setConfig({ ...config, enableRiskBlock: e.target.checked ? 1 : 0 })}
              className="mt-0.5 rounded text-blue-600"
            />
            <div>
              <div className="text-xs font-bold text-slate-800 dark:text-neutral-200">
                启用高危招聘欺诈与黑名单强行拦截 (Zero Risk Block)
              </div>
              <div className="text-[11px] text-slate-400 mt-0.5">
                自动侦测培训贷、押金、自费体检、兼职点赞、租号跑分、纯提成诱饵等欺诈陷阱，并在第一时间强制隔离。
              </div>
            </div>
          </label>
        </div>
      </div>
    </div>
  )
}
