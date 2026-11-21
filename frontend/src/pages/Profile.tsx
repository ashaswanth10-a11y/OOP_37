import { useMutation } from "@tanstack/react-query"
import * as api from "../lib/api"
import { isTech, useAuth } from "../lib/auth"
import { ROLE_LABEL } from "../lib/format"
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

  const prefs = useMutation({
    mutationFn: api.setPreferences,
    onSuccess: (u) => {
      setUser(u)
      toast({ title: "Notification preferences saved", tone: "success" })
    },
  })
  const avail = useMutation({ mutationFn: api.setAvailability, onSuccess: setUser })

  if (!user) return null

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title="Profile" />

      <section className="panel p-5">
        <div className="font-cond text-[18px] font-semibold">{user.fullName}</div>
        <div className="text-sm text-mute">{user.email}</div>
        <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 text-[13.5px]">
          <div>
            <dt className="text-[11.5px] text-mute">Role</dt>
            <dd>{ROLE_LABEL[user.role]}</dd>
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
            <div className="text-sm text-mute">Copies of ticket updates in the email outbox. You can always see updates in CampusOps.</div>
          </div>
          <Toggle
            label="Email notifications"
            on={user.emailNotifications}
            disabled={prefs.isPending}
            onChange={(v) => prefs.mutate(v)}
          />
        </div>
        {prefs.error && <div className="px-5 py-3"><ErrorBox error={prefs.error} /></div>}
      </section>
    </div>
  )
}
