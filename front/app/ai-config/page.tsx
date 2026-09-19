'use client'

import { useState, useEffect } from 'react'
import { BiSave, BiBrain, BiInfoCircle, BiPlay, BiCheckCircle, BiErrorCircle } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import PageHeader from '@/app/components/PageHeader'
import { API_BASE } from '@/lib/api'

export default function AiConfigPage() {
  const [aiConfig, setAiConfig] = useState({
    introduce: '',
    prompt: '',
  })

  const [loading, setLoading] = useState(false)
  // 是否启用AI（映射 boss_config.enable_ai）
  const [enableAi, setEnableAi] = useState<number>(0)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)

  // 连通性测试与话术生成模拟
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<{
    success: boolean
    latencyMs?: number
    reply?: string
    model?: string
    message: string
  } | null>(null)

  // 加载AI配置
  useEffect(() => {
    fetchAiConfig()
    fetchEnableAi()
  }, [])

  const fetchAiConfig = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/ai/config`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const result = await response.json()
      if (result.success && result.data) {
        setAiConfig({
          introduce: result.data.introduce || '',
          prompt: result.data.prompt || '',
        })
      }
    } catch (error) {
      console.error('加载AI配置失败:', error)
      // 如果加载失败，使用默认值，不影响用户使用
      console.log('使用默认配置')
    }
  }

  // 加载 boss_config 的 enable_ai 字段
  const fetchEnableAi = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/boss/config`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const result = await response.json()
      const raw = result?.config?.enableAi
      const val = String(raw ?? '').trim().toLowerCase()
      setEnableAi(val === '1' || val === 'true' || val === 'on' ? 1 : Number(raw) === 1 ? 1 : 0)
    } catch (e) {
      console.error('加载enable_ai失败:', e)
    }
  }

  // 切换 AI 开关并保存到 boss_config
  const toggleEnableAi = async () => {
    try {
      const next = enableAi ? 0 : 1
      setEnableAi(next)
      const response = await fetch(`${API_BASE}/api/boss/config`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ enableAi: next }),
      })
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
    } catch (e) {
      console.error('更新enable_ai失败:', e)
      // 回滚
      setEnableAi((prev) => (prev ? 0 : 1))
      alert('切换失败，请检查后端服务连接')
    }
  }

  const handleSave = async () => {
    setLoading(true)
    try {
      // 保存AI配置
      const response = await fetch(`${API_BASE}/api/ai/config`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(aiConfig),
      })

      const result = await response.json()

      if (result.success) {
        setSaveResult({ success: true, message: 'AI配置已成功保存。' })
        setShowSaveDialog(true)
      } else {
        throw new Error(result.message || '保存配置失败')
      }
    } catch (error) {
      console.error('保存AI配置失败:', error)
      setSaveResult({ success: false, message: '保存配置失败，请检查网络或服务状态。' })
      setShowSaveDialog(true)
    } finally {
      setLoading(false)
    }
  }

  // 测试 AI 当前人设与提示词生成效果
  const handleTestGeneration = async () => {
    try {
      setTesting(true)
      setTestResult(null)
      const sampleJob = '高级Java后端开发工程师，熟练掌握Spring Boot/Cloud微服务与分布式高并发设计'
      const filledPrompt = aiConfig.prompt
        ? aiConfig.prompt.includes('%s')
          ? aiConfig.prompt.replace('%s', aiConfig.introduce || '有丰富的开发经验').replace('%s', sampleJob)
          : `${aiConfig.prompt}\n个人背景：${aiConfig.introduce}\n目标岗位：${sampleJob}`
        : `请根据我的背景（${aiConfig.introduce || '软件开发'}）向招聘HR写一段60字左右得体简洁的打招呼语，应聘岗位：${sampleJob}`

      const response = await fetch(`${API_BASE}/api/ai/test`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          prompt: filledPrompt,
        }),
      })

      const data = await response.json()
      if (data.success && data.data) {
        setTestResult({
          success: true,
          latencyMs: data.data.latencyMs,
          reply: data.data.reply,
          model: data.data.model,
          message: '话术生成测试成功！大模型已根据您的技能与提示词正常生成招呼语。',
        })
      } else {
        setTestResult({
          success: false,
          message: data.message || '测试生成失败，请先在「环境配置」中检查 API 接口是否正常。',
        })
      }
    } catch (e: any) {
      setTestResult({
        success: false,
        message: e?.message ? `请求异常：${e.message}` : '连接服务失败',
      })
    } finally {
      setTesting(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiBrain />}
        title="AI配置"
        subtitle="配置AI相关的技能介绍和提示词，用于生成个性化求职内容"
        iconClass="text-purple-600 dark:text-purple-400"
        accentBgClass="bg-purple-50 dark:bg-purple-900/20"
        actions={
          <div className="flex items-center gap-2">
            <Button
              onClick={handleTestGeneration}
              size="sm"
              type="button"
              disabled={testing || loading}
              className="bg-emerald-600 hover:bg-emerald-700 text-white transition-colors shadow"
            >
              <BiPlay className={`mr-1.5 text-base ${testing ? 'animate-spin' : ''}`} />
              {testing ? '测试生成中...' : '测试话术生成'}
            </Button>
            <Button
              onClick={handleSave}
              size="sm"
              disabled={loading}
              className="bg-purple-600 hover:bg-purple-700 text-white transition-colors"
            >
              <BiSave className="mr-2" /> {loading ? '保存中...' : '保存配置'}
            </Button>
          </div>
        }
      />

      <div className="space-y-6">
        {/* 测试结果预览面板 */}
        {testResult && (
          <div
            className={`p-4 rounded-xl border animate-in fade-in slide-in-from-top-2 duration-300 ${
              testResult.success
                ? 'bg-emerald-50/80 dark:bg-emerald-950/20 border-emerald-200 dark:border-emerald-800/60 text-emerald-900 dark:text-emerald-200'
                : 'bg-rose-50/80 dark:bg-rose-950/20 border-rose-200 dark:border-rose-800/60 text-rose-900 dark:text-rose-200'
            }`}
          >
            <div className="flex items-start gap-3">
              {testResult.success ? (
                <BiCheckCircle className="text-2xl text-emerald-600 dark:text-emerald-400 flex-shrink-0 mt-0.5" />
              ) : (
                <BiErrorCircle className="text-2xl text-rose-600 dark:text-rose-400 flex-shrink-0 mt-0.5" />
              )}
              <div className="space-y-1.5 flex-1 min-w-0">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-semibold text-sm">
                    {testResult.success ? 'AI 接口连通并成功生成打招呼语' : '测试生成失败'}
                  </span>
                  {testResult.latencyMs !== undefined && (
                    <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-100 dark:bg-emerald-900/60 text-emerald-700 dark:text-emerald-300 font-mono">
                      模型耗时: {testResult.latencyMs}ms
                    </span>
                  )}
                </div>
                <p className="text-xs text-slate-600 dark:text-neutral-300 break-words">
                  {testResult.message}
                </p>
                {testResult.reply && (
                  <div className="mt-2 text-xs bg-white/70 dark:bg-black/30 p-2.5 rounded border border-emerald-200/50 dark:border-emerald-800/40">
                    <span className="font-medium text-emerald-800 dark:text-emerald-300">模拟投递生成招呼语：</span>
                    <span className="text-slate-800 dark:text-neutral-200 font-mono">{testResult.reply}</span>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
        {/* AI配置 */}
        <Card className="shadow-sm border-slate-200 dark:border-neutral-800">
          <CardHeader className="flex flex-row items-center justify-between pb-4">
            <div className="space-y-1">
              <CardTitle className="text-lg flex items-center gap-2">
                <BiBrain className="text-purple-500" />
                自动生成配置
              </CardTitle>
              <CardDescription>配置您的个人背景和指令要求，以供 AI 在沟通时使用</CardDescription>
            </div>
            <button
              type="button"
              aria-label="AI启用开关"
              onClick={toggleEnableAi}
              className={`relative inline-flex h-6 w-11 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 ${enableAi ? 'bg-purple-600' : 'bg-slate-200 dark:bg-neutral-700'}`}
            >
              <span
                className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform mt-1 ml-1 ${enableAi ? 'translate-x-5' : 'translate-x-0'}`}
              />
            </button>
          </CardHeader>
          <CardContent>
            <div className="space-y-6">
              <div className="space-y-2">
                <Label htmlFor="introduce">个人介绍与技能</Label>
                <Textarea
                  id="introduce"
                  value={aiConfig.introduce}
                  onChange={(e) => setAiConfig({ ...aiConfig, introduce: e.target.value })}
                  placeholder="请输入您的技能介绍，例如：我熟练使用Java、Python等语言进行开发，有3年后端开发经验..."
                  className="min-h-[150px] resize-y bg-slate-50 dark:bg-neutral-900 border-slate-200 dark:border-neutral-800"
                />
                <p className="text-xs text-slate-500 mt-1">
                  详细描述您的技能、经验和专业背景，AI 将基于这些信息自动匹配岗位需求并生成回复
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="prompt">AI 提示词模板</Label>
                <Textarea
                  id="prompt"
                  value={aiConfig.prompt}
                  onChange={(e) => setAiConfig({ ...aiConfig, prompt: e.target.value })}
                  placeholder="请输入AI提示词模板，例如：我目前在找工作，%s，我期望的岗位方向是【%s】..."
                  className="min-h-[150px] resize-y bg-slate-50 dark:bg-neutral-900 border-slate-200 dark:border-neutral-800"
                />
                <p className="text-xs text-slate-500 mt-1">
                  指导 AI 生成沟通话术的底层 prompt，支持使用 %s 作为占位符，由系统在投递时动态插入具体岗位信息
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 使用说明 */}
        <div className="bg-purple-50 dark:bg-purple-900/10 border border-purple-100 dark:border-purple-900/30 rounded-lg p-5 flex gap-4">
          <BiInfoCircle className="h-5 w-5 text-purple-600 dark:text-purple-400 flex-shrink-0 mt-0.5" />
          <div>
            <h4 className="text-sm font-semibold text-purple-900 dark:text-purple-300 mb-2">使用说明</h4>
            <ul className="text-sm text-purple-800/80 dark:text-purple-300/80 space-y-2">
              <li className="flex items-start gap-2">
                <span className="text-purple-500 dark:text-purple-400 mt-0.5">•</span>
                <span><strong>启用 AI：</strong>打开右上角的开关后，自动投递工具会在符合条件的岗位发起聊天时使用 AI 代为沟通。</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-purple-500 dark:text-purple-400 mt-0.5">•</span>
                <span><strong>个人介绍：</strong>这是 AI 了解您的基础，尽量详实、客观地描述您的技术栈和过往经验。</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-purple-500 dark:text-purple-400 mt-0.5">•</span>
                <span><strong>灵活度：</strong>您可以随时调整提示词模板，使得 AI 的沟通口吻更符合您的个人风格（例如：幽默、专业、简明扼要）。</span>
              </li>
            </ul>
          </div>
        </div>

        {/* 保存结果弹框 */}
        {showSaveDialog && saveResult && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm" role="dialog" aria-modal="true">
            <div className="bg-white dark:bg-neutral-900 rounded-xl shadow-xl w-[90%] max-w-sm border border-slate-200 dark:border-neutral-800 animate-in fade-in zoom-in-95 p-6">
              <div className="flex items-center gap-3 mb-4">
                <div className={`p-2 rounded-full ${saveResult.success ? 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400' : 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400'}`}>
                  <BiSave className="text-xl" />
                </div>
                <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                  {saveResult.success ? '保存成功' : '保存失败'}
                </h3>
              </div>
              <p className="text-slate-600 dark:text-slate-300 text-sm mb-6">
                {saveResult.message}
              </p>
              <div className="flex justify-end">
                <Button
                  onClick={() => setShowSaveDialog(false)}
                  className="bg-slate-900 hover:bg-slate-800 dark:bg-white dark:text-slate-900 dark:hover:bg-slate-100"
                >
                  确认
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
