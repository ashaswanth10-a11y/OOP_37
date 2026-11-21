import { useState } from "react"
import { Link } from "react-router-dom"
import { Check, ChevronDown, Copy } from "lucide-react"

// follows docs/VIDEO_SCRIPT.md, so whoever records always knows the next click
type Action =
  | { kind: "reset" }
  | { kind: "scenario"; name: string; label: string }
  | { kind: "warp"; minutes: number }
  | { kind: "link"; to: string; label: string }

type Step = { segment: string; who: string; text: string; actions?: Action[] }

const STEPS: Step[] = [
  { segment: "Before", who: "D kiran", text: "Reset the demo data so incident numbers match the script (the next new one is INC-1049).", actions: [{ kind: "reset" }] },
  { segment: "Before", who: "All", text: "Log in four windows: A aarav, B ravi, C priya, D kiran. Copy the emails below; the password is Campus@123." },
  { segment: "1 Report", who: "A aarav", text: "Report a problem on Hostel Wi-Fi: Gautam Bhavan. Speak or type it, show the live preview, set Medium / High / 40 users, submit.", actions: [{ kind: "link", to: "/report", label: "Report page" }] },
  { segment: "1 Report", who: "A + B", text: "A: show the priority rationale, SLA deadlines and assignee. B: the bell lights up live; open My queue.", actions: [{ kind: "link", to: "/queue", label: "My queue" }] },
  { segment: "1 Report", who: "D kiran", text: "Flash the email outbox for two seconds.", actions: [{ kind: "link", to: "/admin/outbox", label: "Email outbox" }] },
  { segment: "2 Resolve", who: "B ravi", text: "Acknowledge, Start work, add an internal note, then Ask reporter." },
  { segment: "2 Resolve", who: "A then B", text: "A sends the info. B shows Suggested fixes and Recommended technicians, then resolves." },
  { segment: "2 Resolve", who: "A then B", text: "A confirms the fix with 5 stars. B closes it. Drag the replay slider from start to end." },
  { segment: "3 Burst", who: "D kiran", text: "Fire the payment outage. Open the 2nd and 3rd incidents to show the duplicate score.", actions: [{ kind: "scenario", name: "payment-down", label: "Payment portal down" }] },
  { segment: "3 Burst", who: "A aarav", text: "Start a report on Fee Payment Portal, type 'Payment page not opening', click Me too instead of submitting.", actions: [{ kind: "link", to: "/report", label: "Report page" }] },
  { segment: "3 Burst", who: "D kiran", text: "Fire Core Switch B. Show the outage map and the problem record. On A, the report page now warns about the known outage.", actions: [{ kind: "scenario", name: "core-switch-b", label: "Core Switch B outage" }, { kind: "link", to: "/map", label: "Outage map" }] },
  { segment: "3 Burst", who: "D kiran", text: "Press +5 min twice: the unacknowledged P1 climbs Team Lead, then System Admin.", actions: [{ kind: "warp", minutes: 5 }] },
  { segment: "3 Burst", who: "C priya", text: "Resolve the Core Switch B problem and all its linked incidents at once.", actions: [{ kind: "link", to: "/problems", label: "Problems" }] },
  { segment: "4-5 Design", who: "Any", text: "Walk through the OOP design and intelligent features from the report PDF (sections 4 and 5)." },
  { segment: "6 Extras", who: "Any", text: "Telegram bot, a QR sticker scanned with a phone, and the public status page.", actions: [{ kind: "link", to: "/admin/assets/print", label: "QR sheet" }, { kind: "link", to: "/status", label: "Status page" }] },
  { segment: "7 Admin", who: "D kiran", text: "Switch the assignment strategy live and show an SLA policy.", actions: [{ kind: "link", to: "/admin/settings", label: "Settings" }, { kind: "link", to: "/admin/sla", label: "SLA policies" }] },
  { segment: "8 Tests", who: "Any", text: "Run cd backend && ./mvnw test and show 0 failures, then the CI workflow." },
]

const LOGINS = ["aarav", "ravi", "priya", "vikram", "kiran", "anil"]
const DOMAIN = "@hyderabad.bits-pilani.ac.in"
const KEY = "campusops.presenter"

