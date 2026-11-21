import { useState, type FormEvent } from "react"
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { ArrowRight, Loader2 } from "lucide-react"
import * as api from "../lib/api"
import { homeFor, useAuth } from "../lib/auth"
import { Logo } from "../components/Shell"

const DOMAIN = "@hyderabad.bits-pilani.ac.in"

const DEMO = [
  { who: "aarav", role: "Student", name: "Aarav Mehta" },
  { who: "lakshmi", role: "Faculty", name: "Lakshmi Narayan" },
  { who: "sneha", role: "Employee", name: "Sneha Kulkarni" },
  { who: "ravi", role: "Technician", name: "Ravi Kumar" },
  { who: "priya", role: "Team lead", name: "Priya Sharma" },
  { who: "kiran", role: "System admin", name: "Kiran Desai" },
  { who: "anil", role: "IT manager", name: "Dr. Anil Kapoor" },
]

const OVERALL_TEXT = {
  ALL_OPERATIONAL: "All campus services are running normally.",
  DEGRADED: "Some campus services are running slowly or intermittently.",
  MAJOR_OUTAGE: "A campus IT service is down.",
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
    <main className="mx-auto flex min-h-screen max-w-5xl items-center px-5 py-10 sm:px-8">
      <div className="grid w-full overflow-hidden rounded-xl border border-line bg-card lg:grid-cols-[1fr_1fr]">
        <section className="order-2 flex flex-col justify-between gap-8 border-t border-line bg-sunk p-7 sm:p-10 lg:order-1 lg:border-r lg:border-t-0">
          <div className="hidden lg:block"><Logo /></div>
          <div>
            <h2 className="text-xl font-semibold text-ink sm:text-2xl">Campus IT support</h2>
            <p className="mt-2 max-w-xs text-sm leading-relaxed text-ink-2">
              Get help with hostel Wi-Fi, the fee portal, lab computers and other campus IT services.
            </p>
          </div>
          <div className="border-t border-line pt-4">
            <div className="mb-1 text-sm font-medium">Campus status</div>
            <p className="text-sm text-mute">{s ? OVERALL_TEXT[s.overall] : "See current service notices."}</p>
            <Link to="/status" className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-signal hover:underline">
              View service status <ArrowRight size={15} />
            </Link>
          </div>
        </section>

        <section className="order-1 flex items-center justify-center p-7 sm:p-10 lg:order-2">
          <div className="w-full max-w-sm">
            <div className="mb-6 lg:hidden"><Logo /></div>
            <h1 className="text-[28px] font-semibold leading-tight tracking-tight">Sign in</h1>
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
                Create a student account
              </Link>
            </p>

            <details className="mt-6 border-t border-line pt-4">
              <summary className="cursor-pointer text-sm font-medium text-mute">Try a demo account</summary>
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
            </details>
          </div>
        </section>
      </div>
    </main>
  )
}
