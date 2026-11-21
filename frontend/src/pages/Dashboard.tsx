import type { ReactNode } from "react"
import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"
import * as api from "../lib/api"
import { CHANNEL_LABEL, minutesLabel, PRIORITY_LABEL, PRIORITY_VAR, STATUS_LABEL } from "../lib/format"
import type { Priority, Status } from "../lib/types"
import { Countdown } from "../components/Countdown"
import { ErrorBox, Loading, Meter, PageHeader, PriorityTag } from "../components/ui"

function Kpi({ label, value, unit, tone, to }: { label: string; value: string | number; unit?: string; tone?: string; to?: string }) {
  const body = (
    <>
      <div className="eyebrow min-h-[28px] leading-tight">{label}</div>
      <div className="mt-1 font-mono text-[26px] font-semibold leading-none tnum" style={{ color: tone }}>
        {value}
        {unit && <span className="ml-0.5 text-[13px] font-medium text-mute">{unit}</span>}
      </div>
    </>
  )
  return to ? (
    <Link to={to} className="bg-card p-3.5 hover:bg-sunk">
      {body}
    </Link>
  ) : (
    <div className="bg-card p-3.5">{body}</div>
  )
}

function Bars({ rows }: { rows: { key: string; label: string; value: number; color: string }[] }) {
  const max = Math.max(1, ...rows.map((r) => r.value))
  return (
    <div className="space-y-2">
      {rows.map((r) => (
        <div key={r.key} className="grid grid-cols-[110px_1fr_36px] items-center gap-3 text-[13px]">
          <span className="truncate text-ink-2">{r.label}</span>
          <div className="h-3 rounded-[3px] bg-sunk">
            <div className="h-full rounded-[3px]" style={{ width: (r.value / max) * 100 + "%", background: r.color }} />
          </div>
          <span className="text-right font-mono text-[12.5px]">{r.value}</span>
        </div>
      ))}
    </div>
  )
}

function Card({ title, note, children, className = "" }: { title: string; note?: string; children: ReactNode; className?: string }) {
  return (
    <section className={"panel p-4 " + className}>
      <div className="mb-3 flex items-baseline justify-between gap-2">
        <h2 className="font-cond text-[15px] font-semibold">{title}</h2>
        {note && <span className="text-[11.5px] text-mute">{note}</span>}
      </div>
      {children}
    </section>
  )
}

