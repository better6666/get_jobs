'use client'

import { useEffect, useState } from 'react'
import {
  BiUserCheck,
  BiSave,
  BiRefresh,
  BiCheck,
  BiBriefcase,
  BiBookOpen,
  BiTargetLock,
  BiSliderAlt
} from 'react-icons/bi'
import { motion } from 'framer-motion'
import PageHeader from '../components/PageHeader'
import { apiUrl } from '@/lib/api'

export default function ProfilePage() {
  const [profile, setProfile] = useState<any>({
    name: '求职者',
    gender: '男',
    age: 26,
    currentCity: '北京',
    targetCities: '["北京","上海","深圳","杭州"]',
    acceptRemote: 1,
    acceptRelocation: 1,
    jobStatus: '在职-考虑机会',
    graduateYear: 2022,
    graduateType: '社招',
    onboardTime: '两周内',
    highestDegree: '大专',
    school: '',
    major: '计算机科学与技术',
    isUnified: 1,
    isFullTime: 1,
    workYears: 3,
    skills: '["Java","Spring Boot","Next.js","Playwright","MySQL","Redis","自动化运维"]',
    workExp: '精通全栈工程化开发、自动化工作流编排以及企业级业务系统构建',
    projectExp: '作为核心研发交付多款企业级自动化工具、SaaS服务及AI Agent智能辅助求职系统',
    targetJobs: '["全栈开发","Java工程师","前端工程师","自动化测试","软件工程师"]',
    targetIndustries: '["互联网","企业服务","人工智能"]',
    minSalary: 12000,
    expectedSalary: 20000,
    acceptOutsourcing: 1,
    acceptDispatch: 0,
    acceptSales: 0,
    acceptTeleSales: 0,
  })

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [savedMsg, setSavedMsg] = useState(false)
  const [activeTab, setActiveTab] = useState<'basic' | 'education' | 'skills' | 'preferences'>('basic')

  const loadProfile = async () => {
    setLoading(true)
    try {
      const res = await fetch(apiUrl('/api/agent/profile')).then(r => r.json())
      if (res.success && res.data) {
        setProfile(res.data)
      }
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadProfile()
  }, [])

  const handleSave = async () => {
    setSaving(true)
    try {
      const res = await fetch(apiUrl('/api/agent/profile'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(profile),
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

  const updateField = (key: string, val: any) => {
    setProfile((prev: any) => ({ ...prev, [key]: val }))
  }

  return (
    <div className="max-w-5xl mx-auto pb-12">
      <PageHeader
        icon={<BiUserCheck />}
        title="候选人多维画像系统"
        subtitle="构建高精度真实求职者画像，为 JD 硬性过滤、10 维可解释匹配评分与零幻觉打招呼语提供严谨基准"
        actions={
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold shadow-sm"
          >
            {savedMsg ? <BiCheck className="text-base" /> : <BiSave className="text-base" />}
            {savedMsg ? '画像已保存' : saving ? '正在保存...' : '保存画像配置'}
          </button>
        }
      />

      {/* 分类标签切换 */}
      <div className="flex border-b border-slate-200 dark:border-neutral-800 mb-6 gap-2">
        {[
          { id: 'basic', label: '基本背景', icon: BiUserCheck },
          { id: 'education', label: '学历教育', icon: BiBookOpen },
          { id: 'skills', label: '实战经验与技能栈', icon: BiBriefcase },
          { id: 'preferences', label: '求职意向与底线偏好', icon: BiTargetLock },
        ].map((tab) => {
          const Icon = tab.icon
          const isActive = activeTab === tab.id
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`flex items-center gap-2 px-4 py-2.5 text-xs font-bold border-b-2 transition-all ${
                isActive
                  ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                  : 'border-transparent text-slate-500 hover:text-slate-800 dark:text-neutral-400'
              }`}
            >
              <Icon className="text-base" />
              {tab.label}
            </button>
          )
        })}
      </div>

      <div className="p-6 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs space-y-6">
        {/* 基本背景 */}
        {activeTab === 'basic' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5 text-xs">
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">姓名 / 称呼</label>
              <input
                type="text"
                value={profile.name || ''}
                onChange={(e) => updateField('name', e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">年龄</label>
              <input
                type="number"
                value={profile.age || ''}
                onChange={(e) => updateField('age', parseInt(e.target.value) || 0)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">当前所在地</label>
              <input
                type="text"
                value={profile.currentCity || ''}
                onChange={(e) => updateField('currentCity', e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">期望城市清单 (JSON数组格式)</label>
              <input
                type="text"
                value={profile.targetCities || ''}
                onChange={(e) => updateField('targetCities', e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">当前求职状态</label>
              <select
                value={profile.jobStatus || ''}
                onChange={(e) => updateField('jobStatus', e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              >
                <option value="离职-随时到岗">离职-随时到岗</option>
                <option value="在职-考虑机会">在职-考虑机会</option>
                <option value="在职-月内到岗">在职-月内到岗</option>
                <option value="应届生求职">应届生求职</option>
              </select>
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">期望到岗时间</label>
              <input
                type="text"
                value={profile.onboardTime || ''}
                onChange={(e) => updateField('onboardTime', e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div className="flex items-center gap-6 pt-2">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={profile.acceptRemote === 1}
                  onChange={(e) => updateField('acceptRemote', e.target.checked ? 1 : 0)}
                  className="rounded text-blue-600"
                />
                <span className="font-semibold text-slate-700 dark:text-neutral-300">接受远程 / 在家办公</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={profile.acceptRelocation === 1}
                  onChange={(e) => updateField('acceptRelocation', e.target.checked ? 1 : 0)}
                  className="rounded text-blue-600"
                />
                <span className="font-semibold text-slate-700 dark:text-neutral-300">接受异地迁移</span>
              </label>
            </div>
          </div>
        )}

        {/* 学历背景 */}
        {activeTab === 'education' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5 text-xs">
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">最高学历</label>
              <select
                value={profile.highestDegree || '大专'}
                onChange={(e) => updateField('highestDegree', e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              >
                <option value="大专">大专</option>
                <option value="本科">本科</option>
                <option value="硕士">硕士</option>
                <option value="博士">博士</option>
                <option value="中专/高中">中专/高中</option>
              </select>
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">毕业院校</label>
              <input
                type="text"
                value={profile.school || ''}
                onChange={(e) => updateField('school', e.target.value)}
                placeholder="毕业学校名称"
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">主修专业</label>
              <input
                type="text"
                value={profile.major || ''}
                onChange={(e) => updateField('major', e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">毕业年份</label>
              <input
                type="number"
                value={profile.graduateYear || ''}
                onChange={(e) => updateField('graduateYear', parseInt(e.target.value) || 0)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div className="flex items-center gap-6 pt-2">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={profile.isUnified === 1}
                  onChange={(e) => updateField('isUnified', e.target.checked ? 1 : 0)}
                  className="rounded text-blue-600"
                />
                <span className="font-semibold text-slate-700 dark:text-neutral-300">统招</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={profile.isFullTime === 1}
                  onChange={(e) => updateField('isFullTime', e.target.checked ? 1 : 0)}
                  className="rounded text-blue-600"
                />
                <span className="font-semibold text-slate-700 dark:text-neutral-300">全日制</span>
              </label>
            </div>
          </div>
        )}

        {/* 实战经验与技能栈 */}
        {activeTab === 'skills' && (
          <div className="space-y-4 text-xs">
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">实战工作年限 (年)</label>
              <input
                type="number"
                value={profile.workYears || ''}
                onChange={(e) => updateField('workYears', parseInt(e.target.value) || 0)}
                className="w-36 px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">
                核心技能标签 (JSON 数组)
              </label>
              <input
                type="text"
                value={profile.skills || ''}
                onChange={(e) => updateField('skills', e.target.value)}
                placeholder='["Java","Spring Boot","Next.js","自动化测试"]'
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">
                工作经验与核心优势摘要 (用于 AI 打招呼语与亮点提炼)
              </label>
              <textarea
                rows={3}
                value={profile.workExp || ''}
                onChange={(e) => updateField('workExp', e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">
                核心项目经验摘要
              </label>
              <textarea
                rows={3}
                value={profile.projectExp || ''}
                onChange={(e) => updateField('projectExp', e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
              />
            </div>
          </div>
        )}

        {/* 求职意向与底线偏好 */}
        {activeTab === 'preferences' && (
          <div className="space-y-4 text-xs">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">目标岗位列表 (JSON数组)</label>
                <input
                  type="text"
                  value={profile.targetJobs || ''}
                  onChange={(e) => updateField('targetJobs', e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
                />
              </div>
              <div>
                <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">目标行业列表 (JSON数组)</label>
                <input
                  type="text"
                  value={profile.targetIndustries || ''}
                  onChange={(e) => updateField('targetIndustries', e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">最低可接受月薪 (元/月)</label>
                <input
                  type="number"
                  value={profile.minSalary || ''}
                  onChange={(e) => updateField('minSalary', parseInt(e.target.value) || 0)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
                />
              </div>
              <div>
                <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-1.5">期望目标月薪 (元/月)</label>
                <input
                  type="number"
                  value={profile.expectedSalary || ''}
                  onChange={(e) => updateField('expectedSalary', parseInt(e.target.value) || 0)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-slate-900 dark:text-white shadow-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-colors"
                />
              </div>
            </div>

            <div className="pt-2">
              <label className="block font-semibold text-slate-700 dark:text-neutral-300 mb-2">求职底线与排除条件</label>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <label className="flex items-center gap-2 p-2 rounded-lg bg-slate-50 dark:bg-neutral-800/40 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={profile.acceptOutsourcing === 1}
                    onChange={(e) => updateField('acceptOutsourcing', e.target.checked ? 1 : 0)}
                    className="rounded text-blue-600"
                  />
                  <span>接受外包岗位</span>
                </label>
                <label className="flex items-center gap-2 p-2 rounded-lg bg-slate-50 dark:bg-neutral-800/40 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={profile.acceptDispatch === 1}
                    onChange={(e) => updateField('acceptDispatch', e.target.checked ? 1 : 0)}
                    className="rounded text-blue-600"
                  />
                  <span>接受劳务派遣</span>
                </label>
                <label className="flex items-center gap-2 p-2 rounded-lg bg-slate-50 dark:bg-neutral-800/40 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={profile.acceptSales === 1}
                    onChange={(e) => updateField('acceptSales', e.target.checked ? 1 : 0)}
                    className="rounded text-blue-600"
                  />
                  <span>接受销售属性</span>
                </label>
                <label className="flex items-center gap-2 p-2 rounded-lg bg-slate-50 dark:bg-neutral-800/40 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={profile.acceptTeleSales === 1}
                    onChange={(e) => updateField('acceptTeleSales', e.target.checked ? 1 : 0)}
                    className="rounded text-blue-600"
                  />
                  <span>接受电话销售</span>
                </label>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
