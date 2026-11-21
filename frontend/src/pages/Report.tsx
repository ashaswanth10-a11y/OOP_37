import { useEffect, useMemo, useRef, useState, type FormEvent } from "react"
import { Link, useSearchParams } from "react-router-dom"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { ArrowRight, CheckCircle2, Loader2, Lock, Mic, MicOff, QrCode, Sparkles, Wrench } from "lucide-react"
import * as api from "../lib/api"
import { ApiError } from "../lib/api"
import { isStaff, useAuth } from "../lib/auth"
import { fmtDate, PRIORITY_LABEL, TYPE_LABEL } from "../lib/format"
import type { Channel, IncidentDetail, Level, Preview, Service } from "../lib/types"
import { Countdown } from "../components/Countdown"
import { ErrorBox, PriorityTag, StatusPill } from "../components/ui"
import { MeTooButton } from "../components/incident/MeToo"
import KnownOutages from "../components/KnownOutages"

const IMPACT: { v: Level; label: string; hint: string }[] = [
  { v: "LOW", label: "Just me", hint: "one person" },
  { v: "MEDIUM", label: "A few of us", hint: "a room or group" },
  { v: "HIGH", label: "A whole wing", hint: "hostel floor, lab, class" },
  { v: "CRITICAL", label: "Everyone", hint: "campus-wide" },
]

const URGENCY: { v: Level; label: string; hint: string }[] = [
  { v: "LOW", label: "Whenever", hint: "it's a nuisance" },
  { v: "MEDIUM", label: "Today", hint: "slowing me down" },
  { v: "HIGH", label: "Within the hour", hint: "can't work" },
  { v: "CRITICAL", label: "Right now", hint: "exam, payment, deadline" },
]

const TYPE_ORDER = ["NETWORK", "APPLICATION", "END_USER", "PLATFORM", "INFRASTRUCTURE"]

type SpeechCtor = new () => {
  lang: string
  continuous: boolean
  interimResults: boolean
  onresult: ((e: { resultIndex: number; results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }> }) => void) | null
  onend: (() => void) | null
  onerror: (() => void) | null
  start: () => void
  stop: () => void
}

function getSpeech(): SpeechCtor | null {
  const w = window as unknown as { SpeechRecognition?: SpeechCtor; webkitSpeechRecognition?: SpeechCtor }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}

function useDebounced<T>(value: T, ms: number) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}

function Segmented({
  options,
  value,
  onChange,
  name,
}: {
  options: { v: Level; label: string; hint: string }[]
  value: Level
  onChange: (v: Level) => void
  name: string
}) {
  return (
    <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-4" role="radiogroup" aria-label={name}>
      {options.map((o) => {
        const on = o.v === value
        return (
          <button
            key={o.v}
            type="button"
            role="radio"
            aria-checked={on}
            onClick={() => onChange(o.v)}
            className={
              "rounded-md border px-2.5 py-2 text-left transition-colors " +
              (on ? "border-signal bg-signal-soft text-ink" : "border-line-strong bg-card text-ink-2 hover:border-ink-2")
            }
          >
            <span className="block text-[13px] font-medium">{o.label}</span>
            <span className="block text-[11px] text-mute">{o.hint}</span>
          </button>
        )
      })}
    </div>
  )
}

