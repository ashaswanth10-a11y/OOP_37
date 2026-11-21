import { useEffect, useState, type ReactNode } from "react"
import { Link, useSearchParams } from "react-router-dom"
import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, RotateCcw, Search } from "lucide-react"
import * as api from "../lib/api"
import { isStaff, useAuth } from "../lib/auth"
import { ALL_STATUSES, ago, fmtDate, PRIORITY_VAR, SLA_LABEL, STATUS_LABEL } from "../lib/format"
import type { Priority, SlaState } from "../lib/types"
import { Empty, ErrorBox, Loading, PageHeader, PriorityTag, SlaTag, StatusPill } from "../components/ui"

const PRIORITIES: Priority[] = ["P1", "P2", "P3", "P4"]
const SLA_STATES: SlaState[] = ["ON_TRACK", "AT_RISK", "BREACHED", "MET"]

function Chip({ on, onClick, children, color }: { on: boolean; onClick: () => void; children: ReactNode; color?: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={on}
      className={
        "rounded-full border px-2.5 py-0.5 text-[12px] transition-colors " +
        (on ? "border-transparent font-medium text-white" : "border-line-strong text-ink-2 hover:border-ink-2")
      }
      style={on ? { background: color ?? "var(--signal)" } : undefined}
    >
      {children}
    </button>
  )
}

function SortHead({ field, label, sort, dir, onSort, className = "" }: { field: string; label: string; sort: string; dir: string; onSort: (f: string) => void; className?: string }) {
  const active = sort === field
  return (
    <th className={"eyebrow py-2 pr-3 font-semibold " + className}>
      <button onClick={() => onSort(field)} className={"inline-flex items-center gap-1 uppercase " + (active ? "text-ink" : "")}>
        {label}
        {active && (dir === "asc" ? <ArrowUp size={11} /> : <ArrowDown size={11} />)}
      </button>
    </th>
  )
}

