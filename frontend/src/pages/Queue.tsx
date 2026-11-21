import type { ReactNode } from "react"
import { Link } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CheckCheck, Hand, Loader2, Play } from "lucide-react"
import * as api from "../lib/api"
import { useAuth } from "../lib/auth"
import { useToast } from "../lib/toast"
import { ago, PRIORITY_VAR } from "../lib/format"
import type { IncidentSummary } from "../lib/types"
import { Countdown } from "../components/Countdown"
import { Empty, ErrorBox, Loading, Meter, PageHeader, PriorityTag, SlaTag, StatusPill } from "../components/ui"

const ACTIVE = ["ASSIGNED", "ACKNOWLEDGED", "IN_PROGRESS", "AWAITING_INFO", "REOPENED"]
const PRI_RANK = { P1: 0, P2: 1, P3: 2, P4: 3 }

function byUrgency(a: IncidentSummary, b: IncidentSummary) {
  const ad = a.resolutionDue ? new Date(a.resolutionDue).getTime() : Infinity
  const bd = b.resolutionDue ? new Date(b.resolutionDue).getTime() : Infinity
  if (ad !== bd) return ad - bd
  return (PRI_RANK[a.priority ?? "P4"] ?? 3) - (PRI_RANK[b.priority ?? "P4"] ?? 3)
}

function QueueRow({ i, children }: { i: IncidentSummary; children?: ReactNode }) {
  return (
    <div className="flex items-stretch border-b border-line last:border-0">
      <div className="w-1 shrink-0" style={{ background: i.priority ? PRIORITY_VAR[i.priority] : "var(--line)" }} />
      <Link to={"/incidents/" + i.id} className="grid min-w-0 flex-1 grid-cols-1 gap-x-4 gap-y-1 px-4 py-3 hover:bg-sunk sm:grid-cols-[minmax(0,1fr)_150px]">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-[12.5px] font-medium">{i.number}</span>
            <PriorityTag p={i.priority} />
            <StatusPill s={i.status} />
            {i.escalationLevel > 0 && <span className="rounded bg-p2/15 px-1 text-[11px] font-semibold text-p2">L{i.escalationLevel}</span>}
            {i.problemNumber && <span className="rounded bg-outage px-1 font-mono text-[10.5px] font-semibold text-white">{i.problemNumber}</span>}
          </div>
          <div className="mt-0.5 truncate text-[14px] font-medium">{i.title}</div>
          <div className="truncate text-[12px] text-mute">
            {i.service?.name}
            {i.location ? " · " + i.location : ""} · {i.reporter?.name} · {ago(i.createdAt)}
            {i.supporters > 0 && " · " + i.affectedUsers + " affected, " + i.supporters + " said me too"}
          </div>
        </div>
        <div className="flex items-center gap-3 sm:flex-col sm:items-end sm:justify-center sm:gap-0.5">
          {i.status !== "RESOLVED" && <Countdown due={i.resolutionDue} className="text-[17px]" />}
          <SlaTag s={i.slaState} />
        </div>
      </Link>
      {children && <div className="flex items-center pr-3">{children}</div>}
    </div>
  )
}

