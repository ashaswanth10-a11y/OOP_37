import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Loader2, Plus, Trash2 } from "lucide-react"
import * as api from "../../lib/api"
import { useToast } from "../../lib/toast"
import { minutesLabel, PRIORITY_LABEL } from "../../lib/format"
import type { Priority, SlaPolicy } from "../../lib/types"
import { ErrorBox, Loading, PageHeader, PriorityTag } from "../../components/ui"

const PRIORITIES: Priority[] = ["P1", "P2", "P3", "P4"]

function DefaultRow({ p }: { p: SlaPolicy }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [resp, setResp] = useState(String(p.responseMinutes))
  const [res, setRes] = useState(String(p.resolutionMinutes))
  const dirty = resp !== String(p.responseMinutes) || res !== String(p.resolutionMinutes)
  const m = useMutation({
    mutationFn: () => api.upsertPolicy({ serviceId: null, priority: p.priority, responseMinutes: Number(resp), resolutionMinutes: Number(res) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["sla-policies"] })
      toast({ title: p.priority + " campus default saved", tone: "success" })
    },
    onError: (e) => toast({ title: "Couldn't save", body: api.errorMessage(e), tone: "critical" }),
  })
  return (
    <tr className="border-b border-line last:border-0">
      <td className="px-4 py-2.5">
        <div className="flex items-center gap-2">
          <PriorityTag p={p.priority} /> <span>{PRIORITY_LABEL[p.priority]}</span>
        </div>
      </td>
      <td className="py-2.5 pr-3">
        <div className="flex items-center gap-2">
          <input type="number" min={1} className="field w-24 font-mono" value={resp} onChange={(e) => setResp(e.target.value)} aria-label={p.priority + " response minutes"} />
          <span className="text-[12px] text-mute">{minutesLabel(Number(resp))}</span>
        </div>
      </td>
      <td className="py-2.5 pr-3">
        <div className="flex items-center gap-2">
          <input type="number" min={1} className="field w-24 font-mono" value={res} onChange={(e) => setRes(e.target.value)} aria-label={p.priority + " resolution minutes"} />
          <span className="text-[12px] text-mute">{minutesLabel(Number(res))}</span>
        </div>
      </td>
      <td className="py-2.5 pr-4 text-right">
        <button className="btn btn-primary px-3 py-1" disabled={!dirty || m.isPending} onClick={() => m.mutate()}>
          {m.isPending && <Loader2 size={13} className="animate-spin" />} Save
        </button>
      </td>
    </tr>
  )
}

export default function SlaAdmin() {
  const qc = useQueryClient()
  const toast = useToast()
  const policies = useQuery({ queryKey: ["sla-policies"], queryFn: api.slaPolicies })
  const services = useQuery({ queryKey: ["services"], queryFn: api.services })
  const [serviceId, setServiceId] = useState("")
  const [priority, setPriority] = useState<Priority>("P1")
  const [resp, setResp] = useState("10")
  const [res, setRes] = useState("60")

  const add = useMutation({
    mutationFn: () => api.upsertPolicy({ serviceId: Number(serviceId), priority, responseMinutes: Number(resp), resolutionMinutes: Number(res) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["sla-policies"] })
      toast({ title: "Override saved", tone: "success" })
    },
    onError: (e) => toast({ title: "Couldn't save the override", body: api.errorMessage(e), tone: "critical" }),
  })
  const del = useMutation({
    mutationFn: api.deletePolicy,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["sla-policies"] }),
    onError: (e) => toast({ title: "Couldn't remove it", body: api.errorMessage(e), tone: "critical" }),
  })

  const defaults = (policies.data ?? []).filter((p) => !p.service).sort((a, b) => a.priority.localeCompare(b.priority))
  const overrides = (policies.data ?? []).filter((p) => p.service)

  return (
    <div className="mx-auto max-w-[1000px]">
      <PageHeader eyebrow="Administration" title="SLA policies" />
      {policies.isLoading && <Loading />}
      {policies.error && <ErrorBox error={policies.error} retry={policies.refetch} />}

      <section className="panel overflow-x-auto">
        <div className="border-b border-line px-4 py-3">
          <div className="font-cond text-[16px] font-semibold">Campus defaults</div>
          <div className="text-[12.5px] text-mute">Used for every service without an override. Times in minutes.</div>
        </div>
        <table className="w-full min-w-[600px] text-left text-[13px]">
          <thead className="border-b border-line bg-sunk/60">
            <tr>
              <th className="eyebrow px-4 py-2 font-semibold">Priority</th>
              <th className="eyebrow py-2 pr-3 font-semibold">First response</th>
              <th className="eyebrow py-2 pr-3 font-semibold">Resolution</th>
              <th className="py-2 pr-4" />
            </tr>
          </thead>
          <tbody>
            {defaults.map((p) => (
              <DefaultRow key={p.id + ":" + p.responseMinutes + ":" + p.resolutionMinutes} p={p} />
            ))}
          </tbody>
        </table>
      </section>

      <section className="panel mt-5">
        <div className="border-b border-line px-4 py-3">
          <div className="font-cond text-[16px] font-semibold">Per-service overrides</div>
          <div className="text-[12.5px] text-mute">Tighter targets for critical services, e.g. the fee portal during payment week.</div>
        </div>
        <div className="divide-y divide-line">
          {overrides.length === 0 && <div className="px-4 py-4 text-sm text-mute">No overrides.</div>}
          {overrides.map((p) => (
            <div key={p.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2.5 text-[13px]">
              <span className="min-w-[200px] font-medium">{p.service?.name}</span>
              <PriorityTag p={p.priority} />
              <span className="font-mono text-[12.5px]">respond {minutesLabel(p.responseMinutes)}</span>
              <span className="font-mono text-[12.5px]">resolve {minutesLabel(p.resolutionMinutes)}</span>
              <button className="ml-auto text-mute hover:text-breach" disabled={del.isPending} onClick={() => del.mutate(p.id)} aria-label="Remove override">
                <Trash2 size={15} />
              </button>
            </div>
          ))}
        </div>
        <form
          className="grid grid-cols-2 gap-3 border-t border-line bg-sunk/40 p-4 sm:grid-cols-[1fr_90px_110px_110px_auto] sm:items-end"
          onSubmit={(e) => {
            e.preventDefault()
            if (serviceId) add.mutate()
          }}
        >
          <div className="col-span-2 sm:col-span-1">
            <label className="label" htmlFor="o-svc">Service</label>
            <select id="o-svc" className="field" value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
              <option value="">Choose…</option>
              {services.data
                ?.slice()
                .sort((a, b) => a.name.localeCompare(b.name))
                .map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="o-p">Priority</label>
            <select id="o-p" className="field" value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              {PRIORITIES.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="o-r">Respond (min)</label>
            <input id="o-r" type="number" min={1} className="field font-mono" value={resp} onChange={(e) => setResp(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="o-s">Resolve (min)</label>
            <input id="o-s" type="number" min={1} className="field font-mono" value={res} onChange={(e) => setRes(e.target.value)} />
          </div>
          <button className="btn btn-primary" disabled={!serviceId || add.isPending}>
            {add.isPending ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />} Add override
          </button>
        </form>
      </section>
    </div>
  )
}
