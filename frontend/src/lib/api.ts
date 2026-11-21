import type {
  AdminSettings,
  Asset,
  AuditEntry,
  Category,
  Channel,
  Dashboard,
  IncidentDetail,
  IncidentSummary,
  Level,
  Notification,
  OutageMap,
  OutboxMail,
  Page,
  Preview,
  Priority,
  Problem,
  PublicStatus,
  Role,
  Service,
  ServiceRef,
  ServiceType,
  Settings,
  SlaPolicy,
  SlaViolation,
  User,
} from "./types"

import { markServerDown, markServerUp } from "./serverStatus"

export const API_BASE =(import.meta.env.VITE_API_URL ?? "").replace(/\/$/, "")
const TOKEN_KEY = "campusops.token"

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number
  code: string
  fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.Authorization = "Bearer " + token
  if (body !== undefined) headers["Content-Type"] = "application/json"

  let res: Response
  try {
    res = await fetch(API_BASE + "/api" + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    markServerDown()
    throw new ApiError(0, "OFFLINE", "Can't reach the CampusOps server. Check your connection and try again.")
  }

  if (res.status === 401 && token) {
    setToken(null)
    if (!location.pathname.startsWith("/login")) {
      location.href = "/login?expired=1"
    }
  }

  const text = await res.text()
  let data
  try {
    data = text ? JSON.parse(text) : undefined
  } catch {
    data = undefined
  }
  // a 5xx without our JSON error body comes from the proxy or host, not from CampusOps: the server is down
  if (res.status >= 500 && !data?.code) {
    markServerDown()
    throw new ApiError(res.status, "OFFLINE", "The CampusOps server is starting up. Try again in a moment.")
  }
  markServerUp()
  if (!res.ok) {
    throw new ApiError(
      res.status,
      data?.code ?? "ERROR",
      data?.message ?? "Something went wrong (" + res.status + ")",
      data?.fieldErrors ?? {},
    )
  }
  return data as T
}

const get = <T>(path: string) => request<T>("GET", path)
const post = <T>(path: string, body?: unknown) => request<T>("POST", path, body)
const put = <T>(path: string, body?: unknown) => request<T>("PUT", path, body)
const patch = <T>(path: string) => request<T>("PATCH", path)

export function errorMessage(err: unknown) {
  if (err instanceof ApiError) {
    const fields = Object.entries(err.fieldErrors)
    if (fields.length > 0) return err.message + ": " + fields.map(([k, v]) => k + " " + v).join(", ")
    return err.message
  }
  if (err instanceof Error) return err.message
  return "Something went wrong"
}

// auth
export type AuthResponse = { token: string; user: User }

export const login = (email: string, password: string) => post<AuthResponse>("/auth/login", { email, password })

export const register = (body: {
  fullName: string
  email: string
  password: string
  department: string
  location: string
}) => post<AuthResponse>("/auth/register", body)

export const me = () => get<User>("/auth/me")

// incidents
export type ReportBody = {
  title: string
  description: string
  serviceId: number | null
  assetTag: string | null
  categoryId: number | null
  impact: Level
  urgency: Level
  affectedUsers: number
  location: string | null
  channel: Channel
  confirmedNew: boolean
}

export const reportIncident = (body: ReportBody) => post<IncidentDetail>("/incidents", body)

export const previewIncident = (body: {
  title: string
  description: string
  serviceId: number | null
  assetTag: string | null
}) => post<Preview>("/incidents/preview", body)

export const getIncident = (id: number | string) => get<IncidentDetail>("/incidents/" + id)
export const myIncidents = () => get<IncidentSummary[]>("/incidents/mine")
export const assignedIncidents = () => get<IncidentSummary[]>("/incidents/assigned")

export type SearchParams = {
  q?: string
  status?: string[]
  priority?: string[]
  categoryId?: string
  serviceId?: string
  assigneeId?: string
  slaState?: string
  from?: string
  to?: string
  escalated?: boolean
  openOnly?: boolean
  unassigned?: boolean
  problemId?: string
  page?: number
  size?: number
  sort?: string
  dir?: string
}

export function searchIncidents(p: SearchParams) {
  let qs = ""
  if (p.q) qs += "q=" + encodeURIComponent(p.q) + "&"
  for (const s of p.status ?? []) qs += "status=" + s + "&"
  for (const s of p.priority ?? []) qs += "priority=" + s + "&"
  if (p.categoryId) qs += "categoryId=" + p.categoryId + "&"
  if (p.serviceId) qs += "serviceId=" + p.serviceId + "&"
  if (p.assigneeId) qs += "assigneeId=" + p.assigneeId + "&"
  if (p.slaState) qs += "slaState=" + p.slaState + "&"
  if (p.from) qs += "from=" + p.from + "&"
  if (p.to) qs += "to=" + p.to + "&"
  if (p.escalated) qs += "escalated=true&"
  if (p.openOnly) qs += "openOnly=true&"
  if (p.unassigned) qs += "unassigned=true&"
  if (p.problemId) qs += "problemId=" + p.problemId + "&"
  qs += "page=" + (p.page ?? 0) + "&size=" + (p.size ?? 20)
  qs += "&sort=" + (p.sort ?? "createdAt") + "&dir=" + (p.dir ?? "desc")
  return get<Page<IncidentSummary>>("/incidents?" + qs)
}

