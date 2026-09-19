'use client'

import { useEffect, useState } from 'react'
import {
  BiHistory,
  BiBuilding,
  BiMapPin,
  BiDollarCircle,
  BiSearch,
  BiFilter,
  BiRefresh,
  BiEdit,
  BiCheckCircle,
  BiMessageRoundedDetail,
  BiCalendarEvent,
  BiChevronLeft,
  BiChevronRight,
  BiX
} from 'react-icons/bi'
import { motion } from 'framer-motion'
import PageHeader from '../components/PageHeader'
import { apiUrl } from '@/lib/api'

interface ApplicationItem {
  id: number
  applicationId: string
  platform: string
  jobId: string
  jobTitle: string
  company: string
  salary: string
  location: string
  matchScore: number
  matchLevel: string
  resumeName: string
  greetingUsed: string
  status: string
  applyTime: string
  hrReply: string
  hrReplyTime: string
  interviewStatus: string
  interviewTime: string
  notes: string
  createdAt: string
}

export default function ApplicationsPage() {
  const [apps, setApps] = useState<ApplicationItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(12)
  const [loading, setLoading] = useState(true)

  const [statusFilter, setStatusFilter] = useState('ALL')
  const [platformFilter, setPlatformFilter] = useState('ALL')
  const [search, setSearch] = useState('')

  // 状态流转编辑弹窗
  const [editingItem, setEditingItem] = useState<ApplicationItem | null>(null)
  const [newStatus, setNewStatus] = useState('')
  const [hrReply, setHrReply] = useState('')
  const [interviewStatus, setInterviewStatus] = useState('')
  const [notes, setNotes] = useState('')
  const [updating, setUpdating] = useState(false)

  const loadApplications = async (targetPage = page) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (statusFilter !== 'ALL') params.append('status', statusFilter)
      if (platformFilter !== 'ALL') params.append('platform', platformFilter)
      if (search.trim()) params.append('search', search.trim())
      params.append('page', String(targetPage))
      params.append('size', String(pageSize))

      const res = await fetch(apiUrl(`/api/agent/applications?${params.toString()}`)).then(r => r.json())
      if (res.success) {
        setApps(res.data || [])
        setTotal(res.total || 0)
        setPage(res.current || targetPage)
      }
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadApplications(1)
  }, [statusFilter, platformFilter])

  const openUpdateModal = (item: ApplicationItem) => {
    setEditingItem(item)
    setNewStatus(item.status || 'APPLIED')
    setHrReply(item.hrReply || '')
    setInterviewStatus(item.interviewStatus || '')
    setNotes(item.notes || '')
  }

  const handleSaveStatus = async () => {
    if (!editingItem) return
    setUpdating(true)
    try {
      const res = await fetch(apiUrl(`/api/agent/applications/${editingItem.id}/status`), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: newStatus,
          hrReply,
          interviewStatus,
          notes,
        }),
      }).then(r => r.json())

      if (res.success) {
        setEditingItem(null)
        loadApplications(page)
      }
    } catch (e) {
      console.error(e)
    } finally {
      setUpdating(false)
    }
  }

  const statusBadge = (s: string) => {
    const map: Record<string, { label: string; bg: string; text: string }> = {
      DISCOVERED: { label: '已发现', bg: 'bg-slate-100 dark:bg-neutral-800', text: 'text-slate-600 dark:text-neutral-400' },
      FILTERED: { label: '已过滤', bg: 'bg-rose-50 dark:bg-rose-950/40', text: 'text-rose-600 dark:text-rose-400' },
      REVIEW_PENDING: { label: '待人工复核', bg: 'bg-amber-100 dark:bg-amber-950/40', text: 'text-amber-700 dark:text-amber-300' },
      READY_TO_APPLY: { label: '等待投递', bg: 'bg-cyan-50 dark:bg-cyan-950/40', text: 'text-cyan-600 dark:text-cyan-400' },
      APPLIED: { label: '已投递', bg: 'bg-blue-50 dark:bg-blue-950/40', text: 'text-blue-600 dark:text-blue-400' },
      HR_READ: { label: 'HR已读', bg: 'bg-sky-100 dark:bg-sky-950/40', text: 'text-sky-700 dark:text-sky-300' },
      HR_REPLIED: { label: '收到回复', bg: 'bg-emerald-100 dark:bg-emerald-950/40', text: 'text-emerald-700 dark:text-emerald-300' },
      INTERVIEW_INVITED: { label: '面试邀请', bg: 'bg-indigo-100 dark:bg-indigo-950/40', text: 'text-indigo-700 dark:text-indigo-300' },
      INTERVIEWED: { label: '已面试', bg: 'bg-violet-100 dark:bg-violet-950/40', text: 'text-violet-700 dark:text-violet-300' },
      OFFER: { label: '已获Offer', bg: 'bg-purple-100 dark:bg-purple-950/40', text: 'text-purple-700 dark:text-purple-300' },
      REJECTED: { label: '未通过', bg: 'bg-slate-200 dark:bg-neutral-700', text: 'text-slate-500' },
      CLOSED: { label: '已关闭', bg: 'bg-slate-100 dark:bg-neutral-800', text: 'text-slate-400' },
    }
    const def = map[s] || { label: s, bg: 'bg-slate-100', text: 'text-slate-600' }
    return (
      <span className={`px-2 py-0.5 rounded text-[11px] font-bold ${def.bg} ${def.text}`}>
        {def.label}
      </span>
    )
  }

  return (
    <div className="max-w-7xl mx-auto pb-12">
      <PageHeader
        icon={<BiHistory />}
        title="投递全流程流转跟踪与面试推进"
        subtitle="从岗位发现、智能投递、HR查阅与回复、面试邀约直至最终 Offer 的全流程生命周期管理"
        actions={
          <button
            onClick={() => loadApplications(page)}
            disabled={loading}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-neutral-700 hover:bg-slate-50 dark:hover:bg-neutral-800 text-xs font-medium text-slate-700 dark:text-neutral-300"
          >
            <BiRefresh className={`text-base ${loading ? 'animate-spin' : ''}`} />
            刷新流水
          </button>
        }
      />

      {/* 筛选过滤工具条 */}
      <div className="p-4 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200 dark:border-neutral-800 mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-3">
          {/* 状态筛选 */}
          <div className="flex items-center gap-1 text-xs">
            <span className="text-slate-400 font-medium">状态:</span>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-xs text-slate-800 dark:text-slate-200 focus:outline-none"
            >
              <option value="ALL">全部状态</option>
              <option value="APPLIED">已投递</option>
              <option value="HR_READ">HR已读</option>
              <option value="HR_REPLIED">收到回复</option>
              <option value="INTERVIEW_INVITED">面试邀请</option>
              <option value="INTERVIEWED">已面试</option>
              <option value="OFFER">已获Offer</option>
              <option value="REVIEW_PENDING">待人工复核</option>
              <option value="FILTERED">已过滤/拦截</option>
              <option value="CLOSED">已关闭/跳过</option>
            </select>
          </div>

          {/* 平台筛选 */}
          <div className="flex items-center gap-1 text-xs">
            <span className="text-slate-400 font-medium">平台:</span>
            <select
              value={platformFilter}
              onChange={(e) => setPlatformFilter(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-xs text-slate-800 dark:text-slate-200 focus:outline-none"
            >
              <option value="ALL">全部平台</option>
              <option value="boss">Boss直聘</option>
              <option value="liepin">猎聘网</option>
              <option value="job51">前程无忧 51job</option>
              <option value="zhilian">智联招聘</option>
            </select>
          </div>
        </div>

        {/* 关键词搜索 */}
        <div className="flex items-center gap-2">
          <div className="relative">
            <BiSearch className="absolute left-3 top-2.5 text-slate-400 text-sm" />
            <input
              type="text"
              placeholder="搜索岗位/公司/备注..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && loadApplications(1)}
              className="pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-xs text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 w-52"
            />
          </div>
          <button
            onClick={() => loadApplications(1)}
            className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 dark:bg-neutral-800 dark:hover:bg-neutral-700 text-xs font-semibold text-slate-700 dark:text-neutral-300"
          >
            搜索
          </button>
        </div>
      </div>

      {/* 投递列表 */}
      {loading && apps.length === 0 ? (
        <div className="py-20 text-center text-slate-400 text-xs">正在查询流转记录...</div>
      ) : apps.length === 0 ? (
        <div className="p-12 text-center rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs">
          <div className="text-slate-400 text-sm">暂无符合条件的投递流水记录</div>
        </div>
      ) : (
        <div className="space-y-3">
          {apps.map((item) => (
            <motion.div
              key={item.id}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="p-4 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200 dark:border-neutral-800 flex flex-wrap items-center justify-between gap-3 hover:border-slate-300 dark:hover:border-neutral-700"
            >
              <div className="flex-1 min-w-[280px]">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-bold text-sm text-slate-900 dark:text-white">{item.jobTitle}</span>
                  <span className="text-xs font-bold px-2 py-0.5 rounded bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
                    {item.matchScore}分
                  </span>
                  {statusBadge(item.status)}
                  <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 dark:bg-neutral-800 uppercase font-semibold text-slate-500">
                    {item.platform}
                  </span>
                </div>

                <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500 dark:text-neutral-400">
                  <span className="flex items-center gap-1 font-medium text-slate-700 dark:text-neutral-300">
                    <BiBuilding className="text-slate-400" /> {item.company}
                  </span>
                  <span className="text-blue-600 dark:text-blue-400 font-semibold">{item.salary}</span>
                  <span className="flex items-center gap-1">
                    <BiMapPin className="text-slate-400" /> {item.location}
                  </span>
                  <span>简历: {item.resumeName || '标准简历'}</span>
                </div>

                {/* HR回复或面试进展 */}
                {(item.hrReply || item.notes || item.interviewStatus) && (
                  <div className="mt-2 text-xs p-2 rounded-lg bg-slate-50 dark:bg-neutral-800/50 space-y-1">
                    {item.hrReply && (
                      <div className="text-emerald-700 dark:text-emerald-300 flex items-start gap-1">
                        <BiMessageRoundedDetail className="text-sm mt-0.5" />
                        <span><strong>HR回复:</strong> {item.hrReply}</span>
                      </div>
                    )}
                    {item.interviewStatus && (
                      <div className="text-indigo-700 dark:text-indigo-300 flex items-start gap-1">
                        <BiCalendarEvent className="text-sm mt-0.5" />
                        <span><strong>面试安排:</strong> {item.interviewStatus}</span>
                      </div>
                    )}
                    {item.notes && (
                      <div className="text-slate-600 dark:text-neutral-400 text-[11px]">
                        <strong>备注:</strong> {item.notes}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* 动作区 */}
              <div className="flex items-center gap-2">
                <button
                  onClick={() => openUpdateModal(item)}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-neutral-700 hover:bg-slate-50 dark:hover:bg-neutral-800 text-xs font-semibold text-blue-600 dark:text-blue-400"
                >
                  <BiEdit /> 推进状态
                </button>
              </div>
            </motion.div>
          ))}

          {/* 分页控制 */}
          <div className="mt-6 flex items-center justify-between text-xs text-slate-500 dark:text-neutral-400">
            <div>共 {total} 条流转记录</div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => loadApplications(page - 1)}
                disabled={page <= 1}
                className="p-1.5 rounded border border-slate-200 dark:border-neutral-700 disabled:opacity-40"
              >
                <BiChevronLeft className="text-base" />
              </button>
              <span>第 {page} 页</span>
              <button
                onClick={() => loadApplications(page + 1)}
                disabled={page * pageSize >= total}
                className="p-1.5 rounded border border-slate-200 dark:border-neutral-700 disabled:opacity-40"
              >
                <BiChevronRight className="text-base" />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 状态流转编辑弹窗 */}
      {editingItem && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-lg p-6 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xl">
            <div className="flex items-center justify-between mb-4 pb-2 border-b border-slate-100 dark:border-neutral-800">
              <h3 className="font-bold text-slate-900 dark:text-white text-base">
                更新求职流转状态
              </h3>
              <button onClick={() => setEditingItem(null)} className="text-slate-400 hover:text-slate-600">
                <BiX className="text-2xl" />
              </button>
            </div>

            <div className="space-y-4 text-xs">
              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  当前流转阶段 (Status)
                </label>
                <select
                  value={newStatus}
                  onChange={(e) => setNewStatus(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                >
                  <option value="APPLIED">已投递 (APPLIED)</option>
                  <option value="HR_READ">HR已读 (HR_READ)</option>
                  <option value="HR_REPLIED">收到HR回复 (HR_REPLIED)</option>
                  <option value="INTERVIEW_INVITED">收到面试邀请 (INTERVIEW_INVITED)</option>
                  <option value="INTERVIEWED">已完成面试 (INTERVIEWED)</option>
                  <option value="OFFER">已获Offer (OFFER)</option>
                  <option value="REJECTED">未通过/婉拒 (REJECTED)</option>
                  <option value="CLOSED">关闭/不再跟进 (CLOSED)</option>
                </select>
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  HR 回复内容 / 沟通记录
                </label>
                <textarea
                  rows={2}
                  value={hrReply}
                  onChange={(e) => setHrReply(e.target.value)}
                  placeholder="记录HR在沟通软件中的关键回复（如：方便发一份完整作品集吗？明天下午电话聊）"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                />
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  面试时间与安排 / 状态
                </label>
                <input
                  type="text"
                  value={interviewStatus}
                  onChange={(e) => setInterviewStatus(e.target.value)}
                  placeholder="如：2026-09-22 14:00 腾讯会议一面 (技术初试)"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                />
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  复盘笔记 / 备注
                </label>
                <textarea
                  rows={3}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="记录该岗位的问答重点、薪酬意向、面试考点复盘等..."
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                />
              </div>
            </div>

            <div className="mt-6 flex items-center justify-end gap-2">
              <button
                onClick={() => setEditingItem(null)}
                className="px-4 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 text-xs font-semibold text-slate-600 dark:text-neutral-400"
              >
                取消
              </button>
              <button
                onClick={handleSaveStatus}
                disabled={updating}
                className="px-5 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold shadow-sm"
              >
                {updating ? '正在保存...' : '保存流转状态'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
