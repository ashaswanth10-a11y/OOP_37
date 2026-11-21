import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { ArrowRight, RefreshCw } from "lucide-react"
import * as api from "../lib/api"
import { useAuth } from "../lib/auth"
import { ago, HEALTH_LABEL, HEALTH_VAR, TYPE_LABEL } from "../lib/format"
import type { Health } from "../lib/types"
import { Logo } from "../components/Shell"
import { LampBoard, LampLegend } from "../components/LampBoard"
import { ServiceGraph } from "../components/ServiceGraph"
import { ErrorBox, Loading } from "../components/ui"

const BANNER = {
  ALL_OPERATIONAL: { title: "All campus IT services are operational", tone: "var(--operational)" },
  DEGRADED: { title: "Some services are degraded", tone: "var(--degraded)" },
  MAJOR_OUTAGE: { title: "Major outage in progress", tone: "var(--outage)" },
}

const GROUP_ORDER: Health[] = ["OUTAGE", "DEGRADED", "IMPACTED", "OPERATIONAL"]

const GROUP_NOTE: Record<Health, string> = {
  OUTAGE: "Down or badly broken. No need to report again, the team is already working on it.",
  DEGRADED: "Working, but with open problems reported.",
  IMPACTED: "Depends on something that's down, so you may notice issues.",
  OPERATIONAL: "No known problems.",
}

export default function StatusPage() {
  const { user } = useAuth()
  const { data, error, isLoading, refetch, isFetching } = useQuery({
    queryKey: ["public-status"],
    queryFn: api.publicStatus,
    refetchInterval: 30000,
  })

  return (
    <div className="min-h-screen">
      <header className="border-b border-line bg-card">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-4">
          <Link to="/">
            <Logo />
          </Link>
          <span className="hidden font-cond text-[13px] font-semibold uppercase tracking-wider text-mute sm:inline">/ Status</span>
          <div className="ml-auto flex gap-2">
            {user ? (
              <Link to="/" className="btn btn-ghost">
                Back to app
              </Link>
            ) : (
              <Link to="/login" className="btn btn-ghost">
                Sign in
              </Link>
            )}
            <Link to="/report" className="btn btn-primary">
              Report a problem
            </Link>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8">
        {isLoading && <Loading label="Checking campus services" />}
        {error && <ErrorBox error={error} retry={refetch} />}
        {data && (
          <>
            <section
              className="panel overflow-hidden"
              style={{ borderTop: "5px solid " + BANNER[data.overall].tone }}
            >
              <div className="flex flex-wrap items-start gap-6 p-6">
                <div className="min-w-[260px] flex-1">
                  <div className="eyebrow">Campus status</div>
                  <h1 className="mt-1 font-cond text-[30px] font-semibold leading-tight" style={{ color: BANNER[data.overall].tone }}>
                    {BANNER[data.overall].title}
                  </h1>
                  <div className="mt-2 flex items-center gap-3 text-sm text-mute">
                    <span>Updated {ago(data.generatedAt, Date.now())}</span>
                    <button onClick={() => refetch()} className="inline-flex items-center gap-1 text-signal hover:underline">
                      <RefreshCw size={13} className={isFetching ? "animate-spin" : ""} /> Refresh
                    </button>
                  </div>
                </div>
                <div className="rounded-md bg-sunk p-4">
                  <LampBoard lamps={data.nodes} size="lg" showLabels />
                  <div className="mt-3">
                    <LampLegend />
                  </div>
                </div>
              </div>
            </section>

            {data.problems.length > 0 && (
              <section className="mt-6">
                <h2 className="eyebrow mb-2">Known problems</h2>
                <div className="space-y-2">
                  {data.problems.map((p) => (
                    <div key={p.number} className="panel flex flex-wrap items-center gap-x-4 gap-y-1 border-l-4 p-4" style={{ borderLeftColor: "var(--outage)" }}>
                      <span className="font-mono text-[13px] font-semibold text-outage">{p.number}</span>
                      <span className="font-medium">{p.title}</span>
                      <span className="text-sm text-mute">Root cause: {p.rootService}</span>
                      <span className="ml-auto font-mono text-xs text-mute">
                        {p.reports} reports · since {ago(p.since, Date.now())}
                      </span>
                    </div>
                  ))}
                </div>
                <p className="mt-2 text-sm text-mute">
                  If your problem matches one of these, you don't need to report it. It'll be fixed together.
                </p>
              </section>
            )}

            <section className="mt-8 grid gap-6 md:grid-cols-2">
              {GROUP_ORDER.map((h) => {
                const list = data.services.filter((s) => s.health === h)
                if (list.length === 0) return null
                return (
                  <div key={h} className={h === "OPERATIONAL" ? "md:col-span-2" : ""}>
                    <div className="mb-2 flex items-baseline gap-2">
                      <span className="inline-block h-2.5 w-2.5 rounded-[2px]" style={{ background: HEALTH_VAR[h] }} />
                      <h2 className="font-cond text-[17px] font-semibold">{HEALTH_LABEL[h]}</h2>
                      <span className="font-mono text-xs text-mute">{list.length}</span>
                    </div>
                    <p className="mb-2 text-xs text-mute">{GROUP_NOTE[h]}</p>
                    <div className={"panel divide-y divide-line " + (h === "OPERATIONAL" ? "grid sm:grid-cols-2 lg:grid-cols-3 divide-y-0" : "")}>
                      {list.map((s) => (
                        <div key={s.id} className={"flex items-center gap-3 px-4 py-2.5 " + (h === "OPERATIONAL" ? "border-b border-line" : "")}>
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-[13.5px] font-medium">{s.name}</div>
                            <div className="text-xs text-mute">
                              {TYPE_LABEL[s.type]}
                              {s.location ? " · " + s.location : ""}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )
              })}
            </section>

            <section className="mt-8">
              <div className="mb-2 flex items-baseline justify-between">
                <h2 className="font-cond text-[17px] font-semibold">How campus services depend on each other</h2>
                <span className="hidden text-xs text-mute sm:inline">Arrows point from a service to the ones that rely on it</span>
              </div>
              <div className="panel h-[460px] overflow-hidden sm:h-[560px]">
                <ServiceGraph nodes={data.nodes} edges={data.edges} publicView />
              </div>
            </section>

            <div className="mt-8 flex flex-wrap items-center justify-between gap-3 rounded-lg bg-rail px-6 py-5 text-rail-ink">
              <div>
                <div className="font-cond text-lg font-semibold text-white">Still stuck?</div>
                <div className="text-sm">Report it and we'll route it to the right technician.</div>
              </div>
              <Link to="/report" className="btn bg-beacon font-semibold text-rail hover:bg-[#ffc12e]">
                Report a problem <ArrowRight size={15} />
              </Link>
            </div>
          </>
        )}
      </main>
    </div>
  )
}