export default function QueuePage() {
  const { user, setUser } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const mine = useQuery({ queryKey: ["assigned"], queryFn: api.assignedIncidents })
  const pool = useQuery({
    queryKey: ["queue", "unassigned"],
    queryFn: () => api.searchIncidents({ unassigned: true, openOnly: true, size: 50, sort: "priority", dir: "asc" }),
  })

  const act = useMutation({
    mutationFn: (fn: () => Promise<unknown>) => fn(),
    onSuccess: () => qc.invalidateQueries({ predicate: (q) => ["assigned", "queue", "incident", "incidents"].includes(String(q.queryKey[0])) }),
    onError: (err) => toast({ title: "Couldn't do that", body: api.errorMessage(err), tone: "critical" }),
  })
  const avail = useMutation({ mutationFn: api.setAvailability, onSuccess: setUser })

  const list = (mine.data ?? []).filter((i) => ACTIVE.includes(i.status)).sort(byUrgency)
  const waiting = (mine.data ?? []).filter((i) => i.status === "RESOLVED")
  const unassigned = [...(pool.data?.items ?? [])].sort(byUrgency)
  const max = user?.maxConcurrent ?? 5
  const working = list.filter((i) => i.status !== "REOPENED").length
  const breached = list.filter((i) => i.slaState === "BREACHED").length
  const atRisk = list.filter((i) => i.slaState === "AT_RISK").length

  return (
    <div className="mx-auto max-w-[1100px]">
      <PageHeader eyebrow={user?.team ?? "Field desk"} title="My queue">
        <button
          onClick={() => avail.mutate(!user?.available)}
          disabled={avail.isPending}
          className={"btn " + (user?.available ? "btn-ghost" : "btn-primary")}
        >
          <span className={"h-2 w-2 rounded-full " + (user?.available ? "bg-ok" : "bg-p4")} />
          {user?.available ? "Available · go off shift" : "Off shift · go available"}
        </button>
      </PageHeader>

      <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="panel p-3.5">
          <div className="eyebrow">Workload</div>
          <div className="mt-1 font-mono text-[22px] font-semibold">
            {working}
            <span className="text-[14px] text-mute">/{max}</span>
          </div>
          <Meter value={working} max={max} />
        </div>
        <div className="panel p-3.5">
          <div className="eyebrow">Breached</div>
          <div className="mt-1 font-mono text-[22px] font-semibold" style={{ color: breached ? "var(--breach)" : undefined }}>
            {breached}
          </div>
        </div>
        <div className="panel p-3.5">
          <div className="eyebrow">At risk</div>
          <div className="mt-1 font-mono text-[22px] font-semibold" style={{ color: atRisk ? "var(--risk)" : undefined }}>
            {atRisk}
          </div>
        </div>
        <div className="panel p-3.5">
          <div className="eyebrow">Waiting on reporter</div>
          <div className="mt-1 font-mono text-[22px] font-semibold">{waiting.length + list.filter((i) => i.status === "AWAITING_INFO").length}</div>
        </div>
      </div>

      <section>
        <div className="mb-2 flex items-baseline justify-between">
          <h2 className="font-cond text-[17px] font-semibold">Assigned to me</h2>
          <span className="text-xs text-mute">Soonest deadline first · timers tick live</span>
        </div>
        {mine.isLoading && <Loading />}
        {mine.error && <ErrorBox error={mine.error} retry={mine.refetch} />}
        {mine.data && list.length === 0 && <Empty title="Nothing assigned to you">Take something from the queue below.</Empty>}
        {list.length > 0 && (
          <div className="panel overflow-hidden">
            {list.map((i) => (
              <QueueRow key={i.id} i={i}>
                {(i.status === "ASSIGNED" || i.status === "REOPENED") && (
                  <button className="btn btn-primary px-3" disabled={act.isPending} onClick={() => act.mutate(() => api.acknowledge(i.id))}>
                    <CheckCheck size={14} /> <span className="hidden md:inline">Ack</span>
                  </button>
                )}
                {i.status === "ACKNOWLEDGED" && (
                  <button className="btn btn-ghost px-3" disabled={act.isPending} onClick={() => act.mutate(() => api.startWork(i.id))}>
                    <Play size={14} /> <span className="hidden md:inline">Start</span>
                  </button>
                )}
              </QueueRow>
            ))}
          </div>
        )}
      </section>

      <section className="mt-8">
        <div className="mb-2 flex items-baseline justify-between">
          <h2 className="font-cond text-[17px] font-semibold">Unassigned</h2>
          <span className="text-xs text-mute">Nobody's picked these up yet</span>
        </div>
        {pool.isLoading && <Loading />}
        {pool.error && <ErrorBox error={pool.error} retry={pool.refetch} />}
        {pool.data && unassigned.length === 0 && <Empty title="The queue is clear">Auto-assignment has placed every open incident.</Empty>}
        {unassigned.length > 0 && (
          <div className="panel overflow-hidden">
            {unassigned.map((i) => (
              <QueueRow key={i.id} i={i}>
                <button className="btn btn-primary px-3" disabled={act.isPending} onClick={() => act.mutate(() => api.takeIncident(i.id))}>
                  {act.isPending ? <Loader2 size={14} className="animate-spin" /> : <Hand size={14} />} Take
                </button>
              </QueueRow>
            ))}
          </div>
        )}
      </section>

      {waiting.length > 0 && (
        <section className="mt-8">
          <h2 className="mb-2 font-cond text-[17px] font-semibold">Resolved, waiting for the reporter to confirm</h2>
          <div className="panel overflow-hidden">
            {waiting.map((i) => (
              <QueueRow key={i.id} i={i} />
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
