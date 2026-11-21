import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Loader2, Pencil, Plus } from "lucide-react"
import * as api from "../../lib/api"
import { isCampusReporter } from "../../lib/auth"
import { ROLE_LABEL } from "../../lib/format"
import { useToast } from "../../lib/toast"
import type { Role, User } from "../../lib/types"
import { Dialog, ErrorBox, Loading, PageHeader } from "../../components/ui"

const ROLES: { v: Role; label: string }[] = [
  { v: "STUDENT", label: "Student" },
  { v: "FACULTY", label: "Faculty" },
  { v: "EMPLOYEE", label: "Employee" },
  { v: "TECHNICIAN", label: "Technician" },
  { v: "TEAM_LEAD", label: "Team lead" },
  { v: "SYSTEM_ADMIN", label: "System admin" },
  { v: "IT_MANAGER", label: "IT manager" },
]

const SKILLS = ["NETWORK", "HARDWARE", "SOFTWARE", "DATABASE", "CLOUD", "SECURITY", "AUTH", "APPLICATION", "STORAGE", "PRINTING"]

function SkillPicker({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {SKILLS.map((s) => {
        const on = value.includes(s)
        return (
          <button
            key={s}
            type="button"
            onClick={() => onChange(on ? value.filter((x) => x !== s) : [...value, s])}
            className={"rounded border px-2 py-0.5 font-mono text-[11.5px] " + (on ? "border-signal bg-signal text-white" : "border-line-strong text-ink-2")}
          >
            {s}
          </button>
        )
      })}
    </div>
  )
}

function CreateDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [role, setRole] = useState<Role>("STUDENT")
  const [fullName, setFullName] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [department, setDepartment] = useState("")
  const [location, setLocation] = useState("")
  const [team, setTeam] = useState("")
  const [skills, setSkills] = useState<string[]>([])
  const [max, setMax] = useState("4")
  const [exp, setExp] = useState("2")
  const tech = role === "TECHNICIAN" || role === "TEAM_LEAD"

  const m = useMutation({
    mutationFn: () =>
      api.createUser({
        role,
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        department: isCampusReporter(role) ? department || null : null,
        location: isCampusReporter(role) ? location || null : null,
        team: tech ? team || null : null,
        skills: tech ? skills : null,
        maxConcurrent: tech ? Number(max) : null,
        experienceYears: tech ? Number(exp) : null,
      }),
    onSuccess: (u) => {
      qc.invalidateQueries({ queryKey: ["users"] })
      qc.invalidateQueries({ queryKey: ["technicians"] })
      toast({ title: u.fullName + " added as " + ROLE_LABEL[u.role], tone: "success" })
      onClose()
    },
  })

  return (
    <Dialog title="Add a user" onClose={onClose} wide>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          m.mutate()
        }}
      >
        <div>
          <label className="label" htmlFor="u-role">Account type</label>
          <select id="u-role" className="field" value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.map((r) => (
              <option key={r.v} value={r.v}>{r.label}</option>
            ))}
          </select>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="u-name">Full name</label>
            <input id="u-name" className="field" required value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="u-email">Campus email</label>
            <input id="u-email" className="field" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="u-pw">Initial password</label>
            <input id="u-pw" className="field" type="password" minLength={8} required value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          {isCampusReporter(role) && (
            <>
              <div>
                <label className="label" htmlFor="u-dept">Department</label>
                <input id="u-dept" className="field" value={department} onChange={(e) => setDepartment(e.target.value)} />
              </div>
              <div>
                <label className="label" htmlFor="u-loc">Location</label>
                <input id="u-loc" className="field" value={location} onChange={(e) => setLocation(e.target.value)} />
              </div>
            </>
          )}
          {tech && (
            <>
              <div>
                <label className="label" htmlFor="u-team">Team</label>
                <input id="u-team" className="field" placeholder="Network Ops" value={team} onChange={(e) => setTeam(e.target.value)} />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label" htmlFor="u-max">Max concurrent</label>
                  <input id="u-max" type="number" min={1} className="field font-mono" value={max} onChange={(e) => setMax(e.target.value)} />
                </div>
                <div>
                  <label className="label" htmlFor="u-exp">Years exp.</label>
                  <input id="u-exp" type="number" min={0} className="field font-mono" value={exp} onChange={(e) => setExp(e.target.value)} />
                </div>
              </div>
            </>
          )}
        </div>
        {tech && (
          <div>
            <label className="label">Skills</label>
            <SkillPicker value={skills} onChange={setSkills} />
          </div>
        )}
        {m.error && <ErrorBox error={m.error} />}
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Back</button>
          <button className="btn btn-primary" disabled={m.isPending}>
            {m.isPending && <Loader2 size={14} className="animate-spin" />} Add user
          </button>
        </div>
      </form>
    </Dialog>
  )
}

