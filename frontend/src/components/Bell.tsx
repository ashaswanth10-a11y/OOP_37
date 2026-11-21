import { useEffect, useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { Bell as BellIcon } from "lucide-react"
import * as api from "../lib/api"
import { ago } from "../lib/format"

const SEV_VAR: Record<string, string> = {
  INFO: "var(--signal)",
  SUCCESS: "var(--ok)",
  WARNING: "var(--risk)",
  CRITICAL: "var(--breach)",
}

export function Bell({ dark }: { dark?: boolean }) {
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { data } = useQuery({ queryKey: ["notifications"], queryFn: api.notifications, refetchInterval: 60000 })

  const readOne = useMutation({
    mutationFn: api.markRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  })
  const readAll = useMutation({
    mutationFn: api.markAllRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  })

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener("mousedown", onDown)
    return () => document.removeEventListener("mousedown", onDown)
  }, [open])

  const unread = data?.unread ?? 0

  return (
    <div className="relative" ref={box}>
      <button
        onClick={() => setOpen((o) => !o)}
        className={
          "relative flex h-9 w-9 items-center justify-center rounded-md " +
          (dark ? "text-rail-ink hover:bg-white/10" : "text-ink-2 hover:bg-sunk")
        }
        aria-label={"Notifications, " + unread + " unread"}
      >
        <BellIcon size={18} />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 min-w-[18px] rounded-full bg-breach px-1 text-center font-mono text-[10px] font-semibold leading-[18px] text-white">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="panel absolute right-0 z-40 mt-2 w-[min(380px,calc(100vw-24px))] shadow-xl">
          <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
            <span className="font-cond font-semibold">Notifications</span>
            <button
              className="text-xs text-signal hover:underline disabled:opacity-50"
              disabled={unread === 0 || readAll.isPending}
              onClick={() => readAll.mutate()}
            >
              Mark all read
            </button>
          </div>
          <div className="max-h-[420px] overflow-y-auto">
            {!data?.items.length && <div className="px-4 py-8 text-center text-sm text-mute">Nothing yet.</div>}
            {data?.items.map((n) => (
              <button
                key={n.id}
                onClick={() => {
                  if (!n.read) readOne.mutate(n.id)
                  if (n.incidentId) {
                    navigate("/incidents/" + n.incidentId)
                    setOpen(false)
                  }
                }}
                className={"flex w-full gap-3 border-b border-line px-4 py-2.5 text-left last:border-0 hover:bg-sunk " + (n.read ? "opacity-60" : "")}
              >
                <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full" style={{ background: n.read ? "var(--line-strong)" : SEV_VAR[n.severity] }} />
                <span className="min-w-0 flex-1">
                  <span className="block text-[13px] font-medium leading-snug">{n.title}</span>
                  <span className="block truncate text-xs text-mute">{n.body}</span>
                  <span className="mt-0.5 block font-mono text-[11px] text-mute">{ago(n.createdAt)}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
