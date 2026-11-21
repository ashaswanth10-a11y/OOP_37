import { useState } from "react"
import { Link } from "react-router-dom"
import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight } from "lucide-react"
import * as api from "../../lib/api"
import { fmtDate, fmtTime, STATUS_LABEL } from "../../lib/format"
import { ErrorBox, Loading, PageHeader, PriorityTag, SlaTag } from "../../components/ui"

export default function AuditAdmin() {
  const [page, setPage] = useState(0)
  const { data, error, isLoading, isFetching, refetch } = useQuery({
    queryKey: ["audit", page],
    queryFn: () => api.auditLog(page, 50),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="mx-auto max-w-[1400px]">
      <PageHeader eyebrow="Oversight" title="Audit log">
        {data && <span className="font-mono text-[12px] text-mute">{data.total} records</span>}
      </PageHeader>
      <p className="-mt-3 mb-4 text-sm text-mute">
        Every state change, assignment, escalation and SLA event, newest first. Records are append-only.
      </p>
      {isLoading && <Loading />}
      {error && <ErrorBox error={error} retry={refetch} />}
      {data && (
        <div className={"panel overflow-hidden " + (isFetching ? "opacity-70" : "")}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1000px] text-left text-[12.5px]">
              <thead className="border-b border-line bg-sunk/60">
                <tr>
                  <th className="eyebrow px-4 py-2 font-semibold">When</th>
                  <th className="eyebrow py-2 pr-3 font-semibold">Incident</th>
                  <th className="eyebrow py-2 pr-3 font-semibold">Actor</th>
                  <th className="eyebrow py-2 pr-3 font-semibold">Action</th>
                  <th className="eyebrow py-2 pr-3 font-semibold">State</th>
                  <th className="eyebrow py-2 pr-3 font-semibold">Snapshot</th>
                  <th className="eyebrow py-2 pr-4 font-semibold">Description</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((a) => (
                  <tr key={a.id} className="border-b border-line align-top last:border-0">
                    <td className="whitespace-nowrap px-4 py-2 font-mono text-[11.5px] text-ink-2">
                      {fmtDate(a.at)}
                      <span className="text-mute">:{fmtTime(a.at).slice(-2)}</span>
                    </td>
                    <td className="whitespace-nowrap py-2 pr-3">
                      {a.incidentId ? (
                        <Link to={"/incidents/" + a.incidentId} className="font-mono text-signal hover:underline">
                          {a.incidentNumber}
                        </Link>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td className="whitespace-nowrap py-2 pr-3">
                      <span className="font-mono text-[11px] text-mute">{a.actorCode}</span> {a.actorName}
                    </td>
                    <td className="whitespace-nowrap py-2 pr-3 font-mono text-[11.5px]">{a.action}</td>
                    <td className="whitespace-nowrap py-2 pr-3 text-ink-2">
                      {a.previousState && a.previousState !== a.newState ? STATUS_LABEL[a.previousState] + " → " : ""}
                      {a.newState ? STATUS_LABEL[a.newState] : ""}
                    </td>
                    <td className="whitespace-nowrap py-2 pr-3">
                      <span className="inline-flex items-center gap-2">
                        {a.priority && <PriorityTag p={a.priority} />}
                        <SlaTag s={a.slaState} />
                        {a.escalationLevel > 0 && <span className="font-mono text-[11px] text-p2">L{a.escalationLevel}</span>}
                      </span>
                    </td>
                    <td className="py-2 pr-4 text-ink-2">{a.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between border-t border-line px-4 py-2.5 text-[13px]">
            <span className="text-mute">
              page {data.page + 1} of {Math.max(1, data.totalPages)}
            </span>
            <div className="flex gap-1">
              <button className="btn btn-ghost px-2" disabled={page === 0} onClick={() => setPage(page - 1)} aria-label="Newer">
                <ChevronLeft size={15} />
              </button>
              <button className="btn btn-ghost px-2" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} aria-label="Older">
                <ChevronRight size={15} />
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
