import { Link, Navigate } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { ArrowRight, MessageCircleQuestion, Plus, Star } from "lucide-react"
import * as api from "../lib/api"
import { homeFor, isStaff, useAuth } from "../lib/auth"
import { ago, OPEN_STATUSES } from "../lib/format"
import type { IncidentSummary } from "../lib/types"
import { Empty, ErrorBox, Loading, PriorityTag, StatusPill } from "../components/ui"
import { Reach } from "../components/incident/MeToo"

function greeting() {
  const h = Number(new Intl.DateTimeFormat("en-IN", { hour: "numeric", hour12: false, timeZone: "Asia/Kolkata" }).format(new Date()))
  if (h < 12) return "Good morning"
  if (h < 17) return "Good afternoon"
  return "Good evening"
}

function OpenCard({ i }: { i: IncidentSummary }) {
  return (
    <Link to={"/incidents/" + i.id} className="panel block p-4 transition-colors hover:border-line-strong sm:p-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-[12.5px] font-medium text-signal">{i.number}</span>
        <StatusPill s={i.status} />
        <PriorityTag p={i.priority} />
        <span className="ml-auto text-xs text-mute">{ago(i.createdAt)}</span>
      </div>
      <div className="mt-1.5 text-[16px] font-medium leading-snug">{i.title}</div>
      <div className="mt-0.5 text-sm text-mute">
        {i.service?.name}
        {i.assignee ? " · " + i.assignee.name + " is on it" : " · Waiting for assignment"}
      </div>
      {i.supporters > 0 && <Reach affected={i.affectedUsers} supporters={i.supporters} className="mt-1" />}
    </Link>
  )
}

export default function HomePage() {
  const { user } = useAuth()
  const mine = useQuery({ queryKey: ["mine"], queryFn: api.myIncidents })
  const status = useQuery({ queryKey: ["public-status"], queryFn: api.publicStatus })

  if (user && isStaff(user)) return <Navigate to={homeFor(user)} replace />

  const list = mine.data ?? []
  const needInfo = list.filter((i) => i.status === "AWAITING_INFO")
  const toVerify = list.filter((i) => i.status === "RESOLVED")
  const open = list.filter((i) => OPEN_STATUSES.includes(i.status) && i.status !== "AWAITING_INFO")
  const past = list.filter((i) => !OPEN_STATUSES.includes(i.status) && i.status !== "RESOLVED")
  const s = status.data
  const down = s ? s.services.filter((x) => x.health === "OUTAGE").length : 0

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold leading-tight tracking-tight">{greeting()}, {user?.fullName.split(" ")[0]}</h1>
          <p className="mt-1 text-sm text-mute">Get IT help and keep track of your tickets.</p>
        </div>
        <Link to="/report" className="btn btn-primary"><Plus size={16} /> Report a problem</Link>
      </div>

      <div>
        <Link to="/status" className="panel block p-4 hover:border-line-strong sm:p-5">
          <div>
            <h2 className="font-semibold">Campus status</h2>
            {s ? (
              <div className="mt-2 font-cond text-[20px] font-semibold leading-snug" style={{ color: down ? "var(--outage)" : "var(--operational)" }}>
                {down ? down + " service" + (down > 1 ? "s" : "") + " down" : s.overall === "DEGRADED" ? "Some services are disrupted" : "Services are running normally"}
              </div>
            ) : (
              <div className="mt-2 text-mute">Checking…</div>
            )}
            {s?.problems.slice(0, 2).map((p) => (
              <div key={p.number} className="mt-2 text-sm">
                <span className="font-mono text-[12px] text-outage">{p.number}</span> {p.title}
              </div>
            ))}
          </div>
          <div className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-signal">
            View service status <ArrowRight size={14} />
          </div>
        </Link>
      </div>

      {needInfo.map((i) => (
        <Link
          key={i.id}
          to={"/incidents/" + i.id + "#respond"}
          className="mt-4 flex items-center gap-4 rounded-lg border border-risk/50 bg-risk/10 p-4 hover:bg-risk/15"
        >
          <MessageCircleQuestion className="shrink-0 text-risk" size={22} />
          <div className="min-w-0 flex-1">
            <div className="font-medium">IT support asked for more information</div>
            <div className="truncate text-sm text-ink-2">
              <span className="font-mono text-[12px]">{i.number}</span> {i.title}
            </div>
          </div>
          <span className="btn btn-ghost">Reply</span>
        </Link>
      ))}

      {toVerify.map((i) => (
        <Link
          key={i.id}
          to={"/incidents/" + i.id + "#respond"}
          className="mt-4 flex items-center gap-4 rounded-lg border border-ok/40 bg-ok/10 p-4 hover:bg-ok/15"
        >
          <Star className="shrink-0 text-ok" size={22} />
          <div className="min-w-0 flex-1">
            <div className="font-medium">Can you confirm that this is fixed?</div>
            <div className="truncate text-sm text-ink-2">
              <span className="font-mono text-[12px]">{i.number}</span> {i.title}
            </div>
          </div>
          <span className="btn btn-ghost">Confirm</span>
        </Link>
      ))}

      <section className="mt-8">
        <div className="mb-3 flex items-baseline justify-between">
          <h2 className="font-cond text-[19px] font-semibold">Open tickets</h2>
          <Link to="/incidents" className="text-sm text-signal hover:underline">
            View all tickets
          </Link>
        </div>
        {mine.isLoading && <Loading />}
        {mine.error && <ErrorBox error={mine.error} retry={mine.refetch} />}
        {mine.data && open.length === 0 && needInfo.length === 0 && (
          <Empty title="Nothing open right now">When you report something, you can follow it here step by step.</Empty>
        )}
        <div className="space-y-3">
          {[...needInfo, ...open].map((i) => (
            <OpenCard key={i.id} i={i} />
          ))}
        </div>
      </section>

      {(past.length > 0 || toVerify.length > 0) && (
        <section className="mt-8">
          <h2 className="mb-3 font-cond text-[19px] font-semibold">Resolved and closed tickets</h2>
          <div className="panel divide-y divide-line">
            {[...toVerify, ...past].slice(0, 8).map((i) => (
              <Link key={i.id} to={"/incidents/" + i.id} className="grid grid-cols-[auto_1fr_auto] items-center gap-3 px-4 py-3 hover:bg-sunk sm:grid-cols-[90px_1fr_auto]">
                <span className="font-mono text-[12px] text-mute">{i.number}</span>
                <span className="truncate text-[13.5px]">{i.title}</span>
                <StatusPill s={i.status} />
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