function loadDone(): number[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? "[]")
  } catch {
    return []
  }
}

export default function PresenterScript({
  busy,
  onReset,
  onScenario,
  onWarp,
}: {
  busy: boolean
  onReset: () => void
  onScenario: (name: string) => void
  onWarp: (minutes: number) => void
}) {
  const [open, setOpen] = useState(true)
  const [done, setDone] = useState<number[]>(loadDone)
  const [copied, setCopied] = useState("")

  const toggle = (k: number) => {
    const next = done.includes(k) ? done.filter((d) => d !== k) : [...done, k]
    setDone(next)
    try {
      localStorage.setItem(KEY, JSON.stringify(next))
    } catch {
      // progress just won't be remembered
    }
  }

  const copy = async (who: string) => {
    try {
      await navigator.clipboard.writeText(who + DOMAIN)
      setCopied(who)
      setTimeout(() => setCopied(""), 1500)
    } catch {
      // clipboard blocked; the email is visible anyway
    }
  }

  const run = (a: Action) => {
    if (a.kind === "reset") onReset()
    else if (a.kind === "scenario") onScenario(a.name)
    else if (a.kind === "warp") onWarp(a.minutes)
  }

  return (
    <section className="panel">
      <button className="flex w-full items-center justify-between px-5 py-3.5 text-left" onClick={() => setOpen(!open)}>
        <span>
          <span className="eyebrow">Presenter script</span>
          <span className="ml-2 font-mono text-[12px] text-mute">
            {done.length}/{STEPS.length} done
          </span>
        </span>
        <ChevronDown size={16} className={"transition-transform " + (open ? "rotate-180" : "")} />
      </button>
      {open && (
        <div className="border-t border-line px-5 pb-4">
          <div className="flex flex-wrap items-center gap-1.5 py-3">
            {LOGINS.map((who) => (
              <button key={who} onClick={() => copy(who)} className="inline-flex items-center gap-1 rounded border border-line px-2 py-0.5 font-mono text-[11.5px] hover:bg-line/40" title={"Copy " + who + DOMAIN}>
                {copied === who ? <Check size={11} /> : <Copy size={11} />} {who}
              </button>
            ))}
            <span className="text-[11.5px] text-mute">password Campus@123</span>
            {done.length > 0 && (
              <button className="ml-auto text-[11.5px] text-mute hover:text-ink" onClick={() => { setDone([]); try { localStorage.removeItem(KEY) } catch { /* ignore */ } }}>
                clear ticks
              </button>
            )}
          </div>
          <ol className="space-y-1.5">
            {STEPS.map((s, k) => {
              const ticked = done.includes(k)
              return (
                <li key={k} className={"flex gap-3 rounded-md px-2 py-1.5 " + (ticked ? "opacity-55" : "hover:bg-line/25")}>
                  <input type="checkbox" checked={ticked} onChange={() => toggle(k)} className="mt-1 shrink-0" aria-label={"Step " + (k + 1) + " done"} />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-baseline gap-x-2 text-[11px] font-mono text-mute">
                      <span>{k + 1}.</span>
                      <span>{s.segment}</span>
                      <span className="text-ink-2">{s.who}</span>
                    </div>
                    <div className={"text-[13px] " + (ticked ? "line-through" : "")}>{s.text}</div>
                    {s.actions && (
                      <div className="mt-1 flex flex-wrap gap-1.5">
                        {s.actions.map((a, j) =>
                          a.kind === "link" ? (
                            <Link key={j} to={a.to} className="rounded bg-line/50 px-2 py-0.5 text-[12px] hover:bg-line">
                              {a.label}
                            </Link>
                          ) : (
                            <button key={j} disabled={busy} onClick={() => run(a)} className="rounded bg-ink px-2 py-0.5 text-[12px] text-card hover:opacity-90 disabled:opacity-50">
                              {a.kind === "reset" ? "Reset demo data" : a.kind === "warp" ? "+" + a.minutes + " min" : a.label}
                            </button>
                          ),
                        )}
                      </div>
                    )}
                  </div>
                </li>
              )
            })}
          </ol>
        </div>
      )}
    </section>
  )
}
