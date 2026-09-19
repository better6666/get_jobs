'use client'

import { useEffect, useState } from 'react'
import {
  BiCheckShield,
  BiBuilding,
  BiMapPin,
  BiDollarCircle,
  BiSend,
  BiX,
  BiBlock,
  BiRefresh,
  BiChevronLeft,
  BiChevronRight,
  BiCheck,
  BiLinkExternal,
  BiInfoCircle
} from 'react-icons/bi'
import { motion } from 'framer-motion'
import PageHeader from '../components/PageHeader'
import { apiUrl } from '@/lib/api'

interface ReviewJob {
  id: number
  applicationId: string
  platform: string
  jobId: string
  jobTitle: string
  company: string
  companyScale: string
  industry: string
  salary: string
  location: string
  jobUrl: string
  matchScore: number
  scoreBreakdown: string
  strengths: string
  risks: string
  riskLevel: string
  matchLevel: string
  resumeName: string
  greetingUsed: string
  status: string
  notes: string
  createdAt: string
}

export default function ReviewQueuePage() {
  const [jobs, setJobs] = useState<ReviewJob[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(8)
  const [loading, setLoading] = useState(true)
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null)
  const [editingGreetings, setEditingGreetings] = useState<Record<number, string>>({})
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  const loadQueue = async (targetPage = page) => {
    setLoading(true)
    try {
      const res = await fetch(apiUrl(`/api/agent/review-queue?page=${targetPage}&size=${pageSize}`)).then(r => r.json())
      if (res.success) {
        setJobs(res.data || [])
        setTotal(res.total || 0)
        setPage(res.current || targetPage)

        // 初始化编辑话术缓存
        const greetingsMap: Record<number, string> = {}
        ;(res.data || []).forEach((j: ReviewJob) => {
          greetingsMap[j.id] = j.greetingUsed || ''
        })
        setEditingGreetings(greetingsMap)
      }
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadQueue(1)
  }, [])

  const handleAction = async (id: number, action: 'APPROVE_APPLY' | 'SKIP' | 'BLACKLIST_COMPANY') => {
    setActionLoadingId(id)
    try {
      const customGreeting = editingGreetings[id] || ''
      const res = await fetch(apiUrl('/api/agent/review-queue/action'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id, action, customGreeting }),
      }).then(r => r.json())

      if (res.success) {
        const actionLabels = {
          APPROVE_APPLY: '已确认投递并转入投递流转追踪',
          SKIP: '已跳过该岗位',
          BLACKLIST_COMPANY: '已加入企业黑名单，后续将自动过滤该企业',
        }
        setSuccessMsg(actionLabels[action])
        setTimeout(() => setSuccessMsg(null), 3500)
        // 重新刷新队列
        loadQueue(page)
      }
    } catch (e) {
      console.error(e)
    } finally {
      setActionLoadingId(null)
    }
  }

  const parseJsonArray = (jsonStr: string): string[] => {
    if (!jsonStr) return []
    try {
      const parsed = JSON.parse(jsonStr)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }

  const parseScoreBreakdown = (jsonStr: string): Record<string, { score: number; maxScore: number; reason: string }> => {
    if (!jsonStr) return {}
    try {
      return JSON.parse(jsonStr)
    } catch {
      return {}
    }
  }

  return (
    <div className="max-w-7xl mx-auto pb-12">
      <PageHeader
        icon={<BiCheckShield />}
        title="边缘岗位人工复核中心"
        subtitle="对于处于复核分区间(60-77分)或命中频次风控的岗位，由您进行最后一道把关，支持一键确认、定制话术或拉黑"
        actions={
          <button
            onClick={() => loadQueue(page)}
            disabled={loading}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-neutral-700 hover:bg-slate-50 dark:hover:bg-neutral-800 text-xs font-medium text-slate-700 dark:text-neutral-300"
          >
            <BiRefresh className={`text-base ${loading ? 'animate-spin' : ''}`} />
            刷新复核列表
          </button>
        }
      />

      {successMsg && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-6 p-3.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-xs font-semibold text-emerald-700 dark:text-emerald-300 flex items-center gap-2"
        >
          <BiCheck className="text-lg" />
          {successMsg}
        </motion.div>
      )}

      {loading && jobs.length === 0 ? (
        <div className="py-20 text-center text-slate-400 text-xs">正在加载复核队列...</div>
      ) : jobs.length === 0 ? (
        <div className="p-12 text-center rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs">
          <div className="w-12 h-12 mx-auto rounded-full bg-blue-50 dark:bg-blue-900/30 text-blue-600 flex items-center justify-center text-2xl mb-3">
            <BiCheckShield />
          </div>
          <h3 className="font-bold text-slate-900 dark:text-white text-base">复核队列全部清空！</h3>
          <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1 max-w-sm mx-auto">
            当前没有待人工确认的边缘岗位。当系统在各大平台发现 60-77 分或特殊风控岗位时，会自动沉淀到这里。
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {jobs.map((job) => {
            const strengths = parseJsonArray(job.strengths)
            const risks = parseJsonArray(job.risks)
            const breakdown = parseScoreBreakdown(job.scoreBreakdown)

            return (
              <motion.div
                key={job.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="p-5 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs transition-all hover:border-slate-300 dark:hover:border-neutral-700 hover:shadow-sm"
              >
                {/* 顶部标题与标签 */}
                <div className="flex flex-wrap items-center justify-between gap-2 mb-3 pb-3 border-b border-slate-100 dark:border-neutral-800">
                  <div className="flex items-center gap-3">
                    <span className="text-base font-bold text-slate-900 dark:text-white">
                      {job.jobTitle}
                    </span>
                    <span className="text-sm font-black px-2.5 py-0.5 rounded-full bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300">
                      {job.matchScore} 分
                    </span>
                    <span className="text-xs px-2 py-0.5 rounded bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300 uppercase font-bold">
                      {job.platform}
                    </span>
                    {job.riskLevel === 'HIGH' && (
                      <span className="text-xs px-2 py-0.5 rounded bg-red-100 text-red-700 dark:bg-red-950/40 dark:text-red-300 font-bold">
                        高危风险
                      </span>
                    )}
                  </div>

                  <div className="text-xs text-slate-400 flex items-center gap-3">
                    <span>选用简历: <strong className="text-slate-700 dark:text-neutral-300">{job.resumeName || '标准简历'}</strong></span>
                    {job.jobUrl && (
                      <a
                        href={job.jobUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-0.5"
                      >
                        原始页面 <BiLinkExternal />
                      </a>
                    )}
                  </div>
                </div>

                {/* 公司与基本要求 */}
                <div className="flex flex-wrap items-center gap-4 text-xs text-slate-600 dark:text-neutral-400 mb-3">
                  <span className="font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1">
                    <BiBuilding className="text-sm text-slate-400" /> {job.company}
                  </span>
                  {job.companyScale && <span>规模: {job.companyScale}</span>}
                  {job.industry && <span>行业: {job.industry}</span>}
                  <span className="text-blue-600 dark:text-blue-400 font-bold flex items-center gap-1">
                    <BiDollarCircle className="text-sm" /> {job.salary}
                  </span>
                  <span className="flex items-center gap-1">
                    <BiMapPin className="text-sm text-slate-400" /> {job.location}
                  </span>
                  <span className="text-amber-600 dark:text-amber-400 font-medium bg-amber-50 dark:bg-amber-950/30 px-2 py-0.5 rounded">
                    复核原因: {job.notes || '处于复核分区间'}
                  </span>
                </div>

                {/* 10 维打分与优劣势细览 */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 my-3 p-3 rounded-xl bg-slate-50 dark:bg-neutral-800/40 text-xs">
                  {/* 优势 */}
                  <div>
                    <div className="text-[11px] font-bold text-emerald-600 dark:text-emerald-400 mb-1.5 flex items-center gap-1">
                      <BiCheck /> 核心匹配优势清单
                    </div>
                    {strengths.length > 0 ? (
                      <ul className="space-y-1">
                        {strengths.map((s, idx) => (
                          <li key={idx} className="text-[11px] text-slate-600 dark:text-neutral-400 flex items-start gap-1">
                            <span className="text-emerald-500">•</span> {s}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <div className="text-[11px] text-slate-400">综合匹配度符合基础要求</div>
                    )}
                  </div>

                  {/* 风险 */}
                  <div>
                    <div className="text-[11px] font-bold text-amber-600 dark:text-amber-400 mb-1.5 flex items-center gap-1">
                      <BiInfoCircle /> 扣分点与潜在风险清单
                    </div>
                    {risks.length > 0 ? (
                      <ul className="space-y-1">
                        {risks.map((r, idx) => (
                          <li key={idx} className="text-[11px] text-slate-600 dark:text-neutral-400 flex items-start gap-1">
                            <span className="text-amber-500">•</span> {r}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <div className="text-[11px] text-slate-400">未发现明显违规风险</div>
                    )}
                  </div>
                </div>

                {/* 实时定制打招呼语编辑 */}
                <div className="mt-3">
                  <div className="flex items-center justify-between text-[11px] text-slate-500 dark:text-neutral-400 mb-1">
                    <span className="font-semibold flex items-center gap-1">
                      <BiSend className="text-blue-500" /> AI 专属打招呼语（可在此直接润色修改后发送）:
                    </span>
                    <span className="text-[10px]">
                      字数: {(editingGreetings[job.id] || '').length} 字
                    </span>
                  </div>
                  <input
                    type="text"
                    value={editingGreetings[job.id] ?? ''}
                    onChange={(e) => {
                      setEditingGreetings({ ...editingGreetings, [job.id]: e.target.value })
                    }}
                    placeholder="输入发送给HR的专业求职打招呼语..."
                    className="w-full px-3 py-2 text-xs rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                {/* 操作按钮组 */}
                <div className="mt-4 pt-3 border-t border-slate-100 dark:border-neutral-800 flex flex-wrap items-center justify-between gap-3">
                  <button
                    onClick={() => handleAction(job.id, 'BLACKLIST_COMPANY')}
                    disabled={actionLoadingId === job.id}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-rose-200 dark:border-rose-900/50 hover:bg-rose-50 dark:hover:bg-rose-950/30 text-rose-600 text-xs font-semibold"
                  >
                    <BiBlock /> 拉黑该企业
                  </button>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleAction(job.id, 'SKIP')}
                      disabled={actionLoadingId === job.id}
                      className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-neutral-700 hover:bg-slate-100 dark:hover:bg-neutral-800 text-slate-600 dark:text-neutral-400 text-xs font-medium"
                    >
                      <BiX className="text-base" /> 跳过岗位
                    </button>
                    <button
                      onClick={() => handleAction(job.id, 'APPROVE_APPLY')}
                      disabled={actionLoadingId === job.id}
                      className="flex items-center gap-1 px-4 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold shadow-sm"
                    >
                      <BiCheck className="text-base" /> 确认投递该岗位
                    </button>
                  </div>
                </div>
              </motion.div>
            )
          })}

          {/* 分页控制 */}
          <div className="mt-6 flex items-center justify-between text-xs text-slate-500 dark:text-neutral-400">
            <div>共 {total} 个待复核岗位</div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => loadQueue(page - 1)}
                disabled={page <= 1}
                className="p-1.5 rounded border border-slate-200 dark:border-neutral-700 disabled:opacity-40"
              >
                <BiChevronLeft className="text-base" />
              </button>
              <span>第 {page} 页</span>
              <button
                onClick={() => loadQueue(page + 1)}
                disabled={page * pageSize >= total}
                className="p-1.5 rounded border border-slate-200 dark:border-neutral-700 disabled:opacity-40"
              >
                <BiChevronRight className="text-base" />
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