const act = (id: number, action: string, body?: unknown) =>
  post<IncidentDetail>("/incidents/" + id + "/" + action, body)

export const acknowledge = (id: number) => act(id, "acknowledge")
export const startWork = (id: number) => act(id, "start")
export const requestInfo = (id: number, text: string) => act(id, "request-info", { text, internal: false })
export const provideInfo = (id: number, text: string) => act(id, "provide-info", { text, internal: false })
export const addNote = (id: number, text: string, internal: boolean) => act(id, "notes", { text, internal })
export const resolveIncident = (id: number, summary: string, rootCause: string, code: string) =>
  act(id, "resolve", { summary, rootCause: rootCause || null, code: code || null })
export const verifyIncident = (id: number, rating: number, comment: string) =>
  act(id, "verify", { rating, comment: comment || null })
export const reopenIncident = (id: number, text: string) => act(id, "reopen", { text, internal: false })
export const closeIncident = (id: number) => act(id, "close")
export const cancelIncident = (id: number, text: string) =>
  act(id, "cancel", text.trim() ? { text, internal: false } : undefined)
export const assignIncident = (id: number, technicianId: number, override: boolean) =>
  act(id, "assign", { technicianId, override })
export const takeIncident = (id: number) => act(id, "take")
export const meToo = (id: number) =>
  post<{ summary: IncidentSummary; supporters: number; affectedUsers: number }>("/incidents/" + id + "/me-too")
export const changePriority = (id: number, body: { impact: Level; urgency: Level; affectedUsers: number; reason: string }) =>
  act(id, "priority", body)

// catalogue
export const categories = () => get<Category[]>("/categories")
export type CategoryBody = { code: string; name: string; description: string; keywords: string; color: string; active: boolean }
export const createCategory = (b: CategoryBody) => post<Category>("/categories", b)
export const updateCategory = (id: number, b: CategoryBody) => put<Category>("/categories/" + id, b)

export const services = () => get<Service[]>("/services")
export const blastRadius = (id: number) => get<ServiceRef[]>("/services/" + id + "/blast-radius")
export type ServiceBody = {
  code: string
  name: string
  description: string
  type: ServiceType
  criticality: number
  location: string
  userBase: number
  defaultCategoryId: number | null
  dependsOn: number[]
  mapX: number
  mapY: number
  active: boolean
}
export const createService = (b: ServiceBody) => post<Service>("/services", b)
export const updateService = (id: number, b: ServiceBody) => put<Service>("/services/" + id, b)

export const assets = () => get<Asset[]>("/assets")
export const asset = (tag: string) => get<Asset>("/assets/" + encodeURIComponent(tag))
export const createAsset = (b: { tag: string; name: string; location: string; serviceId: number; categoryId: number | null }) =>
  post<Asset>("/assets", b)

export const slaPolicies = () => get<SlaPolicy[]>("/sla-policies")
export const upsertPolicy = (b: { serviceId: number | null; priority: Priority; responseMinutes: number; resolutionMinutes: number }) =>
  put<SlaPolicy>("/sla-policies", b)
export const deletePolicy = (id: number) => request<void>("DELETE", "/sla-policies/" + id)

// users
export const users = () => get<User[]>("/users")
export const technicians = () => get<User[]>("/users/technicians")
export const createUser = (b: {
  role: Role
  fullName: string
  email: string
  password: string
  department: string | null
  location: string | null
  team: string | null
  skills: string[] | null
  maxConcurrent: number | null
  experienceYears: number | null
}) => post<User>("/users", b)
export const updateTechnician = (
  id: number,
  b: { team: string; skills: string[]; maxConcurrent: number; experienceYears: number; available: boolean },
) => put<User>("/users/" + id + "/technician", b)
export const setUserActive = (id: number, active: boolean) => patch<User>("/users/" + id + "/active?active=" + active)
export const setAvailability = (available: boolean) => patch<User>("/users/me/availability?available=" + available)
export const setPreferences = (emailNotifications: boolean) =>
  put<User>("/users/me/preferences", { emailNotifications })

// problems + map
export const problems = () => get<Problem[]>("/problems")
export const problem = (id: number | string) => get<Problem>("/problems/" + id)
export const resolveProblem = (id: number, summary: string) =>
  post<{ problem: Problem; incidentsResolved: number }>("/problems/" + id + "/resolve", { summary })
export const outageMap = () => get<OutageMap>("/outage-map")
export const publicStatus = () => get<PublicStatus>("/public/status")

// reports + admin
export const dashboard = () => get<Dashboard>("/reports/dashboard")
export const adminSettings = () => get<AdminSettings>("/admin/settings")
export const updateSettings = (s: Settings) => put<Settings>("/admin/settings", s)
export const slaViolations = () => get<SlaViolation[]>("/admin/sla-violations")
export const auditLog = (page: number, size: number) => get<Page<AuditEntry>>("/audit?page=" + page + "&size=" + size)

// notifications
export const notifications = () => get<{ items: Notification[]; unread: number }>("/notifications")
export const markRead = (id: number) => post<void>("/notifications/" + id + "/read")
export const markAllRead = () => post<void>("/notifications/read-all")
export const outbox = () => get<OutboxMail[]>("/notifications/outbox")
