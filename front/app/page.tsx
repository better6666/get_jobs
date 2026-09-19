'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import {
  BiBarChartAlt2,
  BiCheckShield,
  BiHistory,
  BiTrendingUp,
  BiSearch,
  BiSliderAlt,
  BiBrain,
  BiRightArrowAlt,
  BiCheckCircle,
  BiErrorCircle,
  BiInfoCircle,
  BiRefresh,
  BiSend,
  BiBuilding,
  BiBriefcase
} from 'react-icons/bi'
import { motion } from 'framer-motion'
import PageHeader from './components/PageHeader'
import { apiUrl } from '@/lib/api'

interface FunnelData {
  totalDiscovered: number
  totalFiltered: number
  totalReviewPending: number
  totalApplied: number
  totalHrRead: number
  totalHrReplied: number
  totalInterview: number
  totalOffer: number
  rates: {
    applyRate: number
    hrReplyRate: number
    interviewRate: number
    offerRate: number
  }
  platformApplied: Record<string, number>
}

interface StrategyConfig {
  runMode: string
  autoApplyThreshold: number
  reviewMinThreshold: number
  maxJobsPerCompanyPerDay: number
  dedupDays: number
  enableAiGreeting: number
  enableRiskBlock: number
}

export default function DashboardPage() {
  const [funnel, setFunnel] = useState<FunnelData | null>(null)
  const [strategy, setStrategy] = useState<StrategyConfig | null>(null)
  const [reviewJobs, setReviewJobs] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [modeSwitching, setModeSwitching] = useState(false)

  // 诊断测试器表单
  const [diagTitle, setDiagTitle] = useState('Java / 全栈开发工程师')
  const [diagComp, setDiagComp] = useState('科技有限公司')
  const [diagSalary, setDiagSalary] = useState('18k-30k·14薪')
  const [diagDegree, setDiagDegree] = useState('本科优先，大专亦可')
  const [diagExp, setDiagExp] = useState('3-5年经验')
  const [diagJd, setDiagJd] = useState('1. 熟练掌握Java、Spring Boot、Next.js开发；\n2. 具备Playwright或自动化流程设计经验优先；\n3. 良好的系统架构与自驱力，具备AI集成落地经验者更佳。')
  const [diagLoading, setDiagLoading] = useState(false)
  const [diagResult, setDiagResult] = useState<any | null>(null)

  const loadData = async () => {
    setLoading(true)
    try {
      const [funnelRes, stratRes, revRes] = await Promise.all([
        fetch(apiUrl('/api/agent/funnel')).then(r => r.json()),
        fetch(apiUrl('/api/agent/strategy')).then(r => r.json()),
        fetch(apiUrl('/api/agent/review-queue?page=1&size=4')).then(r => r.json()),
      ])
      if (funnelRes.success) setFunnel(funnelRes.data)
      if (stratRes.success) setStrategy(stratRes.data)
      if (revRes.success) setReviewJobs(revRes.data || [])
    } catch (e) {
      console.error('加载看板数据异常', e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [])

  const handleSwitchMode = async (mode: string) => {
    setModeSwitching(true)
    try {
      const res = await fetch(apiUrl('/api/agent/strategy/switch-mode'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode }),
      }).then(r => r.json())
      if (res.success) {
        setStrategy(res.data)
      }
    } catch (e) {
      console.error(e)
    } finally {
      setModeSwitching(false)
    }
  }

  const handleRunDiagnosis = async () => {
    setDiagLoading(true)
    try {
      const res = await fetch(apiUrl('/api/agent/match-preview'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          jobTitle: diagTitle,
          company: diagComp,
          salary: diagSalary,
          degree: diagDegree,
          experience: diagExp,
          jobDescription: diagJd,
        }),
      }).then(r => r.json())
      if (res.success) {
        setDiagResult(res.data)
      }
    } catch (e) {
      console.error(e)
    } finally {
      setDiagLoading(false)
    }
  }

  return (
    <div className="max-w-7xl mx-auto pb-12">
      <PageHeader
        icon={<BiBarChartAlt2 />}
        title="AI 智能求职指挥中心"
        subtitle="基于 10 维可解释打分与全生命周期漏斗，将盲目海投转变为高转化率的精准出击"
        actions={
          <div className="flex items-center gap-3">
            <button
              onClick={loadData}
              disabled={loading}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-neutral-700 hover:bg-slate-50 dark:hover:bg-neutral-800 text-xs font-medium text-slate-700 dark:text-neutral-300"
            >
              <BiRefresh className={`text-base ${loading ? 'animate-spin' : ''}`} />
              刷新数据
            </button>
            <Link
              href="/review-queue"
              className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-medium shadow-sm"
            >
              <BiCheckShield className="text-base" />
              人工复核中心 ({funnel?.totalReviewPending ?? 0})
            </Link>
          </div>
        }
      />

      {/* 策略模式切换栏 */}
      <div className="mb-6 p-4 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 flex flex-wrap items-center justify-between gap-4 shadow-xs">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400">
            <BiSliderAlt className="text-xl" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-bold text-slate-800 dark:text-white">当前策略模式：</span>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300">
                {strategy?.runMode === 'SAFE' ? '保守模式 (Safe)' : strategy?.runMode === 'AGGRESSIVE' ? '积极模式 (Aggressive)' : '稳健平衡模式 (Balanced)'}
              </span>
            </div>
            <p className="text-xs text-slate-500 dark:text-neutral-400 mt-0.5">
              自动投递阈值: <span className="font-semibold text-slate-700 dark:text-neutral-300">{strategy?.autoApplyThreshold ?? 78}分</span> | 
              人工复核区间: <span className="font-semibold text-slate-700 dark:text-neutral-300">{strategy?.reviewMinThreshold ?? 60} - {(strategy?.autoApplyThreshold ?? 78) - 1}分</span> | 
              企业单日上限: <span className="font-semibold text-slate-700 dark:text-neutral-300">{strategy?.maxJobsPerCompanyPerDay ?? 2}次</span>
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs text-slate-500 mr-1">快捷切模:</span>
          {(['SAFE', 'BALANCED', 'AGGRESSIVE'] as const).map(mode => (
            <button
              key={mode}
              onClick={() => handleSwitchMode(mode)}
              disabled={modeSwitching || strategy?.runMode === mode}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                strategy?.runMode === mode
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-200'
              }`}
            >
              {mode === 'SAFE' ? '保守 (85分)' : mode === 'AGGRESSIVE' ? '积极 (70分)' : '稳健 (78分)'}
            </button>
          ))}
          <Link
            href="/strategy"
            className="text-xs text-blue-600 dark:text-blue-400 hover:underline ml-2"
          >
            自定义参数 →
          </Link>
        </div>
      </div>

      {/* 8大核心 KPI 漏斗卡片 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        {[
          { label: '发现岗位总数', value: funnel?.totalDiscovered ?? 0, color: 'text-slate-700 dark:text-slate-200', bg: 'bg-slate-50 dark:bg-neutral-800/40', tag: '数据基数' },
          { label: '硬性过滤/高危拦截', value: funnel?.totalFiltered ?? 0, color: 'text-rose-600 dark:text-rose-400', bg: 'bg-rose-50/50 dark:bg-rose-950/20', tag: '有效减负' },
          { label: '待人工复核', value: funnel?.totalReviewPending ?? 0, color: 'text-amber-600 dark:text-amber-400', bg: 'bg-amber-50/50 dark:bg-amber-950/20', tag: '高质量潜质' },
          { label: '已完成投递', value: funnel?.totalApplied ?? 0, color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-50/50 dark:bg-blue-950/20', tag: '有效出击' },
          { label: 'HR 已读', value: funnel?.totalHrRead ?? 0, color: 'text-cyan-600 dark:text-cyan-400', bg: 'bg-cyan-50/50 dark:bg-cyan-950/20', tag: '引起关注' },
          { label: 'HR 回复/沟通', value: funnel?.totalHrReplied ?? 0, color: 'text-emerald-600 dark:text-emerald-400', bg: 'bg-emerald-50/50 dark:bg-emerald-950/20', tag: '建立连接' },
          { label: '面试邀请/进行中', value: funnel?.totalInterview ?? 0, color: 'text-indigo-600 dark:text-indigo-400', bg: 'bg-indigo-50/50 dark:bg-indigo-950/20', tag: '核心转化' },
          { label: '收获 Offer', value: funnel?.totalOffer ?? 0, color: 'text-purple-600 dark:text-purple-400', bg: 'bg-purple-50/50 dark:bg-purple-950/20', tag: '最终成果' },
        ].map((kpi, idx) => (
          <motion.div
            key={kpi.label}
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: idx * 0.05, duration: 0.3 }}
            className={`p-4 rounded-xl border border-slate-200/80 dark:border-neutral-800 ${kpi.bg} hover:shadow-sm transition-shadow duration-200`}
          >
            <div className="flex items-center justify-between text-xs text-slate-500 dark:text-neutral-400 mb-1">
              <span>{kpi.label}</span>
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/80 dark:bg-neutral-800 font-medium">
                {kpi.tag}
              </span>
            </div>
            <div className={`text-2xl font-black tabular-nums tracking-tight ${kpi.color}`}>
              {kpi.value}
            </div>
          </motion.div>
        ))}
      </div>

      {/* 漏斗流转比率 & 平台分布 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
        {/* 全流程转化漏斗 */}
        <div className="lg:col-span-2 p-5 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-bold text-slate-900 dark:text-white flex items-center gap-2">
              <BiTrendingUp className="text-blue-600" />
              求职全链路转化漏斗
            </h3>
            <span className="text-xs text-slate-400">拒绝盲目海投，数据化验证策略</span>
          </div>

          <div className="space-y-4">
            <div>
              <div className="flex justify-between text-xs mb-1">
                <span className="text-slate-600 dark:text-neutral-400 font-medium">1. 投递转化率 (投递数 / 发现总数)</span>
                <span className="font-bold text-slate-800 dark:text-neutral-200">{funnel?.rates?.applyRate ?? 0}%</span>
              </div>
              <div className="h-3 bg-slate-100 dark:bg-neutral-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-blue-600 rounded-full transition-all duration-500"
                  style={{ width: `${Math.min(100, funnel?.rates?.applyRate ?? 0)}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs mb-1">
                <span className="text-slate-600 dark:text-neutral-400 font-medium">2. HR 回复率 (收到回复 / 投递数)</span>
                <span className="font-bold text-emerald-600 dark:text-emerald-400">{funnel?.rates?.hrReplyRate ?? 0}%</span>
              </div>
              <div className="h-3 bg-slate-100 dark:bg-neutral-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-emerald-500 rounded-full transition-all duration-500"
                  style={{ width: `${Math.min(100, funnel?.rates?.hrReplyRate ?? 0)}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs mb-1">
                <span className="text-slate-600 dark:text-neutral-400 font-medium">3. 面试转化率 (面试邀请 / 投递数)</span>
                <span className="font-bold text-indigo-600 dark:text-indigo-400">{funnel?.rates?.interviewRate ?? 0}%</span>
              </div>
              <div className="h-3 bg-slate-100 dark:bg-neutral-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-indigo-500 rounded-full transition-all duration-500"
                  style={{ width: `${Math.min(100, funnel?.rates?.interviewRate ?? 0)}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs mb-1">
                <span className="text-slate-600 dark:text-neutral-400 font-medium">4. Offer 终局转化率 (Offer / 面试总数)</span>
                <span className="font-bold text-purple-600 dark:text-purple-400">{funnel?.rates?.offerRate ?? 0}%</span>
              </div>
              <div className="h-3 bg-slate-100 dark:bg-neutral-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-purple-500 rounded-full transition-all duration-500"
                  style={{ width: `${Math.min(100, funnel?.rates?.offerRate ?? 0)}%` }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* 平台投递分布 */}
        <div className="p-5 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs flex flex-col justify-between">
          <div>
            <h3 className="font-bold text-slate-900 dark:text-white mb-4 flex items-center gap-2">
              <BiBriefcase className="text-indigo-600" />
              平台投递分布
            </h3>
            <div className="space-y-3">
              {[
                { key: 'boss', name: 'Boss直聘', color: 'bg-emerald-500' },
                { key: 'liepin', name: '猎聘网', color: 'bg-amber-500' },
                { key: 'job51', name: '前程无忧 51job', color: 'bg-orange-500' },
                { key: 'zhilian', name: '智联招聘', color: 'bg-blue-500' },
              ].map(p => {
                const count = funnel?.platformApplied?.[p.key] ?? 0
                return (
                  <div key={p.key} className="flex items-center justify-between p-2.5 rounded-lg bg-slate-50 dark:bg-neutral-800/60">
                    <div className="flex items-center gap-2">
                      <span className={`w-2.5 h-2.5 rounded-full ${p.color}`} />
                      <span className="text-xs font-medium text-slate-700 dark:text-neutral-300">{p.name}</span>
                    </div>
                    <span className="text-xs font-bold text-slate-900 dark:text-white">{count} 次</span>
                  </div>
                )
              })}
            </div>
          </div>

          <div className="mt-4 pt-3 border-t border-slate-100 dark:border-neutral-800">
            <Link
              href="/applications"
              className="flex items-center justify-center gap-1 text-xs text-blue-600 dark:text-blue-400 font-medium hover:underline"
            >
              查看投递流转追踪明细 <BiRightArrowAlt />
            </Link>
          </div>
        </div>
      </div>

      {/* 实时匹配诊断器 (Live Job Match Diagnostics) */}
      <div className="p-6 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs mb-8">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-lg bg-purple-50 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400">
              <BiBrain className="text-xl" />
            </div>
            <div>
              <h3 className="font-bold text-slate-900 dark:text-white">实时岗位匹配与 AI 打招呼语评测台</h3>
              <p className="text-xs text-slate-500 dark:text-neutral-400">输入任意目标岗位信息，即刻预览 10 维可解释打分、简历匹配与真实打招呼语</p>
            </div>
          </div>
          <button
            onClick={handleRunDiagnosis}
            disabled={diagLoading}
            className="flex items-center gap-2 px-5 py-2.5 rounded-lg bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold shadow-xs transition-all active:scale-[0.98]"
          >
            {diagLoading ? <BiRefresh className="animate-spin text-base" /> : <BiSend className="text-base" />}
            {diagLoading ? '正在综合研判中...' : '开始匹配诊断'}
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-neutral-400 mb-1">目标岗位名称</label>
            <input
              type="text"
              value={diagTitle}
              onChange={e => setDiagTitle(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800/80 text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 transition-colors"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-neutral-400 mb-1">企业名称</label>
            <input
              type="text"
              value={diagComp}
              onChange={e => setDiagComp(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800/80 text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 transition-colors"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-neutral-400 mb-1">薪资范围</label>
            <input
              type="text"
              value={diagSalary}
              onChange={e => setDiagSalary(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800/80 text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 transition-colors"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-neutral-400 mb-1">学历与经验要求</label>
            <div className="grid grid-cols-2 gap-2">
              <input
                type="text"
                value={diagDegree}
                onChange={e => setDiagDegree(e.target.value)}
                placeholder="学历要求"
                className="w-full px-3 py-2 text-xs rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800/80 text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 transition-colors"
              />
              <input
                type="text"
                value={diagExp}
                onChange={e => setDiagExp(e.target.value)}
                placeholder="经验要求"
                className="w-full px-3 py-2 text-xs rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800/80 text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 transition-colors"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 dark:text-neutral-400 mb-1">岗位职责与任职要求 (JD)</label>
            <textarea
              rows={3}
              value={diagJd}
              onChange={e => setDiagJd(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800/80 text-slate-900 dark:text-white placeholder:text-slate-400 dark:placeholder:text-neutral-500 focus:outline-none focus:ring-2 focus:ring-purple-500/20 focus:border-purple-500 transition-colors"
            />
          </div>
        </div>

        {/* 评测诊断结果展开区 */}
        {diagResult && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            className="mt-6 pt-5 border-t border-slate-200 dark:border-neutral-800"
          >
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* 总分与路由建议 */}
              <div className="p-4 rounded-xl bg-purple-50/50 dark:bg-purple-950/20 border border-purple-200 dark:border-purple-800/40">
                <div className="text-xs font-semibold text-purple-700 dark:text-purple-300 mb-1">综合匹配得分</div>
                <div className="flex items-baseline gap-2 mb-2">
                  <span className="text-4xl font-black text-purple-700 dark:text-purple-300">
                    {diagResult.scoreResult?.totalScore ?? 0}
                  </span>
                  <span className="text-xs text-purple-600/80">/ 100 分</span>
                  <span className="ml-auto px-2 py-0.5 rounded text-xs font-bold bg-purple-200 text-purple-800 dark:bg-purple-900/60 dark:text-purple-200">
                    {diagResult.scoreResult?.matchLevel}
                  </span>
                </div>

                <div className="mt-3 text-xs space-y-1.5">
                  <div className="flex justify-between">
                    <span className="text-slate-600 dark:text-neutral-400">策略流向判定:</span>
                    <span className="font-bold text-slate-900 dark:text-white">{diagResult.routeVerdict}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-600 dark:text-neutral-400">风险等级:</span>
                    <span className={`font-bold ${diagResult.scoreResult?.riskLevel === 'HIGH' ? 'text-red-600' : 'text-emerald-600'}`}>
                      {diagResult.scoreResult?.riskLevel}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-600 dark:text-neutral-400">选用简历版本:</span>
                    <span className="font-bold text-blue-600 dark:text-blue-400">{diagResult.selectedResume?.versionName ?? '标准简历'}</span>
                  </div>
                </div>

                {/* 打招呼语 */}
                <div className="mt-4 p-3 rounded-lg bg-white dark:bg-neutral-800 border border-purple-100 dark:border-neutral-700">
                  <div className="text-[11px] font-semibold text-slate-500 dark:text-neutral-400 mb-1 flex items-center gap-1">
                    <BiSend className="text-purple-600" /> AI 零幻觉定制打招呼语 (20-80字)
                  </div>
                  <p className="text-xs text-slate-800 dark:text-neutral-200 leading-relaxed font-medium">
                    "{diagResult.generatedGreeting}"
                  </p>
                </div>
              </div>

              {/* 10 维可解释打分卡 */}
              <div className="p-4 rounded-xl bg-slate-50 dark:bg-neutral-800/50 border border-slate-200 dark:border-neutral-800">
                <div className="text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-3">
                  10 维度打分明细拆解 (拒绝黑盒)
                </div>
                <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                  {Object.entries(diagResult.scoreResult?.breakdown || {}).map(([dim, item]: [string, any]) => (
                    <div key={dim} className="text-xs">
                      <div className="flex justify-between text-[11px] mb-0.5">
                        <span className="text-slate-600 dark:text-neutral-400">{dim}</span>
                        <span className="font-semibold text-slate-800 dark:text-neutral-200">{item.score} / {item.maxScore}分</span>
                      </div>
                      <div className="h-1.5 bg-slate-200 dark:bg-neutral-700 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-purple-500 rounded-full"
                          style={{ width: `${(item.score / item.maxScore) * 100}%` }}
                        />
                      </div>
                      <div className="text-[10px] text-slate-400 mt-0.5 truncate">{item.reason}</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* 优势与风险清单 */}
              <div className="p-4 rounded-xl bg-slate-50 dark:bg-neutral-800/50 border border-slate-200 dark:border-neutral-800 flex flex-col justify-between">
                <div>
                  <div className="text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-2">
                    契合优势与风险特征
                  </div>
                  <div className="mb-3">
                    <div className="text-[11px] text-emerald-600 dark:text-emerald-400 font-medium mb-1">✓ 核心优势清单</div>
                    <ul className="space-y-1">
                      {(diagResult.scoreResult?.strengths || []).map((s: string, i: number) => (
                        <li key={i} className="text-[11px] text-slate-600 dark:text-neutral-400 flex items-start gap-1">
                          <span className="text-emerald-500">•</span> {s}
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div>
                    <div className="text-[11px] text-amber-600 dark:text-amber-400 font-medium mb-1">⚠ 风险或差距清单</div>
                    <ul className="space-y-1">
                      {(diagResult.scoreResult?.risks || []).map((r: string, i: number) => (
                        <li key={i} className="text-[11px] text-slate-600 dark:text-neutral-400 flex items-start gap-1">
                          <span className="text-amber-500">•</span> {r}
                        </li>
                      ))}
                      {(diagResult.scoreResult?.risks || []).length === 0 && (
                        <li className="text-[11px] text-slate-400 italic">未发现明显风险</li>
                      )}
                    </ul>
                  </div>
                </div>

                <div className="mt-3 text-[11px] text-slate-400 text-right">
                  硬性过滤检验: {diagResult.filterResult?.passed ? '✓ 通过' : '✕ 未通过'}
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </div>

      {/* 人工复核等待队列预览 */}
      <div className="p-6 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <BiCheckShield className="text-xl text-amber-500" />
            <div>
              <h3 className="font-bold text-slate-900 dark:text-white">待人工复核边缘岗位队列</h3>
              <p className="text-xs text-slate-500 dark:text-neutral-400">处于 60 - {(strategy?.autoApplyThreshold ?? 78) - 1} 分区间或命中同企业频次限制的岗位，等待您一键确认或跳过</p>
            </div>
          </div>
          <Link
            href="/review-queue"
            className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
          >
            进入复核专区 ({reviewJobs.length}) <BiRightArrowAlt />
          </Link>
        </div>

        {reviewJobs.length === 0 ? (
          <div className="text-center py-8 text-slate-400 text-xs bg-slate-50 dark:bg-neutral-800/40 rounded-xl">
            暂无待复核岗位，系统策略运行顺畅
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {reviewJobs.map((job) => (
              <div
                key={job.id}
                className="p-3.5 rounded-xl border border-slate-200/80 dark:border-neutral-800 bg-slate-50/50 dark:bg-neutral-800/30 flex items-center justify-between gap-3 hover:border-slate-300 dark:hover:border-neutral-700 hover:shadow-sm transition-all duration-200"
              >
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-bold text-sm text-slate-900 dark:text-white">{job.jobTitle}</span>
                    <span className="text-xs font-extrabold px-2 py-0.5 rounded bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300">
                      {job.matchScore}分
                    </span>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-200 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300">
                      {job.platform}
                    </span>
                  </div>
                  <div className="text-xs text-slate-500 dark:text-neutral-400 flex items-center gap-2">
                    <BiBuilding /> {job.company} | {job.salary} | {job.location}
                  </div>
                  <div className="text-[11px] text-amber-700 dark:text-amber-400 mt-1">
                    复核原因: {job.notes || '处于复核分区间'}
                  </div>
                </div>

                <Link
                  href="/review-queue"
                  className="px-3 py-1.5 rounded-lg bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-700 text-xs font-semibold text-blue-600 dark:text-blue-400 hover:bg-blue-50"
                >
                  去复核
                </Link>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
