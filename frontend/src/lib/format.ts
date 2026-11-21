import { serverNow } from "./clock"
import type { Health, Priority, SlaState, Status } from "./types"

export const PRIORITY_LABEL: Record<Priority, string> = {
  P1: "Critical",
  P2: "High",
  P3: "Medium",
  P4: "Low",
}

export const PRIORITY_VAR: Record<Priority, string> = {
  P1: "var(--p1)",
  P2: "var(--p2)",
  P3: "var(--p3)",
  P4: "var(--p4)",
}

export const SLA_LABEL: Record<SlaState, string> = {
  ON_TRACK: "On track",
  AT_RISK: "At risk",
  BREACHED: "Breached",
  MET: "Met",
}

export const SLA_VAR: Record<SlaState, string> = {
  ON_TRACK: "var(--ok)",
  AT_RISK: "var(--risk)",
  BREACHED: "var(--breach)",
  MET: "var(--met)",
}

export const HEALTH_LABEL: Record<Health, string> = {
  OPERATIONAL: "Operational",
  DEGRADED: "Degraded",
  OUTAGE: "Outage",
  IMPACTED: "Impacted",
}

export const HEALTH_VAR: Record<Health, string> = {
  OPERATIONAL: "var(--operational)",
  DEGRADED: "var(--degraded)",
  OUTAGE: "var(--outage)",
  IMPACTED: "var(--impacted)",
}

export const STATUS_LABEL: Record<Status, string> = {
  NEW: "New",
  CLASSIFIED: "Classified",
  ASSIGNED: "Assigned",
  ACKNOWLEDGED: "Acknowledged",
  IN_PROGRESS: "In progress",
  AWAITING_INFO: "Awaiting your info",
  RESOLVED: "Resolved",
  VERIFIED: "Verified",
  CLOSED: "Closed",
  REOPENED: "Reopened",
  CANCELLED: "Cancelled",
}

export const ALL_STATUSES = Object.keys(STATUS_LABEL) as Status[]
export const OPEN_STATUSES: Status[] = [
  "NEW",
  "CLASSIFIED",
  "ASSIGNED",
  "ACKNOWLEDGED",
  "IN_PROGRESS",
  "AWAITING_INFO",
  "REOPENED",
]

export const TYPE_LABEL: Record<string, string> = {
  NETWORK: "Network",
  INFRASTRUCTURE: "Infrastructure",
  PLATFORM: "Platform",
  APPLICATION: "Applications",
  END_USER: "End-user devices",
}

export const CHANNEL_LABEL: Record<string, string> = {
  WEB: "Web",
  QR: "QR sticker",
  VOICE: "Voice",
  TELEGRAM: "Telegram",
  SIMULATION: "Demo scenario",
}

export function statusTone(s: Status) {
  if (s === "CANCELLED" || s === "CLOSED") return "var(--mute)"
  if (s === "RESOLVED" || s === "VERIFIED") return "var(--ok)"
  if (s === "AWAITING_INFO" || s === "REOPENED") return "var(--risk)"
  if (s === "IN_PROGRESS" || s === "ACKNOWLEDGED") return "var(--signal)"
  return "var(--ink-2)"
}

const dateFmt = new Intl.DateTimeFormat("en-IN", {
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
  timeZone: "Asia/Kolkata",
})

const timeFmt = new Intl.DateTimeFormat("en-IN", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
  timeZone: "Asia/Kolkata",
})

export function fmtDate(iso: string | null | undefined) {
  if (!iso) return "-"
  return dateFmt.format(new Date(iso))
}

export function fmtTime(iso: string | null | undefined) {
  if (!iso) return "-"
  return timeFmt.format(new Date(iso))
}

export function ago(iso: string, now = serverNow()) {
  const mins = Math.round((now - new Date(iso).getTime()) / 60000)
  if (mins < 1) return "just now"
  if (mins < 60) return mins + " min ago"
  const h = Math.round(mins / 60)
  if (h < 24) return h + " h ago"
  return Math.round(h / 24) + " d ago"
}

export function minutesLabel(mins: number) {
  if (!mins) return "-"
  if (mins < 60) return Math.round(mins) + " min"
  const h = mins / 60
  if (h < 24) return h.toFixed(1) + " h"
  return (h / 24).toFixed(1) + " d"
}

export function countdown(ms: number) {
  const neg = ms < 0
  let s = Math.floor(Math.abs(ms) / 1000)
  const d = Math.floor(s / 86400)
  s -= d * 86400
  const h = Math.floor(s / 3600)
  s -= h * 3600
  const m = Math.floor(s / 60)
  s -= m * 60
  const pad = (n: number) => String(n).padStart(2, "0")
  const body = d > 0 ? d + "d " + pad(h) + ":" + pad(m) + ":" + pad(s) : pad(h) + ":" + pad(m) + ":" + pad(s)
  return (neg ? "-" : "") + body
}

export function initials(name: string) {
  return name
    .replace(/^Dr\.\s*/, "")
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase()
}
