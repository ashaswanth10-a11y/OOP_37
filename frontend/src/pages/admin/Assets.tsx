import { useState } from "react"
import { Link, Navigate, useSearchParams } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { QRCodeSVG } from "qrcode.react"
import { ArrowLeft, Loader2, Plus, Printer } from "lucide-react"
import * as api from "../../lib/api"
import { useAuth } from "../../lib/auth"
import { useToast } from "../../lib/toast"
import type { Asset } from "../../lib/types"
import { ErrorBox, Loading, PageHeader } from "../../components/ui"
import { Logo } from "../../components/Shell"

const stickerUrl = (tag: string) => window.location.origin + "/report?asset=" + encodeURIComponent(tag)

export function AssetsAdmin() {
  const qc = useQueryClient()
  const toast = useToast()
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ["assets"], queryFn: api.assets })
  const services = useQuery({ queryKey: ["services"], queryFn: api.services })
  const cats = useQuery({ queryKey: ["categories"], queryFn: api.categories })
  const [tag, setTag] = useState("")
  const [name, setName] = useState("")
  const [location, setLocation] = useState("")
  const [serviceId, setServiceId] = useState("")
  const [categoryId, setCategoryId] = useState("")
  const [picked, setPicked] = useState<string[]>([])

  const create = useMutation({
    mutationFn: () => api.createAsset({ tag: tag.trim(), name: name.trim(), location: location.trim(), serviceId: Number(serviceId), categoryId: categoryId ? Number(categoryId) : null }),
    onSuccess: (a) => {
      qc.invalidateQueries({ queryKey: ["assets"] })
      toast({ title: "Asset " + a.tag + " registered", body: "Its QR sticker is ready to print.", tone: "success" })
      setTag("")
      setName("")
      setLocation("")
    },
  })

  const printHref = "/admin/assets/print" + (picked.length ? "?tags=" + picked.join(",") : "")

  return (
    <div className="mx-auto max-w-[1200px]">
      <PageHeader eyebrow="Administration" title="Assets & QR stickers">
        <Link to={printHref} target="_blank" className="btn btn-primary">
          <Printer size={15} /> Print {picked.length ? picked.length + " sticker" + (picked.length > 1 ? "s" : "") : "all stickers"}
        </Link>
      </PageHeader>
      <p className="-mt-3 mb-5 max-w-2xl text-sm text-mute">
        Stick one on each access point, lab PC or printer. Scanning it opens the report form with the service and location
        already filled in.
      </p>

      <form
        className="panel mb-5 grid grid-cols-2 gap-3 p-4 lg:grid-cols-[150px_1fr_1fr_1fr_160px_auto] lg:items-end"
        onSubmit={(e) => {
          e.preventDefault()
          create.mutate()
        }}
      >
        <div>
          <label className="label" htmlFor="a-tag">Tag</label>
          <input id="a-tag" className="field font-mono" required placeholder="AP-VYAS-1F" value={tag} onChange={(e) => setTag(e.target.value.toUpperCase())} />
        </div>
        <div>
          <label className="label" htmlFor="a-name">Name</label>
          <input id="a-name" className="field" required placeholder="Access point, 1st floor" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="a-loc">Location</label>
          <input id="a-loc" className="field" placeholder="Vyas Bhawan, 1F" value={location} onChange={(e) => setLocation(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="a-svc">Service</label>
          <select id="a-svc" className="field" required value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
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
          <label className="label" htmlFor="a-cat">Category</label>
          <select id="a-cat" className="field" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            <option value="">From service</option>
            {cats.data?.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </div>
        <button className="btn btn-primary" disabled={create.isPending}>
          {create.isPending ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />} Register
        </button>
        {create.error && (
          <div className="col-span-2 lg:col-span-6">
            <ErrorBox error={create.error} />
          </div>
        )}
      </form>

      {isLoading && <Loading />}
      {error && <ErrorBox error={error} retry={refetch} />}
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {data?.map((a) => {
          const on = picked.includes(a.tag)
          return (
            <label key={a.id} className={"panel flex cursor-pointer gap-3 p-3 " + (on ? "border-signal ring-2 ring-signal/20" : "")}>
              <div className="rounded bg-white p-1">
                <QRCodeSVG value={stickerUrl(a.tag)} size={72} level="M" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[12.5px] font-semibold">{a.tag}</span>
                  <input
                    type="checkbox"
                    className="ml-auto"
                    checked={on}
                    onChange={(e) => setPicked(e.target.checked ? [...picked, a.tag] : picked.filter((t) => t !== a.tag))}
                    aria-label={"Select " + a.tag + " for printing"}
                  />
                </div>
                <div className="truncate text-[13px] font-medium">{a.name}</div>
                <div className="truncate text-[12px] text-mute">{a.location}</div>
                <div className="truncate text-[12px] text-ink-2">{a.service.name}</div>
                <Link to={"/report?asset=" + a.tag} className="text-[11.5px] text-signal hover:underline" onClick={(e) => e.stopPropagation()}>
                  Test the scan
                </Link>
              </div>
            </label>
          )
        })}
      </div>
    </div>
  )
}

function Sticker({ a }: { a: Asset }) {
  return (
    <div className="flex break-inside-avoid flex-col items-center rounded-lg border-2 border-[#0f1b24] bg-white p-4 text-center text-[#13202a]">
      <div className="flex w-full items-center justify-between">
        <span className="font-cond text-[13px] font-bold">CampusOps</span>
        <span className="font-mono text-[9px] tracking-[0.12em]">BITS HYD · IT</span>
      </div>
      <div className="my-3">
        <QRCodeSVG value={stickerUrl(a.tag)} size={132} level="M" />
      </div>
      <div className="font-cond text-[16px] font-bold leading-tight">Scan to report a problem</div>
      <div className="mt-2 w-full rounded bg-[#0f1b24] py-1 font-mono text-[13px] font-semibold tracking-wider text-white">{a.tag}</div>
      <div className="mt-1.5 text-[12px] font-medium leading-tight">{a.name}</div>
      <div className="text-[11px] text-[#64737d]">{a.location}</div>
    </div>
  )
}

export function StickerSheet() {
  const { token } = useAuth()
  const [params] = useSearchParams()
  const { data, error, isLoading } = useQuery({ queryKey: ["assets"], queryFn: api.assets, enabled: !!token })
  if (!token) return <Navigate to="/login?next=/admin/assets/print" replace />
  const tags = params.get("tags")?.split(",")
  const list = (data ?? []).filter((a) => !tags || tags.includes(a.tag))

  return (
    <div className="min-h-screen bg-white">
      <div className="no-print flex items-center gap-3 border-b border-line px-6 py-3">
        <Link to="/admin/assets" className="inline-flex items-center gap-1 text-sm text-mute hover:text-ink">
          <ArrowLeft size={14} /> Assets
        </Link>
        <Logo small />
        <span className="text-sm text-mute">{list.length} stickers · A4, 3 per row</span>
        <button className="btn btn-primary ml-auto" onClick={() => window.print()}>
          <Printer size={15} /> Print
        </button>
      </div>
      {isLoading && <Loading />}
      {error && <div className="p-6"><ErrorBox error={error} /></div>}
      <div className="mx-auto grid max-w-[800px] grid-cols-2 gap-4 p-6 sm:grid-cols-3 print:grid-cols-3 print:p-0">
        {list.map((a) => (
          <Sticker key={a.id} a={a} />
        ))}
      </div>
    </div>
  )
}
