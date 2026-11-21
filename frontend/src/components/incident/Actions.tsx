import { useState, type ReactNode } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CheckCheck, CircleSlash, Hand, Loader2, MessageCircleQuestion, Play, RotateCcw, ShieldCheck, Star, UserPlus, Wrench, Gauge, Lock } from "lucide-react"
import * as api from "../../lib/api"
import { errorMessage } from "../../lib/api"
import { useToast } from "../../lib/toast"
import type { Action, IncidentDetail, Level } from "../../lib/types"
import { Dialog, Meter, Stars } from "../ui"

export type DialogKind = "REQUEST_INFO" | "PROVIDE_INFO" | "RESOLVE" | "VERIFY" | "REOPEN" | "CANCEL" | "ASSIGN" | "CHANGE_PRIORITY" | "CLOSE"

export type ResolvePrefill = { summary: string; rootCause: string; code: string }

const LEVELS: Level[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
const CODES = ["FIXED", "CONFIG_CHANGE", "HARDWARE_REPLACED", "WORKAROUND", "USER_GUIDANCE", "VENDOR_FIX", "NO_FAULT_FOUND"]

export function useIncidentAction(id: number) {
  const qc = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: (fn: () => Promise<IncidentDetail>) => fn(),
    onSuccess: (d) => {
      qc.setQueryData(["incident", String(id)], d)
      qc.invalidateQueries({ predicate: (q) => ["incidents", "mine", "assigned", "dashboard", "outage-map", "problems"].includes(String(q.queryKey[0])) })
    },
    onError: (err) => toast({ title: "That didn't go through", body: errorMessage(err), tone: "critical" }),
  })
}

function Footer({ busy, onCancel, label, danger }: { busy: boolean; onCancel: () => void; label: string; danger?: boolean }) {
  return (
    <div className="mt-5 flex justify-end gap-2">
      <button type="button" className="btn btn-ghost" onClick={onCancel}>
        Back
      </button>
      <button className={"btn " + (danger ? "btn-danger" : "btn-primary")} disabled={busy}>
        {busy && <Loader2 size={14} className="animate-spin" />} {label}
      </button>
    </div>
  )
}

function TextDialog({
  title,
  intro,
  label,
  submitLabel,
  required = true,
  danger,
  onSubmit,
  onClose,
  busy,
}: {
  title: string
  intro?: ReactNode
  label: string
  submitLabel: string
  required?: boolean
  danger?: boolean
  onSubmit: (text: string) => void
  onClose: () => void
  busy: boolean
}) {
  const [text, setText] = useState("")
  return (
    <Dialog title={title} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (required && !text.trim()) return
          onSubmit(text.trim())
        }}
      >
        {intro && <div className="mb-3 text-sm text-ink-2">{intro}</div>}
        <label className="label" htmlFor="dlg-text">{label}</label>
        <textarea id="dlg-text" className="field min-h-[110px]" autoFocus value={text} onChange={(e) => setText(e.target.value)} maxLength={4000} />
        <Footer busy={busy} onCancel={onClose} label={submitLabel} danger={danger} />
      </form>
    </Dialog>
  )
}

function ResolveDialog({ d, prefill, onClose, run, busy }: { d: IncidentDetail; prefill?: ResolvePrefill; onClose: () => void; run: (fn: () => Promise<IncidentDetail>) => void; busy: boolean }) {
  const [summary, setSummary] = useState(prefill?.summary ?? "")
  const [rootCause, setRootCause] = useState(prefill?.rootCause ?? "")
  const [code, setCode] = useState(prefill?.code ?? "FIXED")
  return (
    <Dialog title={"Resolve " + d.summary.number} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (!summary.trim()) return
          run(() => api.resolveIncident(d.summary.id, summary.trim(), rootCause.trim(), code))
        }}
        className="space-y-4"
      >
        {prefill && <div className="rounded-md bg-signal-soft px-3 py-2 text-xs text-ink-2">Pre-filled from a past fix. Edit it to match what you actually did.</div>}
        <div>
          <label className="label" htmlFor="rs">What fixed it?</label>
          <textarea id="rs" className="field min-h-[90px]" autoFocus value={summary} onChange={(e) => setSummary(e.target.value)} maxLength={2000} />
          <div className="mt-1 text-xs text-mute">The reporter sees this and is asked to confirm the fix.</div>
        </div>
        <div>
          <label className="label" htmlFor="rc">Root cause</label>
          <input id="rc" className="field" value={rootCause} onChange={(e) => setRootCause(e.target.value)} maxLength={1000} />
        </div>
        <div>
          <label className="label" htmlFor="code">Resolution code</label>
          <select id="code" className="field font-mono text-[13px]" value={code} onChange={(e) => setCode(e.target.value)}>
            {CODES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </div>
        <Footer busy={busy} onCancel={onClose} label="Mark resolved" />
      </form>
    </Dialog>
  )
}

