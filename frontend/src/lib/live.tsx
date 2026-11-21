import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { API_BASE } from "./api"
import { useAuth } from "./auth"
import { useToast } from "./toast"
import { syncServerClock } from "./clock"

const LiveContext = createContext(false)

const LIVE_KEYS = new Set([
  "incident",
  "incidents",
  "mine",
  "assigned",
  "queue",
  "dashboard",
  "outage-map",
  "problems",
  "problem",
  "public-status",
  "notifications",
  "technicians",
  "audit",
  "violations",
  "outbox",
])

export function LiveProvider({ children }: { children: ReactNode }) {
  const { token } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const [connected, setConnected] = useState(false)
  const pending = useRef<ReturnType<typeof setTimeout> | null>(null)
  const seen = useRef(new Map<string, number>())

  useEffect(() => {
    if (!token) return
    const es = new EventSource(API_BASE + "/api/stream?access_token=" + encodeURIComponent(token))

    const refresh = () => {
      if (pending.current) return
      pending.current = setTimeout(() => {
        pending.current = null
        qc.invalidateQueries({ predicate: (q) => LIVE_KEYS.has(String(q.queryKey[0])) })
      }, 350)
    }

    es.addEventListener("hello", () => {
      setConnected(true)
      syncServerClock()
    })
    es.onopen = () => setConnected(true)
    es.onerror = () => setConnected(false)
    es.addEventListener("incident", refresh)
    const clockTimer = setInterval(syncServerClock, 60000)
    es.addEventListener("notification", (e) => {
      const n = JSON.parse((e as MessageEvent).data)
      qc.invalidateQueries({ queryKey: ["notifications"] })
      // the SLA monitor can re-send the same breach notice on every sweep; one toast is enough
      const last = seen.current.get(n.title)
      if (last && Date.now() - last < 10 * 60000) return
      seen.current.set(n.title, Date.now())
      const sev = String(n.severity).toLowerCase()
      toast({
        title: n.title,
        body: n.body,
        tone: sev === "critical" ? "critical" : sev === "warning" ? "warning" : sev === "success" ? "success" : "info",
        href: n.incidentId ? "/incidents/" + n.incidentId : undefined,
      })
    })

    return () => {
      es.close()
      clearInterval(clockTimer)
      setConnected(false)
      if (pending.current) clearTimeout(pending.current)
      pending.current = null
    }
  }, [token, qc, toast])

  return <LiveContext.Provider value={connected}>{children}</LiveContext.Provider>
}

export function useLive() {
  return useContext(LiveContext)
}
