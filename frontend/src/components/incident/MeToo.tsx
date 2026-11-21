import { useState } from "react"
import { Link } from "react-router-dom"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Check, Hand, Loader2, Users } from "lucide-react"
import * as api from "../../lib/api"
import { useToast } from "../../lib/toast"

export function MeTooButton({ id, already, small, linkAfter }: { id: number; already?: boolean; small?: boolean; linkAfter?: boolean }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [done, setDone] = useState(!!already)
  const [number, setNumber] = useState<string | null>(null)
  const m = useMutation({
    mutationFn: () => api.meToo(id),
    onSuccess: (r) => {
      setDone(true)
      setNumber(r.summary.number)
      toast({
        title: "Added you to " + r.summary.number,
        body: "More reports raise the priority automatically. " + r.affectedUsers + " people affected now.",
        tone: "success",
      })
      qc.invalidateQueries({ predicate: (q) => ["incident", "incidents", "mine", "preview", "dashboard"].includes(String(q.queryKey[0])) })
    },
    onError: (err) => toast({ title: "Couldn't add you", body: api.errorMessage(err), tone: "warning" }),
  })

  if (done) {
    return (
      <span className={"inline-flex items-center gap-1 font-medium text-ok " + (small ? "text-[12px]" : "text-[13px]")}>
        <Check size={small ? 13 : 15} /> You're on this one
        {linkAfter && (
          <Link to={"/incidents/" + id} className="ml-1 text-signal hover:underline">
            Follow {number ?? "it"}
          </Link>
        )}
      </span>
    )
  }
  return (
    <button
      type="button"
      onClick={(e) => {
        e.preventDefault()
        e.stopPropagation()
        m.mutate()
      }}
      disabled={m.isPending}
      className={small ? "btn btn-ghost px-2.5 py-1 text-[12.5px]" : "btn btn-ghost"}
    >
      {m.isPending ? <Loader2 size={14} className="animate-spin" /> : <Hand size={14} />} Me too, I have this problem
    </button>
  )
}

export function Reach({ affected, supporters, className = "" }: { affected: number; supporters: number; className?: string }) {
  if (!affected && !supporters) return null
  return (
    <span className={"inline-flex items-center gap-1 text-[12px] text-ink-2 " + className}>
      <Users size={12} className="text-mute" />
      <span className="font-mono">{affected}</span> affected
      {supporters > 0 && (
        <>
          {" · "}
          <span className="font-mono">{supporters}</span> said me too
        </>
      )}
    </span>
  )
}
