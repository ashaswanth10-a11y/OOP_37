import { useMemo, useState } from "react"
import { Link } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Loader2, ShieldAlert, X } from "lucide-react"
import * as api from "../lib/api"
import { isAdmin, isLead, useAuth } from "../lib/auth"
import { useToast } from "../lib/toast"
import { ago, TYPE_LABEL } from "../lib/format"
import type { Problem } from "../lib/types"
import { ServiceGraph } from "../components/ServiceGraph"
import { LampLegend } from "../components/LampBoard"
import { Dialog, ErrorBox, HealthTag, Loading, PriorityTag, StatusPill } from "../components/ui"

export function ResolveProblemDialog({ p, onClose }: { p: Problem; onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [summary, setSummary] = useState("")
  const m = useMutation({
    mutationFn: () => api.resolveProblem(p.id, summary.trim()),
    onSuccess: (r) => {
      toast({
        title: p.number + " resolved",
        body: r.incidentsResolved + " linked incident" + (r.incidentsResolved === 1 ? "" : "s") + " resolved with it",
        tone: "success",
      })
      qc.invalidateQueries()
      onClose()
    },
    onError: (err) => toast({ title: "Couldn't resolve the problem", body: api.errorMessage(err), tone: "critical" }),
  })
  const open = p.incidents.filter((i) => !["RESOLVED", "VERIFIED", "CLOSED", "CANCELLED"].includes(i.status))
  return (
    <Dialog title={"Resolve " + p.number} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (summary.trim()) m.mutate()
        }}
      >
        <p className="text-sm text-ink-2">
          This closes the problem on <b>{p.rootService}</b> and resolves all {open.length} open linked incident
          {open.length === 1 ? "" : "s"} with the same summary. Each reporter is asked to confirm.
        </p>
        <label className="label mt-4" htmlFor="ps">What fixed it?</label>
        <textarea
          id="ps"
          autoFocus
          className="field min-h-[90px]"
          placeholder="e.g. Replaced the failed supervisor module on Core Switch B and restored the uplinks"
          value={summary}
          onChange={(e) => setSummary(e.target.value)}
        />
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Back
          </button>
          <button className="btn btn-primary" disabled={m.isPending || !summary.trim()}>
            {m.isPending && <Loader2 size={14} className="animate-spin" />} Resolve problem + {open.length} incidents
          </button>
        </div>
      </form>
    </Dialog>
  )
}

