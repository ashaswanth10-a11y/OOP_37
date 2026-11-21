import { useState, type FormEvent } from "react"
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { ArrowRight, Loader2 } from "lucide-react"
import * as api from "../lib/api"
import { homeFor, useAuth } from "../lib/auth"
import { Logo } from "../components/Shell"
import { LampBoard } from "../components/LampBoard"

const DOMAIN = "@hyderabad.bits-pilani.ac.in"

const DEMO = [
  { who: "aarav", role: "Student", name: "Aarav Mehta" },
  { who: "ravi", role: "Technician", name: "Ravi Kumar" },
  { who: "priya", role: "Team lead", name: "Priya Sharma" },
  { who: "kiran", role: "System admin", name: "Kiran Desai" },
  { who: "anil", role: "IT manager", name: "Dr. Anil Kapoor" },
]

const OVERALL_TEXT = {
  ALL_OPERATIONAL: "All campus services are running normally.",
  DEGRADED: "Some services are degraded right now.",
  MAJOR_OUTAGE: "There is an active outage on campus.",
}

export default function LoginPage() {
  const { user, signIn } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState<string | null>(null)
  const status = useQuery({ queryKey: ["public-status"], queryFn: api.publicStatus, refetchInterval: 30000 })

  const next = params.get("next")
  if (user && !busy) return <Navigate to={next || homeFor(user)} replace />

  const go = async (e: string, p: string, tag: string) => {
    setError("")
    setBusy(tag)
    try {
      const u = await signIn(e.includes("@") ? e : e + DOMAIN, p)
      navigate(next || homeFor(u), { replace: true })
    } catch (err) {
      setError(api.errorMessage(err))
      setBusy(null)
    }
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!email || !password) {
      setError("Enter your campus email and password")
      return
    }
    go(email.trim(), password, "form")
  }

  const s = status.data

  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_minmax(440px,520px)]">
      <section className="order-2 flex flex-col justify-between bg-rail px-6 py-8 text-rail-ink sm:px-10 lg:order-1 lg:py-10">
        <div className="hidden lg:block">
          <Logo light />
        </div>
        <div className="my-10 max-w-xl">
          <div className="font-mono text-[11px] tracking-[0.14em] text-beacon">CAMPUS GRID · LIVE</div>
          <h2 className="mt-3 font-cond text-3xl font-semibold leading-tight text-white sm:text-4xl">
            {s ? OVERALL_TEXT[s.overall] : "Checking campus services…"}
          </h2>
          <p className="mt-3 max-w-md text-[15px] text-rail-ink/80">
            Every square is one IT service, from the hostel core switches to the fee portal. If yours is already red,
            the team is on it.
          </p>
          {s && (
            <div className="mt-6 rounded-lg border border-white/10 bg-rail-2 p-5">
              <LampBoard lamps={s.nodes} size="lg" showLabels />
              {s.problems.length > 0 && (
                <div className="mt-5 border-t border-white/10 pt-4">
                  {s.problems.map((p) => (
                    <div key={p.number} className="flex items-baseline gap-3 text-sm">
                      <span className="whitespace-nowrap font-mono text-[12px] text-[#ff8a80]">{p.number}</span>
                      <span className="text-white">{p.title}</span>
                      <span className="ml-auto whitespace-nowrap font-mono text-[11px] text-rail-ink/60">{p.reports} reports</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
          <Link to="/status" className="mt-5 inline-flex items-center gap-1.5 text-sm font-medium text-beacon hover:underline">
            Check campus status before reporting <ArrowRight size={15} />
          </Link>
        </div>
        <div className="max-w-xl border-t border-white/10 pt-5">
          <p className="text-[13.5px] leading-relaxed text-rail-ink/75">
            IT complaints on campus used to live in Shoutbox posts, CCIT Nucleus WhatsApp groups and word of mouth, with
            no tracking, no priority and no deadline. CampusOps gives every issue a number, an owner and a clock.
          </p>
          <div className="mt-3 font-mono text-[11px] text-rail-ink/50">BITS Pilani, Hyderabad Campus · CCIT, B Block</div>
        </div>
      </section>

      <section className="order-1 flex items-center justify-center bg-card px-6 py-10 sm:px-12 lg:order-2">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <Logo />
          </div>
          <h1 className="font-cond text-[28px] font-semibold leading-tight">Sign in</h1>
          <p className="mt-1 text-sm text-mute">Use your BITS Hyderabad campus account.</p>
          {params.get("expired") && (
            <div className="mt-4 rounded-md bg-risk/10 px-3 py-2 text-sm text-ink-2">Your session expired. Sign in again.</div>
          )}

          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label className="label" htmlFor="email">Campus email</label>
              <input
                id="email"
                className="field"
                type="text"
                autoComplete="username"
                placeholder={"name" + DOMAIN}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div>
              <label className="label" htmlFor="password">Password</label>
              <input
                id="password"
                className="field"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            {error && <div className="text-sm text-breach">{error}</div>}
            <button className="btn btn-primary w-full py-2.5" disabled={!!busy}>
              {busy === "form" && <Loader2 size={15} className="animate-spin" />} Sign in
            </button>
          </form>
          <p className="mt-4 text-sm text-mute">
            New here?{" "}
            <Link to="/register" className="font-medium text-signal hover:underline">
              Create a campus account
            </Link>
          </p>

          <div className="mt-10 border-t border-dashed border-line-strong pt-5">
            <div className="eyebrow">Demo accounts · one per role</div>
            <div className="mt-3 grid grid-cols-1 gap-1.5 sm:grid-cols-2">
              {DEMO.map((d) => (
                <button
                  key={d.who}
                  type="button"
                  disabled={!!busy}
                  onClick={() => go(d.who, "Campus@123", d.who)}
                  className="flex items-center justify-between rounded-md border border-line px-3 py-2 text-left hover:border-signal hover:bg-signal-soft/40"
                >
                  <span>
                    <span className="block text-[13px] font-medium">{d.role}</span>
                    <span className="block text-[11.5px] text-mute">{d.name}</span>
                  </span>
                  {busy === d.who ? <Loader2 size={14} className="animate-spin text-mute" /> : <ArrowRight size={14} className="text-mute" />}
                </button>
              ))}
            </div>
            <p className="mt-2 text-[11.5px] text-mute">Demo only: signs in with the seeded password.</p>
          </div>
        </div>
      </section>
    </div>
  )
}
