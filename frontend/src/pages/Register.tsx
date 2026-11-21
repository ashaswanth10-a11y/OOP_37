import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router-dom"
import { Loader2 } from "lucide-react"
import { errorMessage, ApiError } from "../lib/api"
import { homeFor, useAuth } from "../lib/auth"
import { Logo } from "../components/Shell"

export default function RegisterPage() {
  const { signUp } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [department, setDepartment] = useState("")
  const [location, setLocation] = useState("")
  const [error, setError] = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError("")
    setFieldErrors({})
    if (password.length < 8) {
      setFieldErrors({ password: "Use at least 8 characters" })
      return
    }
    setBusy(true)
    try {
      const u = await signUp({ fullName, email: email.trim(), password, department, location })
      navigate(homeFor(u), { replace: true })
    } catch (err) {
      setError(errorMessage(err))
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors)
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <Link to="/login">
          <Logo />
        </Link>
        <div className="panel mt-6 p-6 sm:p-8">
          <h1 className="font-cond text-[26px] font-semibold">Create your account</h1>
          <p className="mt-1 text-sm text-mute">
            For students and staff with a <span className="font-mono text-[12.5px]">@hyderabad.bits-pilani.ac.in</span> email.
          </p>
          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label className="label" htmlFor="name">Full name</label>
              <input id="name" className="field" required value={fullName} onChange={(e) => setFullName(e.target.value)} />
            </div>
            <div>
              <label className="label" htmlFor="email">Campus email</label>
              <input
                id="email"
                className="field"
                type="email"
                required
                placeholder="f2024xxxx@hyderabad.bits-pilani.ac.in"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
              {fieldErrors.email && <div className="mt-1 text-xs text-breach">{fieldErrors.email}</div>}
            </div>
            <div>
              <label className="label" htmlFor="pw">Password</label>
              <input id="pw" className="field" type="password" required value={password} onChange={(e) => setPassword(e.target.value)} />
              {fieldErrors.password && <div className="mt-1 text-xs text-breach">{fieldErrors.password}</div>}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="dept">Department</label>
                <input id="dept" className="field" placeholder="Computer Science" value={department} onChange={(e) => setDepartment(e.target.value)} />
              </div>
              <div>
                <label className="label" htmlFor="loc">Room / location</label>
                <input id="loc" className="field" placeholder="Vyas Bhawan 312" value={location} onChange={(e) => setLocation(e.target.value)} />
              </div>
            </div>
            {error && <div className="text-sm text-breach">{error}</div>}
            <button className="btn btn-primary w-full py-2.5" disabled={busy}>
              {busy && <Loader2 size={15} className="animate-spin" />} Create account
            </button>
          </form>
        </div>
        <p className="mt-4 text-center text-sm text-mute">
          Already registered?{" "}
          <Link to="/login" className="font-medium text-signal hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
