import { useEffect } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { Loader2 } from "lucide-react"
import { API_BASE } from "../lib/api"
import { markServerUp, useServerDown } from "../lib/serverStatus"
import { syncServerClock } from "../lib/clock"

// shown while the backend is unreachable; polls until it answers, then refetches everything
export default function ServerBanner() {
  const down = useServerDown()
  const qc = useQueryClient()

  useEffect(() => {
    if (!down) return
    const timer = setInterval(async () => {
      try {
        const res = await fetch(API_BASE + "/api/public/time")
        if (res.ok) {
          markServerUp()
          syncServerClock()
          qc.invalidateQueries()
        }
      } catch {
        // still down
      }
    }, 3000)
    return () => clearInterval(timer)
  }, [down, qc])

  if (!down) return null
  return (
    <div role="status" className="fixed inset-x-0 top-0 z-[60] flex items-center justify-center gap-2 bg-rail px-4 py-2 text-center text-[13px] text-rail-ink shadow">
      <Loader2 size={14} className="shrink-0 animate-spin text-beacon" />
      <span>
        <b className="text-white">The CampusOps server is starting up.</b> Free hosting sleeps when idle, so this can take
        a minute. Retrying...
      </span>
    </div>
  )
}