export function VerifyForm({ d, run, busy, onDone }: { d: IncidentDetail; run: (fn: () => Promise<IncidentDetail>) => void; busy: boolean; onDone?: () => void }) {
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState("")
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        run(() => api.verifyIncident(d.summary.id, rating, comment.trim()))
        onDone?.()
      }}
    >
      <div className="label">How did we do?</div>
      <Stars value={rating} onChange={setRating} size={28} />
      <label className="label mt-4" htmlFor="vc">Anything to add? (optional)</label>
      <textarea id="vc" className="field min-h-[70px]" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} />
      <div className="mt-4 flex justify-end">
        <button className="btn btn-primary" disabled={busy}>
          {busy && <Loader2 size={14} className="animate-spin" />} Yes, it's fixed
        </button>
      </div>
    </form>
  )
}

function AssignDialog({ d, onClose, run, busy }: { d: IncidentDetail; onClose: () => void; run: (fn: () => Promise<IncidentDetail>) => void; busy: boolean }) {
  const techs = useQuery({ queryKey: ["technicians"], queryFn: api.technicians })
  const [picked, setPicked] = useState<number | null>(d.recommendations[0]?.technician.id ?? null)
  const [override, setOverride] = useState(false)
  const current = d.summary.assignee?.id

  return (
    <Dialog title={"Assign " + d.summary.number} onClose={onClose} wide>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (picked == null) return
          run(() => api.assignIncident(d.summary.id, picked, override))
        }}
      >
        {d.recommendations.length > 0 && (
          <>
            <div className="eyebrow mb-2">Recommended ({d.assignmentStrategy?.replace(/_/g, " ").toLowerCase()} scoring)</div>
            <div className="space-y-1.5">
              {d.recommendations.map((r, k) => (
                <label
                  key={r.technician.id}
                  className={
                    "flex cursor-pointer items-center gap-3 rounded-md border p-2.5 " +
                    (picked === r.technician.id ? "border-signal bg-signal-soft" : "border-line hover:border-line-strong")
                  }
                >
                  <input type="radio" name="tech-rec" checked={picked === r.technician.id} onChange={() => setPicked(r.technician.id)} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{r.technician.name}</span>
                      <span className="font-mono text-[11px] text-mute">{r.technician.code}</span>
                      {k === 0 && <span className="rounded bg-signal px-1.5 text-[10.5px] font-semibold text-white">BEST</span>}
                      {r.technician.id === current && <span className="text-[11px] text-mute">current</span>}
                    </div>
                    <div className="text-xs text-mute">
                      {r.team} · {r.reason}
                    </div>
                  </div>
                  <div className="w-20 text-right">
                    <div className="font-mono text-[12px]">{r.score.toFixed(2)}</div>
                    <Meter value={r.score} max={1} tone="var(--signal)" />
                  </div>
                </label>
              ))}
            </div>
          </>
        )}
        <div className="eyebrow mb-2 mt-5">Everyone</div>
        <div className="max-h-[240px] overflow-y-auto rounded-md border border-line">
          {techs.isLoading && <div className="p-3 text-sm text-mute">Loading technicians…</div>}
          {techs.data?.map((t) => {
            const full = (t.activeIncidents ?? 0) >= (t.maxConcurrent ?? 99)
            return (
              <label key={t.id} className={"flex cursor-pointer items-center gap-3 border-b border-line px-3 py-2 last:border-0 " + (picked === t.id ? "bg-signal-soft" : "hover:bg-sunk")}>
                <input type="radio" name="tech-all" checked={picked === t.id} onChange={() => setPicked(t.id)} />
                <span className="min-w-0 flex-1">
                  <span className="text-[13.5px] font-medium">{t.fullName}</span>{" "}
                  <span className="font-mono text-[11px] text-mute">{t.code}</span>
                  <span className="block truncate text-[11.5px] text-mute">
                    {t.team} · {(t.skills ?? []).join(", ")}
                    {!t.available && " · off shift"}
                  </span>
                </span>
                <span className={"font-mono text-[12px] " + (full ? "text-breach" : "text-ink-2")}>
                  {t.activeIncidents ?? 0}/{t.maxConcurrent}
                </span>
              </label>
            )
          })}
        </div>
        <label className="mt-4 flex items-center gap-2 text-sm">
          <input type="checkbox" checked={override} onChange={(e) => setOverride(e.target.checked)} />
          Override workload limit (assign even if they're at capacity or off shift)
        </label>
        <Footer busy={busy} onCancel={onClose} label="Assign" />
      </form>
    </Dialog>
  )
}

