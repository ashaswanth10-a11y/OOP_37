import { useSyncExternalStore } from "react"

// set when a request can't reach the backend (stopped, or a free host still waking up)
let down = false
const listeners = new Set<() => void>()

function set(value: boolean) {
  if (down === value) return
  down = value
  listeners.forEach((l) => l())
}

export const markServerDown = () => set(true)
export const markServerUp = () => set(false)

export function useServerDown() {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => down,
  )
}
