'use client'

import { useState, useEffect } from 'react'
import { BiSave, BiKey, BiLinkExternal, BiCodeAlt, BiInfoCircle, BiRefresh, BiCheckCircle, BiErrorCircle, BiPlay } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import PageHeader from '@/app/components/PageHeader'
import { API_BASE } from '@/lib/api'

interface ProviderPreset {
  id: string
  name: string
  tag: string
  baseUrl: string
  defaultModel: string
  models: string[]
  notes: string
  apiKeyPlaceholder: string
}

const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    id: 'openai',
    name: 'OpenAI / Codex',
    tag: '官方',
    baseUrl: 'https://api.openai.com/v1',
    defaultModel: 'gpt-4o',
    models: ['gpt-4o', 'gpt-4o-mini', 'o1-mini', 'o3-mini', 'gpt-4-turbo'],
    notes: 'OpenAI 官方 ChatGPT/Codex 接口。需绑定海外支付信用卡生成 sk- 密钥。',
    apiKeyPlaceholder: 'sk-proj-...',
  },
  {
    id: 'claude',
    name: 'Claude (Anthropic)',
    tag: '官方/兼容',
    baseUrl: 'https://api.anthropic.com/v1',
    defaultModel: 'claude-3-5-sonnet-20241022',
    models: ['claude-3-7-sonnet-20250219', 'claude-3-5-sonnet-20241022', 'claude-3-5-haiku-20241022'],
    notes: 'Anthropic Claude 接口。直连或使用 Claude OpenAI 兼容格式中转端点均可。',
    apiKeyPlaceholder: 'sk-ant-...',
  },
  {
    id: 'glm',
    name: '智谱清言 (GLM)',
    tag: '国产官方',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    defaultModel: 'glm-4-flash',
    models: ['glm-4-flash', 'glm-4-plus', 'glm-4-air', 'glm-zero-preview'],
    notes: '智谱大模型开放平台，glm-4-flash 免费且调用极速，官方已全面兼容 OpenAI 协议。',
    apiKeyPlaceholder: 'xxxxxxxx.xxxxxxxx',
  },
  {
    id: 'qwen',
    name: '通义千问 (Qwen)',
    tag: '阿里云百炼',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    defaultModel: 'qwen-plus',
    models: ['qwen-plus', 'qwen-turbo', 'qwen-max', 'qwen2.5-72b-instruct'],
    notes: '阿里云百炼大模型服务，采用 DashScope 兼容模式地址，国内直连响应极快。',
    apiKeyPlaceholder: 'sk-...',
  },
  {
    id: 'doubao',
    name: '豆包 (火山引擎)',
    tag: '字节跳动',
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    defaultModel: 'doubao-pro-32k',
    models: ['doubao-pro-32k', 'doubao-lite-32k', 'doubao-1.5-pro-32k'],
    notes: '火山引擎方舟平台兼容接口。注意：模型名称通常为火山后台创建的「接入点 ID（ep-...）」。',
    apiKeyPlaceholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
  },
  {
    id: 'kimi',
    name: 'Kimi (月之暗面)',
    tag: '国产官方',
    baseUrl: 'https://api.moonshot.cn/v1',
    defaultModel: 'moonshot-v1-8k',
    models: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'],
    notes: 'Moonshot AI 官方开放平台，上下文理解出色，原生提供 OpenAI 格式端点。',
    apiKeyPlaceholder: 'sk-...',
  },
  {
    id: 'gemini',
    name: 'Google Gemini',
    tag: '官方兼容',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    defaultModel: 'gemini-1.5-flash',
    models: ['gemini-1.5-flash', 'gemini-1.5-pro', 'gemini-2.0-flash'],
    notes: 'Google AI Studio 原生提供的 OpenAI 兼容端点（需能访问海外网络）。',
    apiKeyPlaceholder: 'AIzaSy...',
  },
  {
    id: 'relay',
    name: '自定义中转站 / NewAPI',
    tag: '多模型汇聚',
    baseUrl: 'https://api.abnt.it/v1',
    defaultModel: 'gpt-4o',
    models: ['gpt-4o', 'gpt-4o-mini', 'claude-3-5-sonnet', 'deepseek-chat'],
    notes: '支持 OneAPI / NewAPI / 独立中转站，配置后可点击下方「获取可用模型」拉取全部模型。',
    apiKeyPlaceholder: 'sk-...',
  },
]