function TechDialog({ u, onClose }: { u: User; onClose: () => void }) {
  const qc = useQueryClient()
  const [team, setTeam] = useState(u.team ?? "")
  const [skills, setSkills] = useState<string[]>(u.skills ?? [])
  const [max, setMax] = useState(String(u.maxConcurrent ?? 4))
  const [exp, setExp] = useState(String(u.experienceYears ?? 0))
  const [available, setAvailable] = useState(!!u.available)
  const m = useMutation({
    mutationFn: () => api.updateTechnician(u.id, { team, skills, maxConcurrent: Number(max), experienceYears: Number(exp), available }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["users"] })
      qc.invalidateQueries({ queryKey: ["technicians"] })
      onClose()
    },
  })
  return (
    <Dialog title={"Edit " + u.fullName} onClose={onClose}>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          m.mutate()
        }}
      >
        <div>
          <label className="label" htmlFor="t-team">Team</label>
          <input id="t-team" className="field" value={team} onChange={(e) => setTeam(e.target.value)} />
        </div>
        <div>
          <label className="label">Skills</label>
          <SkillPicker value={skills} onChange={setSkills} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label" htmlFor="t-max">Max concurrent</label>
            <input id="t-max" type="number" min={1} className="field font-mono" value={max} onChange={(e) => setMax(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="t-exp">Years experience</label>
            <input id="t-exp" type="number" min={0} className="field font-mono" value={exp} onChange={(e) => setExp(e.target.value)} />
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={available} onChange={(e) => setAvailable(e.target.checked)} /> Available for new work
        </label>
        {m.error && <ErrorBox error={m.error} />}
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Back</button>
          <button className="btn btn-primary" disabled={m.isPending}>
            {m.isPending && <Loader2 size={14} className="animate-spin" />} Save
          </button>
        </div>
      </form>
    </Dialog>
  )
}

export default function UsersAdmin() {
  const qc = useQueryClient()
  const toast = useToast()
  const [filter, setFilter] = useState<Role | "ALL">("ALL")
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ["users"], queryFn: api.users })
  const techs = useQuery({ queryKey: ["technicians"], queryFn: api.technicians })
  const loadOf = new Map((techs.data ?? []).map((t) => [t.id, t.activeIncidents]))

  const active = useMutation({
    mutationFn: (p: { id: number; active: boolean }) => api.setUserActive(p.id, p.active),
    onSuccess: (u) => {
      qc.invalidateQueries({ queryKey: ["users"] })
      toast({ title: u.fullName + (u.active ? " reactivated" : " deactivated"), tone: u.active ? "success" : "warning" })
    },
    onError: (e) => toast({ title: "Couldn't change that account", body: api.errorMessage(e), tone: "critical" }),
  })

  const list = (data ?? []).filter((u) => filter === "ALL" || u.role === filter)

  return (
    <div className="mx-auto max-w-[1300px]">
      <PageHeader eyebrow="Administration" title="Users">
        <button className="btn btn-primary" onClick={() => setCreating(true)}>
          <Plus size={15} /> Add user
        </button>
      </PageHeader>

      <div className="mb-3 flex flex-wrap gap-1.5">
        {[{ v: "ALL" as const, label: "Everyone" }, ...ROLES].map((r) => (
          <button
            key={r.v}
            onClick={() => setFilter(r.v)}
            className={"rounded-full border px-3 py-0.5 text-[12.5px] " + (filter === r.v ? "border-ink bg-ink text-paper" : "border-line-strong text-ink-2")}
          >
            {r.label}
            <span className="ml-1.5 font-mono text-[11px] opacity-70">
              {r.v === "ALL" ? data?.length ?? 0 : (data ?? []).filter((u) => u.role === r.v).length}
            </span>
          </button>
        ))}
      </div>

      {isLoading && <Loading />}
      {error && <ErrorBox error={error} retry={refetch} />}
      {data && (
        <div className="panel overflow-x-auto">
          <table className="w-full min-w-[900px] text-left text-[13px]">
            <thead className="border-b border-line bg-sunk/60">
              <tr>
                <th className="eyebrow px-4 py-2 font-semibold">User</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Role</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Team / department</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Skills</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Load</th>
                <th className="eyebrow py-2 pr-3 font-semibold">Account</th>
                <th className="py-2 pr-4" />
              </tr>
            </thead>
            <tbody>
              {list.map((u) => {
                const tech = u.roles.includes("TECHNICIAN")
                return (
                  <tr key={u.id} className={"border-b border-line last:border-0 " + (u.active ? "" : "opacity-55")}>
                    <td className="px-4 py-2">
                      <div className="font-medium">
                        {u.fullName} <span className="font-mono text-[11px] text-mute">{u.code}</span>
                      </div>
                      <div className="text-[12px] text-mute">{u.email}</div>
                    </td>
                    <td className="py-2 pr-3 text-ink-2">{ROLES.find((r) => r.v === u.role)?.label}</td>
                    <td className="py-2 pr-3 text-ink-2">{u.team ?? u.department ?? "-"}</td>
                    <td className="py-2 pr-3 font-mono text-[11.5px] text-ink-2">{u.skills?.join(" ") ?? ""}</td>
                    <td className="py-2 pr-3 font-mono text-[12px]">
                      {tech ? (
                        <span className="inline-flex items-center gap-1.5">
                          <span className={"h-2 w-2 rounded-full " + (u.available ? "bg-ok" : "bg-p4")} />
                          {loadOf.get(u.id) ?? "?"}/{u.maxConcurrent} · {u.experienceYears}y
                        </span>
                      ) : (
                        ""
                      )}
                    </td>
                    <td className="py-2 pr-3">
                      <button
                        disabled={active.isPending}
                        onClick={() => active.mutate({ id: u.id, active: !u.active })}
                        className={"rounded-full px-2 py-0.5 text-[11.5px] font-medium " + (u.active ? "bg-ok/15 text-ok hover:bg-breach/15 hover:text-breach" : "bg-sunk text-mute hover:bg-ok/15 hover:text-ok")}
                        title={u.active ? "Deactivate" : "Reactivate"}
                      >
                        {u.active ? "Active" : "Deactivated"}
                      </button>
                    </td>
                    <td className="py-2 pr-4 text-right">
                      {tech && (
                        <button className="btn btn-ghost px-2 py-1 text-[12px]" onClick={() => setEditing(u)}>
                          <Pencil size={12} /> Technician
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {creating && <CreateDialog onClose={() => setCreating(false)} />}
      {editing && <TechDialog u={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
