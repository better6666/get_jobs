'use client'

import { useEffect, useState } from 'react'
import {
  BiDetail,
  BiPlus,
  BiEdit,
  BiTrash,
  BiCheck,
  BiRefresh,
  BiStar,
  BiBriefcase,
  BiCodeAlt,
  BiFile,
  BiX
} from 'react-icons/bi'
import { motion } from 'framer-motion'
import PageHeader from '../components/PageHeader'
import { apiUrl } from '@/lib/api'

interface ResumeVersion {
  id: number
  resumeName: string
  targetJobs: string
  targetIndustries: string
  matchedSkills: string
  strengths: string
  filePath: string
  isDefault: number
  version: string
  createdAt: string
}

export default function ResumesPage() {
  const [resumes, setResumes] = useState<ResumeVersion[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Partial<ResumeVersion> | null>(null)
  const [saving, setSaving] = useState(false)

  const loadResumes = async () => {
    setLoading(true)
    try {
      const res = await fetch(apiUrl('/api/agent/resumes')).then(r => r.json())
      if (res.success) {
        setResumes(res.data || [])
      }
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadResumes()
  }, [])

  const handleSave = async () => {
    if (!editing || !editing.resumeName) return
    setSaving(true)
    try {
      const res = await fetch(apiUrl('/api/agent/resumes'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(editing),
      }).then(r => r.json())

      if (res.success) {
        setEditing(null)
        loadResumes()
      }
    } catch (e) {
      console.error(e)
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!confirm('确定删除该简历版本吗？')) return
    try {
      const res = await fetch(apiUrl(`/api/agent/resumes/${id}`), { method: 'DELETE' }).then(r => r.json())
      if (res.success) {
        loadResumes()
      }
    } catch (e) {
      console.error(e)
    }
  }

  const parseTags = (str: string): string[] => {
    if (!str) return []
    try {
      const p = JSON.parse(str)
      return Array.isArray(p) ? p : []
    } catch {
      return str.split(',').map(s => s.trim()).filter(Boolean)
    }
  }

  return (
    <div className="max-w-6xl mx-auto pb-12">
      <PageHeader
        icon={<BiDetail />}
        title="多版本简历管理与自适应匹配"
        subtitle="支持针对不同技术方向与岗位建立差异化简历版本，系统根据目标 JD 自动挑选匹配度最高的版本出击"
        actions={
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                setEditing({
                  resumeName: '',
                  targetJobs: '["全栈开发","Java工程师"]',
                  targetIndustries: '["互联网","企业服务"]',
                  matchedSkills: '["Java","Spring Boot","Next.js"]',
                  strengths: '具备丰富实战落地经验',
                  filePath: '',
                  isDefault: 0,
                })
              }}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold shadow-sm"
            >
              <BiPlus className="text-base" /> 新增简历版本
            </button>
          </div>
        }
      />

      {loading && resumes.length === 0 ? (
        <div className="py-20 text-center text-slate-400 text-xs">正在加载简历列表...</div>
      ) : resumes.length === 0 ? (
        <div className="p-12 text-center rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs">
          <div className="text-slate-400 text-sm">暂无简历版本，请点击右上角新增</div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {resumes.map((item) => {
            const targetJobs = parseTags(item.targetJobs)
            const skills = parseTags(item.matchedSkills)

            return (
              <motion.div
                key={item.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className={`p-5 rounded-xl bg-white dark:bg-neutral-900 border flex flex-col justify-between transition-all ${
                  item.isDefault === 1
                    ? 'border-blue-500 shadow-sm ring-1 ring-blue-500/20'
                    : 'border-slate-200 dark:border-neutral-800 shadow-xs'
                }`}
              >
                <div>
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <h3 className="font-bold text-sm text-slate-900 dark:text-white flex items-center gap-1.5">
                      <BiFile className="text-blue-500" />
                      {item.resumeName}
                    </h3>
                    {item.isDefault === 1 && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-blue-100 text-blue-800 dark:bg-blue-900/50 dark:text-blue-300 flex items-center gap-0.5">
                        <BiStar /> 默认底版
                      </span>
                    )}
                  </div>

                  <p className="text-xs text-slate-500 dark:text-neutral-400 mb-3 line-clamp-2">
                    {item.strengths || '标准求职描述'}
                  </p>

                  <div className="space-y-2 mb-4 text-xs">
                    <div>
                      <div className="text-[11px] font-semibold text-slate-400 mb-1 flex items-center gap-1">
                        <BiBriefcase /> 适配岗位方向:
                      </div>
                      <div className="flex flex-wrap gap-1">
                        {targetJobs.map((j, idx) => (
                          <span key={idx} className="px-2 py-0.5 rounded bg-slate-100 dark:bg-neutral-800 text-[10px] text-slate-700 dark:text-neutral-300">
                            {j}
                          </span>
                        ))}
                      </div>
                    </div>

                    <div>
                      <div className="text-[11px] font-semibold text-slate-400 mb-1 flex items-center gap-1">
                        <BiCodeAlt /> 重点契合技能:
                      </div>
                      <div className="flex flex-wrap gap-1">
                        {skills.map((s, idx) => (
                          <span key={idx} className="px-2 py-0.5 rounded bg-blue-50 dark:bg-blue-950/40 text-[10px] text-blue-700 dark:text-blue-300">
                            {s}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>

                <div className="pt-3 border-t border-slate-100 dark:border-neutral-800 flex items-center justify-between text-xs">
                  <span className="text-[11px] text-slate-400">ID: #{item.id}</span>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setEditing(item)}
                      className="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-neutral-800 text-slate-600 dark:text-neutral-300"
                    >
                      <BiEdit className="text-base" />
                    </button>
                    <button
                      onClick={() => handleDelete(item.id)}
                      className="p-1.5 rounded hover:bg-rose-50 dark:hover:bg-rose-950/30 text-rose-600"
                    >
                      <BiTrash className="text-base" />
                    </button>
                  </div>
                </div>
              </motion.div>
            )
          })}
        </div>
      )}

      {/* 编辑/新增弹窗 */}
      {editing && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-lg p-6 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xl">
            <div className="flex items-center justify-between mb-4 pb-2 border-b border-slate-100 dark:border-neutral-800">
              <h3 className="font-bold text-slate-900 dark:text-white text-base">
                {editing.id ? '编辑简历版本' : '新增简历版本'}
              </h3>
              <button onClick={() => setEditing(null)} className="text-slate-400 hover:text-slate-600">
                <BiX className="text-2xl" />
              </button>
            </div>

            <div className="space-y-4 text-xs">
              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  版本名称
                </label>
                <input
                  type="text"
                  value={editing.resumeName || ''}
                  onChange={(e) => setEditing({ ...editing, resumeName: e.target.value })}
                  placeholder="如：AI与自动化方向版 / 全栈开发版"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                />
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  适配目标岗位 (JSON 数组)
                </label>
                <input
                  type="text"
                  value={editing.targetJobs || ''}
                  onChange={(e) => setEditing({ ...editing, targetJobs: e.target.value })}
                  placeholder='["全栈开发","自动化运维","Java工程师"]'
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                />
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  契合技能词 (JSON 数组)
                </label>
                <input
                  type="text"
                  value={editing.matchedSkills || ''}
                  onChange={(e) => setEditing({ ...editing, matchedSkills: e.target.value })}
                  placeholder='["Java","Spring Boot","Playwright","Next.js"]'
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                />
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  核心亮点与优势提炼 (用于 AI 打招呼语及匹配增强)
                </label>
                <textarea
                  rows={3}
                  value={editing.strengths || ''}
                  onChange={(e) => setEditing({ ...editing, strengths: e.target.value })}
                  placeholder="提炼该版本简历最吸引HR的核心优势亮点..."
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                />
              </div>

              <div className="pt-2">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={editing.isDefault === 1}
                    onChange={(e) => setEditing({ ...editing, isDefault: e.target.checked ? 1 : 0 })}
                    className="rounded text-blue-600"
                  />
                  <span className="font-semibold text-slate-700 dark:text-neutral-300">设为全局默认版本（当未命中专属版本时使用）</span>
                </label>
              </div>
            </div>

            <div className="mt-6 flex items-center justify-end gap-2">
              <button
                onClick={() => setEditing(null)}
                className="px-4 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 text-xs font-semibold text-slate-600 dark:text-neutral-400"
              >
                取消
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-5 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold shadow-sm"
              >
                {saving ? '正在保存...' : '保存版本'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