export default function IncidentsPage() {
  const { user } = useAuth()
  const staff = isStaff(user)
  const [params, setParams] = useSearchParams()
  const [text, setText] = useState(params.get("q") ?? "")

  const list = (k: string) => (params.get(k) ? params.get(k)!.split(",") : [])
  const status = list("status")
  const priority = list("priority")
  const sort = params.get("sort") ?? "createdAt"
  const dir = params.get("dir") ?? "desc"
  const page = Number(params.get("page") ?? 0)

  const update = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (v == null || v === "") next.delete(k)
      else next.set(k, v)
    }
    if (!("page" in patch)) next.delete("page")
    setParams(next, { replace: true })
  }

  useEffect(() => {
    const t = setTimeout(() => {
      if ((params.get("q") ?? "") !== text) update({ q: text })
    }, 350)
    return () => clearTimeout(t)
  }, [text])

  const toggle = (key: string, values: string[], v: string) => {
    const next = values.includes(v) ? values.filter((x) => x !== v) : [...values, v]
    update({ [key]: next.join(",") })
  }

  const search = {
    q: params.get("q") ?? undefined,
    status,
    priority,
    categoryId: params.get("categoryId") ?? undefined,
    serviceId: params.get("serviceId") ?? undefined,
    assigneeId: params.get("assigneeId") ?? undefined,
    slaState: params.get("slaState") ?? undefined,
    from: params.get("from") ?? undefined,
    to: params.get("to") ?? undefined,
    escalated: params.get("escalated") === "1",
    openOnly: params.get("openOnly") === "1",
    page,
    size: 20,
    sort,
    dir,
  }

  const q = useQuery({ queryKey: ["incidents", search], queryFn: () => api.searchIncidents(search), placeholderData: keepPreviousData })
  const cats = useQuery({ queryKey: ["categories"], queryFn: api.categories, staleTime: 300000, enabled: staff })
  const svcs = useQuery({ queryKey: ["services"], queryFn: api.services, staleTime: 300000, enabled: staff })
  const techs = useQuery({ queryKey: ["technicians"], queryFn: api.technicians, enabled: staff })

  const onSort = (f: string) => update({ sort: f, dir: sort === f && dir === "desc" ? "asc" : "desc" })
  const data = q.data

  return (
    <div className="mx-auto max-w-[1400px]">
      <PageHeader eyebrow={staff ? "Search" : "Your reports"} title={staff ? "Incidents" : "My tickets"}>
        {!staff && (
          <Link to="/report" className="btn btn-primary">
            Report a problem
          </Link>
        )}
      </PageHeader>

      <div className="panel mb-4 p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative min-w-[220px] flex-1">
            <Search size={15} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-mute" />
            <input
              className="field pl-8"
              placeholder={staff ? "Search title, description or INC number" : "Search your incidents"}
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
          </div>
          <label className="flex items-center gap-2 text-[13px]">
            <input type="checkbox" checked={search.openOnly} onChange={(e) => update({ openOnly: e.target.checked ? "1" : null })} /> Open only
          </label>
          {staff && (
            <label className="flex items-center gap-2 text-[13px]">
              <input type="checkbox" checked={search.escalated} onChange={(e) => update({ escalated: e.target.checked ? "1" : null })} /> Escalated only
            </label>
          )}
          <button
            className="btn btn-ghost px-2.5"
            onClick={() => {
              setText("")
              setParams({}, { replace: true })
            }}
          >
            <RotateCcw size={14} /> Reset
          </button>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-1.5">
          <span className="eyebrow mr-1">Status</span>
          {ALL_STATUSES.map((s) => (
            <Chip key={s} on={status.includes(s)} onClick={() => toggle("status", status, s)}>
              {STATUS_LABEL[s]}
            </Chip>
          ))}
        </div>

        {staff && (
          <>
            <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
              <span className="eyebrow mr-1">Priority</span>
              {PRIORITIES.map((p) => (
                <Chip key={p} on={priority.includes(p)} color={PRIORITY_VAR[p]} onClick={() => toggle("priority", priority, p)}>
                  {p}
                </Chip>
              ))}
            </div>
            <div className="mt-3 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
              <div>
                <label className="label">Category</label>
                <select className="field" value={search.categoryId ?? ""} onChange={(e) => update({ categoryId: e.target.value })}>
                  <option value="">Any</option>
                  {cats.data?.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="label">Service</label>
                <select className="field" value={search.serviceId ?? ""} onChange={(e) => update({ serviceId: e.target.value })}>
                  <option value="">Any</option>
                  {svcs.data
                    ?.slice()
                    .sort((a, b) => a.name.localeCompare(b.name))
                    .map((s) => (
                      <option key={s.id} value={s.id}>{s.name}</option>
                    ))}
                </select>
              </div>
              <div>
                <label className="label">Technician</label>
                <select className="field" value={search.assigneeId ?? ""} onChange={(e) => update({ assigneeId: e.target.value })}>
                  <option value="">Anyone</option>
                  {techs.data?.map((t) => (
                    <option key={t.id} value={t.id}>{t.fullName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="label">SLA</label>
                <select className="field" value={search.slaState ?? ""} onChange={(e) => update({ slaState: e.target.value })}>
                  <option value="">Any</option>
                  {SLA_STATES.map((s) => (
                    <option key={s} value={s}>{SLA_LABEL[s]}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="label">From</label>
                <input type="date" className="field" value={search.from ?? ""} onChange={(e) => update({ from: e.target.value })} />
              </div>
              <div>
                <label className="label">To</label>
                <input type="date" className="field" value={search.to ?? ""} onChange={(e) => update({ to: e.target.value })} />
              </div>
            </div>
          </>
        )}
      </div>

      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} retry={q.refetch} />}
      {data && data.items.length === 0 && <Empty title="No incidents match">Try removing a filter.</Empty>}

      {data && data.items.length > 0 && (
        <div className={"panel overflow-hidden transition-opacity " + (q.isFetching ? "opacity-70" : "")}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] text-left text-[13px]">
              <thead className="border-b border-line bg-sunk/60">
                <tr>
                  <th className="w-1 p-0" />
                  <SortHead field="number" label="Number" sort={sort} dir={dir} onSort={onSort} className="pl-3" />
                  <th className="eyebrow py-2 pr-3 font-semibold">Title</th>
                  <SortHead field="priority" label="Pri" sort={sort} dir={dir} onSort={onSort} />
                  <SortHead field="status" label="Status" sort={sort} dir={dir} onSort={onSort} />
                  <th className="eyebrow py-2 pr-3 font-semibold">Service</th>
                  {staff && <th className="eyebrow py-2 pr-3 font-semibold">Assignee</th>}
                  <th className="eyebrow py-2 pr-3 font-semibold">SLA</th>
                  <SortHead field="createdAt" label="Opened" sort={sort} dir={dir} onSort={onSort} />
                  <SortHead field="updatedAt" label="Updated" sort={sort} dir={dir} onSort={onSort} />
                </tr>
              </thead>
              <tbody>
                {data.items.map((i) => (
                  <tr key={i.id} className="border-b border-line last:border-0 hover:bg-sunk">
                    <td className="w-1 p-0" style={{ background: i.priority ? PRIORITY_VAR[i.priority] : "transparent" }} />
                    <td className="whitespace-nowrap py-2 pl-3 pr-3">
                      <Link to={"/incidents/" + i.id} className="font-mono text-[12.5px] font-medium text-signal hover:underline">
                        {i.number}
                      </Link>
                    </td>
                    <td className="max-w-[340px] py-2 pr-3">
                      <Link to={"/incidents/" + i.id} className="block truncate font-medium hover:underline">
                        {i.title}
                      </Link>
                      <div className="flex gap-2 text-[11.5px] text-mute">
                        {i.category && <span>{i.category.name}</span>}
                        {i.escalationLevel > 0 && <span className="font-semibold text-p2">escalated L{i.escalationLevel}</span>}
                        {i.problemNumber && <span className="font-mono text-outage">{i.problemNumber}</span>}
                        {i.duplicateOf && <span>dup of {i.duplicateOf}</span>}
                        {i.supporters > 0 && <span>+{i.supporters} me too</span>}
                      </div>
                    </td>
                    <td className="py-2 pr-3"><PriorityTag p={i.priority} /></td>
                    <td className="py-2 pr-3"><StatusPill s={i.status} /></td>
                    <td className="max-w-[180px] truncate py-2 pr-3 text-ink-2">{i.service?.name}</td>
                    {staff && <td className="whitespace-nowrap py-2 pr-3 text-ink-2">{i.assignee?.name ?? <span className="text-mute">-</span>}</td>}
                    <td className="py-2 pr-3"><SlaTag s={i.slaState} /></td>
                    <td className="whitespace-nowrap py-2 pr-3 font-mono text-[11.5px] text-ink-2">{fmtDate(i.createdAt)}</td>
                    <td className="whitespace-nowrap py-2 pr-3 text-[12px] text-mute">{ago(i.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between border-t border-line px-4 py-2.5 text-[13px]">
            <span className="text-mute">
              <span className="font-mono">{data.total}</span> incident{data.total === 1 ? "" : "s"} · page {data.page + 1} of {Math.max(1, data.totalPages)}
            </span>
            <div className="flex gap-1">
              <button className="btn btn-ghost px-2" disabled={page <= 0} onClick={() => update({ page: String(page - 1) })} aria-label="Previous page">
                <ChevronLeft size={15} />
              </button>
              <button className="btn btn-ghost px-2" disabled={page + 1 >= data.totalPages} onClick={() => update({ page: String(page + 1) })} aria-label="Next page">
                <ChevronRight size={15} />
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
