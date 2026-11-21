import { useState } from "react"
import { Link, useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import * as api from "../lib/api"
import { isAdmin, isLead, useAuth } from "../lib/auth"
import { ago, fmtDate } from "../lib/format"
import { Empty, ErrorBox, Loading, PageHeader, PriorityTag, SlaTag, StatusPill } from "../components/ui"
import { ResolveProblemDialog } from "./OutageMap"

export function ProblemsPage() {
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ["problems"], queryFn: api.problems })
  return (
    <div className="mx-auto max-w-[1100px]">
      <PageHeader eyebrow="Problem management" title="Problems">
        <Link to="/map" className="btn btn-ghost">
          Open outage map
        </Link>
      </PageHeader>
      <p className="-mt-3 mb-5 max-w-2xl text-sm text-mute">
        When several incidents on related services arrive close together, CampusOps correlates them through the
        dependency graph and opens a problem on the most likely shared root cause.
      </p>
      {isLoading && <Loading />}
      {error && <ErrorBox error={error} retry={refetch} />}
      {data && data.length === 0 && <Empty title="No problems detected">Correlated outages will show up here.</Empty>}
      <div className="space-y-3">
        {data?.map((p) => (
          <Link
            key={p.id}
            to={"/problems/" + p.id}
            className="panel flex items-stretch overflow-hidden hover:border-line-strong"
          >
            <div className="w-1.5 shrink-0" style={{ background: p.status === "OPEN" ? "var(--outage)" : "var(--ok)" }} />
            <div className="min-w-0 flex-1 p-4">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-mono text-[13px] font-semibold">{p.number}</span>
                <span
                  className="rounded px-1.5 py-px font-cond text-[11px] font-semibold uppercase tracking-wider"
                  style={{
                    color: p.status === "OPEN" ? "#fff" : "var(--ok)",
                    background: p.status === "OPEN" ? "var(--outage)" : "color-mix(in srgb, var(--ok) 12%, transparent)",
                  }}
                >
                  {p.status}
                </span>
                <span className="ml-auto font-mono text-[11.5px] text-mute">detected {ago(p.detectedAt)}</span>
              </div>
              <div className="mt-1 font-medium">{p.title}</div>
              <div className="mt-0.5 text-[13px] text-mute">
                Root cause: {p.rootService} · {p.incidents.length} linked incidents
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}

export function ProblemPage() {
  const { id = "" } = useParams()
  const { user } = useAuth()
  const [resolving, setResolving] = useState(false)
  const { data: p, error, isLoading, refetch } = useQuery({ queryKey: ["problem", id], queryFn: () => api.problem(id) })
  if (isLoading) return <Loading />
  if (error) return <ErrorBox error={error} retry={refetch} />
  if (!p) return null
  const canResolve = (isLead(user) || isAdmin(user)) && p.status === "OPEN"

  return (
    <div className="mx-auto max-w-[1000px]">
      <Link to="/problems" className="mb-3 inline-flex items-center gap-1 text-sm text-mute hover:text-ink">
        <ArrowLeft size={14} /> Problems
      </Link>
      <div className="panel overflow-hidden">
        <div className="h-1.5" style={{ background: p.status === "OPEN" ? "var(--outage)" : "var(--ok)" }} />
        <div className="p-5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-[15px] font-semibold">{p.number}</span>
            <span className="font-cond text-[12px] font-semibold uppercase tracking-wider" style={{ color: p.status === "OPEN" ? "var(--outage)" : "var(--ok)" }}>
              {p.status}
            </span>
          </div>
          <h1 className="mt-1 font-cond text-[24px] font-semibold leading-tight">{p.title}</h1>
          <div className="mt-2 flex flex-wrap gap-x-5 text-[13px] text-ink-2">
            <span>
              Root service:{" "}
              <Link to="/map" className="font-medium text-signal hover:underline">
                {p.rootService}
              </Link>
            </span>
            <span className="font-mono text-[12px] text-mute">detected {fmtDate(p.detectedAt)}</span>
            {p.resolvedAt && <span className="font-mono text-[12px] text-mute">resolved {fmtDate(p.resolvedAt)}</span>}
          </div>
          <div className="mt-4 rounded-md bg-sunk p-3.5">
            <div className="eyebrow mb-1">Correlation analysis</div>
            <p className="text-[13.5px] leading-relaxed">{p.analysis}</p>
          </div>
          {p.resolutionSummary && (
            <div className="mt-3 rounded-md border-l-4 bg-ok/5 p-3.5" style={{ borderLeftColor: "var(--ok)" }}>
              <div className="eyebrow mb-1">Resolution</div>
              <p className="text-[13.5px]">{p.resolutionSummary}</p>
            </div>
          )}
          {canResolve && (
            <button className="btn btn-primary mt-4" onClick={() => setResolving(true)}>
              Resolve problem + all linked incidents
            </button>
          )}
        </div>
      </div>

      <h2 className="mb-2 mt-6 font-cond text-[17px] font-semibold">Linked incidents</h2>
      <div className="panel divide-y divide-line">
        {p.incidents.map((i) => (
          <Link key={i.id} to={"/incidents/" + i.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 hover:bg-sunk">
            <span className="font-mono text-[12.5px]">{i.number}</span>
            <PriorityTag p={i.priority} />
            <span className="min-w-0 flex-1 truncate text-[13.5px]">{i.title}</span>
            <span className="text-[12px] text-mute">{i.service?.name}</span>
            <SlaTag s={i.slaState} />
            <StatusPill s={i.status} />
          </Link>
        ))}
      </div>
      {resolving && <ResolveProblemDialog p={p} onClose={() => setResolving(false)} />}
    </div>
  )
}
