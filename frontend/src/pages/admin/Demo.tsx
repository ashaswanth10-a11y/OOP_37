import { useState } from "react"
import { Link } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CreditCard, FastForward, Loader2, Network, PartyPopper, RotateCcw, ScanLine } from "lucide-react"
import * as api from "../../lib/api"
import { getServerOffsetMinutes, syncServerClock } from "../../lib/clock"
import { useToast } from "../../lib/toast"
import type { SweepResult } from "../../lib/types"
import { Dialog, PageHeader } from "../../components/ui"
import PresenterScript from "../../components/PresenterScript"

const SCENARIO_NAME: Record<string, string> = {
  "core-switch-b": "Core Switch B outage",
  "payment-down": "Payment portal down",
  "fest-load": "Fest traffic surge (Atmos)",
}

type LogLine = { at: Date; title: string; detail: string; links?: string[]; tone: string }

function sweepText(r: SweepResult) {
  return r.checked + " open incidents checked · " + r.warnings + " warnings · " + r.breaches + " breaches · " + r.escalations + " escalations"
}

export default function DemoAdmin() {
  const qc = useQueryClient()
  const toast = useToast()
  const [log, setLog] = useState<LogLine[]>([])
  const [confirmReset, setConfirmReset] = useState(false)
  const settings = useQuery({ queryKey: ["admin-settings"], queryFn: api.adminSettings })
  const add = (l: Omit<LogLine, "at">) => setLog((prev) => [{ ...l, at: new Date() }, ...prev].slice(0, 30))
  const refreshAll = () => qc.invalidateQueries()

  const scenario = useMutation({
    mutationFn: api.runScenario,
    onSuccess: (r) => {
      add({
        title: (SCENARIO_NAME[r.scenario] ?? r.scenario) + " fired",
        detail: r.created.length + " incidents reported by students. Watch the outage map and Problems for correlation.",
        links: r.created,
        tone: "var(--outage)",
      })
      refreshAll()
    },
    onError: (e) => toast({ title: "Scenario failed", body: api.errorMessage(e), tone: "critical" }),
  })

  const warp = useMutation({
    mutationFn: api.timeWarp,
    onSuccess: (r, minutes) => {
      syncServerClock()
      add({
        title: "Clock moved forward " + minutes + " min (now +" + r.offsetMinutes + " min)",
        detail: "SLA sweep: " + sweepText(r.sweep),
        tone: r.sweep.breaches ? "var(--breach)" : r.sweep.warnings ? "var(--risk)" : "var(--signal)",
      })
      refreshAll()
    },
    onError: (e) => toast({ title: "Time warp failed", body: api.errorMessage(e), tone: "critical" }),
  })

  const sweep = useMutation({
    mutationFn: api.runSweep,
    onSuccess: (r) => {
      add({ title: "SLA sweep run", detail: sweepText(r), tone: r.breaches ? "var(--breach)" : "var(--signal)" })
      refreshAll()
    },
    onError: (e) => toast({ title: "Sweep failed", body: api.errorMessage(e), tone: "critical" }),
  })

  const reset = useMutation({
    mutationFn: api.resetDemo,
    onSuccess: (r) => {
      setConfirmReset(false)
      syncServerClock()
      add({
        title: "Demo data reset",
        detail: r.tables + " tables emptied and the campus seeded again in " + (r.millis / 1000).toFixed(1) + " s. Clock back to real time.",
        tone: "var(--signal)",
      })
      refreshAll()
    },
    onError: (e) => {
      setConfirmReset(false)
      toast({ title: "Reset failed", body: api.errorMessage(e), tone: "critical" })
    },
  })

  const offset = settings.data?.clockOffsetMinutes ?? getServerOffsetMinutes()
  const busy = scenario.isPending || warp.isPending || sweep.isPending || reset.isPending

  return (
    <div className="mx-auto max-w-[1100px]">
      <PageHeader eyebrow="Administration" title="Demo control">
        <button className="btn btn-ghost" disabled={busy} onClick={() => setConfirmReset(true)}>
          {reset.isPending ? <Loader2 size={14} className="animate-spin" /> : <RotateCcw size={14} />} Reset demo data
        </button>
        <Link to="/map" className="btn btn-ghost">Outage map</Link>
        <Link to="/dashboard" className="btn btn-ghost">Overview</Link>
      </PageHeader>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-4">
          <PresenterScript
            busy={busy}
            onReset={() => setConfirmReset(true)}
            onScenario={(name) => scenario.mutate(name)}
            onWarp={(m) => warp.mutate(m)}
          />

          <section className="panel p-5">
            <div className="eyebrow">Scenarios</div>
            <div className="mt-3 grid gap-3 sm:grid-cols-3">
              <button
                disabled={busy}
                onClick={() => scenario.mutate("core-switch-b")}
                className="group rounded-lg border border-line-strong p-4 text-left transition-colors hover:border-outage hover:bg-outage/5 disabled:opacity-60"
              >
                <div className="flex items-center gap-2">
                  <Network size={18} className="text-outage" />
                  <span className="font-cond text-[17px] font-semibold">Core Switch B outage</span>
                  {scenario.isPending && scenario.variables === "core-switch-b" && <Loader2 size={14} className="ml-auto animate-spin" />}
                </div>
                <p className="mt-1.5 text-[13px] text-ink-2">
                  Four hostels (Gautam, Krishna, Vyas, Meera) report Wi-Fi down within seconds. CampusOps should correlate
                  them into one problem on Core Switch B.
                </p>
              </button>
              <button
                disabled={busy}
                onClick={() => scenario.mutate("payment-down")}
                className="group rounded-lg border border-line-strong p-4 text-left transition-colors hover:border-outage hover:bg-outage/5 disabled:opacity-60"
              >
                <div className="flex items-center gap-2">
                  <CreditCard size={18} className="text-outage" />
                  <span className="font-cond text-[17px] font-semibold">Payment portal down</span>
                  {scenario.isPending && scenario.variables === "payment-down" && <Loader2 size={14} className="ml-auto animate-spin" />}
                </div>
                <p className="mt-1.5 text-[13px] text-ink-2">
                  Three students hit fee payment errors during the deadline. Expect P1, duplicate detection and the 20-minute
                  fee portal SLA.
                </p>
              </button>
              <button
                disabled={busy}
                onClick={() => scenario.mutate("fest-load")}
                className="group rounded-lg border border-line-strong p-4 text-left transition-colors hover:border-outage hover:bg-outage/5 disabled:opacity-60"
              >
                <div className="flex items-center gap-2">
                  <PartyPopper size={18} className="text-outage" />
                  <span className="font-cond text-[17px] font-semibold">Fest traffic surge (Atmos)</span>
                  {scenario.isPending && scenario.variables === "fest-load" && <Loader2 size={14} className="ml-auto animate-spin" />}
                </div>
                <p className="mt-1.5 text-[13px] text-ink-2">
                  Internet in the academic blocks collapses during the fest. Reports from different blocks should trace back
                  to Core Switch A.
                </p>
              </button>
            </div>
          </section>

          <section className="panel p-5">
            <div className="flex items-baseline justify-between">
              <div className="eyebrow">Time warp</div>
              <span className="font-mono text-[12px] text-ink-2">
                server clock <b>+{offset} min</b>
              </span>
            </div>
            <p className="mt-1.5 text-[13px] text-mute">
              Moves the server clock forward and runs the SLA monitor, so warnings, breaches and escalations happen on
              camera instead of after hours.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              {[5, 15, 60].map((m) => (
                <button key={m} className="btn btn-ghost font-mono" disabled={busy} onClick={() => warp.mutate(m)}>
                  {warp.isPending && warp.variables === m ? <Loader2 size={14} className="animate-spin" /> : <FastForward size={14} />} +{m} min
                </button>
              ))}
              <button className="btn btn-primary" disabled={busy} onClick={() => sweep.mutate()}>
                {sweep.isPending ? <Loader2 size={14} className="animate-spin" /> : <ScanLine size={14} />} Run SLA sweep now
              </button>
            </div>
          </section>

          {settings.data && (
            <section className="panel grid grid-cols-2 gap-px overflow-hidden bg-line sm:grid-cols-4">
              <div className="bg-card p-3.5">
                <div className="eyebrow">Assignment</div>
                <div className="mt-1 font-mono text-[12.5px]">{settings.data.settings.assignmentStrategy}</div>
              </div>
              <div className="bg-card p-3.5">
                <div className="eyebrow">Priority</div>
                <div className="mt-1 font-mono text-[12.5px]">{settings.data.settings.priorityStrategy}</div>
              </div>
              <div className="bg-card p-3.5">
                <div className="eyebrow">Live viewers</div>
                <div className="mt-1 font-mono text-[12.5px]">{settings.data.integrations.liveConnections}</div>
              </div>
              <div className="bg-card p-3.5">
                <div className="eyebrow">Auto escalation</div>
                <div className="mt-1 font-mono text-[12.5px]">{settings.data.settings.autoEscalation ? "ON" : "OFF"}</div>
              </div>
            </section>
          )}
        </div>

        <section className="rounded-lg bg-rail p-4 text-rail-ink">
          <div className="flex items-center justify-between">
            <span className="font-mono text-[11px] tracking-[0.14em] text-beacon">RESULT LOG</span>
            {log.length > 0 && (
              <button className="text-[11px] text-rail-ink/60 hover:text-white" onClick={() => setLog([])}>
                clear
              </button>
            )}
          </div>
          {log.length === 0 && <div className="mt-6 text-center text-[13px] text-rail-ink/60">Fire a scenario or warp time. Results land here.</div>}
          <div className="mt-3 space-y-3">
            {log.map((l, k) => (
              <div key={k} className="border-l-2 pl-3" style={{ borderColor: l.tone }}>
                <div className="font-mono text-[10.5px] text-rail-ink/50">{l.at.toLocaleTimeString("en-IN", { hour12: false })}</div>
                <div className="text-[13.5px] font-medium text-white">{l.title}</div>
                <div className="text-[12.5px]">{l.detail}</div>
                {l.links && (
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {l.links.map((n) => (
                      <Link key={n} to={"/incidents?q=" + n} className="rounded bg-white/10 px-1.5 font-mono text-[11.5px] text-beacon hover:bg-white/20">
                        {n}
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </section>
      </div>

      {confirmReset && (
        <Dialog title="Reset demo data?" onClose={() => !reset.isPending && setConfirmReset(false)}>
          <p className="text-[13.5px] text-ink-2">
            This deletes every incident, problem, note and notification, puts the clock back to real time and seeds the
            demo campus again. Everyone stays logged in. Use it before each recording take.
          </p>
          <div className="mt-4 flex justify-end gap-2">
            <button className="btn btn-ghost" disabled={reset.isPending} onClick={() => setConfirmReset(false)}>
              Cancel
            </button>
            <button className="btn btn-primary" disabled={reset.isPending} onClick={() => reset.mutate()}>
              {reset.isPending ? <Loader2 size={14} className="animate-spin" /> : <RotateCcw size={14} />} Reset now
            </button>
          </div>
        </Dialog>
      )}
    </div>
  )
}
