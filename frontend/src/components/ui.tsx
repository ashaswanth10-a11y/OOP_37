import { useEffect, type ReactNode } from "react"
import { AlertTriangle, Loader2, X } from "lucide-react"
import { errorMessage } from "../lib/api"
import {
  HEALTH_LABEL,
  HEALTH_VAR,
  PRIORITY_LABEL,
  PRIORITY_VAR,
  SLA_LABEL,
  SLA_VAR,
  STATUS_LABEL,
  statusTone,
} from "../lib/format"
import type { Health, Priority, SlaState, Status } from "../lib/types"

export function PriorityTag({ p, long }: { p: Priority | null; long?: boolean }) {
  if (!p) return <span className="font-mono text-xs text-mute">--</span>
  const c = PRIORITY_VAR[p]
  return (
    <span
      className="inline-flex items-center gap-1 rounded-[4px] px-1.5 py-px font-mono text-[11px] font-semibold"
      style={{
        color: p === "P1" ? "#fff" : c,
        background: p === "P1" ? c : "color-mix(in srgb, " + c + " 13%, transparent)",
        boxShadow: "inset 0 0 0 1px " + c,
      }}
      title={PRIORITY_LABEL[p]}
    >
      {p}
      {long && <span className="font-sans font-medium">{PRIORITY_LABEL[p]}</span>}
    </span>
  )
}

export function SlaTag({ s }: { s: SlaState | null }) {
  if (!s) return null
  const c = SLA_VAR[s]
  return (
    <span
      className="inline-flex items-center gap-1.5 whitespace-nowrap font-cond text-[11px] font-semibold uppercase tracking-wider"
      style={{ color: c }}
    >
      <span
        className={"inline-block h-2 w-2 rounded-full " + (s === "BREACHED" ? "lamp-blink" : "")}
        style={{ background: c }}
      />
      {SLA_LABEL[s]}
    </span>
  )
}

export function StatusPill({ s }: { s: Status }) {
  const c = statusTone(s)
  return (
    <span
      className="inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium"
      style={{ color: c, background: "color-mix(in srgb, " + c + " 12%, transparent)" }}
    >
      {STATUS_LABEL[s]}
    </span>
  )
}

export function HealthTag({ h }: { h: Health }) {
  const c = HEALTH_VAR[h]
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-xs font-medium" style={{ color: c }}>
      <span className={"inline-block h-2.5 w-2.5 rounded-[3px] " + (h === "OUTAGE" ? "lamp-blink" : "")} style={{ background: c }} />
      {HEALTH_LABEL[h]}
    </span>
  )
}

export function Loading({ label = "Loading" }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 py-10 justify-center text-mute">
      <Loader2 size={16} className="animate-spin" /> {label}…
    </div>
  )
}

export function ErrorBox({ error, retry }: { error: unknown; retry?: () => void }) {
  return (
    <div className="panel flex items-start gap-3 border-l-4 p-4" style={{ borderLeftColor: "var(--breach)" }}>
      <AlertTriangle size={18} className="mt-0.5 shrink-0 text-breach" />
      <div className="flex-1">
        <div className="font-medium">{errorMessage(error)}</div>
        {retry && (
          <button className="mt-2 text-sm text-signal hover:underline" onClick={retry}>
            Try again
          </button>
        )}
      </div>
    </div>
  )
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-line-strong px-6 py-10 text-center">
      <div className="font-medium text-ink-2">{title}</div>
      {children && <div className="mt-1 text-sm text-mute">{children}</div>}
    </div>
  )
}

export function Dialog({
  title,
  onClose,
  children,
  wide,
}: {
  title: string
  onClose: () => void
  children: ReactNode
  wide?: boolean
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose()
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-[#0f1b24]/45 p-0 sm:p-6" onMouseDown={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={
          "panel max-h-[92vh] w-full overflow-y-auto rounded-b-none sm:rounded-b-lg shadow-xl " + (wide ? "sm:max-w-2xl" : "sm:max-w-lg")
        }
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-line px-5 py-3">
          <h2 className="font-cond text-[17px] font-semibold">{title}</h2>
          <button onClick={onClose} className="text-mute hover:text-ink" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  )
}

export function Meter({ value, max, tone }: { value: number; max: number; tone?: string }) {
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0
  const c = tone ?? (pct >= 100 ? "var(--breach)" : pct >= 75 ? "var(--risk)" : "var(--signal)")
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-sunk">
      <div className="h-full rounded-full transition-[width] duration-300" style={{ width: pct + "%", background: c }} />
    </div>
  )
}

export function PageHeader({ eyebrow, title, children }: { eyebrow?: string; title: string; children?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        {eyebrow && <div className="eyebrow mb-1">{eyebrow}</div>}
        <h1 className="font-cond text-[26px] font-semibold leading-tight tracking-tight">{title}</h1>
      </div>
      {children && <div className="flex flex-wrap items-center gap-2">{children}</div>}
    </div>
  )
}

export function Stars({ value, onChange, size = 22 }: { value: number; onChange?: (v: number) => void; size?: number }) {
  return (
    <div className="flex gap-1" role={onChange ? "radiogroup" : undefined} aria-label="Rating">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          disabled={!onChange}
          onClick={() => onChange?.(n)}
          aria-label={n + " star" + (n > 1 ? "s" : "")}
          className="disabled:cursor-default"
          style={{ color: n <= value ? "var(--beacon)" : "var(--line-strong)", fontSize: size, lineHeight: 1 }}
        >
          ★
        </button>
      ))}
    </div>
  )
}
