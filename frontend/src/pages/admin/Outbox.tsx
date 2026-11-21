import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Mail } from "lucide-react"
import * as api from "../../lib/api"
import { ago, fmtDate } from "../../lib/format"
import { Empty, ErrorBox, Loading, PageHeader } from "../../components/ui"

export default function OutboxAdmin() {
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ["outbox"], queryFn: api.outbox })
  const [openId, setOpenId] = useState<number | null>(null)
  const open = data?.find((m) => m.id === openId) ?? data?.[0]

  return (
    <div className="mx-auto max-w-[1200px]">
      <PageHeader eyebrow="Administration" title="Email outbox" />
      <p className="-mt-3 mb-4 text-sm text-mute">
        Email delivery is simulated for the demo. Everything the system would have sent lands here instead of a real inbox.
      </p>
      {isLoading && <Loading />}
      {error && <ErrorBox error={error} retry={refetch} />}
      {data && data.length === 0 && <Empty title="No emails yet" />}
      {data && data.length > 0 && (
        <div className="panel grid overflow-hidden md:h-[calc(100vh-220px)] md:grid-cols-[380px_1fr]">
          <div className="max-h-[50vh] overflow-y-auto border-b border-line md:max-h-none md:border-b-0 md:border-r">
            {data.map((m) => (
              <button
                key={m.id}
                onClick={() => setOpenId(m.id)}
                className={"block w-full border-b border-line px-4 py-2.5 text-left " + (open?.id === m.id ? "bg-signal-soft" : "hover:bg-sunk")}
              >
                <div className="flex items-baseline gap-2">
                  <span className="truncate text-[12px] text-ink-2">{m.to}</span>
                  <span className="ml-auto whitespace-nowrap font-mono text-[10.5px] text-mute">{ago(m.sentAt)}</span>
                </div>
                <div className="truncate text-[13px] font-medium">{m.subject}</div>
              </button>
            ))}
          </div>
          {open && (
            <div className="overflow-y-auto p-5">
              <div className="flex items-center gap-2 text-mute">
                <Mail size={15} /> <span className="font-mono text-[12px]">{fmtDate(open.sentAt)}</span>
              </div>
              <h2 className="mt-2 font-cond text-[19px] font-semibold">{open.subject}</h2>
              <div className="mt-1 text-[13px] text-ink-2">
                To: <span className="font-mono">{open.to}</span>
              </div>
              <pre className="mt-4 whitespace-pre-wrap rounded-md bg-sunk p-4 font-sans text-[14px] leading-relaxed">{open.body}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