export default function EnvConfig() {
  const [envConfig, setEnvConfig] = useState({
    hookUrl: '',
    baseUrl: '',
    apiKey: '',
    model: '',
    botIsSend: 0,
  })

  const [showApiKey, setShowApiKey] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [models, setModels] = useState<string[]>([])
  const [fetchingModels, setFetchingModels] = useState(false)
  const [modelFetchMsg, setModelFetchMsg] = useState<string | null>(null)

  // 接口测试状态
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<{
    success: boolean
    latencyMs?: number
    reply?: string
    model?: string
    message: string
  } | null>(null)
  const [activePresetId, setActivePresetId] = useState<string>('')

  // 根据 URL + API Key 拉取可用模型列表
  const handleFetchModels = async () => {
    try {
      setFetchingModels(true)
      setModelFetchMsg(null)
      const params = new URLSearchParams()
      if (envConfig.baseUrl.trim()) params.set('baseUrl', envConfig.baseUrl.trim())
      if (envConfig.apiKey.trim()) params.set('apiKey', envConfig.apiKey.trim())
      const response = await fetch(`${API_BASE}/api/ai/models?${params.toString()}`)
      const result = await response.json()
      if (result.success && Array.isArray(result.data)) {
        setModels(result.data)
        setModelFetchMsg(`已获取 ${result.data.length} 个模型`)
      } else {
        throw new Error(result.message || '获取模型列表失败')
      }
    } catch (error: any) {
      setModels([])
      setModelFetchMsg(error?.message ? `获取失败：${error.message}` : '获取失败，请检查 URL 和 Key')
    } finally {
      setFetchingModels(false)
    }
  }

  // 应用服务商预设
  const handleApplyPreset = (preset: ProviderPreset) => {
    setActivePresetId(preset.id)
    setEnvConfig((prev) => ({
      ...prev,
      baseUrl: preset.baseUrl,
      model: preset.defaultModel,
    }))
    setModels(preset.models)
    setModelFetchMsg(`已应用【${preset.name}】官方端点与推荐模型`)
    setTestResult(null)
  }

  // 测试 AI 接口连通性
  const handleTestConnection = async () => {
    if (!envConfig.baseUrl.trim()) {
      setTestResult({
        success: false,
        message: '请先填写 API Base URL 接口地址后再测试',
      })
      return
    }
    if (!envConfig.apiKey.trim()) {
      setTestResult({
        success: false,
        message: '请先填写 API Key 密钥后再测试',
      })
      return
    }
    if (!envConfig.model.trim()) {
      setTestResult({
        success: false,
        message: '请先选择或输入 AI 模型名称后再测试',
      })
      return
    }

    try {
      setTesting(true)
      setTestResult(null)
      const response = await fetch(`${API_BASE}/api/ai/test`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          baseUrl: envConfig.baseUrl.trim(),
          apiKey: envConfig.apiKey.trim(),
          model: envConfig.model.trim(),
        }),
      })

      const data = await response.json()
      if (data.success && data.data) {
        setTestResult({
          success: true,
          latencyMs: data.data.latencyMs,
          reply: data.data.reply,
          model: data.data.model,
          message: data.message || '接口连通成功！',
        })
      } else {
        setTestResult({
          success: false,
          message: data.message || '接口测试失败，请检查配置与网络',
        })
      }
    } catch (e: any) {
      setTestResult({
        success: false,
        message: e?.message ? `网络或服务请求异常：${e.message}` : '连接后端测试接口失败',
      })
    } finally {
      setTesting(false)
    }
  }

  // 从数据库加载配置
  const fetchConfig = async () => {
    try {
      setLoading(true)
      const response = await fetch(`${API_BASE}/api/config`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error('获取配置失败')
      }

      const result = await response.json()

      if (result.success && result.data) {
        setEnvConfig({
          hookUrl: result.data.HOOK_URL || '',
          baseUrl: result.data.BASE_URL || '',
          apiKey: result.data.API_KEY || '',
          model: result.data.MODEL || '',
          botIsSend: (() => {
            const raw = result.data.BOT_IS_SEND
            const val = String(raw ?? '').trim().toLowerCase()
            return val === '1' || val === 'true' ? 1 : 0
          })(),
        })
      }
    } catch (error) {
      console.error('获取配置失败:', error)
      alert('获取配置失败，请检查后端服务是否正常运行')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchConfig()
  }, [])

  const handleSave = async (silent: boolean = false) => {
    try {
      setSaving(true)

      const configMap = {
        HOOK_URL: envConfig.hookUrl,
        BASE_URL: envConfig.baseUrl,
        API_KEY: envConfig.apiKey,
        MODEL: envConfig.model,
        BOT_IS_SEND: String(envConfig.botIsSend ?? 0),
      }

      const response = await fetch(`${API_BASE}/api/config`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(configMap),
      })

      if (!response.ok) {
        throw new Error('保存配置失败')
      }

      const result = await response.json()

      if (result.success) {
        if (!silent) {
          setSaveResult({ success: true, message: '环境配置已成功保存。' })
          setShowSaveDialog(true)
        }
      } else {
        throw new Error(result.message || '保存配置失败')
      }
    } catch (error) {
      console.error('保存配置失败:', error)
      if (!silent) {
        setSaveResult({ success: false, message: '保存配置失败，请检查网络或服务状态。' })
        setShowSaveDialog(true)
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={<BiCodeAlt />}
        title="环境变量配置"
        subtitle="管理系统的全局环境变量和 AI 模型设置"
        actions={
          <Button
            onClick={() => handleSave(false)}
            size="sm"
            disabled={saving}
            className="bg-blue-600 hover:bg-blue-700 text-white transition-colors"
          >
            <BiSave className="mr-2" /> {saving ? '保存中...' : '保存配置'}
          </Button>
        }
      />

      {loading && (
        <Card>
          <CardContent className="pt-6">
            <p className="text-center text-sm text-slate-500">正在加载配置信息...</p>
          </CardContent>
        </Card>
      )}

      <div className="space-y-6">
        {/* 企业微信 Webhook */}
        <Card className="shadow-sm border-slate-200 dark:border-neutral-800">
          <CardHeader className="flex flex-row items-center justify-between pb-4">
            <div className="space-y-1">
              <CardTitle className="text-lg flex items-center gap-2">
                <BiLinkExternal className="text-blue-500" />
                Webhook 通知
              </CardTitle>
              <CardDescription>配置企业微信群机器人，用于接收投递结果通知</CardDescription>
            </div>
            <button
              type="button"
              aria-label="发送开关"
              onClick={() => setEnvConfig({ ...envConfig, botIsSend: envConfig.botIsSend ? 0 : 1 })}
              className={`relative inline-flex h-6 w-11 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 ${envConfig.botIsSend ? 'bg-blue-600' : 'bg-slate-200 dark:bg-neutral-700'}`}
            >
              <span
                className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform mt-1 ml-1 ${envConfig.botIsSend ? 'translate-x-5' : 'translate-x-0'}`}
              />
            </button>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              <Label htmlFor="hookUrl">Webhook URL</Label>
              <Input
                id="hookUrl"
                type="text"
                value={envConfig.hookUrl}
                onChange={(e) => setEnvConfig({ ...envConfig, hookUrl: e.target.value })}
                placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=your_key"
                className="bg-slate-50 dark:bg-neutral-900 border-slate-200 dark:border-neutral-800"
              />
            </div>
          </CardContent>
        </Card>

        {/* API 配置 */}
        <Card className="shadow-sm border-slate-200 dark:border-neutral-800">
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <CardTitle className="text-lg flex items-center gap-2">
                  <BiCodeAlt className="text-blue-500" />
                  AI 服务配置与接口测试
                </CardTitle>
                <CardDescription>支持各大主流模型服务商官方接口、中转站以及一键可用性测试</CardDescription>
              </div>
              <Button
                onClick={handleTestConnection}
                disabled={testing}
                size="sm"
                type="button"
                className="bg-emerald-600 hover:bg-emerald-700 text-white shadow transition-all duration-200"
              >
                <BiPlay className={`mr-1.5 text-base ${testing ? 'animate-spin' : ''}`} />
                {testing ? '测试连通性中...' : '测试接口可用性'}
              </Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* 常用服务商预设模版选择器 */}
            <div className="space-y-2.5">
              <div className="flex items-center justify-between">
                <Label className="text-xs font-semibold text-slate-700 dark:text-neutral-300">
                  ⚡ 快速填入官方服务商 / 中转站预设：
                </Label>
                <span className="text-[11px] text-slate-400">点击自动填充 Base URL 与推荐模型</span>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {PROVIDER_PRESETS.map((p) => {
                  const isSelected = activePresetId === p.id || envConfig.baseUrl.trim() === p.baseUrl
                  return (
                    <button
                      key={p.id}
                      type="button"
                      onClick={() => handleApplyPreset(p)}
                      className={`flex flex-col items-start p-2.5 rounded-lg border text-left transition-all ${
                        isSelected
                          ? 'border-blue-500 bg-blue-50/80 dark:bg-blue-950/40 text-blue-950 dark:text-blue-200 shadow-sm ring-1 ring-blue-500/50'
                          : 'border-slate-200 dark:border-neutral-800 bg-slate-50/50 dark:bg-neutral-900/50 hover:bg-slate-100 dark:hover:bg-neutral-800 text-slate-800 dark:text-neutral-200'
                      }`}
                    >
                      <div className="flex items-center justify-between w-full mb-1">
                        <span className="text-xs font-semibold truncate">{p.name}</span>
                        <span className={`text-[10px] px-1.5 py-0.2 rounded font-medium ${
                          isSelected
                            ? 'bg-blue-200 dark:bg-blue-900 text-blue-800 dark:text-blue-100'
                            : 'bg-slate-200/80 dark:bg-neutral-800 text-slate-600 dark:text-neutral-400'
                        }`}>
                          {p.tag}
                        </span>
                      </div>
                      <span className="text-[11px] text-slate-500 dark:text-neutral-400 truncate w-full">
                        默认: {p.defaultModel}
                      </span>
                    </button>
                  )
                })}
              </div>
              {/* 当前激活的预设说明 */}
              {activePresetId && (
                <div className="text-xs bg-slate-100/70 dark:bg-neutral-800/60 p-2.5 rounded-lg border border-slate-200/60 dark:border-neutral-700/60 text-slate-600 dark:text-neutral-300 flex items-start gap-2">
                  <BiInfoCircle className="text-blue-500 mt-0.5 flex-shrink-0" />
                  <div>
                    {PROVIDER_PRESETS.find((x) => x.id === activePresetId)?.notes}
                  </div>
                </div>
              )}
            </div>

            {/* 连通性测试结果面板 */}
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
                        {testResult.success ? '接口可用性测试通过' : '接口连通失败'}
                      </span>
                      {testResult.latencyMs !== undefined && (
                        <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-100 dark:bg-emerald-900/60 text-emerald-700 dark:text-emerald-300 font-mono">
                          延迟: {testResult.latencyMs}ms
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-600 dark:text-neutral-300 break-words">
                      {testResult.message}
                    </p>
                    {testResult.reply && (
                      <div className="mt-2 text-xs bg-white/70 dark:bg-black/30 p-2.5 rounded border border-emerald-200/50 dark:border-emerald-800/40">
                        <span className="font-medium text-emerald-800 dark:text-emerald-300">模型回复片段：</span>
                        <span className="text-slate-800 dark:text-neutral-200 font-mono">{testResult.reply}</span>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-1">
              <div className="space-y-2">
                <Label htmlFor="baseUrl">API Base URL</Label>
                <Input
                  id="baseUrl"
                  type="text"
                  value={envConfig.baseUrl}
                  onChange={(e) => setEnvConfig({ ...envConfig, baseUrl: e.target.value })}
                  placeholder="https://api.openai.com/v1"
                  className="bg-slate-50 dark:bg-neutral-900 border-slate-200 dark:border-neutral-800"
                />
                <p className="text-xs text-slate-500 mt-1">
                  支持 OpenAI 官方及兼容的代理/中转接口（系统自动兼容 /v1 与各种端点路径）
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="model">AI 模型</Label>
                {models.length > 0 ? (
                  <Select
                    id="model"
                    value={envConfig.model}
                    onChange={(e) => setEnvConfig({ ...envConfig, model: e.target.value })}
                    className="bg-slate-50 dark:bg-neutral-900 border-slate-200 dark:border-neutral-800"
                  >
                    <option value="" disabled>选择一个模型</option>
                    {models.map((m) => (
                      <option key={m} value={m}>{m}</option>
                    ))}
                  </Select>
                ) : (
                  <Input
                    id="model"
                    type="text"
                    value={envConfig.model}
                    onChange={(e) => setEnvConfig({ ...envConfig, model: e.target.value })}
                    placeholder="gpt-4o"
                    className="bg-slate-50 dark:bg-neutral-900 border-slate-200 dark:border-neutral-800"
                  />
                )}
                <div className="flex items-center gap-2 pt-1">
                  <Button
                    onClick={handleFetchModels}
                    variant="outline"
                    size="sm"
                    type="button"
                    disabled={fetchingModels}
                    className="h-8 text-xs border-slate-200 dark:border-neutral-800"
                  >
                    <BiRefresh className={`mr-1 ${fetchingModels ? 'animate-spin' : ''}`} />
                    {fetchingModels ? '获取中...' : '从接口拉取模型列表'}
                  </Button>
                  {models.length > 0 && (
                    <Button
                      onClick={() => { setModels([]); setModelFetchMsg(null) }}
                      variant="ghost"
                      size="sm"
                      type="button"
                      className="h-8 text-xs text-slate-500"
                    >
                      自定义输入
                    </Button>
                  )}
                </div>
                {modelFetchMsg && (
                  <p className={`text-xs mt-1 ${modelFetchMsg.startsWith('已获取') || modelFetchMsg.startsWith('已应用') ? 'text-green-600' : 'text-amber-600'}`}>
                    {modelFetchMsg}
                  </p>
                )}
              </div>
            </div>
          </CardContent>
        </Card>

        {/* API 密钥 */}
        <Card className="shadow-sm border-slate-200 dark:border-neutral-800">
          <CardHeader>
            <CardTitle className="text-lg flex items-center gap-2">
              <BiKey className="text-blue-500" />
              API 密钥
            </CardTitle>
            <CardDescription>用于请求 AI 服务的授权令牌</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              <Label htmlFor="apiKey">API Key</Label>
              <div className="relative">
                <Input
                  id="apiKey"
                  type={showApiKey ? 'text' : 'password'}
                  value={envConfig.apiKey}
                  onChange={(e) => setEnvConfig({ ...envConfig, apiKey: e.target.value })}
                  placeholder="sk-..."
                  className="bg-slate-50 dark:bg-neutral-900 border-slate-200 dark:border-neutral-800 pr-20"
                />
                <Button
                  onClick={() => setShowApiKey(!showApiKey)}
                  variant="ghost"
                  size="sm"
                  className="absolute right-1 top-1/2 -translate-y-1/2 h-7 text-xs text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
                  type="button"
                >
                  {showApiKey ? '隐藏' : '显示'}
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 安全提示 */}
        <div className="bg-blue-50 dark:bg-blue-900/10 border border-blue-100 dark:border-blue-900/30 rounded-lg p-4 flex gap-3 text-sm text-blue-800 dark:text-blue-300">
          <BiInfoCircle className="h-5 w-5 flex-shrink-0" />
          <p>
            上述环境变量将持久化保存到系统配置文件中，请确保您的 API 密钥等敏感信息不被泄露。
          </p>
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
