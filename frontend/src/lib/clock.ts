import { useEffect, useState } from "react"
import { API_BASE } from "./api"

// Align countdowns with the backend clock.
let offsetMs = 0
const listeners = new Set<() => void>()

export function serverNow() {
  return Date.now() + offsetMs
}

export async function syncServerClock() {
  try {
    const sent = Date.now()
    const res = await fetch(API_BASE + "/api/public/time")
    if (!res.ok) return
    const t: { now: string } = await res.json()
    const received = Date.now()
    offsetMs = new Date(t.now).getTime() - (sent + received) / 2
    listeners.forEach((l) => l())
  } catch {
    // keep the last known offset, the next sync will fix it
  }
}

export function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(serverNow)
  useEffect(() => {
    const t = setInterval(() => setNow(serverNow()), intervalMs)
    const l = () => setNow(serverNow())
    listeners.add(l)
    return () => {
      clearInterval(t)
      listeners.delete(l)
    }
  }, [intervalMs])
  return now
}
