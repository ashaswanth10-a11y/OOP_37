import { useNow } from "../lib/clock"
import { countdown } from "../lib/format"

export function Countdown({
  due,
  start,
  stopped,
  className = "",
}: {
  due: string | null
  start?: string | null
  stopped?: string | null
  className?: string
}) {
  const now = useNow()
  if (!due) return <span className={"font-mono text-mute " + className}>--:--:--</span>

  const dueMs = new Date(due).getTime()
  const ref = stopped ? new Date(stopped).getTime() : now
  const left = dueMs - ref
  let color = "var(--ok)"
  if (left < 0) color = "var(--breach)"
  else if (start) {
    const span = dueMs - new Date(start).getTime()
    if (span > 0 && left / span < 0.25) color = "var(--risk)"
  } else if (left < 15 * 60000) color = "var(--risk)"
  if (stopped) color = left < 0 ? "var(--breach)" : "var(--met)"

  return (
    <span className={"font-mono tnum font-medium " + className} style={{ color }}>
      {countdown(left)}
    </span>
  )
}
