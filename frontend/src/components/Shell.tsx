import { Suspense, useEffect, useState, type ReactNode } from "react"
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom"
import { useMutation, useQuery } from "@tanstack/react-query"
import {
  Activity,
  BookOpen,
  ClipboardList,
  FileClock,
  Gauge,
  Inbox,
  LayoutGrid,
  ListChecks,
  LogOut,
  Mail,
  Menu,
  Moon,
  Network,
  Plus,
  QrCode,
  Search,
  Settings,
  ShieldAlert,
  Sun,
  Timer,
  User as UserIcon,
  Users,
  Wrench,
  X,
} from "lucide-react"
import * as api from "../lib/api"
import { isAdmin, isLead, isTech, shellFor, useAuth } from "../lib/auth"
import { useNow } from "../lib/clock"
import { useLive } from "../lib/live"
import { useTheme } from "../lib/theme"
import { initials } from "../lib/format"
import { Bell } from "./Bell"
import { LampBoard } from "./LampBoard"
import { Loading, Meter } from "./ui"

export function Logo({ light, small }: { light?: boolean; small?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <svg width={small ? 22 : 26} height={small ? 22 : 26} viewBox="0 0 64 64" aria-hidden>
        <rect width="64" height="64" rx="12" fill={light ? "#1c2e3a" : "#0f1b24"} />
        {[12, 26.5, 41].map((y, i) =>
          [12, 26.5, 41].map((x, j) => (
            <rect
              key={i + "-" + j}
              x={x}
              y={y}
              width="11"
              height="11"
              rx="2"
              fill={i === 1 && j === 1 ? "#f2a900" : i === 2 && j === 2 ? "#d02b2b" : "#1e8a4f"}
            />
          )),
        )}
      </svg>
      <span className="leading-none">
        <span className={"block font-cond text-[17px] font-bold tracking-tight " + (light ? "text-white" : "text-ink")}>
          CampusOps
        </span>
        {!small && (
          <span className={"block font-mono text-[9.5px] tracking-[0.14em] " + (light ? "text-rail-ink/70" : "text-mute")}>
            BITS HYD · IT
          </span>
        )}
      </span>
    </span>
  )
}

function ThemeButton({ dark: onDark }: { dark?: boolean }) {
  const [dark, toggle] = useTheme()
  return (
    <button
      onClick={toggle}
      className={
        "flex h-9 w-9 items-center justify-center rounded-md " +
        (onDark ? "text-rail-ink hover:bg-white/10" : "text-ink-2 hover:bg-sunk")
      }
      aria-label={dark ? "Switch to light theme" : "Switch to dark theme"}
      title={dark ? "Light theme" : "Dark theme"}
    >
      {dark ? <Sun size={17} /> : <Moon size={17} />}
    </button>
  )
}

function UserMenu() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  if (!user) return null
  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 rounded-md py-1 pl-1 pr-2 hover:bg-sunk"
        aria-label="Account menu"
      >
        <span className="flex h-7 w-7 items-center justify-center rounded-full bg-ink font-cond text-xs font-semibold text-paper">
          {initials(user.fullName)}
        </span>
        <span className="hidden text-left leading-tight md:block">
          <span className="block text-[13px] font-medium">{user.fullName}</span>
          <span className="block font-mono text-[10.5px] text-mute">{user.code}</span>
        </span>
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="panel absolute right-0 z-40 mt-2 w-52 py-1 shadow-xl">
            <div className="border-b border-line px-3 pb-2 pt-1.5 text-xs text-mute">{user.email}</div>
            <Link to="/profile" onClick={() => setOpen(false)} className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-sunk">
              <UserIcon size={15} /> Profile & notifications
            </Link>
            <button
              onClick={() => {
                signOut()
                navigate("/login")
              }}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-sunk"
            >
              <LogOut size={15} /> Sign out
            </button>
          </div>
        </>
      )}
    </div>
  )
}

