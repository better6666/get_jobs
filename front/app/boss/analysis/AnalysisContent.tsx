"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import PageHeader from "@/app/components/PageHeader"
import {
  BiRefresh,
  BiDownload,
  BiBarChart,
  BiLineChart,
  BiPieChart,
  BiBriefcase,
  BiBuilding,
  BiMapPin,
  BiDollarCircle,
  BiFilter,
  BiLinkExternal,
  BiX,
  BiChevronLeft,
  BiChevronRight,
  BiSearch
} from "react-icons/bi"
import { API_BASE } from "@/lib/api"

type NameValue = { name: string; value: number }
type BucketValue = { bucket: string; value: number }

type StatsResponse = {
  kpi: {
    total: number
    delivered: number
    pending: number
    filtered: number
    failed: number
    avgMonthlyK?: number | null
  }
  charts: {
    byStatus: NameValue[]
    byCity: NameValue[]
    byIndustry: NameValue[]
    byCompany: NameValue[]
    byExperience: NameValue[]
    byDegree: NameValue[]
    salaryBuckets: BucketValue[]
    dailyTrend: NameValue[]
    hrActivity: NameValue[]
  }
}

type BossJob = {
  id: number
  companyName?: string
  jobName?: string
  salary?: string
  location?: string
  experience?: string
  degree?: string
  hrName?: string
  hrPosition?: string
  hrActiveStatus?: string
  deliveryStatus?: string
  jobUrl?: string
  recruitmentStatus?: string
  companyAddress?: string
  industry?: string
  introduce?: string
  financingStage?: string
  companyScale?: string
  jobDescription?: string
  createdAt?: string
}

type PagedResult = {
  items: BossJob[]
  total: number
  page: number
  size: number
}

const CATEGORY_COLORS = [
  "#3b82f6",
  "#10b981",
  "#f59e0b",
  "#ef4444",
  "#6366f1",
  "#22c55e",
  "#fb7185",
  "#a78bfa",
  "#f97316",
  "#06b6d4",
  "#4ade80",
  "#2dd4bf",
  "#f472b6",
  "#64748b",
]

function ChartCanvas({
  type,
  labels,
  data,
  title,
  color = "#3b82f6",
  colors,
}: {
  type: "pie" | "bar" | "line"
  labels: string[]
  data: number[]
  title?: string
  color?: string
  colors?: string[]
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const chartRef = useRef<any | null>(null)

  async function ensureChart(): Promise<any> {
    if (typeof window !== "undefined" && (window as any).Chart) return (window as any).Chart
    return new Promise((resolve, reject) => {
      const existing = document.querySelector("script[data-chartjs-cdn='true']") as HTMLScriptElement | null
      if (existing) {
        existing.addEventListener("load", () => resolve((window as any).Chart))
        existing.addEventListener("error", () => reject(new Error("Chart.js CDN load error")))
        return
      }
      const script = document.createElement("script")
      script.src = "https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js"
      script.async = true
      script.setAttribute("data-chartjs-cdn", "true")
      script.addEventListener("load", () => resolve((window as any).Chart))
      script.addEventListener("error", () => reject(new Error("Chart.js CDN load error")))
      document.head.appendChild(script)
    })
  }

  useEffect(() => {
    const ctx = canvasRef.current?.getContext("2d")
    if (!ctx) return

    if (chartRef.current) {
      chartRef.current.destroy()
      chartRef.current = null
    }

    let cancelled = false

    const pieColorsBase = [
      "#3b82f6",
      "#10b981",
      "#f59e0b",
      "#ef4444",
      "#6366f1",
      "#22c55e",
      "#fb7185",
      "#a78bfa",
      "#f97316",
      "#06b6d4",
    ]

    const backgroundColor = (() => {
      if (type === "pie") {
        return (colors && colors.length ? colors : pieColorsBase).slice(0, labels.length)
      }
      if (type === "bar" && colors && colors.length) {
        return colors.slice(0, data.length)
      }
      return color ?? "#3b82f6"
    })()

    const dataset: any = {
      label: title || "",
      data,
      backgroundColor,
      borderColor: type === "line" ? color : undefined,
    }

    if (type === "line") {
      dataset.fill = false
      dataset.pointBackgroundColor = color
      dataset.pointBorderColor = color
    }

    ;(async () => {
      try {
        const Chart = await ensureChart()
        if (cancelled) return

        chartRef.current = new Chart(ctx, {
          type,
          data: {
            labels,
            datasets: [dataset],
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
              legend: {
                display: type === "pie",
                position: "bottom",
                labels: { boxWidth: 12, font: { size: 11 } }
              },
              tooltip: { enabled: true },
            },
            scales: type === "pie" ? {} : {
              x: { ticks: { font: { size: 10 } } },
              y: { beginAtZero: true, ticks: { font: { size: 10 } } }
            }
          },
        })
      } catch (err) {
        console.error("Chart render error:", err)
      }
    })()

    return () => {
      cancelled = true
      if (chartRef.current) {
        chartRef.current.destroy()
        chartRef.current = null
      }
    }
  }, [type, labels, data, title, color, colors])

  return (
    <div className="w-full h-44 relative">
      <canvas ref={canvasRef} />
    </div>
  )
}

