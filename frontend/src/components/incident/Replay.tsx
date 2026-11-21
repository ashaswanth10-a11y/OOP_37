import { useEffect, useState } from "react"
import { Pause, Play, SkipBack, SkipForward } from "lucide-react"
import { fmtDate, fmtTime, SLA_LABEL, SLA_VAR, STATUS_LABEL } from "../../lib/format"
import type { AuditEntry } from "../../lib/types"
import { PriorityTag, StatusPill } from "../ui"

const LADDER = ["Technician", "Team Lead", "System Admin", "IT Manager"]

function tickColor(action: string) {
  if (action.includes("BREACH")) return "var(--breach)"
  if (action.includes("ESCALAT")) return "var(--p2)"
  if (action.includes("WARN")) return "var(--risk)"
  if (action.includes("RESOLV") || action.includes("VERIF") || action.includes("CLOS")) return "var(--ok)"
  if (action === "CREATED") return "var(--ink)"
  return "var(--signal)"
}

function prettyAction(a: string) {
  const s = a.replace(/_/g, " ").toLowerCase()
  return s.charAt(0).toUpperCase() + s.slice(1)
}

export function Replay({ timeline }: { timeline: AuditEntry[] }) {
  const [idx, setIdx] = useState(timeline.length - 1)
  const [playing, setPlaying] = useState(false)
  const last = timeline.length - 1

  useEffect(() => {
    setIdx((i) => (i > last || i === last - 1 ? last : i))
  }, [last])

  useEffect(() => {
    if (!playing) return
    const t = setInterval(() => {
      setIdx((i) => {
        if (i >= last) {
          setPlaying(false)
          return i
        }
        return i + 1
      })
    }, 1100)
    return () => clearInterval(t)
  }, [playing, last])

  if (timeline.length === 0) return null
  const e = timeline[Math.min(idx, last)]
  const state = e.newState ?? e.previousState
  const used = e.slaUsed ?? 0
  const usedPct = Math.round(used * 100)

  const play = () => {
    if (idx >= last) setIdx(0)
    setPlaying((p) => !p)
  }

  return (
    <div>
      <div className="rounded-lg bg-rail p-4 text-rail-ink sm:p-5">
        <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="font-mono text-[12px] text-beacon">
            {String(idx + 1).padStart(2, "0")}/{String(timeline.length).padStart(2, "0")}
          </span>
          <span className="font-mono text-[12px] text-rail-ink/70">
            {fmtDate(e.at)} · {fmtTime(e.at)}
          </span>
          <span className="font-cond text-[16px] font-semibold text-white">{prettyAction(e.action)}</span>
          <span className="text-[12.5px] text-rail-ink/70">by {e.actorName}</span>
        </div>
        <div className="mt-1.5 min-h-[40px] text-[13.5px] leading-snug text-rail-ink">{e.description}</div>

        <div className="mt-4 grid grid-cols-2 gap-px overflow-hidden rounded-md bg-white/10 sm:grid-cols-3">
          <div className="bg-rail-2 p-2.5">
            <div className="text-[10.5px] uppercase tracking-wider text-rail-ink/60">Status</div>
            <div className="mt-1">{state ? <StatusPill s={state} /> : "--"}</div>
          </div>
          <div className="bg-rail-2 p-2.5">
            <div className="text-[10.5px] uppercase tracking-wider text-rail-ink/60">Priority</div>
            <div className="mt-1">{e.priority ? <PriorityTag p={e.priority} long /> : <span className="font-mono text-rail-ink/50">--</span>}</div>
          </div>
          <div className="bg-rail-2 p-2.5">
            <div className="text-[10.5px] uppercase tracking-wider text-rail-ink/60">Assignee</div>
            <div className="mt-1 truncate text-[13px] text-white">{e.assignee ?? <span className="text-rail-ink/50">unassigned</span>}</div>
          </div>
          <div className="bg-rail-2 p-2.5">
            <div className="text-[10.5px] uppercase tracking-wider text-rail-ink/60">Category</div>
            <div className="mt-1 truncate text-[13px] text-white">{e.category ?? <span className="text-rail-ink/50">not yet</span>}</div>
          </div>
          <div className="bg-rail-2 p-2.5">
            <div className="text-[10.5px] uppercase tracking-wider text-rail-ink/60">SLA</div>
            <div className="mt-1 flex items-center gap-2">
              {e.slaState && (
                <span className="text-[12px] font-semibold" style={{ color: SLA_VAR[e.slaState] }}>
                  {SLA_LABEL[e.slaState]}
                </span>
              )}
              <span className="ml-auto font-mono text-[11.5px] text-white">{e.slaUsed == null ? "--" : usedPct + "%"}</span>
            </div>
            <div className="relative mt-1.5 h-1.5 rounded-full bg-white/10">
              <div
                className="h-full rounded-full transition-[width] duration-500"
                style={{
                  width: Math.min(100, used * 100) + "%",
                  background: used >= 1 ? "var(--breach)" : used >= 0.75 ? "var(--risk)" : "var(--ok)",
                }}
              />
            </div>
          </div>
          <div className="bg-rail-2 p-2.5">
            <div className="text-[10.5px] uppercase tracking-wider text-rail-ink/60">Escalation</div>
            <div className="mt-1.5 flex gap-0.5" title={"Level " + e.escalationLevel + ": " + LADDER[e.escalationLevel]}>
              {LADDER.map((l, k) => (
                <span
                  key={l}
                  className="h-2 flex-1 rounded-[2px] transition-colors"
                  style={{ background: k <= e.escalationLevel ? (k === 0 ? "var(--signal)" : "var(--p2)") : "rgba(255,255,255,0.1)" }}
                />
              ))}
            </div>
            <div className="mt-1 text-[11px] text-rail-ink">{LADDER[e.escalationLevel] ?? "L" + e.escalationLevel}</div>
          </div>
        </div>

        <div className="mt-4 flex items-center gap-2">
          <button
            onClick={() => setIdx((i) => Math.max(0, i - 1))}
            className="flex h-8 w-8 items-center justify-center rounded-md text-rail-ink hover:bg-white/10"
            aria-label="Previous event"
          >
            <SkipBack size={15} />
          </button>
          <button
            onClick={play}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-beacon text-rail hover:bg-[#ffc12e]"
            aria-label={playing ? "Pause replay" : "Play replay"}
          >
            {playing ? <Pause size={16} fill="currentColor" /> : <Play size={16} fill="currentColor" className="ml-0.5" />}
          </button>
          <button
            onClick={() => setIdx((i) => Math.min(last, i + 1))}
            className="flex h-8 w-8 items-center justify-center rounded-md text-rail-ink hover:bg-white/10"
            aria-label="Next event"
          >
            <SkipForward size={15} />
          </button>
          <div className="relative ml-2 h-8 flex-1">
            <div className="absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 rounded-full bg-white/10" />
            <div
              className="absolute left-0 top-1/2 h-1 -translate-y-1/2 rounded-full bg-beacon/70"
              style={{ width: last === 0 ? "100%" : (idx / last) * 100 + "%" }}
            />
            {timeline.map((t, k) => (
              <span
                key={t.id}
                className="absolute top-1/2 h-3 w-[3px] -translate-x-1/2 -translate-y-1/2 rounded-full"
                style={{ left: last === 0 ? "50%" : (k / last) * 100 + "%", background: tickColor(t.action) }}
              />
            ))}
            <input
              type="range"
              min={0}
              max={last}
              value={idx}
              onChange={(ev) => {
                setPlaying(false)
                setIdx(Number(ev.target.value))
              }}
              aria-label="Scrub through the incident history"
              className="absolute inset-0 w-full cursor-pointer appearance-none bg-transparent [&::-moz-range-thumb]:h-4 [&::-moz-range-thumb]:w-4 [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:border-2 [&::-moz-range-thumb]:border-rail [&::-moz-range-thumb]:bg-white [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-rail [&::-webkit-slider-thumb]:bg-white"
            />
          </div>
        </div>
      </div>

      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[640px] text-left text-[13px]">
          <thead>
            <tr className="border-b border-line text-mute">
              <th className="eyebrow py-2 pr-3 font-semibold">When</th>
              <th className="eyebrow py-2 pr-3 font-semibold">Actor</th>
              <th className="eyebrow py-2 pr-3 font-semibold">Action</th>
              <th className="eyebrow py-2 pr-3 font-semibold">State</th>
              <th className="eyebrow py-2 font-semibold">Details</th>
            </tr>
          </thead>
          <tbody>
            {timeline.map((t, k) => (
              <tr
                key={t.id}
                onClick={() => {
                  setPlaying(false)
                  setIdx(k)
                }}
                className={"cursor-pointer border-b border-line align-top " + (k === idx ? "bg-beacon/10" : "hover:bg-sunk")}
              >
                <td className="whitespace-nowrap py-2 pr-3 font-mono text-[12px] text-ink-2">
                  {fmtDate(t.at)}
                  <span className="text-mute">:{fmtTime(t.at).slice(-2)}</span>
                </td>
                <td className="whitespace-nowrap py-2 pr-3">
                  <span className="font-mono text-[11.5px] text-mute">{t.actorCode}</span>
                  <div className="text-[12.5px]">{t.actorName}</div>
                </td>
                <td className="whitespace-nowrap py-2 pr-3">
                  <span className="inline-flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full" style={{ background: tickColor(t.action) }} />
                    {prettyAction(t.action)}
                  </span>
                </td>
                <td className="whitespace-nowrap py-2 pr-3 font-mono text-[11.5px] text-ink-2">
                  {t.previousState && t.previousState !== t.newState ? STATUS_LABEL[t.previousState] + " → " : ""}
                  {t.newState ? STATUS_LABEL[t.newState] : ""}
                </td>
                <td className="py-2 text-ink-2">{t.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