function Suggestions({ preview, loading, typed, staff }: { preview: Preview | undefined; loading: boolean; typed: boolean; staff: boolean }) {
  if (!typed) {
    return (
      <div className="rounded-lg border border-dashed border-line-strong p-5 text-sm text-mute">
        Start describing the problem. CampusOps will suggest a category, check whether someone already reported it, and
        show fixes that worked before.
      </div>
    )
  }
  return (
    <div className="space-y-4">
      <div className="panel p-4">
        <div className="flex items-center justify-between">
          <span className="eyebrow">Suggested category</span>
          {loading && <Loader2 size={13} className="animate-spin text-mute" />}
        </div>
        {preview?.category ? (
          <>
            <div className="mt-1.5 flex items-baseline justify-between gap-2">
              <span className="font-medium">{preview.category.name}</span>
              <span className="font-mono text-xs text-signal">{Math.round(preview.category.confidence * 100)}% sure</span>
            </div>
            <div className="mt-1 h-1 overflow-hidden rounded-full bg-sunk">
              <div className="h-full bg-signal" style={{ width: Math.round(preview.category.confidence * 100) + "%" }} />
            </div>
            <div className="mt-2 text-xs text-mute">{preview.category.reason}</div>
          </>
        ) : (
          <div className="mt-1.5 text-sm text-mute">Not sure yet. Add a few more details.</div>
        )}
        {preview && (
          <div className="mt-2 flex items-center gap-1 font-mono text-[10.5px] text-mute">
            <Sparkles size={11} /> {preview.aiAvailable ? "AI triage runs on submission" : "Keyword classifier"}
          </div>
        )}
      </div>

      <div className="panel p-4">
        <span className="eyebrow">Is this already reported?</span>
        {preview && preview.similar.length === 0 && <div className="mt-1.5 text-sm text-mute">No matching open incidents.</div>}
        <div className="mt-1 space-y-2">
          {preview?.similar.map((s) => (
            <div key={s.id} className="rounded-md border border-risk/40 bg-risk/5 p-2.5">
              <div className="flex items-center gap-2">
                {staff ? (
                  <Link to={"/incidents/" + s.id} target="_blank" className="font-mono text-[12px] text-ink hover:underline">
                    {s.number}
                  </Link>
                ) : (
                  <span className="font-mono text-[12px] text-ink">{s.number}</span>
                )}
                <StatusPill s={s.status} />
                <span className="ml-auto font-mono text-[11px] text-risk">{Math.round(s.score * 100)}% match</span>
              </div>
              <div className="mt-0.5 text-[13px] font-medium leading-snug">{s.title}</div>
              <div className="text-[11.5px] text-mute">{s.reason}</div>
              <div className="mt-2">
                <MeTooButton id={s.id} small linkAfter />
              </div>
            </div>
          ))}
          {preview && preview.similar.length > 0 && (
            <div className="text-[11.5px] text-mute">Same problem? Add yourself instead of filing a new report. It gets fixed faster.</div>
          )}
        </div>
      </div>

      <div className="panel p-4">
        <span className="eyebrow">Known fixes</span>
        {preview && preview.fixes.length === 0 && <div className="mt-1.5 text-sm text-mute">No past fixes match yet.</div>}
        <div className="mt-1 divide-y divide-line">
          {preview?.fixes.map((f, k) => (
            <div key={f.number + k} className="py-2">
              <div className="flex items-center gap-2 text-[12px]">
                <Wrench size={12} className="text-signal" />
                <span className="font-mono">{f.number}</span>
                <span className="truncate text-mute">{f.title}</span>
              </div>
              <div className="mt-0.5 text-[13px] text-ink-2">{f.resolution}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function Created({ d, onAnother }: { d: IncidentDetail; onAnother: () => void }) {
  const s = d.summary
  return (
    <div className="mx-auto max-w-2xl">
      <div className="panel overflow-hidden">
        <div className="flex items-center gap-3 bg-rail px-6 py-5 text-white">
          <CheckCircle2 size={26} className="text-ok" />
          <div>
            <div className="font-cond text-[22px] font-semibold leading-tight">Reported. We're on it.</div>
            <div className="font-mono text-[13px] text-beacon">{s.number}</div>
          </div>
        </div>
        <div className="divide-y divide-line">
          <div className="px-6 py-4">
            <div className="text-[17px] font-medium">{s.title}</div>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-mute">
              <StatusPill s={s.status} />
              {s.category && <span>{s.category.name}</span>}
              {s.service && <span>· {s.service.name}</span>}
            </div>
          </div>
          <div className="grid gap-4 px-6 py-4 sm:grid-cols-[auto_1fr]">
            <div>
              <div className="eyebrow">Priority</div>
              <div className="mt-1 flex items-center gap-2">
                <PriorityTag p={s.priority} />
                <span className="font-medium">{s.priority ? PRIORITY_LABEL[s.priority] : "-"}</span>
              </div>
            </div>
            <div>
              <div className="eyebrow">Why</div>
              <div className="mt-1 font-mono text-[12px] leading-relaxed text-ink-2">{d.priorityRationale}</div>
            </div>
          </div>
          {d.sla && (
            <div className="grid grid-cols-2 gap-4 px-6 py-4">
              <div>
                <div className="eyebrow">First response by</div>
                <div className="mt-1 font-medium">{fmtDate(d.sla.responseDue)}</div>
                <Countdown due={d.sla.responseDue} start={d.sla.start} className="text-sm" />
              </div>
              <div>
                <div className="eyebrow">Fix target</div>
                <div className="mt-1 font-medium">{fmtDate(d.sla.resolutionDue)}</div>
                <Countdown due={d.sla.resolutionDue} start={d.sla.start} className="text-sm" />
              </div>
            </div>
          )}
          <div className="px-6 py-4">
            <div className="eyebrow">Assigned to</div>
            <div className="mt-1">
              {s.assignee ? (
                <span className="font-medium">
                  {s.assignee.name} <span className="font-mono text-xs text-mute">{s.assignee.code}</span>
                </span>
              ) : (
                <span className="text-mute">Waiting for a free technician</span>
              )}
            </div>
            {s.duplicateOf && (
              <div className="mt-2 text-sm text-risk">Looks related to {s.duplicateOf}, the technician will check both together.</div>
            )}
          </div>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        <Link to={"/incidents/" + s.id} className="btn btn-primary">
          Track this incident <ArrowRight size={15} />
        </Link>
        <button className="btn btn-ghost" onClick={onAnother}>
          Report another problem
        </button>
      </div>
    </div>
  )
}

export default function ReportPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [params, setParams] = useSearchParams()
  const assetTag = params.get("asset")

  const servicesQ = useQuery({ queryKey: ["services"], queryFn: api.services, staleTime: 300000 })
  const categoriesQ = useQuery({ queryKey: ["categories"], queryFn: api.categories, staleTime: 300000 })
  const assetQ = useQuery({ queryKey: ["asset", assetTag], queryFn: () => api.asset(assetTag!), enabled: !!assetTag, retry: false })
  const asset = assetQ.data

  const [title, setTitle] = useState("")
  const [description, setDescription] = useState("")
  const [serviceId, setServiceId] = useState("")
  const [categoryId, setCategoryId] = useState("")
  const [impact, setImpact] = useState<Level>("LOW")
  const [urgency, setUrgency] = useState<Level>("MEDIUM")
  const [affectedUsers, setAffectedUsers] = useState("1")
  const [location, setLocation] = useState(user?.location ?? "")
  const [voiceUsed, setVoiceUsed] = useState(false)
  const [listening, setListening] = useState(false)
  const [interim, setInterim] = useState("")
  const [error, setError] = useState<unknown>(null)
  const [duplicate, setDuplicate] = useState<{ message: string; number: string | null } | null>(null)
  const [busy, setBusy] = useState(false)
  const [created, setCreated] = useState<IncidentDetail | null>(null)
  const recognition = useRef<InstanceType<SpeechCtor> | null>(null)
  const Speech = useMemo(getSpeech, [])

  useEffect(() => {
    if (asset) {
      setServiceId(String(asset.service.id))
      if (asset.location) setLocation(asset.location)
      if (asset.category) setCategoryId(String(asset.category.id))
    }
  }, [asset])

  useEffect(() => () => recognition.current?.stop(), [])

  const typedText = title + " " + description
  const typed = typedText.trim().length >= 8
  const debounced = useDebounced({ title, description, serviceId, assetTag }, 600)
  const previewQ = useQuery({
    queryKey: ["preview", debounced],
    queryFn: () =>
      api.previewIncident({
        title: debounced.title,
        description: debounced.description,
        serviceId: debounced.serviceId ? Number(debounced.serviceId) : null,
        assetTag: asset ? debounced.assetTag : null,
      }),
    enabled: (debounced.title + " " + debounced.description).trim().length >= 8,
    placeholderData: (prev) => prev,
    staleTime: 60000,
  })

  const grouped = useMemo(() => {
    const list = (servicesQ.data ?? []).filter((s) => s.active)
    const out: { type: string; items: Service[] }[] = []
    for (const t of TYPE_ORDER) {
      const items = list.filter((s) => s.type === t).sort((a, b) => a.name.localeCompare(b.name))
      if (items.length) out.push({ type: t, items })
    }
    return out
  }, [servicesQ.data])

  const toggleVoice = () => {
    if (!Speech) return
    if (listening) {
      recognition.current?.stop()
      return
    }
    const r = new Speech()
    r.lang = "en-IN"
    r.continuous = true
    r.interimResults = true
    r.onresult = (e) => {
      let finalText = ""
      let partial = ""
      for (let k = e.resultIndex; k < e.results.length; k++) {
        const res = e.results[k]
        if (res.isFinal) finalText += res[0].transcript
        else partial += res[0].transcript
      }
      if (finalText) {
        setDescription((d) => (d ? d.trimEnd() + " " : "") + finalText.trim())
        setVoiceUsed(true)
      }
      setInterim(partial)
    }
    r.onend = () => {
      setListening(false)
      setInterim("")
    }
    r.onerror = () => {
      setListening(false)
      setInterim("")
    }
    recognition.current = r
    r.start()
    setListening(true)
  }

  const submit = async (confirmedNew: boolean) => {
    setError(null)
    setBusy(true)
    const channel: Channel = asset ? "QR" : voiceUsed ? "VOICE" : "WEB"
    try {
      const d = await api.reportIncident({
        title: title.trim(),
        description: description.trim(),
        serviceId: serviceId ? Number(serviceId) : null,
        assetTag: asset ? asset.tag : null,
        categoryId: categoryId ? Number(categoryId) : null,
        impact,
        urgency,
        affectedUsers: Math.max(1, Number(affectedUsers) || 1),
        location: location.trim() || null,
        channel,
        confirmedNew,
      })
      setCreated(d)
      setDuplicate(null)
      qc.invalidateQueries({ queryKey: ["mine"] })
      window.scrollTo({ top: 0 })
    } catch (err) {
      if (err instanceof ApiError && err.code === "DUPLICATE_INCIDENT") {
        setDuplicate({ message: err.message, number: err.message.match(/INC-\d+/)?.[0] ?? null })
      } else {
        setError(err)
      }
    } finally {
      setBusy(false)
    }
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (title.trim().length < 5) {
      setError(new Error("Give it a short title (at least 5 characters)"))
      return
    }
    if (description.trim().length < 10) {
      setError(new Error("Describe what's happening in at least a sentence"))
      return
    }
    if (!serviceId) {
      setError(new Error("Pick the service that's affected"))
      return
    }
    submit(false)
  }

  const reset = () => {
    setCreated(null)
    setTitle("")
    setDescription("")
    setVoiceUsed(false)
    setDuplicate(null)
    if (assetTag) {
      setParams({})
      setServiceId("")
      setCategoryId("")
      setLocation(user?.location ?? "")
    }
  }

  if (created) return <Created d={created} onAnother={reset} />

  const dupId = duplicate?.number
    ? qc.getQueryData<{ id: number; number: string }[]>(["mine"])?.find((i) => i.number === duplicate.number)?.id
    : undefined

  return (
    <div>
      <div className="mb-5">
        <div className="eyebrow">IT help desk</div>
        <h1 className="font-cond text-[28px] font-semibold leading-tight">Report a problem</h1>
        <p className="mt-1 text-sm text-mute">
          Tell us what's wrong in plain words. Priority, category and the right technician are worked out automatically.
        </p>
      </div>

      {assetTag && (
        <div className="mb-5 flex items-center gap-3 rounded-lg border border-signal/40 bg-signal-soft p-3.5">
          <QrCode size={22} className="shrink-0 text-signal" />
          {assetQ.isLoading && <span className="text-sm">Looking up sticker {assetTag}…</span>}
          {asset && (
            <div className="min-w-0 text-sm">
              <div className="font-medium">
                Reported from QR sticker <span className="font-mono">{asset.tag}</span>
              </div>
              <div className="text-ink-2">
                {asset.name} · {asset.location} · {asset.service.name}
              </div>
            </div>
          )}
          {assetQ.error && (
            <span className="text-sm text-breach">
              Sticker <span className="font-mono">{assetTag}</span> isn't in the asset register. Fill in the service yourself.
            </span>
          )}
        </div>
      )}

      <KnownOutages serviceId={serviceId} />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <form onSubmit={handleSubmit} className="space-y-5">
          <div className="panel space-y-4 p-5">
            <div>
              <label className="label" htmlFor="title">What's the problem?</label>
              <input
                id="title"
                className="field text-[15px]"
                maxLength={160}
                placeholder="e.g. Wi-Fi keeps dropping on Gautam 2nd floor"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </div>
            <div>
              <div className="flex items-end justify-between">
                <label className="label" htmlFor="desc">Details</label>
                {Speech && (
                  <button
                    type="button"
                    onClick={toggleVoice}
                    className={
                      "mb-1 inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium " +
                      (listening ? "bg-breach text-white" : "bg-sunk text-ink-2 hover:bg-line")
                    }
                    aria-pressed={listening}
                  >
                    {listening ? <MicOff size={13} /> : <Mic size={13} />}
                    {listening ? "Stop dictation" : "Speak instead"}
                  </button>
                )}
              </div>
              <textarea
                id="desc"
                className="field min-h-[130px] text-[14.5px]"
                maxLength={4000}
                placeholder="When did it start? What do you see? Error messages, room numbers, what you already tried."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
              {listening && (
                <div className="mt-1 flex items-center gap-2 text-xs text-breach">
                  <span className="lamp-blink inline-block h-2 w-2 rounded-full bg-breach" /> Listening (English, India)…
                  <span className="truncate italic text-mute">{interim}</span>
                </div>
              )}
              {voiceUsed && !listening && <div className="mt-1 text-xs text-mute">Dictated by voice. Check the text before sending.</div>}
            </div>
          </div>

          <div className="lg:hidden">
            <Suggestions preview={previewQ.data} loading={previewQ.isFetching} typed={typed} staff={isStaff(user)} />
          </div>

          <div className="panel space-y-4 p-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="svc">
                  Affected service {asset && <Lock size={11} className="ml-1 inline" />}
                </label>
                <select id="svc" className="field" value={serviceId} disabled={!!asset} onChange={(e) => setServiceId(e.target.value)}>
                  <option value="">Choose a service…</option>
                  {grouped.map((g) => (
                    <optgroup key={g.type} label={TYPE_LABEL[g.type]}>
                      {g.items.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name}
                        </option>
                      ))}
                    </optgroup>
                  ))}
                </select>
              </div>
              <div>
                <label className="label" htmlFor="cat">Category</label>
                <select id="cat" className="field" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                  <option value="">Let CampusOps decide</option>
                  {(categoriesQ.data ?? [])
                    .filter((c) => c.active)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                </select>
              </div>
            </div>
            <div>
              <span className="label">Who is affected?</span>
              <Segmented name="Impact" options={IMPACT} value={impact} onChange={setImpact} />
            </div>
            <div>
              <span className="label">How soon does it need fixing?</span>
              <Segmented name="Urgency" options={URGENCY} value={urgency} onChange={setUrgency} />
            </div>
            <div className="grid gap-4 sm:grid-cols-[160px_1fr]">
              <div>
                <label className="label" htmlFor="users">People affected</label>
                <input
                  id="users"
                  className="field font-mono"
                  type="number"
                  min={1}
                  max={100000}
                  value={affectedUsers}
                  onChange={(e) => setAffectedUsers(e.target.value)}
                />
              </div>
              <div>
                <label className="label" htmlFor="loc">
                  Location {asset && <Lock size={11} className="ml-1 inline" />}
                </label>
                <input
                  id="loc"
                  className="field"
                  maxLength={120}
                  disabled={!!asset}
                  placeholder="Building, floor, room"
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                />
              </div>
            </div>
          </div>

          {duplicate && (
            <div className="rounded-lg border border-risk/50 bg-risk/10 p-4">
              <div className="font-medium">You've already reported this</div>
              <div className="mt-1 text-sm text-ink-2">{duplicate.message}</div>
              <div className="mt-3 flex flex-wrap gap-2">
                {dupId && (
                  <Link to={"/incidents/" + dupId} className="btn btn-primary">
                    Open {duplicate.number}
                  </Link>
                )}
                <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => submit(true)}>
                  This is a different problem
                </button>
              </div>
            </div>
          )}
          {error != null && <ErrorBox error={error} />}

          <div className="flex items-center gap-3">
            <button className="btn btn-primary px-6 py-2.5 text-[15px]" disabled={busy}>
              {busy && <Loader2 size={15} className="animate-spin" />} Send report
            </button>
            <span className="text-xs text-mute">You'll get updates by email{user?.telegramLinked ? " and Telegram" : ""}.</span>
          </div>
        </form>

        <aside className="hidden lg:block">
          <div className="sticky top-20">
            <Suggestions preview={previewQ.data} loading={previewQ.isFetching} typed={typed} staff={isStaff(user)} />
          </div>
        </aside>
      </div>
    </div>
  )
}