function PriorityDialog({ d, onClose, run, busy }: { d: IncidentDetail; onClose: () => void; run: (fn: () => Promise<IncidentDetail>) => void; busy: boolean }) {
  const [impact, setImpact] = useState<Level>(d.impact)
  const [urgency, setUrgency] = useState<Level>(d.urgency)
  const [users, setUsers] = useState(String(d.affectedUsers))
  const [reason, setReason] = useState("")
  return (
    <Dialog title={"Change priority of " + d.summary.number} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (!reason.trim()) return
          run(() => api.changePriority(d.summary.id, { impact, urgency, affectedUsers: Math.max(1, Number(users) || 1), reason: reason.trim() }))
        }}
        className="space-y-4"
      >
        <div className="rounded-md bg-sunk px-3 py-2 font-mono text-[11.5px] text-ink-2">Now: {d.priorityRationale}</div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label" htmlFor="pi">Impact</label>
            <select id="pi" className="field" value={impact} onChange={(e) => setImpact(e.target.value as Level)}>
              {LEVELS.map((l) => (
                <option key={l}>{l}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="pu">Urgency</label>
            <select id="pu" className="field" value={urgency} onChange={(e) => setUrgency(e.target.value as Level)}>
              {LEVELS.map((l) => (
                <option key={l}>{l}</option>
              ))}
            </select>
          </div>
        </div>
        <div>
          <label className="label" htmlFor="pn">People affected</label>
          <input id="pn" type="number" min={1} className="field font-mono" value={users} onChange={(e) => setUsers(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="pr">Reason (goes in the audit log)</label>
          <input id="pr" className="field" required value={reason} onChange={(e) => setReason(e.target.value)} />
        </div>
        <Footer busy={busy} onCancel={onClose} label="Recalculate priority" />
      </form>
    </Dialog>
  )
}

const BUTTONS: { a: Action; label: string; icon: ReactNode; kind?: "primary" | "danger" }[] = [
  { a: "TAKE", label: "Take it", icon: <Hand size={15} />, kind: "primary" },
  { a: "ACKNOWLEDGE", label: "Acknowledge", icon: <CheckCheck size={15} />, kind: "primary" },
  { a: "START", label: "Start work", icon: <Play size={15} />, kind: "primary" },
  { a: "RESOLVE", label: "Resolve", icon: <Wrench size={15} />, kind: "primary" },
  { a: "VERIFY", label: "Confirm fix", icon: <Star size={15} />, kind: "primary" },
  { a: "PROVIDE_INFO", label: "Send the info", icon: <MessageCircleQuestion size={15} />, kind: "primary" },
  { a: "REQUEST_INFO", label: "Ask reporter", icon: <MessageCircleQuestion size={15} /> },
  { a: "ASSIGN", label: "Assign", icon: <UserPlus size={15} /> },
  { a: "CHANGE_PRIORITY", label: "Change priority", icon: <Gauge size={15} /> },
  { a: "REOPEN", label: "Not fixed, reopen", icon: <RotateCcw size={15} /> },
  { a: "CLOSE", label: "Close", icon: <Lock size={15} /> },
  { a: "CANCEL", label: "Cancel incident", icon: <CircleSlash size={15} />, kind: "danger" },
]

export function ActionBar({
  d,
  dialog,
  setDialog,
  prefill,
}: {
  d: IncidentDetail
  dialog: DialogKind | null
  setDialog: (k: DialogKind | null) => void
  prefill?: ResolvePrefill
}) {
  const m = useIncidentAction(d.summary.id)
  const id = d.summary.id
  const close = () => setDialog(null)
  const run = (fn: () => Promise<IncidentDetail>) => m.mutate(fn, { onSuccess: close })

  const click = (a: Action) => {
    if (a === "TAKE") return m.mutate(() => api.takeIncident(id))
    if (a === "ACKNOWLEDGE") return m.mutate(() => api.acknowledge(id))
    if (a === "START") return m.mutate(() => api.startWork(id))
    setDialog(a as DialogKind)
  }

  const visible = BUTTONS.filter((b) => d.actions.includes(b.a))

  return (
    <>
      {visible.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {visible.map((b) => (
            <button
              key={b.a}
              onClick={() => click(b.a)}
              disabled={m.isPending}
              className={"btn " + (b.kind === "primary" ? "btn-primary" : b.kind === "danger" ? "btn-ghost text-breach" : "btn-ghost")}
            >
              {m.isPending && ["TAKE", "ACKNOWLEDGE", "START"].includes(b.a) ? <Loader2 size={15} className="animate-spin" /> : b.icon}
              {b.label}
            </button>
          ))}
        </div>
      ) : (
        <div className="flex items-center gap-2 text-sm text-mute">
          <ShieldCheck size={15} /> Nothing for you to do here right now.
        </div>
      )}

      {dialog === "REQUEST_INFO" && (
        <TextDialog
          title="Ask the reporter for more info"
          intro="The incident pauses in Awaiting info until they reply. They get an email and a notification."
          label="Your question"
          submitLabel="Send question"
          onSubmit={(t) => run(() => api.requestInfo(id, t))}
          onClose={close}
          busy={m.isPending}
        />
      )}
      {dialog === "PROVIDE_INFO" && (
        <TextDialog
          title="Send the info the technician asked for"
          label="Your reply"
          submitLabel="Send reply"
          onSubmit={(t) => run(() => api.provideInfo(id, t))}
          onClose={close}
          busy={m.isPending}
        />
      )}
      {dialog === "REOPEN" && (
        <TextDialog
          title="Reopen this incident"
          intro="Tell the technician what's still wrong. It goes back into their queue."
          label="What's still not working?"
          submitLabel="Reopen"
          onSubmit={(t) => run(() => api.reopenIncident(id, t))}
          onClose={close}
          busy={m.isPending}
        />
      )}
      {dialog === "CANCEL" && (
        <TextDialog
          title={"Cancel " + d.summary.number}
          intro="Use this when the problem went away or was reported by mistake."
          label="Reason (optional)"
          submitLabel="Cancel incident"
          required={false}
          danger
          onSubmit={(t) => run(() => api.cancelIncident(id, t))}
          onClose={close}
          busy={m.isPending}
        />
      )}
      {dialog === "CLOSE" && (
        <Dialog title={"Close " + d.summary.number} onClose={close}>
          <p className="text-sm text-ink-2">Closing ends the lifecycle. The reporter can no longer reopen it.</p>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              run(() => api.closeIncident(id))
            }}
          >
            <Footer busy={m.isPending} onCancel={close} label="Close incident" />
          </form>
        </Dialog>
      )}
      {dialog === "RESOLVE" && <ResolveDialog key={prefill?.summary ?? "blank"} d={d} prefill={prefill} onClose={close} run={run} busy={m.isPending} />}
      {dialog === "VERIFY" && (
        <Dialog title="Confirm the fix" onClose={close}>
          <p className="mb-4 text-sm text-ink-2">{d.resolutionSummary}</p>
          <VerifyForm d={d} run={run} busy={m.isPending} />
        </Dialog>
      )}
      {dialog === "ASSIGN" && <AssignDialog d={d} onClose={close} run={run} busy={m.isPending} />}
      {dialog === "CHANGE_PRIORITY" && <PriorityDialog d={d} onClose={close} run={run} busy={m.isPending} />}
    </>
  )
}
