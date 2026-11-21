import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Loader2, Pencil, Plus } from "lucide-react"
import * as api from "../../lib/api"
import type { Category } from "../../lib/types"
import { Dialog, ErrorBox, Loading, PageHeader } from "../../components/ui"

function CategoryDialog({ c, onClose }: { c: Category | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [code, setCode] = useState(c?.code ?? "")
  const [name, setName] = useState(c?.name ?? "")
  const [description, setDescription] = useState(c?.description ?? "")
  const [keywords, setKeywords] = useState(c?.keywords ?? "")
  const [color, setColor] = useState(c?.color ?? "#0a6c7e")
  const [active, setActive] = useState(c?.active ?? true)
  const m = useMutation({
    mutationFn: () => {
      const body = { code: code.trim(), name: name.trim(), description, keywords, color, active }
      return c ? api.updateCategory(c.id, body) : api.createCategory(body)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["categories"] })
      onClose()
    },
  })
  return (
    <Dialog title={c ? "Edit " + c.name : "New category"} onClose={onClose}>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          m.mutate()
        }}
      >
        <div className="grid grid-cols-[140px_1fr] gap-3">
          <div>
            <label className="label" htmlFor="c-code">Code</label>
            <input id="c-code" className="field font-mono" required disabled={!!c} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} />
          </div>
          <div>
            <label className="label" htmlFor="c-name">Name</label>
            <input id="c-name" className="field" required value={name} onChange={(e) => setName(e.target.value)} />
          </div>
        </div>
        <div>
          <label className="label" htmlFor="c-desc">Description</label>
          <input id="c-desc" className="field" value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="c-kw">Keywords</label>
          <textarea id="c-kw" className="field min-h-[70px] font-mono text-[12.5px]" value={keywords} onChange={(e) => setKeywords(e.target.value)} />
          <div className="mt-1 text-xs text-mute">Comma separated. The keyword classifier matches these against report text.</div>
        </div>
        <div className="flex items-center gap-4">
          <div>
            <label className="label" htmlFor="c-color">Colour</label>
            <div className="flex items-center gap-2">
              <input id="c-color" type="color" className="h-9 w-12 cursor-pointer rounded border border-line-strong bg-card" value={color} onChange={(e) => setColor(e.target.value)} />
              <span className="font-mono text-[12px] text-mute">{color}</span>
            </div>
          </div>
          <label className="mt-5 flex items-center gap-2 text-sm">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> Active
          </label>
        </div>
        {m.error && <ErrorBox error={m.error} />}
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Back</button>
          <button className="btn btn-primary" disabled={m.isPending}>
            {m.isPending && <Loader2 size={14} className="animate-spin" />} {c ? "Save category" : "Create category"}
          </button>
        </div>
      </form>
    </Dialog>
  )
}

export default function CategoriesAdmin() {
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ["categories"], queryFn: api.categories })
  const [editing, setEditing] = useState<Category | null | "new">(null)
  return (
    <div className="mx-auto max-w-[1100px]">
      <PageHeader eyebrow="Administration" title="Categories">
        <button className="btn btn-primary" onClick={() => setEditing("new")}>
          <Plus size={15} /> New category
        </button>
      </PageHeader>
      {isLoading && <Loading />}
      {error && <ErrorBox error={error} retry={refetch} />}
      <div className="grid gap-3 md:grid-cols-2">
        {data?.map((c) => (
          <div key={c.id} className={"panel flex overflow-hidden " + (c.active ? "" : "opacity-50")}>
            <div className="w-1.5 shrink-0" style={{ background: c.color ?? "var(--line)" }} />
            <div className="min-w-0 flex-1 p-4">
              <div className="flex items-center gap-2">
                <span className="font-medium">{c.name}</span>
                <span className="font-mono text-[11px] text-mute">{c.code}</span>
                {!c.active && <span className="text-[11px] text-mute">inactive</span>}
                <button className="ml-auto text-mute hover:text-ink" onClick={() => setEditing(c)} aria-label={"Edit " + c.name}>
                  <Pencil size={14} />
                </button>
              </div>
              <div className="text-[13px] text-ink-2">{c.description}</div>
              <div className="mt-2 flex flex-wrap gap-1">
                {(c.keywords ?? "")
                  .split(",")
                  .filter(Boolean)
                  .map((k) => (
                    <span key={k} className="rounded bg-sunk px-1.5 font-mono text-[11px] text-ink-2">{k.trim()}</span>
                  ))}
              </div>
            </div>
          </div>
        ))}
      </div>
      {editing && <CategoryDialog c={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