export function PortalShell() {
  const items = [
    { to: "/home", label: "Home" },
    { to: "/report", label: "Report a problem" },
    { to: "/incidents", label: "My incidents" },
    { to: "/status", label: "Campus status" },
  ]
  return (
    <div className="min-h-screen">
      <header className="no-print sticky top-0 z-20 border-b border-line bg-card/95 backdrop-blur-sm">
        <div className="mx-auto flex h-14 max-w-5xl items-center gap-6 px-4">
          <Link to="/home">
            <Logo />
          </Link>
          <nav className="hidden flex-1 items-center gap-1 md:flex">
            {items.map((i) => (
              <NavLink
                key={i.to}
                to={i.to}
                className={({ isActive }) =>
                  "rounded-md px-3 py-1.5 text-sm " + (isActive ? "bg-sunk font-medium text-ink" : "text-ink-2 hover:text-ink")
                }
              >
                {i.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1">
            <ThemeButton />
            <Bell />
            <UserMenu />
          </div>
        </div>
        <nav className="flex gap-1 overflow-x-auto border-t border-line px-3 py-1.5 md:hidden">
          {items.map((i) => (
            <NavLink
              key={i.to}
              to={i.to}
              className={({ isActive }) =>
                "whitespace-nowrap rounded-md px-3 py-1 text-[13px] " + (isActive ? "bg-sunk font-medium" : "text-ink-2")
              }
            >
              {i.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6 sm:py-8">
        <Suspense fallback={<Loading />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}

type NavItem = { to: string; label: string; icon: ReactNode; end?: boolean }

function RailLink({ item, onNavigate }: { item: NavItem; onNavigate: () => void }) {
  return (
    <NavLink
      to={item.to}
      end={item.end}
      onClick={onNavigate}
      className={({ isActive }) =>
        "flex items-center gap-2.5 rounded-md px-2.5 py-[7px] text-[13.5px] transition-colors " +
        (isActive ? "bg-white/10 font-medium text-white" : "text-rail-ink/80 hover:bg-white/5 hover:text-white")
      }
    >
      {({ isActive }) => (
        <>
          <span className={isActive ? "text-beacon" : ""}>{item.icon}</span>
          {item.label}
        </>
      )}
    </NavLink>
  )
}

function Annunciator() {
  const live = useLive()
  const now = useNow()
  const navigate = useNavigate()
  const { data } = useQuery({ queryKey: ["outage-map"], queryFn: api.outageMap, refetchInterval: 60000 })
  const nodes = data?.nodes ?? []
  const outages = nodes.filter((n) => n.health === "OUTAGE").length
  const degraded = nodes.filter((n) => n.health === "DEGRADED" || n.health === "IMPACTED").length
  const open = nodes.reduce((s, n) => s + n.openIncidents, 0)
  const clock = new Intl.DateTimeFormat("en-IN", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    timeZone: "Asia/Kolkata",
  }).format(new Date(now))

  return (
    <div className="flex min-w-0 flex-1 items-center gap-5">
      <button onClick={() => navigate("/map")} className="hidden shrink-0 items-center gap-3 xl:flex" title="Open the outage map">
        <span className="eyebrow">Campus grid</span>
        <LampBoard lamps={nodes} />
      </button>
      <div className="hidden items-center gap-4 whitespace-nowrap font-mono text-[12px] md:flex">
        <span style={{ color: outages ? "var(--outage)" : "var(--mute)" }}>
          <b className="text-[13px]">{outages}</b> outage
        </span>
        <span style={{ color: degraded ? "var(--degraded)" : "var(--mute)" }}>
          <b className="text-[13px]">{degraded}</b> degraded
        </span>
        <span className="text-ink-2">
          <b className="text-[13px]">{open}</b> open
        </span>
        {data && data.problems.length > 0 && (
          <Link to="/problems" className="rounded bg-outage px-1.5 py-px font-semibold text-white">
            {data.problems.length} PROBLEM{data.problems.length > 1 ? "S" : ""}
          </Link>
        )}
      </div>
      <div className="ml-auto flex items-center gap-3 font-mono text-[12px]">
        <span className="tnum hidden whitespace-nowrap text-ink-2 sm:inline">{clock} IST</span>
        <span className="flex items-center gap-1.5" title={live ? "Receiving live updates" : "Live updates disconnected"}>
          <span className={"h-2 w-2 rounded-full " + (live ? "bg-ok" : "bg-p4")} />
          <span className={live ? "text-ok" : "text-mute"}>{live ? "LIVE" : "OFFLINE"}</span>
        </span>
      </div>
    </div>
  )
}

function TechPanel() {
  const { user, setUser } = useAuth()
  const tech = !!user && isTech(user)
  const { data: mine } = useQuery({ queryKey: ["assigned"], queryFn: api.assignedIncidents, enabled: tech })
  const toggle = useMutation({ mutationFn: (v: boolean) => api.setAvailability(v), onSuccess: setUser })
  if (!user || !isTech(user)) return null
  const active = (mine ?? []).filter((i) => ["ASSIGNED", "ACKNOWLEDGED", "IN_PROGRESS", "AWAITING_INFO"].includes(i.status)).length
  const max = user.maxConcurrent ?? 5
  const available = !!user.available

  return (
    <div className="mx-3 mb-3 rounded-md bg-rail-2 p-3">
      <div className="flex items-center justify-between">
        <span className="text-[12px] text-rail-ink">{available ? "On shift" : "Off shift"}</span>
        <button
          role="switch"
          aria-checked={available}
          aria-label="Available for new work"
          disabled={toggle.isPending}
          onClick={() => toggle.mutate(!available)}
          className={"relative h-5 w-9 rounded-full transition-colors " + (available ? "bg-ok" : "bg-white/20")}
        >
          <span
            className={"absolute top-0.5 h-4 w-4 rounded-full bg-white transition-[left] " + (available ? "left-[18px]" : "left-0.5")}
          />
        </button>
      </div>
      <div className="mt-3 flex items-baseline justify-between text-[12px] text-rail-ink/80">
        <span>Workload</span>
        <span className="font-mono text-white">
          {active}/{max}
        </span>
      </div>
      <div className="mt-1.5">
        <Meter value={active} max={max} />
      </div>
    </div>
  )
}

export function StaffShell() {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const loc = useLocation()
  const shell = user ? shellFor(user) : "tech"
  const lead = isLead(user)
  const admin = isAdmin(user)

  useEffect(() => setOpen(false), [loc.pathname])

  const work: NavItem[] =
    shell === "tech"
      ? [
          { to: "/queue", label: "My queue", icon: <Inbox size={16} /> },
          { to: "/incidents", label: "All incidents", icon: <Search size={16} /> },
          { to: "/map", label: "Outage map", icon: <Network size={16} /> },
          { to: "/problems", label: "Problems", icon: <ShieldAlert size={16} /> },
          { to: "/report", label: "Log an incident", icon: <Plus size={16} /> },
        ]
      : [
          { to: "/dashboard", label: "Overview", icon: <Gauge size={16} /> },
          { to: "/map", label: "Outage map", icon: <Network size={16} /> },
          { to: "/problems", label: "Problems", icon: <ShieldAlert size={16} /> },
          { to: "/incidents", label: "Incidents", icon: <Search size={16} /> },
          ...(isTech(user) ? [{ to: "/queue", label: "My queue", icon: <Inbox size={16} /> }] : []),
          { to: "/report", label: "Log an incident", icon: <Plus size={16} /> },
        ]

  const adminItems: NavItem[] = admin
    ? [
        { to: "/admin/users", label: "Users", icon: <Users size={16} /> },
        { to: "/admin/services", label: "Services", icon: <LayoutGrid size={16} /> },
        { to: "/admin/categories", label: "Categories", icon: <ListChecks size={16} /> },
        { to: "/admin/sla", label: "SLA policies", icon: <Timer size={16} /> },
        { to: "/admin/assets", label: "Assets & QR", icon: <QrCode size={16} /> },
        { to: "/admin/settings", label: "Settings", icon: <Settings size={16} /> },
        { to: "/admin/violations", label: "SLA violations", icon: <Activity size={16} /> },
        { to: "/admin/audit", label: "Audit log", icon: <FileClock size={16} /> },
        { to: "/admin/outbox", label: "Email outbox", icon: <Mail size={16} /> },
      ]
    : lead
      ? [
          { to: "/admin/violations", label: "SLA violations", icon: <Activity size={16} /> },
          { to: "/admin/audit", label: "Audit log", icon: <FileClock size={16} /> },
          { to: "/admin/settings", label: "Settings", icon: <Settings size={16} /> },
        ]
      : []

  const roleLabel =
    user?.role === "IT_MANAGER"
      ? "IT Manager"
      : user?.role === "SYSTEM_ADMIN"
        ? "System Admin"
        : user?.role === "TEAM_LEAD"
          ? "Team Lead"
          : "Technician"

  const rail = (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between px-4 pb-4 pt-4">
        <Link to="/">
          <Logo light />
        </Link>
        <button className="text-rail-ink lg:hidden" onClick={() => setOpen(false)} aria-label="Close menu">
          <X size={18} />
        </button>
      </div>
      <div className="mx-3 mb-3 flex items-center gap-2 rounded-md border border-white/10 px-2.5 py-1.5">
        {shell === "tech" ? <Wrench size={13} className="text-beacon" /> : <ClipboardList size={13} className="text-beacon" />}
        <span className="font-cond text-[12px] font-semibold uppercase tracking-wider text-rail-ink">
          {shell === "tech" ? "Field desk" : "Control room"}
        </span>
        <span className="ml-auto font-mono text-[10.5px] text-rail-ink/60">{roleLabel}</span>
      </div>
      <TechPanel />
      <nav className="flex-1 space-y-0.5 overflow-y-auto px-3 pb-4">
        {work.map((i) => (
          <RailLink key={i.to} item={i} onNavigate={() => setOpen(false)} />
        ))}
        {adminItems.length > 0 && (
          <>
            <div className="px-2.5 pb-1 pt-5 font-cond text-[10.5px] font-semibold uppercase tracking-[0.12em] text-rail-ink/50">
              {admin ? "Administration" : "Oversight"}
            </div>
            {adminItems.map((i) => (
              <RailLink key={i.to} item={i} onNavigate={() => setOpen(false)} />
            ))}
          </>
        )}
        <div className="px-2.5 pb-1 pt-5 font-cond text-[10.5px] font-semibold uppercase tracking-[0.12em] text-rail-ink/50">
          Public
        </div>
        <RailLink item={{ to: "/status", label: "Status page", icon: <BookOpen size={16} /> }} onNavigate={() => setOpen(false)} />
      </nav>
    </div>
  )

  return (
    <div className="min-h-screen lg:pl-[232px]">
      <aside className="no-print fixed inset-y-0 left-0 z-30 hidden w-[232px] bg-rail lg:block">{rail}</aside>
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/40" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-[260px] bg-rail">{rail}</aside>
        </div>
      )}
      <header className="no-print sticky top-0 z-20 flex h-12 items-center gap-3 border-b border-line bg-card px-3 sm:px-5">
        <button className="text-ink-2 lg:hidden" onClick={() => setOpen(true)} aria-label="Open menu">
          <Menu size={20} />
        </button>
        <span className="lg:hidden">
          <Logo small />
        </span>
        <Annunciator />
        <div className="flex items-center gap-0.5">
          <ThemeButton />
          <Bell />
          <UserMenu />
        </div>
      </header>
      <main className="px-4 py-5 sm:px-6 lg:px-8">
        <Suspense fallback={<Loading />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}