export default function MapPage() {
  const { user } = useAuth()
  const canResolve = isLead(user) || isAdmin(user)
  const [selected, setSelected] = useState<number | null>(null)
  const [resolving, setResolving] = useState<Problem | null>(null)
  const map = useQuery({ queryKey: ["outage-map"], queryFn: api.outageMap, refetchInterval: 30000 })
  const problems = useQuery({ queryKey: ["problems"], queryFn: api.problems })
  const blast = useQuery({
    queryKey: ["blast", selected],
    queryFn: () => api.blastRadius(selected!),
    enabled: selected != null,
  })
  const nodeIncidents = useQuery({
    queryKey: ["incidents", "service", selected],
    queryFn: () => api.searchIncidents({ serviceId: String(selected), openOnly: true, size: 10, sort: "priority", dir: "asc" }),
    enabled: selected != null,
  })

  const blastSet = useMemo(() => new Set((blast.data ?? []).map((s) => s.id)), [blast.data])
  const node = map.data?.nodes.find((n) => n.id === selected)
  const openProblems = (problems.data ?? []).filter((p) => p.status === "OPEN")

  if (map.isLoading) return <Loading label="Drawing the outage map" />
  if (map.error) return <ErrorBox error={map.error} retry={map.refetch} />
  if (!map.data) return null

  return (
    <div className="-mx-4 -my-5 sm:-mx-6 lg:-mx-8">
      <div className="grid lg:h-[calc(100vh-48px)] lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="relative h-[62vh] border-b border-line lg:h-auto lg:border-b-0 lg:border-r">
          <div className="pointer-events-none absolute left-4 top-3 z-10">
            <div className="eyebrow">Live outage map</div>
            <div className="font-cond text-[20px] font-semibold">Service dependencies</div>
            <div className="pointer-events-auto mt-1 rounded-md bg-card/90 px-2 py-1">
              <LampLegend />
            </div>
          </div>
          <ServiceGraph nodes={map.data.nodes} edges={map.data.edges} selectedId={selected} blast={selected ? blastSet : undefined} onSelect={setSelected} />
        </div>

        <aside className="overflow-y-auto bg-card p-4 lg:h-[calc(100vh-48px)]">
          {node ? (
            <div className="mb-5">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="font-mono text-[11.5px] text-mute">{node.code}</div>
                  <div className="font-cond text-[19px] font-semibold leading-tight">{node.name}</div>
                </div>
                <button onClick={() => setSelected(null)} className="text-mute hover:text-ink" aria-label="Clear selection">
                  <X size={17} />
                </button>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-3 text-[13px]">
                <HealthTag h={node.health} />
                <span className="text-mute">{TYPE_LABEL[node.type]}</span>
                {node.location && <span className="text-mute">{node.location}</span>}
                <span className="font-mono text-[12px] text-mute">crit {node.criticality}/5</span>
              </div>
              {node.rootCause && (
                <div className="mt-3 rounded-md bg-outage px-3 py-2 text-[13px] text-white">
                  Root cause of <b className="font-mono">{node.problemNumber}</b>
                </div>
              )}
              <div className="mt-4">
                <div className="eyebrow mb-1">Blast radius</div>
                {blast.isLoading && <div className="text-sm text-mute">Tracing dependents…</div>}
                {blast.data && blast.data.length === 0 && <div className="text-sm text-mute">Nothing depends on this service.</div>}
                {blast.data && blast.data.length > 0 && (
                  <>
                    <div className="mb-1.5 text-[12.5px] text-ink-2">
                      If this fails, <b>{blast.data.length}</b> service{blast.data.length > 1 ? "s" : ""} downstream {blast.data.length > 1 ? "are" : "is"} affected:
                    </div>
                    <div className="flex flex-wrap gap-1">
                      {blast.data.map((s) => (
                        <button
                          key={s.id}
                          onClick={() => setSelected(s.id)}
                          className="rounded border border-beacon/60 bg-beacon/10 px-1.5 py-0.5 text-[12px] hover:bg-beacon/20"
                        >
                          {s.name}
                        </button>
                      ))}
                    </div>
                  </>
                )}
              </div>
              <div className="mt-4">
                <div className="eyebrow mb-1">Open incidents ({node.openIncidents})</div>
                {nodeIncidents.data?.items.length === 0 && <div className="text-sm text-mute">None.</div>}
                <div className="divide-y divide-line">
                  {nodeIncidents.data?.items.map((i) => (
                    <Link key={i.id} to={"/incidents/" + i.id} className="flex items-center gap-2 py-1.5 text-[13px] hover:bg-sunk">
                      <PriorityTag p={i.priority} />
                      <span className="font-mono text-[11.5px]">{i.number}</span>
                      <span className="min-w-0 flex-1 truncate">{i.title}</span>
                    </Link>
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <div className="mb-5 rounded-md bg-sunk px-3 py-2.5 text-[13px] text-ink-2">
              Click a service to see what depends on it. The blast radius lights up in amber.
            </div>
          )}

          <div className="eyebrow mb-2 flex items-center gap-1.5">
            <ShieldAlert size={12} /> Open problems
          </div>
          {problems.data && openProblems.length === 0 && <div className="text-sm text-mute">No correlated problems right now.</div>}
          <div className="space-y-3">
            {openProblems.map((p) => (
              <div key={p.id} className="rounded-md border border-outage/40">
                <button onClick={() => setSelected(p.rootServiceId)} className="block w-full px-3 pt-2.5 text-left">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[12.5px] font-semibold text-outage">{p.number}</span>
                    <span className="ml-auto font-mono text-[11px] text-mute">{ago(p.detectedAt)}</span>
                  </div>
                  <div className="mt-0.5 text-[13.5px] font-medium leading-snug">{p.title}</div>
                </button>
                <div className="divide-y divide-line px-3 py-1.5">
                  {p.incidents.map((i) => (
                    <Link key={i.id} to={"/incidents/" + i.id} className="flex items-center gap-2 py-1 text-[12.5px] hover:underline">
                      <span className="font-mono text-[11.5px]">{i.number}</span>
                      <span className="min-w-0 flex-1 truncate">{i.service?.name}</span>
                      <StatusPill s={i.status} />
                    </Link>
                  ))}
                </div>
                <div className="flex gap-2 border-t border-line px-3 py-2">
                  <Link to={"/problems/" + p.id} className="btn btn-ghost px-2.5 py-1 text-[12.5px]">
                    Details
                  </Link>
                  {canResolve && (
                    <button onClick={() => setResolving(p)} className="btn btn-primary px-2.5 py-1 text-[12.5px]">
                      Resolve problem + all linked
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </aside>
      </div>
      {resolving && <ResolveProblemDialog p={resolving} onClose={() => setResolving(null)} />}
    </div>
  )
}
