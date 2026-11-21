import { Check } from "lucide-react"
import { STATUS_LABEL } from "../lib/format"
import type { Status } from "../lib/types"

const PATH: Status[] = ["NEW", "CLASSIFIED", "ASSIGNED", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "VERIFIED", "CLOSED"]

const SHORT: Partial<Record<Status, string>> = {
  ACKNOWLEDGED: "Acknowledged",
  IN_PROGRESS: "In progress",
}

function positionOf(status: Status, visited: Status[]) {
  if (status === "AWAITING_INFO") return PATH.indexOf("IN_PROGRESS")
  if (status === "REOPENED") return PATH.indexOf("ASSIGNED")
  if (status === "CANCELLED") {
    let last = 0
    for (const v of visited) last = Math.max(last, PATH.indexOf(v))
    return last
  }
  return PATH.indexOf(status)
}

const SIDE_NOTE: Partial<Record<Status, { text: string; tone: string }>> = {
  AWAITING_INFO: { text: "Paused: waiting for info from the reporter", tone: "var(--risk)" },
  REOPENED: { text: "Reopened: the fix didn't hold, back in the queue", tone: "var(--risk)" },
  CANCELLED: { text: "Cancelled", tone: "var(--mute)" },
}

export function Lifecycle({ status, visited = [] }: { status: Status; visited?: Status[] }) {
  const pos = positionOf(status, visited)
  const cancelled = status === "CANCELLED"
  const side = SIDE_NOTE[status]
  const tone = side?.tone ?? "var(--signal)"

  return (
    <div>
      <ol className="flex items-start overflow-x-auto pb-1">
        {PATH.map((step, i) => {
          const done = i < pos || (i === pos && (status === "CLOSED" || cancelled))
          const current = i === pos && !cancelled && status !== "CLOSED"
          return (
            <li key={step} className="flex min-w-[76px] flex-1 flex-col items-center text-center">
              <div className="flex w-full items-center">
                <div className={"h-0.5 flex-1 " + (i === 0 ? "opacity-0" : "")} style={{ background: i <= pos ? "var(--signal)" : "var(--line)" }} />
                <div
                  className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 text-[11px] font-semibold"
                  style={{
                    borderColor: done ? "var(--signal)" : current ? tone : "var(--line-strong)",
                    background: done ? "var(--signal)" : current ? "color-mix(in srgb, " + tone + " 15%, var(--card))" : "var(--card)",
                    color: done ? "#fff" : current ? tone : "var(--mute)",
                  }}
                >
                  {done ? <Check size={13} strokeWidth={3} /> : i + 1}
                </div>
                <div
                  className={"h-0.5 flex-1 " + (i === PATH.length - 1 ? "opacity-0" : "")}
                  style={{ background: i < pos ? "var(--signal)" : "var(--line)" }}
                />
              </div>
              <div
                className="mt-1.5 px-1 text-[11.5px] leading-tight"
                style={{ color: current ? tone : done ? "var(--ink-2)" : "var(--mute)", fontWeight: current ? 600 : 400 }}
              >
                {SHORT[step] ?? STATUS_LABEL[step]}
              </div>
            </li>
          )
        })}
      </ol>
      {side && (
        <div className="mt-2 text-xs font-medium" style={{ color: side.tone }}>
          {side.text}
        </div>
      )}
    </div>
  )
}

export function LifecycleBar({ status }: { status: Status }) {
  const pos = positionOf(status, [])
  const tone = SIDE_NOTE[status]?.tone ?? "var(--signal)"
  return (
    <div className="flex gap-0.5" aria-label={"Progress: " + STATUS_LABEL[status]}>
      {PATH.map((step, i) => (
        <div
          key={step}
          className="h-1.5 flex-1 rounded-[2px]"
          style={{
            background:
              status === "CANCELLED"
                ? "var(--line)"
                : i < pos || status === "CLOSED"
                  ? "var(--signal)"
                  : i === pos
                    ? tone
                    : "var(--line)",
          }}
        />
      ))}
    </div>
  )
}
