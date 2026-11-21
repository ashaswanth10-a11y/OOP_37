import { useEffect, useState, type ReactNode } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ChevronRight, Loader2 } from "lucide-react"
import * as api from "../../lib/api"
import { isAdmin, useAuth } from "../../lib/auth"
import { useToast } from "../../lib/toast"
import type { Settings } from "../../lib/types"
import { ErrorBox, Loading, PageHeader } from "../../components/ui"

function Row({ label, hint, children }: { label: string; hint: string; children: ReactNode }) {
  return (
    <div className="grid gap-2 px-5 py-4 sm:grid-cols-[1fr_minmax(0,320px)] sm:items-center sm:gap-6">
      <div>
        <div className="font-medium">{label}</div>
        <div className="text-[12.5px] text-mute">{hint}</div>
      </div>
      <div>{children}</div>
    </div>
  )
}

export default function SettingsAdmin() {
  const { user } = useAuth()
  const canEdit = isAdmin(user)
  const qc = useQueryClient()
  const toast = useToast()
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ["admin-settings"], queryFn: api.adminSettings })
  const [form, setForm] = useState<Settings | null>(null)

  useEffect(() => {
    if (data && !form) setForm(data.settings)
  }, [data, form])

  const save = useMutation({
    mutationFn: (s: Settings) => api.updateSettings(s),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin-settings"] })
      toast({ title: "Settings saved", body: "New incidents use them straight away.", tone: "success" })
    },
    onError: (e) => toast({ title: "Couldn't save settings", body: api.errorMessage(e), tone: "critical" }),
  })

  if (isLoading || (data && !form)) return <Loading />
  if (error) return <ErrorBox error={error} retry={refetch} />
  if (!data || !form) return null
  const set = <K extends keyof Settings>(k: K, v: Settings[K]) => setForm({ ...form, [k]: v })

  return (
    <div className="mx-auto max-w-[900px]">
      <PageHeader eyebrow="Administration" title="Settings">
        {canEdit && (
          <button className="btn btn-primary" disabled={save.isPending} onClick={() => save.mutate(form)}>
            {save.isPending && <Loader2 size={14} className="animate-spin" />} Save settings
          </button>
        )}
      </PageHeader>
      {!canEdit && <div className="mb-4 rounded-md bg-sunk px-3 py-2 text-sm text-ink-2">Read-only for team leads. A system admin can change these.</div>}

      <fieldset disabled={!canEdit} className="panel divide-y divide-line">
        <Row label="Assignment strategy" hint="How a new incident picks its technician.">
          <select className="field" value={form.assignmentStrategy} onChange={(e) => set("assignmentStrategy", e.target.value)}>
            {data.assignmentStrategies.map((s) => (
              <option key={s.key} value={s.key}>{s.label}</option>
            ))}
          </select>
        </Row>
        <Row label="Priority strategy" hint="How impact, urgency and reach become P1 to P4.">
          <select className="field" value={form.priorityStrategy} onChange={(e) => set("priorityStrategy", e.target.value)}>
            {data.priorityStrategies.map((s) => (
              <option key={s.key} value={s.key}>{s.label}</option>
            ))}
          </select>
        </Row>
        <Row label="SLA warning at" hint="Warn the technician when this much of the window is used.">
          <div className="flex items-center gap-3">
            <input type="range" min={50} max={95} step={5} className="flex-1" value={form.slaWarningPercent} onChange={(e) => set("slaWarningPercent", Number(e.target.value))} />
            <span className="w-12 text-right font-mono">{form.slaWarningPercent}%</span>
          </div>
        </Row>
        <Row label="Duplicate threshold" hint="Similarity score above which a report is flagged as a duplicate.">
          <div className="flex items-center gap-3">
            <input type="range" min={0.2} max={0.9} step={0.05} className="flex-1" value={form.duplicateThreshold} onChange={(e) => set("duplicateThreshold", Number(e.target.value))} />
            <span className="w-12 text-right font-mono">{form.duplicateThreshold.toFixed(2)}</span>
          </div>
        </Row>
        <Row label="Correlation window" hint="Incidents this close together can be grouped into one problem.">
          <div className="flex items-center gap-2">
            <input type="number" min={5} className="field w-28 font-mono" value={form.correlationWindowMinutes} onChange={(e) => set("correlationWindowMinutes", Number(e.target.value))} />
            <span className="text-sm text-mute">minutes</span>
          </div>
        </Row>
        <Row label="Minimum incidents for a problem" hint="How many related reports before a problem opens.">
          <input type="number" min={2} className="field w-28 font-mono" value={form.correlationMinIncidents} onChange={(e) => set("correlationMinIncidents", Number(e.target.value))} />
        </Row>
        <Row label="Auto escalation" hint="Escalate up the chain when response or resolution targets are missed.">
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.autoEscalation} onChange={(e) => set("autoEscalation", e.target.checked)} /> Enabled
          </label>
        </Row>
        <Row label="AI triage" hint="Classify reports with the configured AI provider (Claude or Gemini), with keywords as the fallback.">
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.aiTriage} onChange={(e) => set("aiTriage", e.target.checked)} /> Enabled
          </label>
          <div className="mt-1 text-xs text-mute">
            {!data.integrations.claude && !data.integrations.gemini
              ? "Keyword classifier (offline): no AI key configured"
              : "Using " +
                [data.integrations.claude && "Claude", data.integrations.gemini && "Gemini"].filter(Boolean).join(" + ") +
                (form.aiTriage ? "" : " when enabled")}
          </div>
        </Row>
      </fieldset>

      <section className="mt-5 grid gap-4 md:grid-cols-2">
        <div className="panel p-5">
          <div className="eyebrow mb-3">Integrations</div>
          {[
            { k: "Claude API", sub: null, on: data.integrations.claude },
            { k: "Google Gemini API", sub: data.integrations.geminiModel, on: !!data.integrations.gemini },
            { k: "Telegram Bot", sub: null, on: data.integrations.telegram },
          ].map((i) => (
            <div key={i.k} className="flex items-center justify-between gap-3 border-b border-line py-2">
              <span className="text-[13.5px]">
                {i.k}
                {i.sub && <span className="block font-mono text-[11px] text-mute">model: {i.sub}</span>}
              </span>
              <span className="flex shrink-0 items-center gap-1.5 whitespace-nowrap text-[12.5px]" style={{ color: i.on ? "var(--ok)" : "var(--mute)" }}>
                <span className={"h-2 w-2 shrink-0 rounded-full " + (i.on ? "bg-ok" : "bg-p4")} />
                {i.on ? "Configured" : "Not configured (add key to .env)"}
              </span>
            </div>
          ))}
          {!data.integrations.claude && !data.integrations.gemini && (
            <div className="border-b border-line py-2 text-[12.5px] text-mute">AI triage: Keyword classifier (offline)</div>
          )}
          <div className="flex items-center justify-between py-2">
            <span className="text-[13.5px]">Live dashboard connections</span>
            <span className="font-mono text-[13px]">{data.integrations.liveConnections}</span>
          </div>
        </div>
        <div className="panel p-5">
          <div className="eyebrow mb-3">Escalation chain</div>
          <div className="flex flex-wrap items-center gap-1.5">
            {data.escalationChain.map((step, k) => (
              <span key={step} className="inline-flex items-center gap-1.5">
                <span className="rounded-md border border-line-strong px-2.5 py-1 text-[13px]">
                  <span className="mr-1.5 font-mono text-[11px] text-mute">L{k}</span>
                  {step}
                </span>
                {k < data.escalationChain.length - 1 && <ChevronRight size={14} className="text-mute" />}
              </span>
            ))}
          </div>
          <p className="mt-3 text-[12.5px] text-mute">
            A missed response or resolution target moves the incident one step up. Each step is notified by email,
            Telegram and in the app.
          </p>
        </div>
      </section>
    </div>
  )
}
