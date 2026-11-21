import { useQuery } from "@tanstack/react-query"
import { Radio } from "lucide-react"
import * as api from "../lib/api"
import { MeTooButton } from "./incident/MeToo"

const since = (iso: string) => new Date(iso).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", hour12: false })

// open root-cause problems, shown before someone files a duplicate report
export default function KnownOutages({ serviceId }: { serviceId: string }) {
  const q = useQuery({ queryKey: ["public-status"], queryFn: api.publicStatus, refetchInterval: 30000 })
  const problems = q.data?.problems ?? []
  if (problems.length === 0) return null

  const chosen = serviceId ? Number(serviceId) : null
  const match = chosen ? problems.find((p) => p.affectedServiceIds?.includes(chosen)) : undefined

  if (match) {
    return (
      <div className="mb-5 rounded-lg border-2 border-outage/60 bg-outage/5 p-4" role="alert">
        <div className="flex items-center gap-2 text-outage">
          <Radio size={16} />
          <span className="font-cond text-[17px] font-semibold">There’s a known outage for this service</span>
        </div>
        <p className="mt-1 text-[13.5px]">
          <b>{match.title}</b> ({match.number}) is already being fixed: {match.reports} reports since {since(match.since)}. The
          service you picked depends on {match.rootService}.
        </p>
        {match.followIncidentId && (
          <div className="mt-2.5 flex flex-wrap items-center gap-3">
            <MeTooButton id={match.followIncidentId} linkAfter />
            <span className="text-[12px] text-mute">Follow this outage for updates, or submit a ticket below for a different issue.</span>
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="mb-5 flex flex-wrap items-start gap-x-3 gap-y-1 rounded-lg border border-outage/40 bg-outage/5 px-3.5 py-2.5 text-[13px]">
      <span className="inline-flex items-center gap-1.5 font-medium text-outage">
        <Radio size={14} /> Known outage{problems.length > 1 ? "s" : ""}
      </span>
      {problems.map((p) => (
        <span key={p.number}>
          {p.rootService}: {p.reports} reports since {since(p.since)}
        </span>
      ))}
      <span className="text-mute">Pick your service below to check if yours is the same.</span>
    </div>
  )
}
