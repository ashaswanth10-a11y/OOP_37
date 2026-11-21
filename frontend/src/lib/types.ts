export type Role = "EMPLOYEE" | "TECHNICIAN" | "TEAM_LEAD" | "SYSTEM_ADMIN" | "IT_MANAGER"
export type Priority = "P1" | "P2" | "P3" | "P4"
export type Level = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
export type SlaState = "ON_TRACK" | "AT_RISK" | "BREACHED" | "MET"
export type Health = "OPERATIONAL" | "DEGRADED" | "OUTAGE" | "IMPACTED"
export type ServiceType = "NETWORK" | "INFRASTRUCTURE" | "PLATFORM" | "APPLICATION" | "END_USER"
export type Channel = "WEB" | "QR" | "VOICE" | "TELEGRAM" | "SIMULATION"
export type Status =
  | "NEW"
  | "CLASSIFIED"
  | "ASSIGNED"
  | "ACKNOWLEDGED"
  | "IN_PROGRESS"
  | "AWAITING_INFO"
  | "RESOLVED"
  | "VERIFIED"
  | "CLOSED"
  | "REOPENED"
  | "CANCELLED"

export type Action =
  | "ACKNOWLEDGE"
  | "START"
  | "REQUEST_INFO"
  | "PROVIDE_INFO"
  | "RESOLVE"
  | "VERIFY"
  | "REOPEN"
  | "CLOSE"
  | "CANCEL"
  | "ASSIGN"
  | "TAKE"
  | "CHANGE_PRIORITY"
  | "ADD_NOTE"

export interface User {
  id: number
  code: string
  fullName: string
  email: string
  role: Role
  roles: Role[]
  department: string | null
  location: string | null
  team: string | null
  skills: string[] | null
  maxConcurrent: number | null
  experienceYears: number | null
  available: boolean | null
  activeIncidents: number | null
  telegramLinked: boolean
  emailNotifications: boolean
  telegramNotifications: boolean
  active: boolean
}

export interface Category {
  id: number
  code: string
  name: string
  description: string | null
  keywords: string | null
  color: string | null
  active: boolean
}

export interface ServiceRef {
  id: number
  code: string
  name: string
}

export interface Service {
  id: number
  code: string
  name: string
  description: string | null
  type: ServiceType
  criticality: number
  location: string | null
  userBase: number
  defaultCategory: Category | null
  dependsOn: ServiceRef[]
  mapX: number
  mapY: number
  active: boolean
}

export interface Asset {
  id: number
  tag: string
  name: string
  location: string | null
  service: ServiceRef
  category: Category | null
}

export interface SlaPolicy {
  id: number
  service: ServiceRef | null
  priority: Priority
  responseMinutes: number
  resolutionMinutes: number
}

export interface PersonRef {
  id: number
  code: string
  name: string
}

export interface IncidentSummary {
  id: number
  number: string
  title: string
  status: Status
  priority: Priority | null
  category: Category | null
  service: ServiceRef | null
  reporter: PersonRef | null
  assignee: PersonRef | null
  slaState: SlaState | null
  resolutionDue: string | null
  createdAt: string
  updatedAt: string
  escalationLevel: number
  problemNumber: string | null
  duplicateOf: string | null
  channel: Channel
  location: string | null
  supporters: number
  affectedUsers: number
}

export interface Sla {
  policy: string
  start: string
  responseDue: string
  resolutionDue: string
  respondedAt: string | null
  resolvedAt: string | null
  responseBreached: boolean
  resolutionBreached: boolean
  state: SlaState
  used: number
}

export interface Note {
  id: number
  author: PersonRef | null
  authorRole: string | null
  type: "INVESTIGATION" | "INFO_REQUEST" | "INFO_RESPONSE" | "COMMENT" | "SYSTEM"
  text: string
  internal: boolean
  createdAt: string
}

export interface AuditEntry {
  id: number
  at: string
  actorName: string
  actorCode: string
  action: string
  incidentId: number | null
  incidentNumber: string | null
  previousState: Status | null
  newState: Status | null
  description: string
  priority: Priority | null
  assignee: string | null
  category: string | null
  slaState: SlaState | null
  escalationLevel: number
  slaUsed: number | null
}

export interface Duplicate {
  id: number
  number: string
  title: string
  status: Status
  score: number
  reason: string
}

export interface Fix {
  id: number
  number: string
  title: string
  similarity: number
  resolution: string
  rootCause: string | null
}

export interface Recommendation {
  technician: PersonRef
  team: string | null
  score: number
  reason: string
}

