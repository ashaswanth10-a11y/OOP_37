import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import * as api from "../../lib/api"
import { fmtDate } from "../../lib/format"
import { Empty, ErrorBox, Loading, PageHeader, PriorityTag } from "../../components/ui"

function lateBy(due: string, detected: string) {
  const mins = Math.round((new Date(detected).getTime() - new Date(due).getTime()) / 60000)
  if (mins < 60) return mins + " min"
  return (mins / 60).toFixed(1) + " h"
}

export default function ViolationsAdmin() {
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ["violations"], queryFn: api.slaViolations })
  const response = data?.filter((v) => v.kind === "RESPONSE").length ?? 0
  const resolution = data?.filter((v) => v.kind === "RESOLUTION").length ?? 0

  return (
    <div className="mx-auto max-w-[1200px]">
      <PageHeader eyebrow="Oversight" title="SLA violations">
        {data && (
          <span className="font-mono text-[12px] text-mute">
            {response} response · {resolution} resolution
          </span>
        )}
      </PageHeader>
      {isLoading && <Loading />}
      {error && <ErrorBox error={error} retry={refetch} />}
      {data && data.length === 0 && <Empty title="No SLA violations recorded">Every target has been met so far.</Empty>}
      {data && data.length > 0 && (
        <div className="panel overflow-x-auto">
          <table className="w-full min-w-[820px] text-left text-[13px]">
            <thead className="border-b border-line bg-sunk/60">
              <tr>
                <th className="eyebrow px-4 py-2 font-semibold">Incident</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Missed</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Pri</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Service</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Assignee</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Was due</th>
                <th className="eyebrow py-2 pr-4 font-semibold">Detected</th>
              </tr>
            </thead>
            <tbody>
              {data.map((v) => (
                <tr key={v.id} className="border-b border-line last:border-0">
                  <td className="px-4 py-2">
                    <Link to={"/incidents/" + v.incidentId} className="font-mono text-signal hover:underline">
                      {v.incidentNumber}
                    </Link>
                  </td>
                  <td className="py-2 pr-3">
                    <span className="rounded bg-breach/10 px-1.5 py-0.5 font-cond text-[11px] font-semibold uppercase tracking-wider text-breach">
                      {v.kind}
                    </span>
                  </td>
                  <td className="py-2 pr-3">
                    <PriorityTag p={v.priority} />
                  </td>
                  <td className="py-2 pr-3 text-ink-2">{v.serviceName}</td>
                  <td className="py-2 pr-3 text-ink-2">{v.assigneeName ?? "-"}</td>
                  <td className="whitespace-nowrap py-2 pr-3 font-mono text-[12px]">{fmtDate(v.dueAt)}</td>
                  <td className="whitespace-nowrap py-2 pr-4 font-mono text-[12px] text-ink-2">
                    {fmtDate(v.detectedAt)} <span className="text-breach">+{lateBy(v.dueAt, v.detectedAt)}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
