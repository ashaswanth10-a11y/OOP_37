import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Loader2, Pencil, Plus } from "lucide-react"
import * as api from "../../lib/api"
import { TYPE_LABEL } from "../../lib/format"
import type { Service, ServiceType } from "../../lib/types"
import { Dialog, ErrorBox, Loading, PageHeader } from "../../components/ui"

const TYPES: ServiceType[] = ["NETWORK", "INFRASTRUCTURE", "PLATFORM", "APPLICATION", "END_USER"]

function ServiceDialog({ s, all, onClose }: { s: Service | null; all: Service[]; onClose: () => void }) {
  const qc = useQueryClient()
  const cats = useQuery({ queryKey: ["categories"], queryFn: api.categories })
  const [code, setCode] = useState(s?.code ?? "")
  const [name, setName] = useState(s?.name ?? "")
  const [description, setDescription] = useState(s?.description ?? "")
  const [type, setType] = useState<ServiceType>(s?.type ?? "APPLICATION")
  const [criticality, setCriticality] = useState(String(s?.criticality ?? 3))
  const [location, setLocation] = useState(s?.location ?? "")
  const [userBase, setUserBase] = useState(String(s?.userBase ?? 100))
  const [categoryId, setCategoryId] = useState(s?.defaultCategory ? String(s.defaultCategory.id) : "")
  const [deps, setDeps] = useState<number[]>(s?.dependsOn.map((d) => d.id) ?? [])
  const [mapX, setMapX] = useState(String(s?.mapX ?? 0))
  const [mapY, setMapY] = useState(String(s?.mapY ?? 0))
  const [active, setActive] = useState(s?.active ?? true)
  const [depFilter, setDepFilter] = useState("")

  const m = useMutation({
    mutationFn: () => {
      const body = {
        code: code.trim(),
        name: name.trim(),
        description,
        type,
        criticality: Number(criticality),
        location,
        userBase: Number(userBase),
        defaultCategoryId: categoryId ? Number(categoryId) : null,
        dependsOn: deps,
        mapX: Number(mapX),
        mapY: Number(mapY),
        active,
      }
      return s ? api.updateService(s.id, body) : api.createService(body)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["services"] })
      qc.invalidateQueries({ queryKey: ["outage-map"] })
      onClose()
    },
  })

  const candidates = all.filter((x) => x.id !== s?.id && x.name.toLowerCase().includes(depFilter.toLowerCase()))

  return (
    <Dialog title={s ? "Edit " + s.name : "New service"} onClose={onClose} wide>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          m.mutate()
        }}
      >
        <div className="grid gap-3 sm:grid-cols-[160px_1fr]">
          <div>
            <label className="label" htmlFor="s-code">Code</label>
            <input id="s-code" className="field font-mono" required disabled={!!s} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} />
          </div>
          <div>
            <label className="label" htmlFor="s-name">Name</label>
            <input id="s-name" className="field" required value={name} onChange={(e) => setName(e.target.value)} />
          </div>
        </div>
        <div>
          <label className="label" htmlFor="s-desc">Description</label>
          <input id="s-desc" className="field" value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div>
            <label className="label" htmlFor="s-type">Type</label>
            <select id="s-type" className="field" value={type} onChange={(e) => setType(e.target.value as ServiceType)}>
              {TYPES.map((t) => (
                <option key={t} value={t}>{TYPE_LABEL[t]}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="s-crit">Criticality 1-5</label>
            <input id="s-crit" type="number" min={1} max={5} className="field font-mono" value={criticality} onChange={(e) => setCriticality(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="s-users">User base</label>
            <input id="s-users" type="number" min={0} className="field font-mono" value={userBase} onChange={(e) => setUserBase(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="s-cat">Default category</label>
            <select id="s-cat" className="field" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              <option value="">None</option>
              {cats.data?.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-[1fr_90px_90px]">
          <div className="col-span-2 sm:col-span-1">
            <label className="label" htmlFor="s-loc">Location</label>
            <input id="s-loc" className="field" value={location} onChange={(e) => setLocation(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="s-x">Map column</label>
            <input id="s-x" type="number" min={0} className="field font-mono" value={mapX} onChange={(e) => setMapX(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="s-y">Map row</label>
            <input id="s-y" type="number" min={0} className="field font-mono" value={mapY} onChange={(e) => setMapY(e.target.value)} />
          </div>
        </div>
        <div>
          <div className="flex items-end justify-between gap-2">
            <label className="label">Depends on ({deps.length})</label>
            <input className="field mb-1 max-w-[180px] py-1 text-[12.5px]" placeholder="Filter" value={depFilter} onChange={(e) => setDepFilter(e.target.value)} />
          </div>
          <div className="grid max-h-[180px] grid-cols-1 overflow-y-auto rounded-md border border-line p-2 sm:grid-cols-2">
            {candidates.map((x) => (
              <label key={x.id} className="flex items-center gap-2 rounded px-1.5 py-1 text-[13px] hover:bg-sunk">
                <input
                  type="checkbox"
                  checked={deps.includes(x.id)}
                  onChange={(e) => setDeps(e.target.checked ? [...deps, x.id] : deps.filter((d) => d !== x.id))}
                />
                <span className="truncate">{x.name}</span>
              </label>
            ))}
          </div>
          <div className="mt-1 text-xs text-mute">Upstream services this one needs. Cycles are rejected by the server.</div>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> Active in the catalogue
        </label>
        {m.error && <ErrorBox error={m.error} />}
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Back</button>
          <button className="btn btn-primary" disabled={m.isPending}>
            {m.isPending && <Loader2 size={14} className="animate-spin" />} {s ? "Save service" : "Create service"}
          </button>
        </div>
      </form>
    </Dialog>
  )
}

export default function ServicesAdmin() {
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ["services"], queryFn: api.services })
  const [editing, setEditing] = useState<Service | null | "new">(null)
  const list = [...(data ?? [])].sort((a, b) => TYPES.indexOf(a.type) - TYPES.indexOf(b.type) || a.name.localeCompare(b.name))

  return (
    <div className="mx-auto max-w-[1300px]">
      <PageHeader eyebrow="Administration" title="Service catalogue">
        <button className="btn btn-primary" onClick={() => setEditing("new")}>
          <Plus size={15} /> New service
        </button>
      </PageHeader>
      {isLoading && <Loading />}
      {error && <ErrorBox error={error} retry={refetch} />}
      {data && (
        <div className="panel overflow-x-auto">
          <table className="w-full min-w-[900px] text-left text-[13px]">
            <thead className="border-b border-line bg-sunk/60">
              <tr>
                <th className="eyebrow px-4 py-2 font-semibold">Service</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Type</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Crit.</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Users</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Depends on</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Map</th>
                <th className="py-2 pr-4" />
              </tr>
            </thead>
            <tbody>
              {list.map((s) => (
                <tr key={s.id} className={"border-b border-line last:border-0 " + (s.active ? "" : "opacity-50")}>
                  <td className="px-4 py-2">
                    <div className="font-medium">{s.name}</div>
                    <div className="font-mono text-[11px] text-mute">
                      {s.code}
                      {s.location ? " · " + s.location : ""}
                    </div>
                  </td>
                  <td className="py-2 pr-3 text-ink-2">{TYPE_LABEL[s.type]}</td>
                  <td className="py-2 pr-3 font-mono">{s.criticality}</td>
                  <td className="py-2 pr-3 font-mono">{s.userBase}</td>
                  <td className="max-w-[320px] py-2 pr-3 text-[12px] text-ink-2">{s.dependsOn.map((d) => d.name).join(", ") || <span className="text-mute">-</span>}</td>
                  <td className="py-2 pr-3 font-mono text-[11.5px] text-mute">
                    {s.mapX},{s.mapY}
                  </td>
                  <td className="py-2 pr-4 text-right">
                    <button className="btn btn-ghost px-2 py-1 text-[12px]" onClick={() => setEditing(s)}>
                      <Pencil size={12} /> Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <ServiceDialog s={editing === "new" ? null : editing} all={data ?? []} onClose={() => setEditing(null)} />}
    </div>
  )
}