export default function DashboardPage() {
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ["dashboard"], queryFn: api.dashboard, refetchInterval: 60000 })

  if (isLoading) return <Loading label="Loading the control room" />
  if (error) return <ErrorBox error={error} retry={refetch} />
  if (!data) return null
  const o = data.overview

  const trend = data.trend.map((t) => ({
    ...t,
    day: new Date(t.date + "T00:00:00").toLocaleDateString("en-IN", { day: "numeric", month: "short" }),
  }))

  return (
    <div className="mx-auto max-w-[1400px]">
      <PageHeader eyebrow="Control room" title="Campus IT overview">
        <Link to="/map" className="btn btn-ghost">Outage map</Link>
        <Link to="/incidents?openOnly=1" className="btn btn-primary">Open incidents</Link>
      </PageHeader>

      <div className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-line bg-line sm:grid-cols-3 lg:grid-cols-6">
        <Kpi label="Total incidents" value={o.total} to="/incidents" />
        <Kpi label="Open" value={o.open} to="/incidents?openOnly=1" />
        <Kpi label="Critical open" value={o.criticalOpen} tone={o.criticalOpen ? "var(--p1)" : undefined} to="/incidents?openOnly=1&priority=P1,P2" />
        <Kpi label="Resolved" value={o.resolved} tone="var(--ok)" />
        <Kpi
          label="SLA met"
          value={o.slaCompliance.toFixed(0)}
          unit="%"
          tone={o.slaCompliance >= 90 ? "var(--ok)" : o.slaCompliance >= 75 ? "var(--risk)" : "var(--breach)"}
        />
        <Kpi label="SLA breaches" value={o.slaBreaches} tone={o.slaBreaches ? "var(--breach)" : undefined} to="/admin/violations" />
        <Kpi label="Avg time to fix" value={minutesLabel(o.avgResolutionMinutes)} />
        <Kpi label="Avg rating" value={o.avgRating ? o.avgRating.toFixed(1) : "-"} unit={o.avgRating ? "/5" : undefined} />
        <Kpi label="Escalated" value={o.escalated} tone={o.escalated ? "var(--p2)" : undefined} to="/incidents?escalated=1" />
        <Kpi label="Unassigned" value={o.unassigned} tone={o.unassigned ? "var(--risk)" : undefined} />
        <Kpi label="Breached, still open" value={o.breachedOpen} tone={o.breachedOpen ? "var(--breach)" : undefined} to="/incidents?slaState=BREACHED&openOnly=1" />
        <Kpi label="Open problems" value={o.problemsOpen} tone={o.problemsOpen ? "var(--outage)" : undefined} to="/problems" />
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Card title="Created vs resolved" note="last 14 days">
          <div className="h-[230px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trend} margin={{ top: 6, right: 8, left: -18, bottom: 0 }}>
                <CartesianGrid stroke="var(--line)" vertical={false} />
                <XAxis dataKey="day" tick={{ fontSize: 11, fill: "var(--mute)" }} tickLine={false} axisLine={{ stroke: "var(--line)" }} interval="preserveStartEnd" />
                <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: "var(--mute)" }} tickLine={false} axisLine={false} />
                <Tooltip
                  contentStyle={{ background: "var(--card)", border: "1px solid var(--line)", borderRadius: 6, fontSize: 12 }}
                  labelStyle={{ color: "var(--ink)", fontWeight: 600 }}
                />
                <Area type="monotone" dataKey="created" name="Created" stroke="var(--p2)" fill="var(--p2)" fillOpacity={0.12} strokeWidth={2} />
                <Area type="monotone" dataKey="resolved" name="Resolved" stroke="var(--ok)" fill="var(--ok)" fillOpacity={0.12} strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <div className="mt-2 flex gap-4 text-xs text-mute">
            <span className="inline-flex items-center gap-1.5"><span className="h-0.5 w-4 bg-p2" /> Created</span>
            <span className="inline-flex items-center gap-1.5"><span className="h-0.5 w-4 bg-ok" /> Resolved</span>
          </div>
        </Card>

        <Card title="Likely to breach" note="risk model">
          {data.atRisk.length === 0 && <div className="py-6 text-center text-sm text-mute">Nothing at risk right now.</div>}
          <div className="divide-y divide-line">
            {data.atRisk.map((r) => (
              <Link key={r.id} to={"/incidents/" + r.id} className="block py-2.5 hover:bg-sunk">
                <div className="flex items-center gap-2">
                  <PriorityTag p={r.priority} />
                  <span className="font-mono text-[12px]">{r.number}</span>
                  <span
                    className="ml-auto font-cond text-[11px] font-semibold uppercase tracking-wider"
                    style={{ color: r.level === "HIGH" ? "var(--breach)" : r.level === "MEDIUM" ? "var(--risk)" : "var(--ok)" }}
                  >
                    {r.level}
                  </span>
                  <Countdown due={r.resolutionDue} className="text-[12.5px]" />
                </div>
                <div className="mt-0.5 truncate text-[13px]">{r.title}</div>
                <div className="truncate text-[11.5px] text-mute">{r.reason}</div>
              </Link>
            ))}
          </div>
        </Card>
      </div>

      <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <Card title="By priority">
          <Bars
            rows={(["P1", "P2", "P3", "P4"] as Priority[]).map((p) => ({
              key: p,
              label: p + " " + PRIORITY_LABEL[p],
              value: data.byPriority[p] ?? 0,
              color: PRIORITY_VAR[p],
            }))}
          />
        </Card>
        <Card title="By category">
          <Bars rows={data.categories.map((c) => ({ key: c.code, label: c.name, value: c.total, color: c.color ?? "var(--signal)" }))} />
        </Card>
        <Card title="By status">
          <Bars
            rows={Object.entries(data.byStatus).map(([k, v]) => ({
              key: k,
              label: STATUS_LABEL[k as Status] ?? k,
              value: v,
              color: "var(--signal)",
            }))}
          />
        </Card>
        <Card title="By channel">
          <Bars
            rows={Object.entries(data.byChannel)
              .sort((a, b) => b[1] - a[1])
              .map(([k, v]) => ({ key: k, label: CHANNEL_LABEL[k] ?? k, value: v, color: "var(--ink-2)" }))}
          />
        </Card>
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-2">
        <Card title="Technician workload" note="active vs max concurrent">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[480px] text-[13px]">
              <thead>
                <tr className="border-b border-line text-left">
                  <th className="eyebrow pb-2 font-semibold">Technician</th>
                  <th className="eyebrow w-[38%] pb-2 font-semibold">Load</th>
                  <th className="eyebrow pb-2 text-right font-semibold">30d fixed</th>
                  <th className="eyebrow pb-2 text-right font-semibold">Rating</th>
                </tr>
              </thead>
              <tbody>
                {[...data.technicians]
                  .sort((a, b) => b.active / b.max - a.active / a.max)
                  .map((t) => (
                    <tr key={t.id} className="border-b border-line last:border-0">
                      <td className="py-2 pr-3">
                        <div className="flex items-center gap-2">
                          <span className={"h-2 w-2 rounded-full " + (t.available ? "bg-ok" : "bg-p4")} title={t.available ? "On shift" : "Off shift"} />
                          <span className="font-medium">{t.name}</span>
                        </div>
                        <div className="pl-4 text-[11.5px] text-mute">{t.team}</div>
                      </td>
                      <td className="py-2 pr-3">
                        <div className="flex items-center gap-2">
                          <Meter value={t.active} max={t.max} />
                          <span className="w-9 shrink-0 text-right font-mono text-[12px]">
                            {t.active}/{t.max}
                          </span>
                        </div>
                      </td>
                      <td className="py-2 text-right font-mono">{t.resolved30d}</td>
                      <td className="py-2 text-right font-mono">{t.avgRating ? t.avgRating.toFixed(1) : "-"}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </Card>

        <Card title="Service analysis" note="all-time incidents per service">
          <div className="max-h-[420px] overflow-auto">
            <table className="w-full min-w-[420px] text-[13px]">
              <thead className="sticky top-0 bg-card">
                <tr className="border-b border-line text-left">
                  <th className="eyebrow pb-2 font-semibold">Service</th>
                  <th className="eyebrow pb-2 text-right font-semibold">Total</th>
                  <th className="eyebrow pb-2 text-right font-semibold">Open</th>
                  <th className="eyebrow pb-2 text-right font-semibold">P1</th>
                  <th className="eyebrow pb-2 text-right font-semibold">Breaches</th>
                </tr>
              </thead>
              <tbody>
                {data.services.map((s) => (
                  <tr key={s.id} className="border-b border-line last:border-0">
                    <td className="py-1.5 pr-3">
                      <Link to={"/incidents?serviceId=" + s.id} className="hover:underline">
                        {s.name}
                      </Link>
                    </td>
                    <td className="py-1.5 text-right font-mono">{s.total}</td>
                    <td className="py-1.5 text-right font-mono" style={{ color: s.open ? "var(--ink)" : "var(--mute)" }}>{s.open}</td>
                    <td className="py-1.5 text-right font-mono" style={{ color: s.p1 ? "var(--p1)" : "var(--mute)" }}>{s.p1}</td>
                    <td className="py-1.5 text-right font-mono" style={{ color: s.breaches ? "var(--breach)" : "var(--mute)" }}>{s.breaches}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </div>
  )
}