export default function AnalysisContent({ showHeader = false }: { showHeader?: boolean }) {
  const [stats, setStats] = useState<StatsResponse | null>(null)
  const [loadingStats, setLoadingStats] = useState(false)

  // 图表展开/收起状态（默认收起以保证界面紧凑、快速查阅明细）
  const [showCharts, setShowCharts] = useState(false)
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false)

  // 列表相关状态
  const [items, setItems] = useState<BossJob[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(15)
  const [loadingList, setLoadingList] = useState(false)

  // 筛选字段
  const [statuses, setStatuses] = useState<string[]>([])
  const [location, setLocation] = useState("")
  const [experience, setExperience] = useState("")
  const [degree, setDegree] = useState("")
  const [minK, setMinK] = useState<string>("")
  const [maxK, setMaxK] = useState<string>("")
  const [keyword, setKeyword] = useState("")
  const [filterHeadhunter, setFilterHeadhunter] = useState(false)

  const [exporting, setExporting] = useState(false)
  const [reloading, setReloading] = useState(false)

  // 图表分类选项卡
  const [chartTab, setChartTab] = useState<"overview" | "companies" | "dimensions">("overview")

  // 详情弹窗
  const [detailJob, setDetailJob] = useState<BossJob | null>(null)

  const statusOptions = ["已投递", "未投递", "已过滤", "失败"]

  const loadStats = async () => {
    try {
      setLoadingStats(true)
      const res = await fetch(`${API_BASE}/api/boss/stats`)
      if (!res.ok) throw new Error("加载统计失败")
      const data: StatsResponse = await res.json()
      setStats(data)
    } catch (e) {
      console.error(e)
    } finally {
      setLoadingStats(false)
    }
  }

  const loadList = async (targetPage = page, targetSize = size) => {
    try {
      setLoadingList(true)
      const params = new URLSearchParams()
      params.set("page", String(targetPage))
      params.set("size", String(targetSize))
      if (statuses.length) params.set("statuses", statuses.join(","))
      if (location) params.set("location", location)
      if (experience) params.set("experience", experience)
      if (degree) params.set("degree", degree)
      if (minK) params.set("minK", String(Number(minK)))
      if (maxK) params.set("maxK", String(Number(maxK)))
      if (keyword) params.set("keyword", keyword)
      if (filterHeadhunter) params.set("filterHeadhunter", "true")

      const res = await fetch(`${API_BASE}/api/boss/list?${params.toString()}`)
      if (!res.ok) throw new Error("加载列表失败")
      const data: PagedResult = await res.json()

      let cleanItems = data.items || []
      if (filterHeadhunter) {
        cleanItems = cleanItems.filter(it => {
          const hp = (it.hrPosition || "").toLowerCase()
          return !(hp.includes("猎头") || hp.includes("獵頭"))
        })
      }

      setItems(cleanItems)
      setTotal(data.total || 0)
      setPage(data.page || targetPage)
      setSize(data.size || targetSize)
    } catch (e) {
      console.error(e)
    } finally {
      setLoadingList(false)
    }
  }

  useEffect(() => {
    loadStats()
    loadList(1, size)
  }, [])

  const onReload = async () => {
    try {
      setReloading(true)
      await Promise.all([loadStats(), loadList(1, size)])
    } finally {
      setReloading(false)
    }
  }

  const exportCSV = async () => {
    try {
      setExporting(true)
      const baseParams = new URLSearchParams()
      if (statuses.length) baseParams.set("statuses", statuses.join(","))
      if (location) baseParams.set("location", location)
      if (experience) baseParams.set("experience", experience)
      if (degree) baseParams.set("degree", degree)
      if (minK) baseParams.set("minK", String(Number(minK)))
      if (maxK) baseParams.set("maxK", String(Number(maxK)))
      if (keyword) baseParams.set("keyword", keyword)
      if (filterHeadhunter) baseParams.set("filterHeadhunter", "true")

      const pageSize = 1000
      let currentPage = 1
      let all: BossJob[] = []
      let totalCount = 0

      while (true) {
        const params = new URLSearchParams(baseParams)
        params.set("page", String(currentPage))
        params.set("size", String(pageSize))
        const res = await fetch(`${API_BASE}/api/boss/list?${params.toString()}`)
        const data: PagedResult = await res.json()
        let chunk = data.items || []
        if (filterHeadhunter) {
          chunk = chunk.filter(it => {
            const hp = (it.hrPosition || "").toLowerCase()
            return !(hp.includes("猎头") || hp.includes("獵頭"))
          })
        }
        if (currentPage === 1) totalCount = data.total || chunk.length
        all = all.concat(chunk)
        if (all.length >= totalCount || chunk.length === 0) break
        currentPage += 1
      }

      const header = ["公司名称", "岗位名称", "薪资", "工作地点", "经验", "学历", "HR", "投递状态", "链接", "创建时间"]
      const rows = all.map((it) => [
        it.companyName || "",
        it.jobName || "",
        it.salary || "",
        it.location || "",
        it.experience || "",
        it.degree || "",
        it.hrName || "",
        it.deliveryStatus || "",
        it.jobUrl || "",
        it.createdAt || "",
      ])
      const csv = [header, ...rows]
        .map((r) => r.map((v) => (String(v).includes(",") ? `"${String(v).replace(/"/g, '""')}"` : String(v))).join(","))
        .join("\n")
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `boss_jobs_${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      console.error("export CSV failed", e)
      alert("导出失败，请稍后重试")
    } finally {
      setExporting(false)
    }
  }

  const kpiCards = useMemo(() => {
    const k = stats?.kpi
    return [
      { title: "总岗位数", value: k?.total ?? 0, color: "text-slate-900 dark:text-white" },
      { title: "已投递", value: k?.delivered ?? 0, color: "text-emerald-600 dark:text-emerald-400" },
      { title: "未投递", value: k?.pending ?? 0, color: "text-blue-600 dark:text-blue-400" },
      { title: "已过滤", value: k?.filtered ?? 0, color: "text-rose-600 dark:text-rose-400" },
      { title: "投递失败", value: k?.failed ?? 0, color: "text-amber-600 dark:text-amber-400" },
      { title: "平均月薪(K)", value: k?.avgMonthlyK ?? 0, color: "text-indigo-600 dark:text-indigo-400" },
    ]
  }, [stats])

  return (
    <div className="w-full max-w-full space-y-3.5 overflow-hidden">
      {showHeader && (
        <PageHeader
          title="Boss 投递与岗位数据分析"
          subtitle="岗位抓取、筛选过滤、投递状态聚合与全量数据管理"
          icon={<BiBarChart className="text-xl" />}
        />
      )}

      {/* 紧凑 KPI 指标条 */}
      <div className="grid grid-cols-3 md:grid-cols-6 divide-y md:divide-y-0 md:divide-x divide-slate-100 dark:divide-neutral-800 bg-white dark:bg-neutral-900 rounded-xl border border-slate-200/80 dark:border-neutral-800 shadow-xs">
        {kpiCards.map((c, idx) => (
          <div key={idx} className="px-3.5 py-2.5">
            <div className="text-[11px] font-medium text-slate-500 dark:text-neutral-400 truncate">{c.title}</div>
            <div className={`text-lg font-bold tracking-tight mt-0.5 ${c.color}`}>{c.value}</div>
          </div>
        ))}
      </div>

      {/* 选项卡式图表区 (可按需展开/收起，默认收起以保证界面小巧紧凑) */}
      {showCharts && (
        <div className="p-4 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs animate-in fade-in duration-200">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-3 pb-2.5 border-b border-slate-100 dark:border-neutral-800">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-700 dark:text-neutral-300">透视维度：</span>
              <div className="flex rounded-md bg-slate-100 dark:bg-neutral-800 p-0.5 text-xs">
                <button
                  onClick={() => setChartTab("overview")}
                  className={`px-2.5 py-0.5 rounded text-xs font-medium transition-all ${
                    chartTab === "overview"
                      ? "bg-white dark:bg-neutral-700 text-blue-600 dark:text-blue-400 shadow-xs"
                      : "text-slate-600 dark:text-neutral-400"
                  }`}
                >
                  状态与薪资
                </button>
                <button
                  onClick={() => setChartTab("companies")}
                  className={`px-2.5 py-0.5 rounded text-xs font-medium transition-all ${
                    chartTab === "companies"
                      ? "bg-white dark:bg-neutral-700 text-blue-600 dark:text-blue-400 shadow-xs"
                      : "text-slate-600 dark:text-neutral-400"
                  }`}
                >
                  行业与企业 TOP10
                </button>
                <button
                  onClick={() => setChartTab("dimensions")}
                  className={`px-2.5 py-0.5 rounded text-xs font-medium transition-all ${
                    chartTab === "dimensions"
                      ? "bg-white dark:bg-neutral-700 text-blue-600 dark:text-blue-400 shadow-xs"
                      : "text-slate-600 dark:text-neutral-400"
                  }`}
                >
                  学历与经验分布
                </button>
              </div>
            </div>
            <button
              onClick={() => setShowCharts(false)}
              className="text-xs text-slate-400 hover:text-slate-600 dark:hover:text-neutral-300 transition-colors"
            >
              收起图表 ▲
            </button>
          </div>

          {/* 图表内容 */}
          {chartTab === "overview" && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <div className="text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-1.5 flex items-center gap-1">
                  <BiPieChart className="text-blue-500" /> 投递状态构成
                </div>
                {stats ? (
                  <ChartCanvas
                    type="pie"
                    labels={stats.charts.byStatus.map((x) => x.name)}
                    data={stats.charts.byStatus.map((x) => x.value)}
                  />
                ) : (
                  <div className="h-44 flex items-center justify-center text-xs text-slate-400">加载图表中...</div>
                )}
              </div>
              <div>
                <div className="text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-1.5 flex items-center gap-1">
                  <BiLineChart className="text-rose-500" /> 薪资区间分布 (K/月)
                </div>
                {stats ? (
                  <ChartCanvas
                    type="line"
                    labels={stats.charts.salaryBuckets.map((x) => x.bucket)}
                    data={stats.charts.salaryBuckets.map((x) => x.value)}
                    color="#ef4444"
                  />
                ) : (
                  <div className="h-44 flex items-center justify-center text-xs text-slate-400">加载图表中...</div>
                )}
              </div>
            </div>
          )}

          {chartTab === "companies" && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <div className="text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-1.5 flex items-center gap-1">
                  <BiBarChart className="text-indigo-500" /> 行业招聘岗位数 TOP10
                </div>
                {stats ? (
                  <ChartCanvas
                    type="bar"
                    labels={stats.charts.byIndustry.map((x) => x.name)}
                    data={stats.charts.byIndustry.map((x) => x.value)}
                    colors={CATEGORY_COLORS}
                  />
                ) : (
                  <div className="h-44 flex items-center justify-center text-xs text-slate-400">加载图表中...</div>
                )}
              </div>
              <div>
                <div className="text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-1.5 flex items-center gap-1">
                  <BiBuilding className="text-emerald-500" /> 企业招聘岗位数 TOP10
                </div>
                {stats ? (
                  <ChartCanvas
                    type="bar"
                    labels={stats.charts.byCompany.map((x) => x.name)}
                    data={stats.charts.byCompany.map((x) => x.value)}
                    colors={CATEGORY_COLORS}
                  />
                ) : (
                  <div className="h-44 flex items-center justify-center text-xs text-slate-400">加载图表中...</div>
                )}
              </div>
            </div>
          )}

          {chartTab === "dimensions" && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <div className="text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-1.5 flex items-center gap-1">
                  <BiBarChart className="text-amber-500" /> 工作经验要求分布
                </div>
                {stats ? (
                  <ChartCanvas
                    type="bar"
                    labels={stats.charts.byExperience.map((x) => x.name)}
                    data={stats.charts.byExperience.map((x) => x.value)}
                    colors={CATEGORY_COLORS}
                  />
                ) : (
                  <div className="h-44 flex items-center justify-center text-xs text-slate-400">加载图表中...</div>
                )}
              </div>
              <div>
                <div className="text-xs font-semibold text-slate-700 dark:text-neutral-300 mb-1.5 flex items-center gap-1">
                  <BiBarChart className="text-purple-500" /> 学历要求门槛分布
                </div>
                {stats ? (
                  <ChartCanvas
                    type="bar"
                    labels={stats.charts.byDegree.map((x) => x.name)}
                    data={stats.charts.byDegree.map((x) => x.value)}
                    colors={CATEGORY_COLORS}
                  />
                ) : (
                  <div className="h-44 flex items-center justify-center text-xs text-slate-400">加载图表中...</div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* 紧凑工具栏与筛选控制 */}
      <div className="p-3 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs space-y-2.5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          {/* 搜索与快捷状态过滤 */}
          <div className="flex flex-wrap items-center gap-1.5 flex-1 min-w-[280px]">
            <div className="relative w-48 sm:w-60">
              <Input
                className="h-8 text-xs pl-8"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="搜索职位或公司..."
              />
              <BiSearch className="absolute left-2.5 top-2.5 text-slate-400 text-xs pointer-events-none" />
            </div>

            {statusOptions.map((s) => {
              const checked = statuses.includes(s)
              return (
                <button
                  key={s}
                  onClick={() => {
                    setStatuses(checked ? statuses.filter(x => x !== s) : [...statuses, s])
                  }}
                  className={`px-2 py-1 rounded-md text-xs font-medium border transition-colors ${
                    checked
                      ? "bg-blue-50 dark:bg-blue-900/40 border-blue-300 dark:border-blue-700 text-blue-700 dark:text-blue-300 font-bold"
                      : "bg-slate-50 dark:bg-neutral-800/80 border-slate-200 dark:border-neutral-700 text-slate-600 dark:text-neutral-400 hover:bg-slate-100"
                  }`}
                >
                  {s}
                </button>
              )
            })}

            <button
              onClick={() => setFilterHeadhunter(!filterHeadhunter)}
              className={`px-2 py-1 rounded-md text-xs font-medium border transition-colors ${
                filterHeadhunter
                  ? "bg-emerald-50 dark:bg-emerald-900/40 border-emerald-300 dark:border-emerald-700 text-emerald-700 dark:text-emerald-300 font-bold"
                  : "bg-slate-50 dark:bg-neutral-800/80 border-slate-200 dark:border-neutral-700 text-slate-600 dark:text-neutral-400 hover:bg-slate-100"
              }`}
            >
              {filterHeadhunter ? "✓ 过滤猎头" : "过滤猎头"}
            </button>

            <button
              onClick={() => setShowAdvancedFilters(!showAdvancedFilters)}
              className="px-2 py-1 rounded-md text-xs font-medium text-slate-500 hover:text-slate-800 dark:hover:text-neutral-200 transition-colors flex items-center gap-0.5"
            >
              更多条件 {showAdvancedFilters ? "▲" : "▼"}
            </button>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            <Button
              size="sm"
              onClick={async () => {
                await loadList(1, size)
                await loadStats()
              }}
              disabled={loadingList}
              className="text-xs h-8 px-3"
            >
              <BiFilter className="mr-1" /> 筛选
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={exportCSV}
              disabled={exporting}
              className="text-xs h-8 px-2.5"
            >
              <BiDownload className="mr-1" /> 导出
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={onReload}
              disabled={reloading}
              className="text-xs h-8 px-2"
              title="刷新数据"
            >
              <BiRefresh className="text-sm" />
            </Button>
            {!showCharts && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowCharts(true)}
                className="text-xs h-8 px-2.5 text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20"
              >
                <BiBarChart className="mr-1" /> 展开图表
              </Button>
            )}
          </div>
        </div>

        {/* 展开的高级筛选行 */}
        {showAdvancedFilters && (
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 pt-2 border-t border-slate-100 dark:border-neutral-800 text-xs animate-in fade-in duration-150">
            <div>
              <Label className="text-[10px] text-slate-400 mb-0.5 block">城市</Label>
              <Input
                className="h-7 text-xs"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder="如：深圳"
              />
            </div>
            <div>
              <Label className="text-[10px] text-slate-400 mb-0.5 block">经验</Label>
              <Input
                className="h-7 text-xs"
                value={experience}
                onChange={(e) => setExperience(e.target.value)}
                placeholder="如：3-5年"
              />
            </div>
            <div>
              <Label className="text-[10px] text-slate-400 mb-0.5 block">学历</Label>
              <Input
                className="h-7 text-xs"
                value={degree}
                onChange={(e) => setDegree(e.target.value)}
                placeholder="如：本科"
              />
            </div>
            <div>
              <Label className="text-[10px] text-slate-400 mb-0.5 block">最低月薪(K)</Label>
              <Input
                className="h-7 text-xs"
                type="number"
                value={minK}
                onChange={(e) => setMinK(e.target.value)}
                placeholder="5"
              />
            </div>
            <div>
              <Label className="text-[10px] text-slate-400 mb-0.5 block">最高月薪(K)</Label>
              <Input
                className="h-7 text-xs"
                type="number"
                value={maxK}
                onChange={(e) => setMaxK(e.target.value)}
                placeholder="30"
              />
            </div>
          </div>
        )}
      </div>

      {/* 岗位数据表格 */}
      <div className="rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-xs overflow-hidden">
        <div className="px-3.5 py-2.5 border-b border-slate-100 dark:border-neutral-800 flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs">
            <BiBriefcase className="text-blue-600" />
            <span className="font-semibold text-slate-900 dark:text-white">抓取岗位数据列表</span>
            <span className="text-slate-400 text-[11px]">（共 {total} 个匹配岗位）</span>
          </div>
        </div>

        <div className="w-full overflow-x-auto">
          <table className="w-full text-xs text-left">
            <thead className="bg-slate-50 dark:bg-neutral-800/60 text-slate-600 dark:text-neutral-400 font-semibold border-b border-slate-200 dark:border-neutral-800">
              <tr>
                <th className="px-3.5 py-2.5 min-w-[150px]">公司信息</th>
                <th className="px-3.5 py-2.5 min-w-[160px]">招聘岗位</th>
                <th className="px-3.5 py-2.5 min-w-[110px]">薪资与地点</th>
                <th className="px-3.5 py-2.5 min-w-[120px]">HR 与活跃度</th>
                <th className="px-3.5 py-2.5 min-w-[90px]">投递状态</th>
                <th className="px-3.5 py-2.5 text-right min-w-[90px]">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-neutral-800">
              {loadingList ? (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-slate-400 text-xs">
                    正在查询岗位数据...
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-slate-400 text-xs">
                    暂无符合条件的岗位记录
                  </td>
                </tr>
              ) : (
                items.map((it) => (
                  <tr
                    key={it.id}
                    className="hover:bg-slate-50/80 dark:hover:bg-neutral-800/40 transition-colors"
                  >
                    {/* 公司 */}
                    <td className="px-3.5 py-2.5 align-top">
                      <div className="font-bold text-slate-900 dark:text-white text-xs">
                        {it.companyName || "-"}
                      </div>
                      <div className="flex flex-wrap gap-1 mt-1 text-[10px] text-slate-400">
                        {it.industry && <span>{it.industry}</span>}
                        {it.companyScale && <span>• {it.companyScale}</span>}
                      </div>
                    </td>

                    {/* 岗位 */}
                    <td className="px-3.5 py-2.5 align-top">
                      <div className="font-bold text-blue-600 dark:text-blue-400 text-xs">
                        {it.jobName || "-"}
                      </div>
                      <div className="flex flex-wrap gap-1.5 mt-1">
                        {it.experience && (
                          <span className="px-1.5 py-0.5 rounded bg-slate-100 dark:bg-neutral-800 text-[10px] text-slate-600 dark:text-neutral-400">
                            {it.experience}
                          </span>
                        )}
                        {it.degree && (
                          <span className="px-1.5 py-0.5 rounded bg-slate-100 dark:bg-neutral-800 text-[10px] text-slate-600 dark:text-neutral-400">
                            {it.degree}
                          </span>
                        )}
                      </div>
                    </td>

                    {/* 薪资与地点 */}
                    <td className="px-3.5 py-2.5 align-top">
                      <div className="font-bold text-rose-600 dark:text-rose-400 text-xs">
                        {it.salary || "-"}
                      </div>
                      <div className="text-[10px] text-slate-400 mt-1 flex items-center gap-0.5">
                        <BiMapPin className="text-slate-400" />
                        <span className="truncate max-w-[120px]">{it.location || "-"}</span>
                      </div>
                    </td>

                    {/* HR */}
                    <td className="px-3.5 py-2.5 align-top">
                      <div className="font-medium text-slate-800 dark:text-neutral-200 text-xs">
                        {it.hrName || "-"}
                        {it.hrPosition && (
                          <span className="text-slate-400 font-normal ml-1">({it.hrPosition})</span>
                        )}
                      </div>
                      {it.hrActiveStatus && (
                        <div className="mt-1">
                          <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                            it.hrActiveStatus.includes("今日") || it.hrActiveStatus.includes("刚刚") || it.hrActiveStatus.includes("在线")
                              ? "bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400 font-medium"
                              : "bg-slate-100 text-slate-500 dark:bg-neutral-800 dark:text-neutral-400"
                          }`}>
                            {it.hrActiveStatus}
                          </span>
                        </div>
                      )}
                    </td>

                    {/* 状态 */}
                    <td className="px-3.5 py-2.5 align-top">
                      <span className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                        it.deliveryStatus === "已投递"
                          ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300"
                          : it.deliveryStatus === "已过滤"
                          ? "bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-300"
                          : "bg-slate-100 text-slate-700 dark:bg-neutral-800 dark:text-neutral-300"
                      }`}>
                        {it.deliveryStatus || "未投递"}
                      </span>
                    </td>

                    {/* 操作 */}
                    <td className="px-3.5 py-2.5 align-top text-right space-x-2 whitespace-nowrap">
                      <button
                        onClick={() => setDetailJob(it)}
                        className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline"
                      >
                        详情
                      </button>
                      {it.jobUrl && (
                        <a
                          href={it.jobUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-xs text-slate-400 hover:text-slate-600 inline-flex items-center gap-0.5"
                        >
                          链接 <BiLinkExternal />
                        </a>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* 表格底部翻页 */}
        <div className="px-3.5 py-2.5 border-t border-slate-100 dark:border-neutral-800 flex items-center justify-between text-xs text-slate-500 dark:text-neutral-400">
          <div>共 {total} 条数据，每页 {size} 条</div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => loadList(page - 1, size)}
              disabled={page <= 1}
              className="p-1.5 rounded border border-slate-200 dark:border-neutral-700 disabled:opacity-40"
            >
              <BiChevronLeft className="text-base" />
            </button>
            <span>第 {page} 页 / 共 {Math.ceil(total / size) || 1} 页</span>
            <button
              onClick={() => loadList(page + 1, size)}
              disabled={page * size >= total}
              className="p-1.5 rounded border border-slate-200 dark:border-neutral-700 disabled:opacity-40"
            >
              <BiChevronRight className="text-base" />
            </button>
          </div>
        </div>
      </div>

      {/* 岗位详情弹窗 (替代原来占满整屏的19列臃肿表格) */}
      {detailJob && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-2xl p-6 rounded-xl bg-white dark:bg-neutral-900 border border-slate-200/80 dark:border-neutral-800 shadow-2xl max-h-[85vh] overflow-y-auto">
            <div className="flex items-start justify-between pb-3 border-b border-slate-100 dark:border-neutral-800 mb-4">
              <div>
                <h3 className="text-base font-bold text-slate-900 dark:text-white">
                  {detailJob.jobName}
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  {detailJob.companyName} | {detailJob.salary} | {detailJob.location}
                </p>
              </div>
              <button
                onClick={() => setDetailJob(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg"
              >
                <BiX className="text-2xl" />
              </button>
            </div>

            <div className="space-y-4 text-xs">
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 p-3 rounded-xl bg-slate-50 dark:bg-neutral-800/40">
                <div>
                  <span className="text-slate-400 block text-[10px]">经验要求</span>
                  <span className="font-semibold text-slate-700 dark:text-neutral-300">{detailJob.experience || "不限"}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-[10px]">学历要求</span>
                  <span className="font-semibold text-slate-700 dark:text-neutral-300">{detailJob.degree || "不限"}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-[10px]">所属行业</span>
                  <span className="font-semibold text-slate-700 dark:text-neutral-300">{detailJob.industry || "未填写"}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-[10px]">企业规模</span>
                  <span className="font-semibold text-slate-700 dark:text-neutral-300">{detailJob.companyScale || "未填写"}</span>
                </div>
              </div>

              {detailJob.companyAddress && (
                <div>
                  <span className="font-semibold text-slate-700 dark:text-neutral-300">公司地址: </span>
                  <span className="text-slate-600 dark:text-neutral-400">{detailJob.companyAddress}</span>
                </div>
              )}

              {detailJob.jobDescription && (
                <div>
                  <h4 className="font-bold text-slate-800 dark:text-neutral-200 mb-1.5">岗位职责与任职要求</h4>
                  <div className="p-3 rounded-xl bg-slate-50 dark:bg-neutral-800/50 text-slate-700 dark:text-neutral-300 whitespace-pre-wrap leading-relaxed">
                    {detailJob.jobDescription}
                  </div>
                </div>
              )}

              {detailJob.introduce && (
                <div>
                  <h4 className="font-bold text-slate-800 dark:text-neutral-200 mb-1.5">公司介绍</h4>
                  <div className="p-3 rounded-xl bg-slate-50 dark:bg-neutral-800/50 text-slate-600 dark:text-neutral-400 whitespace-pre-wrap leading-relaxed">
                    {detailJob.introduce}
                  </div>
                </div>
              )}
            </div>

            <div className="mt-6 pt-3 border-t border-slate-100 dark:border-neutral-800 flex items-center justify-between">
              {detailJob.jobUrl ? (
                <a
                  href={detailJob.jobUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
                >
                  在 Boss 平台打开原始岗位链接 <BiLinkExternal />
                </a>
              ) : <div />}
              <button
                onClick={() => setDetailJob(null)}
                className="px-4 py-2 rounded-lg bg-slate-100 dark:bg-neutral-800 text-xs font-semibold text-slate-700 dark:text-neutral-300"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}