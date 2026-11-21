import { lazy, Suspense, type ReactNode } from "react"
import { Link, Navigate, Route, Routes, useLocation } from "react-router-dom"
import { homeFor, isAdmin, isLead, isStaff, isTech, shellFor, useAuth } from "./lib/auth"
import { PortalShell, StaffShell } from "./components/Shell"
import { Loading } from "./components/ui"
import type { User } from "./lib/types"
import LoginPage from "./pages/Login"
import RegisterPage from "./pages/Register"
import HomePage from "./pages/Home"
import ReportPage from "./pages/Report"

const StatusPage = lazy(() => import("./pages/Status"))
const IncidentPage = lazy(() => import("./pages/Incident"))
const IncidentsPage = lazy(() => import("./pages/Incidents"))
const QueuePage = lazy(() => import("./pages/Queue"))
const DashboardPage = lazy(() => import("./pages/Dashboard"))
const MapPage = lazy(() => import("./pages/OutageMap"))
const ProblemsPage = lazy(() => import("./pages/Problems").then((m) => ({ default: m.ProblemsPage })))
const ProblemPage = lazy(() => import("./pages/Problems").then((m) => ({ default: m.ProblemPage })))
const ProfilePage = lazy(() => import("./pages/Profile"))
const UsersAdmin = lazy(() => import("./pages/admin/Users"))
const ServicesAdmin = lazy(() => import("./pages/admin/Services"))
const CategoriesAdmin = lazy(() => import("./pages/admin/Categories"))
const SlaAdmin = lazy(() => import("./pages/admin/Sla"))
const AssetsAdmin = lazy(() => import("./pages/admin/Assets").then((m) => ({ default: m.AssetsAdmin })))
const StickerSheet = lazy(() => import("./pages/admin/Assets").then((m) => ({ default: m.StickerSheet })))
const SettingsAdmin = lazy(() => import("./pages/admin/Settings"))
const AuditAdmin = lazy(() => import("./pages/admin/Audit"))
const OutboxAdmin = lazy(() => import("./pages/admin/Outbox"))
const ViolationsAdmin = lazy(() => import("./pages/admin/Violations"))
const DemoAdmin = lazy(() => import("./pages/admin/Demo"))

function Protected() {
  const { user, token, loading } = useAuth()
  const loc = useLocation()
  if (!token) return <Navigate to={"/login?next=" + encodeURIComponent(loc.pathname + loc.search)} replace />
  if (loading || !user) return <Loading label="Signing you in" />
  return shellFor(user) === "portal" ? <PortalShell /> : <StaffShell />
}

function Gate({ allow, children }: { allow: (u: User | null) => boolean; children: ReactNode }) {
  const { user } = useAuth()
  if (!allow(user)) {
    return (
      <div className="panel mx-auto max-w-md p-6 text-center">
        <div className="font-cond text-lg font-semibold">This page isn't part of your role</div>
        <p className="mt-1 text-sm text-mute">Your account doesn't have access here.</p>
        <Link to={user ? homeFor(user) : "/login"} className="btn btn-primary mt-4">
          Go to my home
        </Link>
      </div>
    )
  }
  return <>{children}</>
}

function HomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={user ? homeFor(user) : "/login"} replace />
}

function NotFound() {
  return (
    <div className="mx-auto max-w-md py-24 text-center">
      <div className="font-mono text-sm text-mute">404</div>
      <h1 className="mt-1 font-cond text-2xl font-semibold">There's nothing at this address</h1>
      <Link to="/" className="btn btn-primary mt-5">
        Back to CampusOps
      </Link>
    </div>
  )
}

const staff = (u: User | null) => isStaff(u)
const admin = (u: User | null) => isAdmin(u)
const oversight = (u: User | null) => isAdmin(u) || isLead(u)

export default function App() {
  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/status" element={<StatusPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/admin/assets/print" element={<StickerSheet />} />
        <Route element={<Protected />}>
          <Route index element={<HomeRedirect />} />
          <Route path="home" element={<HomePage />} />
          <Route path="report" element={<ReportPage />} />
          <Route path="incidents" element={<IncidentsPage />} />
          <Route path="incidents/:id" element={<IncidentPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="queue" element={<Gate allow={isTech}><QueuePage /></Gate>} />
          <Route path="dashboard" element={<Gate allow={staff}><DashboardPage /></Gate>} />
          <Route path="map" element={<Gate allow={staff}><MapPage /></Gate>} />
          <Route path="problems" element={<Gate allow={staff}><ProblemsPage /></Gate>} />
          <Route path="problems/:id" element={<Gate allow={staff}><ProblemPage /></Gate>} />
          <Route path="admin/demo" element={<Gate allow={admin}><DemoAdmin /></Gate>} />
          <Route path="admin/users" element={<Gate allow={admin}><UsersAdmin /></Gate>} />
          <Route path="admin/services" element={<Gate allow={admin}><ServicesAdmin /></Gate>} />
          <Route path="admin/categories" element={<Gate allow={admin}><CategoriesAdmin /></Gate>} />
          <Route path="admin/sla" element={<Gate allow={admin}><SlaAdmin /></Gate>} />
          <Route path="admin/assets" element={<Gate allow={admin}><AssetsAdmin /></Gate>} />
          <Route path="admin/settings" element={<Gate allow={oversight}><SettingsAdmin /></Gate>} />
          <Route path="admin/audit" element={<Gate allow={oversight}><AuditAdmin /></Gate>} />
          <Route path="admin/outbox" element={<Gate allow={admin}><OutboxAdmin /></Gate>} />
          <Route path="admin/violations" element={<Gate allow={oversight}><ViolationsAdmin /></Gate>} />
          <Route path="admin" element={<Navigate to="/admin/demo" replace />} />
        </Route>
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  )
}
