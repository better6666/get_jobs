'use client'

import { useEffect, useState } from 'react'
import {
  BiGridAlt,
  BiPlus,
  BiTrash,
  BiEdit,
  BiCheck,
  BiRefresh,
  BiCheckCircle,
  BiXCircle,
  BiSearch,
  BiX,
  BiCheckShield
} from 'react-icons/bi'
import { motion } from 'framer-motion'
import PageHeader from '../components/PageHeader'
import { apiUrl } from '@/lib/api'

interface KeywordItem {
  id: number
  category: 'PRIMARY' | 'SECONDARY' | 'NEGATIVE'
  groupName: string
  word: string
  weight: number
  isActive: number
  contextRule: string
  createdAt: string
}

export default function KeywordsPage() {
  const [keywords, setKeywords] = useState<KeywordItem[]>([])
  const [loading, setLoading] = useState(true)
  const [categoryFilter, setCategoryFilter] = useState('ALL')
  const [search, setSearch] = useState('')

  const [editing, setEditing] = useState<Partial<KeywordItem> | null>(null)
  const [saving, setSaving] = useState(false)

  const loadKeywords = async () => {
    setLoading(true)
    try {
      const url = categoryFilter !== 'ALL'
        ? apiUrl(`/api/agent/keywords?category=${categoryFilter}`)
        : apiUrl('/api/agent/keywords')
      const res = await fetch(url).then(r => r.json())
      if (res.success) {
        setKeywords(res.data || [])
      }
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadKeywords()
  }, [categoryFilter])

  const handleSave = async () => {
    if (!editing || !editing.word) return
    setSaving(true)
    try {
      const res = await fetch(apiUrl('/api/agent/keywords'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(editing),
      }).then(r => r.json())

      if (res.success) {
        setEditing(null)
        loadKeywords()
      }
    } catch (e) {
      console.error(e)
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!confirm('确定删除该关键词吗？')) return
    try {
      const res = await fetch(apiUrl(`/api/agent/keywords/${id}`), { method: 'DELETE' }).then(r => r.json())
      if (res.success) {
        loadKeywords()
      }
    } catch (e) {
      console.error(e)
    }
  }

  const handleToggle = async (id: number, current: number) => {
    const next = current === 1 ? 0 : 1
    try {
      const res = await fetch(apiUrl(`/api/agent/keywords/${id}/toggle`), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isActive: next }),
      }).then(r => r.json())
      if (res.success) {
        setKeywords(keywords.map(k => k.id === id ? { ...k, isActive: next } : k))
      }
    } catch (e) {
      console.error(e)
    }
  }

  const filteredList = keywords.filter(k => {
    if (!search.trim()) return true
    const q = search.toLowerCase()
    return (
      (k.word && k.word.toLowerCase().includes(q)) ||
      (k.groupName && k.groupName.toLowerCase().includes(q)) ||
      (k.contextRule && k.contextRule.toLowerCase().includes(q))
    )
  })

  return (
    <div className="max-w-6xl mx-auto pb-12">
      <PageHeader
        icon={<BiGridAlt />}
        title="关键词矩阵与智能反误伤规则"
        subtitle="结构化管理核心词、扩展词与负向黑名单，配置上下文保护规则，避免盲目过滤"
        actions={
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                setEditing({
                  category: 'PRIMARY',
                  groupName: '核心技能',
                  word: '',
                  weight: 20,
                  isActive: 1,
                  contextRule: '',
                })
              }}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold shadow-sm"
            >
              <BiPlus className="text-base" /> 新增关键词
            </button>
          </div>
        }
      />

      {/* 分类切换与搜索 */}
      <div className="p-4 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200 dark:border-neutral-800 mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          {[
            { key: 'ALL', label: '全部关键词' },
            { key: 'PRIMARY', label: '核心词 (Primary)' },
            { key: 'SECONDARY', label: '扩展词 (Secondary)' },
            { key: 'NEGATIVE', label: '负向词 (Negative)' },
          ].map((cat) => (
            <button
              key={cat.key}
              onClick={() => setCategoryFilter(cat.key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                categoryFilter === cat.key
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'bg-slate-100 dark:bg-neutral-800 text-slate-600 dark:text-neutral-400 hover:bg-slate-200'
              }`}
            >
              {cat.label}
            </button>
          ))}
        </div>

        <div className="relative">
          <BiSearch className="absolute left-3 top-2.5 text-slate-400 text-sm" />
          <input
            type="text"
            placeholder="搜索词条 / 规则 / 分组..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-xs text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 w-56"
          />
        </div>
      </div>

      {/* 词条列表 */}
      {loading && keywords.length === 0 ? (
        <div className="py-20 text-center text-slate-400 text-xs">正在加载关键词矩阵...</div>
      ) : filteredList.length === 0 ? (
        <div className="p-12 text-center rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs">
          <div className="text-slate-400 text-sm">暂无匹配的关键词词条</div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredList.map((item) => (
            <motion.div
              key={item.id}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className={`p-4 rounded-xl bg-white dark:bg-neutral-900 border flex flex-col justify-between transition-all ${
                item.isActive === 1
                  ? 'border-slate-200 dark:border-neutral-800 shadow-sm'
                  : 'border-slate-200 dark:border-neutral-800 opacity-50 bg-slate-50 dark:bg-neutral-900/50'
              }`}
            >
              <div>
                <div className="flex items-center justify-between gap-2 mb-2">
                  <div className="flex items-center gap-1.5">
                    <span className="font-bold text-sm text-slate-900 dark:text-white">{item.word}</span>
                    <span
                      className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                        item.category === 'PRIMARY'
                          ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300'
                          : item.category === 'SECONDARY'
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300'
                          : 'bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-300'
                      }`}
                    >
                      {item.category === 'PRIMARY' ? '核心' : item.category === 'SECONDARY' ? '扩展' : '负向'}
                    </span>
                  </div>

                  <span className="text-xs font-mono font-bold text-slate-500">
                    权重: {item.weight}
                  </span>
                </div>

                <div className="text-xs text-slate-500 dark:text-neutral-400 mb-2">
                  分组: <span className="font-medium text-slate-700 dark:text-neutral-300">{item.groupName || '默认'}</span>
                </div>

                {item.contextRule && (
                  <div className="text-[11px] p-2 rounded-lg bg-slate-50 dark:bg-neutral-800/60 text-slate-600 dark:text-neutral-400 flex items-start gap-1 mb-2">
                    <BiCheckShield className="text-emerald-600 text-xs mt-0.5 shrink-0" />
                    <span>反误伤: {item.contextRule}</span>
                  </div>
                )}
              </div>

              <div className="pt-2 border-t border-slate-100 dark:border-neutral-800 flex items-center justify-between text-xs">
                <button
                  onClick={() => handleToggle(item.id, item.isActive)}
                  className={`flex items-center gap-1 text-[11px] font-semibold ${
                    item.isActive === 1 ? 'text-emerald-600' : 'text-slate-400'
                  }`}
                >
                  {item.isActive === 1 ? <BiCheckCircle className="text-sm" /> : <BiXCircle className="text-sm" />}
                  {item.isActive === 1 ? '启用中' : '已停用'}
                </button>

                <div className="flex items-center gap-1">
                  <button
                    onClick={() => setEditing(item)}
                    className="p-1 rounded hover:bg-slate-100 dark:hover:bg-neutral-800 text-slate-500"
                  >
                    <BiEdit className="text-base" />
                  </button>
                  <button
                    onClick={() => handleDelete(item.id)}
                    className="p-1 rounded hover:bg-rose-50 dark:hover:bg-rose-950/30 text-rose-500"
                  >
                    <BiTrash className="text-base" />
                  </button>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      )}

      {/* 新增/编辑弹窗 */}
      {editing && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-md p-6 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xl">
            <div className="flex items-center justify-between mb-4 pb-2 border-b border-slate-100 dark:border-neutral-800">
              <h3 className="font-bold text-slate-900 dark:text-white text-base">
                {editing.id ? '编辑关键词' : '新增关键词'}
              </h3>
              <button onClick={() => setEditing(null)} className="text-slate-400 hover:text-slate-600">
                <BiX className="text-2xl" />
              </button>
            </div>

            <div className="space-y-4 text-xs">
              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  词条类别
                </label>
                <select
                  value={editing.category || 'PRIMARY'}
                  onChange={(e) => setEditing({ ...editing, category: e.target.value as any })}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                >
                  <option value="PRIMARY">核心关键词 (Primary)</option>
                  <option value="SECONDARY">扩展关键词 (Secondary)</option>
                  <option value="NEGATIVE">负向排除词 (Negative)</option>
                </select>
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  关键词 / 排除短语
                </label>
                <input
                  type="text"
                  value={editing.word || ''}
                  onChange={(e) => setEditing({ ...editing, word: e.target.value })}
                  placeholder="如：Playwright / 电话销售 / 某某劳务外包"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                    所属分组名
                  </label>
                  <input
                    type="text"
                    value={editing.groupName || ''}
                    onChange={(e) => setEditing({ ...editing, groupName: e.target.value })}
                    placeholder="如：技术栈/企业黑名单"
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                  />
                </div>
                <div>
                  <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                    权重分 (1-100)
                  </label>
                  <input
                    type="number"
                    value={editing.weight || 10}
                    onChange={(e) => setEditing({ ...editing, weight: parseInt(e.target.value) || 10 })}
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                  />
                </div>
              </div>

              <div>
                <label className="block font-medium text-slate-600 dark:text-neutral-400 mb-1">
                  上下文反误伤规则 (选填)
                </label>
                <input
                  type="text"
                  value={editing.contextRule || ''}
                  onChange={(e) => setEditing({ ...editing, contextRule: e.target.value })}
                  placeholder="如：排除电话销售，但不排除售前技术支持/架构师"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
                />
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
                {saving ? '正在保存...' : '保存词条'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
