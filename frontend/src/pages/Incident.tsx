import { useEffect, useState } from "react"
import { Link, useLocation, useParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { AlertOctagon, ArrowLeft, Copy, Eye, EyeOff, Loader2, MapPin, Radio, Send, ShieldAlert, Sparkles, User as UserIcon, Wrench } from "lucide-react"
import * as api from "../lib/api"
import { isStaff, useAuth } from "../lib/auth"
import { useNow } from "../lib/clock"
import { CHANNEL_LABEL, fmtDate, OPEN_STATUSES, PRIORITY_LABEL } from "../lib/format"
import type { IncidentDetail, Note, Status } from "../lib/types"
import { Lifecycle } from "../components/Lifecycle"
import { Countdown } from "../components/Countdown"
import { ActionBar, useIncidentAction, VerifyForm, type DialogKind, type ResolvePrefill } from "../components/incident/Actions"
import { Replay } from "../components/incident/Replay"
import { MeTooButton, Reach } from "../components/incident/MeToo"
import { ErrorBox, Loading, PriorityTag, SlaTag, Stars, StatusPill } from "../components/ui"

function SlaPanel({ d }: { d: IncidentDetail }) {
  const now = useNow()
  const sla = d.sla
  if (!sla) return null
  const start = new Date(sla.start).getTime()
  const due = new Date(sla.resolutionDue).getTime()
  const end = sla.resolvedAt ? new Date(sla.resolvedAt).getTime() : now
  const used = due > start ? (end - start) / (due - start) : sla.used
  const risk = d.risk

  return (
    <div className="panel p-4">
      <div className="flex items-center justify-between">
        <span className="eyebrow">Service level</span>
        <SlaTag s={sla.state} />
      </div>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <div>
          <div className="text-[11.5px] text-mute">First response</div>
          <Countdown due={sla.responseDue} start={sla.start} stopped={sla.respondedAt} className="text-[19px]" />
          <div className="font-mono text-[11px] text-mute">
            {sla.respondedAt ? "responded " + fmtDate(sla.respondedAt) : "due " + fmtDate(sla.responseDue)}
          </div>
          {sla.responseBreached && <div className="mt-0.5 text-[11px] font-semibold uppercase tracking-wide text-breach">Breached</div>}
        </div>
        <div>
          <div className="text-[11.5px] text-mute">Resolution</div>
          <Countdown due={sla.resolutionDue} start={sla.start} stopped={sla.resolvedAt} className="text-[19px]" />
          <div className="font-mono text-[11px] text-mute">
            {sla.resolvedAt ? "resolved " + fmtDate(sla.resolvedAt) : "due " + fmtDate(sla.resolutionDue)}
          </div>
          {sla.resolutionBreached && <div className="mt-0.5 text-[11px] font-semibold uppercase tracking-wide text-breach">Breached</div>}
        </div>
      </div>
      <div className="mt-3">
        <div className="flex justify-between text-[11px] text-mute">
          <span>Window used</span>
          <span className="font-mono">{Math.round(used * 100)}%</span>
        </div>
        <div className="relative mt-1 h-2 overflow-hidden rounded-full bg-sunk">
          <div
            className="h-full rounded-full"
            style={{
              width: Math.min(100, used * 100) + "%",
              background: used >= 1 ? "var(--breach)" : used >= 0.75 ? "var(--risk)" : "var(--ok)",
            }}
          />
          <div className="absolute inset-y-0 left-[75%] w-px bg-ink/30" title="Warning threshold" />
        </div>
      </div>
      {risk && !sla.resolvedAt && (
        <div className="mt-3 rounded-md bg-sunk px-3 py-2">
          <div className="flex items-center justify-between text-[12px]">
            <span className="text-mute">Breach risk</span>
            <span
              className="font-semibold"
              style={{ color: risk.level === "HIGH" ? "var(--breach)" : risk.level === "MEDIUM" ? "var(--risk)" : "var(--ok)" }}
            >
              {risk.level}
            </span>
          </div>
          <div className="mt-0.5 text-[12px] text-ink-2">{risk.reason}</div>
        </div>
      )}
      <div className="mt-2 font-mono text-[10.5px] text-mute">policy: {sla.policy}</div>
    </div>
  )
}

const NOTE_LABEL: Record<Note["type"], string> = {
  INVESTIGATION: "Work note",
  INFO_REQUEST: "Asked for info",
  INFO_RESPONSE: "Reply",
  COMMENT: "Comment",
  SYSTEM: "System",
}

function Conversation({ d, staff }: { d: IncidentDetail; staff: boolean }) {
  const { user } = useAuth()
  const [showInternal, setShowInternal] = useState(true)
  const [text, setText] = useState("")
  const [internal, setInternal] = useState(staff)
  const m = useIncidentAction(d.summary.id)
  const canNote = d.actions.includes("ADD_NOTE")
  const notes = d.notes.filter((n) => showInternal || !n.internal)

  return (
    <div className="panel">
      <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
        <span className="font-cond text-[15px] font-semibold">Conversation</span>
        {staff && (
          <button onClick={() => setShowInternal((s) => !s)} className="inline-flex items-center gap-1.5 text-xs text-mute hover:text-ink">
            {showInternal ? <Eye size={13} /> : <EyeOff size={13} />}
            {showInternal ? "Showing internal notes" : "Internal notes hidden"}
          </button>
        )}
      </div>
      <div className="space-y-3 p-4">
        {notes.length === 0 && <div className="text-sm text-mute">No messages yet.</div>}
        {notes.map((n) => {
          const mine = n.author?.id === user?.id
          return (
            <div key={n.id} className={"flex " + (mine ? "justify-end" : "")}>
              <div
                className={
                  "max-w-[85%] rounded-lg px-3 py-2 " +
                  (n.internal ? "border border-dashed border-risk/60 bg-risk/5" : mine ? "bg-signal-soft" : "bg-sunk")
                }
              >
                <div className="flex flex-wrap items-center gap-x-2 text-[11.5px] text-mute">
                  <span className="font-medium text-ink-2">{n.author?.name ?? "CampusOps"}</span>
                  <span>{NOTE_LABEL[n.type]}</span>
                  {n.internal && <span className="font-semibold uppercase tracking-wide text-risk">internal</span>}
                  <span className="font-mono">{fmtDate(n.createdAt)}</span>
                </div>
                <div className="mt-0.5 whitespace-pre-wrap text-[13.5px]">{n.text}</div>
              </div>
            </div>
          )
        })}
      </div>
      {canNote && (
        <form
          className="border-t border-line p-3"
          onSubmit={(e) => {
            e.preventDefault()
            if (!text.trim()) return
            m.mutate(() => api.addNote(d.summary.id, text.trim(), staff && internal), { onSuccess: () => setText("") })
          }}
        >
          <textarea
            className="field min-h-[64px]"
            placeholder={staff ? "Add a work note or a message to the reporter" : "Add a comment for the technician"}
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={4000}
          />
          <div className="mt-2 flex items-center justify-between gap-2">
            {staff ? (
              <label className="flex items-center gap-2 text-xs text-ink-2">
                <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} />
                Internal note (staff only)
              </label>
            ) : (
              <span />
            )}
            <button className="btn btn-primary" disabled={m.isPending || !text.trim()}>
              {m.isPending ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
              {staff && internal ? "Add note" : "Send"}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}

function Respond({ d }: { d: IncidentDetail }) {
  const m = useIncidentAction(d.summary.id)
  const [text, setText] = useState("")
  const asked = [...d.notes].reverse().find((n) => n.type === "INFO_REQUEST")

  if (d.actions.includes("PROVIDE_INFO")) {
    return (
      <div id="respond" className="rounded-lg border border-risk/50 bg-risk/10 p-4 sm:p-5">
        <div className="font-cond text-[18px] font-semibold">We need more info from you</div>
        {asked && (
          <div className="mt-2 rounded-md bg-card px-3 py-2 text-sm">
            <span className="text-mute">{asked.author?.name} asked:</span> {asked.text}
          </div>
        )}
        <form
          className="mt-3"
          onSubmit={(e) => {
            e.preventDefault()
            if (!text.trim()) return
            m.mutate(() => api.provideInfo(d.summary.id, text.trim()))
          }}
        >
          <textarea className="field min-h-[80px]" value={text} onChange={(e) => setText(e.target.value)} placeholder="Your answer" />
          <div className="mt-2 flex justify-end">
            <button className="btn btn-primary" disabled={m.isPending || !text.trim()}>
              {m.isPending && <Loader2 size={14} className="animate-spin" />} Send reply
            </button>
          </div>
        </form>
      </div>
    )
  }

  if (d.actions.includes("VERIFY")) {
    return (
      <div id="respond" className="rounded-lg border border-ok/40 bg-ok/10 p-4 sm:p-5">
        <div className="font-cond text-[18px] font-semibold">Is it fixed? Please confirm and rate us</div>
        <div className="mt-1 text-sm text-ink-2">
          <b>{d.resolvedBy}</b>: {d.resolutionSummary}
        </div>
        <div className="mt-4 rounded-md bg-card p-4">
          <VerifyForm d={d} run={(fn) => m.mutate(fn)} busy={m.isPending} />
        </div>
        <div className="mt-2 text-xs text-mute">Still broken? Use "Not fixed, reopen" above.</div>
      </div>
    )
  }
  return null
}

export default function IncidentPage() {
  const { id = "" } = useParams()
  const { user } = useAuth()
  const loc = useLocation()
  const staff = isStaff(user)
  const [dialog, setDialog] = useState<DialogKind | null>(null)
  const [prefill, setPrefill] = useState<ResolvePrefill | undefined>()
  const { data: d, error, isLoading, refetch } = useQuery({ queryKey: ["incident", id], queryFn: () => api.getIncident(id) })

  useEffect(() => {
    if (d && loc.hash === "#respond") document.getElementById("respond")?.scrollIntoView({ behavior: "smooth", block: "center" })
  }, [d, loc.hash])

  if (isLoading) return <Loading label="Opening incident" />
  if (error) return <ErrorBox error={error} retry={refetch} />
  if (!d) return null

  const s = d.summary
  const visited = d.timeline.map((t) => t.newState).filter(Boolean) as Status[]

  return (
    <div className="mx-auto max-w-[1200px]">
      <Link to={staff ? "/incidents" : "/home"} className="mb-3 inline-flex items-center gap-1 text-sm text-mute hover:text-ink">
        <ArrowLeft size={14} /> {staff ? "Incidents" : "Home"}
      </Link>

      <header className="panel overflow-hidden">
        <div className="flex">
          <div className="w-1.5 shrink-0" style={{ background: s.priority ? "var(--" + s.priority.toLowerCase() + ")" : "var(--line)" }} />
          <div className="min-w-0 flex-1 p-4 sm:p-5">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-[15px] font-semibold tracking-tight">{s.number}</span>
              <StatusPill s={s.status} />
              <PriorityTag p={s.priority} long />
              {s.escalationLevel > 0 && (
                <span className="inline-flex items-center gap-1 rounded bg-p2/15 px-1.5 py-0.5 text-[11.5px] font-semibold text-p2">
                  <AlertOctagon size={12} /> Escalated L{s.escalationLevel}
                  {d.escalatedTo ? " → " + d.escalatedTo.name : ""}
                </span>
              )}
              {staff && s.problemNumber && d.problem && (
                <Link to={"/problems/" + d.problem.id} className="inline-flex items-center gap-1 rounded bg-outage px-1.5 py-0.5 font-mono text-[11px] font-semibold text-white">
                  <ShieldAlert size={12} /> {s.problemNumber}
                </Link>
              )}
            </div>
            <h1 className="mt-1.5 font-cond text-[24px] font-semibold leading-tight sm:text-[26px]">{s.title}</h1>
            <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-[13px] text-ink-2">
              {s.category && (
                <span className="inline-flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full" style={{ background: s.category.color ?? "var(--mute)" }} />
                  {s.category.name}
                  {d.classifiedBy === "GEMINI" && <span className="text-xs text-mute">Suggested by Gemini</span>}
                  {staff && d.classifiedBy && d.classifiedBy !== "GEMINI" && (
                    <span className="text-xs text-mute">{d.classifiedBy.toLowerCase()}</span>
                  )}
                </span>
              )}
              {s.service && <span>{s.service.name}</span>}
              {s.location && (
                <span className="inline-flex items-center gap-1">
                  <MapPin size={13} className="text-mute" /> {s.location}
                </span>
              )}
              <span className="inline-flex items-center gap-1">
                <Radio size={13} className="text-mute" /> {CHANNEL_LABEL[s.channel] ?? s.channel}
                {d.assetTag && <span className="font-mono text-[11.5px]">· {d.assetTag}</span>}
              </span>
              {s.reporter && (
                <span className="inline-flex items-center gap-1">
                  <UserIcon size={13} className="text-mute" /> {s.reporter.name}
                </span>
              )}
              <span className="font-mono text-[12px] text-mute">opened {fmtDate(s.createdAt)}</span>
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-3">
              <Reach affected={s.affectedUsers || d.affectedUsers} supporters={s.supporters} />
              {!isStaff(user) && s.reporter?.id !== user?.id && OPEN_STATUSES.includes(s.status) && (
                <MeTooButton key={String(d.supportedByMe)} id={s.id} already={d.supportedByMe} small />
              )}
            </div>
          </div>
        </div>
        <div className="border-t border-line bg-sunk/50 px-4 py-3 sm:px-5">
          <ActionBar d={d} dialog={dialog} setDialog={setDialog} prefill={prefill} />
        </div>
      </header>

      {!staff && (
        <div className="mt-4">
          <Respond d={d} />
        </div>
      )}

      <section className="panel mt-4 p-4 sm:p-5">
        <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
          <span className="eyebrow">Lifecycle</span>
          {d.stateDescription && <span className="text-[13px] text-ink-2">{d.stateDescription}</span>}
        </div>
        <Lifecycle status={s.status} visited={visited} />
      </section>

      <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-w-0 space-y-4">
          <div className="panel p-4 sm:p-5">
            <div className="eyebrow mb-1.5">Description</div>
            <p className="whitespace-pre-wrap text-[14.5px] leading-relaxed">{d.description}</p>
            <div className="mt-4 grid grid-cols-3 gap-3 border-t border-line pt-3 text-[13px]">
              <div>
                <div className="text-[11.5px] text-mute">Impact</div>
                <div className="font-medium">{d.impact}</div>
              </div>
              <div>
                <div className="text-[11.5px] text-mute">Urgency</div>
                <div className="font-medium">{d.urgency}</div>
              </div>
              <div>
                <div className="text-[11.5px] text-mute">People affected</div>
                <div className="font-mono font-medium">{d.affectedUsers}</div>
              </div>
            </div>
          </div>

          {d.resolutionSummary && (
            <div className="panel border-l-4 p-4 sm:p-5" style={{ borderLeftColor: "var(--ok)" }}>
              <div className="eyebrow mb-1.5">Resolution</div>
              <p className="text-[14.5px]">{d.resolutionSummary}</p>
              <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-[12.5px] text-ink-2">
                {d.rootCause && <span>Root cause: {d.rootCause}</span>}
                {d.resolutionCode && <span className="font-mono">{d.resolutionCode}</span>}
                {d.resolvedBy && <span>by {d.resolvedBy}</span>}
              </div>
              {d.feedbackRating != null && (
                <div className="mt-3 flex items-center gap-3 border-t border-line pt-3">
                  <Stars value={d.feedbackRating} size={16} />
                  {d.feedbackComment && <span className="text-sm italic text-ink-2">"{d.feedbackComment}"</span>}
                </div>
              )}
            </div>
          )}

          <Conversation d={d} staff={staff} />

          {staff && (
            <section className="panel p-4 sm:p-5">
              <h2 className="mb-3 text-base font-semibold">Ticket history replay</h2>
              <Replay timeline={d.timeline} />
            </section>
          )}
        </div>

        <aside className="space-y-4">
          <SlaPanel d={d} />

          <div className="panel p-4">
            <div className="eyebrow">Assigned to</div>
            {s.assignee ? (
              <div className="mt-1.5">
                <span className="font-medium">{s.assignee.name}</span> <span className="font-mono text-xs text-mute">{s.assignee.code}</span>
              </div>
            ) : (
              <div className="mt-1.5 text-sm text-mute">Nobody yet</div>
            )}
            {staff && d.assignmentStrategy && <div className="mt-1 font-mono text-[11px] text-mute">strategy: {d.assignmentStrategy}</div>}
            {staff && d.priorityRationale && (
              <div className="mt-3 border-t border-line pt-3">
                <div className="eyebrow">Why {s.priority} {s.priority ? PRIORITY_LABEL[s.priority] : ""}</div>
                <div className="mt-1 font-mono text-[11.5px] leading-relaxed text-ink-2">{d.priorityRationale}</div>
              </div>
            )}
          </div>

          {staff && d.problem && (
            <Link to={"/problems/" + d.problem.id} className="panel block border-l-4 p-4 hover:bg-sunk" style={{ borderLeftColor: "var(--outage)" }}>
              <div className="eyebrow">Linked problem</div>
              <div className="mt-1 font-mono text-[13px] font-semibold text-outage">{d.problem.number}</div>
              <div className="text-sm">{d.problem.title}</div>
              <div className="mt-0.5 text-xs text-mute">{d.problem.status}</div>
            </Link>
          )}

          {d.similar.length > 0 && (
            <div className="panel p-4">
              <div className="eyebrow flex items-center gap-1.5">
                <Copy size={12} /> Possible duplicates
              </div>
              <div className="mt-2 space-y-2">
                {d.similar.map((x) => (
                  <Link key={x.id} to={"/incidents/" + x.id} className="block rounded-md border border-line p-2.5 hover:border-line-strong">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-[12px]">{x.number}</span>
                      <StatusPill s={x.status} />
                      <span className="ml-auto font-mono text-[11px] text-risk">{Math.round(x.score * 100)}%</span>
                    </div>
                    <div className="mt-0.5 text-[13px]">{x.title}</div>
                    <div className="text-[11.5px] text-mute">{x.reason}</div>
                  </Link>
                ))}
              </div>
            </div>
          )}

          {staff && d.suggestedFixes.length > 0 && (
            <div className="panel p-4">
              <div className="eyebrow flex items-center gap-1.5">
                <Sparkles size={12} /> Suggested fixes
              </div>
              <div className="mt-2 divide-y divide-line">
                {d.suggestedFixes.map((f) => (
                  <div key={f.id} className="py-2.5">
                    <div className="flex items-center gap-2 text-[12px]">
                      <Link to={"/incidents/" + f.id} className="font-mono hover:underline">
                        {f.number}
                      </Link>
                      <span className="truncate text-mute">{f.title}</span>
                      <span className="ml-auto font-mono text-[11px] text-signal">{Math.round(f.similarity * 100)}%</span>
                    </div>
                    <div className="mt-1 text-[13px]">{f.resolution}</div>
                    {f.rootCause && <div className="text-[11.5px] text-mute">Cause: {f.rootCause}</div>}
                    {d.actions.includes("RESOLVE") && (
                      <button
                        className="mt-1.5 inline-flex items-center gap-1 text-xs font-medium text-signal hover:underline"
                        onClick={() => {
                          setPrefill({ summary: f.resolution, rootCause: f.rootCause ?? "", code: "FIXED" })
                          setDialog("RESOLVE")
                        }}
                      >
                        <Wrench size={12} /> Use this fix
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}
