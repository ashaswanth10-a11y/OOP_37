import { useEffect, useState } from "react"
import { API_BASE } from "./api"

// The backend can be fast-forwarded for the demo (time warp). SLA deadlines are in
// server time, so countdowns use the server's clock, not the laptop's.
let offsetMs = 0
let warpMinutes = 0
const listeners = new Set<() => void>()

export function serverNow() {
  return Date.now() + offsetMs
}

export function getServerOffsetMinutes() {
  return warpMinutes
}

export async function syncServerClock() {
  try {
    const sent = Date.now()
    const res = await fetch(API_BASE + "/api/public/time")
    if (!res.ok) return
    const t: { now: string; offsetMinutes: number } = await res.json()
    const received = Date.now()
    offsetMs = new Date(t.now).getTime() - (sent + received) / 2
    warpMinutes = t.offsetMinutes
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