export interface IncidentDetail {
  summary: IncidentSummary
  description: string
  impact: Level
  urgency: Level
  affectedUsers: number
  priorityScore: number
  priorityRationale: string | null
  classifiedBy: string | null
  classificationConfidence: number | null
  assetTag: string | null
  assignmentStrategy: string | null
  escalatedTo: PersonRef | null
  sla: Sla | null
  risk: { level: string; score: number; reason: string } | null
  resolutionSummary: string | null
  rootCause: string | null
  resolutionCode: string | null
  resolvedBy: string | null
  feedbackRating: number | null
  feedbackComment: string | null
  closedAt: string | null
  stateDescription: string | null
  allowedNext: Status[]
  actions: Action[]
  notes: Note[]
  timeline: AuditEntry[]
  similar: Duplicate[]
  suggestedFixes: Fix[]
  recommendations: Recommendation[]
  problem: { id: number; number: string; title: string; status: string } | null
  supportedByMe: boolean
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface Preview {
  category: { id: number; code: string; name: string; confidence: number; reason: string } | null
  similar: { id: number; number: string; title: string; status: Status; score: number; reason: string }[]
  fixes: { number: string; title: string; resolution: string; similarity: number }[]
  aiAvailable: boolean
}

export interface MapNode {
  id: number
  code: string
  name: string
  type: ServiceType
  location: string | null
  criticality: number
  x: number
  y: number
  health: Health
  openIncidents: number
  worstPriority: Priority | null
  rootCause: boolean
  problemNumber: string | null
}

export interface OutageMap {
  nodes: MapNode[]
  edges: { from: number; to: number }[]
  problems: { id: number; number: string; title: string; rootService: string; incidents: number; detectedAt: string }[]
}

export interface Problem {
  id: number
  number: string
  title: string
  analysis: string
  status: "OPEN" | "RESOLVED"
  rootServiceId: number
  rootService: string
  detectedAt: string
  resolvedAt: string | null
  resolutionSummary: string | null
  incidents: IncidentSummary[]
}

export interface PublicStatus {
  overall: "ALL_OPERATIONAL" | "DEGRADED" | "MAJOR_OUTAGE"
  generatedAt: string
  services: { id: number; name: string; type: ServiceType; location: string | null; health: Health }[]
  problems: {
    number: string
    title: string
    rootService: string
    reports: number
    since: string
    rootServiceId: number
    affectedServiceIds: number[]
    followIncidentId: number | null
    followIncidentNumber: string | null
  }[]
  edges: { from: number; to: number }[]
  nodes: MapNode[]
}

export interface Dashboard {
  overview: {
    total: number
    open: number
    criticalOpen: number
    resolved: number
    slaBreaches: number
    breachedOpen: number
    slaCompliance: number
    avgResolutionMinutes: number
    avgRating: number
    escalated: number
    unassigned: number
    problemsOpen: number
  }
  technicians: {
    id: number
    code: string
    name: string
    team: string | null
    active: number
    max: number
    resolved30d: number
    avgRating: number
    available: boolean
  }[]
  services: { id: number; name: string; total: number; open: number; p1: number; breaches: number }[]
  categories: { code: string; name: string; color: string | null; total: number; open: number; avgMinutes: number }[]
  byPriority: Record<string, number>
  byStatus: Record<string, number>
  byChannel: Record<string, number>
  trend: { date: string; created: number; resolved: number }[]
  atRisk: {
    id: number
    number: string
    title: string
    priority: Priority
    assignee: string | null
    level: string
    score: number
    reason: string
    resolutionDue: string | null
  }[]
}

export interface Settings {
  assignmentStrategy: string
  priorityStrategy: string
  slaWarningPercent: number
  duplicateThreshold: number
  correlationWindowMinutes: number
  correlationMinIncidents: number
  autoEscalation: boolean
  aiTriage: boolean
}

export interface AdminSettings {
  settings: Settings & { id: number }
  assignmentStrategies: { key: string; label: string }[]
  priorityStrategies: { key: string; label: string }[]
  escalationChain: string[]
  integrations: {
    claude: boolean
    gemini?: boolean
    geminiModel?: string | null
    telegram: boolean
    liveConnections: number
  }
}

export interface Notification {
  id: number
  title: string
  body: string
  incidentId: number | null
  incidentNumber: string | null
  severity: "INFO" | "SUCCESS" | "WARNING" | "CRITICAL"
  read: boolean
  createdAt: string
}

export interface OutboxMail {
  id: number
  to: string
  subject: string
  body: string
  sentAt: string
}

export interface SlaViolation {
  id: number
  incidentId: number
  incidentNumber: string
  kind: "RESPONSE" | "RESOLUTION"
  priority: Priority
  serviceName: string
  assigneeName: string | null
  dueAt: string
  detectedAt: string
}
