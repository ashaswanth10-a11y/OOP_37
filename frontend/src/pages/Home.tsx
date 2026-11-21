import { Link, Navigate } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { ArrowRight, MessageCircleQuestion, Mic, QrCode, Star } from "lucide-react"
import * as api from "../lib/api"
import { homeFor, isStaff, useAuth } from "../lib/auth"
import { ago, OPEN_STATUSES } from "../lib/format"
import type { IncidentSummary } from "../lib/types"
import { Lifecycle, LifecycleBar } from "../components/Lifecycle"
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
        {i.assignee ? " · " + i.assignee.name + " is on it" : " · finding the right technician"}
      </div>
      {i.supporters > 0 && <Reach affected={i.affectedUsers} supporters={i.supporters} className="mt-1" />}
      <div className="mt-4">
        <Lifecycle status={i.status} />
      </div>
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
      <div className="mb-6">
        <div className="text-sm text-mute">{greeting()},</div>
        <h1 className="font-cond text-[30px] font-semibold leading-tight">{user?.fullName.split(" ")[0]}, what's not working?</h1>
      </div>

      <div className="grid gap-4 md:grid-cols-[1.4fr_1fr]">
        <Link
          to="/report"
          className="group relative overflow-hidden rounded-xl bg-rail p-6 text-white sm:p-7"
        >
          <div className="font-mono text-[11px] tracking-[0.14em] text-beacon">IT HELP DESK</div>
          <div className="mt-2 font-cond text-[28px] font-semibold leading-tight">Report a problem</div>
          <p className="mt-1 max-w-sm text-[14.5px] text-rail-ink">
            Wi-Fi, ERP, CMS, lab PCs, printers. Describe it in your own words and we'll work out the rest.
          </p>
          <div className="mt-5 inline-flex items-center gap-2 rounded-md bg-beacon px-4 py-2 font-semibold text-rail transition-colors group-hover:bg-[#ffc12e]">
            Start a report <ArrowRight size={16} />
          </div>
          <div className="mt-5 flex flex-wrap gap-x-5 gap-y-1 text-[12.5px] text-rail-ink/80">
            <span className="inline-flex items-center gap-1.5">
              <Mic size={14} /> Speak instead of typing
            </span>
            <span className="inline-flex items-center gap-1.5">
              <QrCode size={14} /> Or scan the sticker on the device
            </span>
          </div>
        </Link>

        <Link to="/status" className="panel flex flex-col justify-between p-5 hover:border-line-strong">
          <div>
            <div className="eyebrow">Campus right now</div>
            {s ? (
              <div className="mt-2 font-cond text-[20px] font-semibold leading-snug" style={{ color: down ? "var(--outage)" : "var(--operational)" }}>
                {down ? down + " service" + (down > 1 ? "s" : "") + " down" : "Everything is running"}
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
            Check before reporting <ArrowRight size={14} />
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
            <div className="font-medium">We need more info from you</div>
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
            <div className="font-medium">We think it's fixed. Please confirm and rate us</div>
            <div className="truncate text-sm text-ink-2">
              <span className="font-mono text-[12px]">{i.number}</span> {i.title}
            </div>
          </div>
          <span className="btn btn-ghost">Confirm</span>
        </Link>
      ))}

      <section className="mt-8">
        <div className="mb-3 flex items-baseline justify-between">
          <h2 className="font-cond text-[19px] font-semibold">My open incidents</h2>
          <Link to="/incidents" className="text-sm text-signal hover:underline">
            See all
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
          <h2 className="mb-3 font-cond text-[19px] font-semibold">Recently fixed</h2>
          <div className="panel divide-y divide-line">
            {[...toVerify, ...past].slice(0, 8).map((i) => (
              <Link key={i.id} to={"/incidents/" + i.id} className="grid grid-cols-[auto_1fr_auto] items-center gap-3 px-4 py-3 hover:bg-sunk sm:grid-cols-[90px_1fr_140px_auto]">
                <span className="font-mono text-[12px] text-mute">{i.number}</span>
                <span className="truncate text-[13.5px]">{i.title}</span>
                <span className="hidden sm:block">
                  <LifecycleBar status={i.status} />
                </span>
                <StatusPill s={i.status} />
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
