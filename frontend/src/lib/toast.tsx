import { createContext, useCallback, useContext, useState, type ReactNode } from "react"
import { Link } from "react-router-dom"
import { X } from "lucide-react"

type Tone = "info" | "success" | "warning" | "critical"
type Toast = { id: number; title: string; body?: string; tone: Tone; href?: string }

const ToastContext = createContext<(t: Omit<Toast, "id">) => void>(() => {})

const TONE_VAR: Record<Tone, string> = {
  info: "var(--signal)",
  success: "var(--ok)",
  warning: "var(--risk)",
  critical: "var(--breach)",
}

let nextId = 1

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const push = useCallback((t: Omit<Toast, "id">) => {
    const id = nextId++
    setToasts((prev) => [...prev.slice(-3), { ...t, id }])
    setTimeout(() => setToasts((prev) => prev.filter((x) => x.id !== id)), t.tone === "critical" ? 9000 : 6000)
  }, [])

  const dismiss = (id: number) => setToasts((prev) => prev.filter((x) => x.id !== id))

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="no-print fixed bottom-4 right-4 left-4 sm:left-auto z-[100] flex flex-col gap-2 sm:w-[360px]" aria-live="polite">
        {toasts.map((t) => (
          <div
            key={t.id}
            className="toast-in panel flex gap-3 p-3 shadow-lg"
            style={{ borderLeft: "4px solid " + TONE_VAR[t.tone] }}
          >
            <div className="min-w-0 flex-1">
              {t.href ? (
                <Link to={t.href} onClick={() => dismiss(t.id)} className="font-medium hover:underline">
                  {t.title}
                </Link>
              ) : (
                <div className="font-medium">{t.title}</div>
              )}
              {t.body && <div className="mt-0.5 text-[13px] text-mute">{t.body}</div>}
            </div>
            <button onClick={() => dismiss(t.id)} className="text-mute hover:text-ink" aria-label="Dismiss">
              <X size={16} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  return useContext(ToastContext)
}
