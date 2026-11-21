import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { Loader2, Send } from "lucide-react"
import * as api from "../lib/api"
import { isTech, useAuth } from "../lib/auth"
import { useToast } from "../lib/toast"
import { ErrorBox, PageHeader } from "../components/ui"

function Toggle({ on, onChange, disabled, label }: { on: boolean; onChange: (v: boolean) => void; disabled?: boolean; label: string }) {
  return (
    <button
      role="switch"
      aria-checked={on}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!on)}
      className={"relative h-6 w-11 shrink-0 rounded-full transition-colors " + (on ? "bg-signal" : "bg-line-strong")}
    >
      <span className={"absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-[left] " + (on ? "left-[22px]" : "left-0.5")} />
    </button>
  )
}

export default function ProfilePage() {
  const { user, setUser } = useAuth()
  const toast = useToast()
  const [code, setCode] = useState<string | null>(null)

  const prefs = useMutation({
    mutationFn: (p: { email: boolean; telegram: boolean }) => api.setPreferences(p.email, p.telegram),
    onSuccess: (u) => {
      setUser(u)
      toast({ title: "Notification preferences saved", tone: "success" })
    },
  })
  const tg = useMutation({ mutationFn: api.telegramCode, onSuccess: (r) => setCode(r.code) })
  const avail = useMutation({ mutationFn: api.setAvailability, onSuccess: setUser })

  if (!user) return null

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader eyebrow={user.code} title="Profile" />

      <section className="panel p-5">
        <div className="font-cond text-[18px] font-semibold">{user.fullName}</div>
        <div className="text-sm text-mute">{user.email}</div>
        <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 text-[13.5px]">
          <div>
            <dt className="text-[11.5px] text-mute">Role</dt>
            <dd>{user.roles.map((r) => r.replace("_", " ").toLowerCase()).join(", ")}</dd>
          </div>
          {user.department && (
            <div>
              <dt className="text-[11.5px] text-mute">Department</dt>
              <dd>{user.department}</dd>
            </div>
          )}
          {user.location && (
            <div>
              <dt className="text-[11.5px] text-mute">Location</dt>
              <dd>{user.location}</dd>
            </div>
          )}
          {user.team && (
            <div>
              <dt className="text-[11.5px] text-mute">Team</dt>
              <dd>{user.team}</dd>
            </div>
          )}
          {user.skills && user.skills.length > 0 && (
            <div>
              <dt className="text-[11.5px] text-mute">Skills</dt>
              <dd className="font-mono text-[12px]">{user.skills.join(", ")}</dd>
            </div>
          )}
          {user.maxConcurrent != null && (
            <div>
              <dt className="text-[11.5px] text-mute">Max concurrent</dt>
              <dd className="font-mono">{user.maxConcurrent}</dd>
            </div>
          )}
        </dl>
      </section>

      {isTech(user) && (
        <section className="panel mt-4 flex items-center justify-between gap-4 p-5">
          <div>
            <div className="font-medium">Available for new work</div>
            <div className="text-sm text-mute">When off, auto-assignment skips you. Your current incidents stay with you.</div>
          </div>
          <Toggle label="Available" on={!!user.available} disabled={avail.isPending} onChange={(v) => avail.mutate(v)} />
        </section>
      )}

      <section className="panel mt-4 divide-y divide-line">
        <div className="px-5 py-3 font-cond text-[16px] font-semibold">Notifications</div>
        <div className="flex items-center justify-between gap-4 px-5 py-4">
          <div>
            <div className="font-medium">Email</div>
            <div className="text-sm text-mute">Status changes, info requests and fixes, sent to {user.email}.</div>
          </div>
          <Toggle
            label="Email notifications"
            on={user.emailNotifications}
            disabled={prefs.isPending}
            onChange={(v) => prefs.mutate({ email: v, telegram: user.telegramNotifications })}
          />
        </div>
        <div className="flex items-center justify-between gap-4 px-5 py-4">
          <div>
            <div className="font-medium">Telegram</div>
            <div className="text-sm text-mute">
              {user.telegramLinked ? "Linked. You'll get messages from the CampusOps bot." : "Link your Telegram account below first."}
            </div>
          </div>
          <Toggle
            label="Telegram notifications"
            on={user.telegramNotifications}
            disabled={prefs.isPending}
            onChange={(v) => prefs.mutate({ email: user.emailNotifications, telegram: v })}
          />
        </div>
        <div className="px-5 py-4">
          <div className="font-medium">{user.telegramLinked ? "Link a different Telegram account" : "Link Telegram"}</div>
          <div className="text-sm text-mute">You can also report problems by chatting to the bot.</div>
          {code ? (
            <div className="mt-3 rounded-md bg-sunk p-4">
              <div className="text-sm">Send this message in a private chat with the CampusOps bot on Telegram.</div>
              <div className="mt-2 flex items-center gap-3">
                <code className="rounded-md bg-card px-3 py-2 font-mono text-[18px] font-semibold tracking-wider">/start {code}</code>
                <button
                  className="text-sm text-signal hover:underline"
                  onClick={() => navigator.clipboard?.writeText("/start " + code).then(() => toast({ title: "Copied", tone: "info" }))}
                >
                  Copy
                </button>
              </div>
              <div className="mt-2 text-xs text-mute">The code works once. Generate a new one if it expires.</div>
            </div>
          ) : (
            <button className="btn btn-ghost mt-3" onClick={() => tg.mutate()} disabled={tg.isPending}>
              {tg.isPending ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />} Get my link code
            </button>
          )}
          {tg.error && <div className="mt-3"><ErrorBox error={tg.error} /></div>}
          {prefs.error && <div className="mt-3"><ErrorBox error={prefs.error} /></div>}
        </div>
      </section>
    </div>
  )
}
