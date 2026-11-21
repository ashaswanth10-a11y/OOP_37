import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react"
import { useQueryClient } from "@tanstack/react-query"
import * as api from "./api"
import type { Role, User } from "./types"

type AuthState = {
  user: User | null
  token: string | null
  loading: boolean
  signIn: (email: string, password: string) => Promise<User>
  signUp: (body: Parameters<typeof api.register>[0]) => Promise<User>
  signOut: () => void
  setUser: (u: User) => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [token, setTokenState] = useState(api.getToken)
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(!!token)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    let retry: ReturnType<typeof setTimeout> | undefined
    const load = () =>
      api
        .me()
        .then((u) => {
          if (cancelled) return
          setUser(u)
          setLoading(false)
        })
        .catch((err) => {
          if (cancelled) return
          // server asleep or restarting: keep the session and try again
          if (err instanceof api.ApiError && err.code === "OFFLINE") {
            retry = setTimeout(load, 3000)
            return
          }
          api.setToken(null)
          setTokenState(null)
          setLoading(false)
        })
    load()
    return () => {
      cancelled = true
      clearTimeout(retry)
    }
  }, [token])

  const signIn = useCallback(async (email: string, password: string) => {
    const res = await api.login(email, password)
    qc.clear()
    api.setToken(res.token)
    setUser(res.user)
    setTokenState(res.token)
    return res.user
  }, [qc])

  const signUp = useCallback(async (body: Parameters<typeof api.register>[0]) => {
    const res = await api.register(body)
    qc.clear()
    api.setToken(res.token)
    setUser(res.user)
    setTokenState(res.token)
    return res.user
  }, [qc])

  const signOut = useCallback(() => {
    api.setToken(null)
    setTokenState(null)
    setUser(null)
    qc.clear()
  }, [qc])

  return (
    <AuthContext.Provider value={{ user, token, loading, signIn, signUp, signOut, setUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error("useAuth outside AuthProvider")
  return ctx
}

export function hasRole(user: User | null, role: Role) {
  return !!user && (user.roles.includes(role) || user.role === role)
}

export type Shell = "portal" | "tech" | "control"

export function shellFor(user: User): Shell {
  if (isCampusReporter(user.role)) return "portal"
  if (user.role === "TECHNICIAN") return "tech"
  return "control"
}

export function homeFor(user: User) {
  const shell = shellFor(user)
  if (shell === "portal") return "/home"
  if (shell === "tech") return "/queue"
  return "/dashboard"
}

export const isCampusReporter = (role: Role) => role === "STUDENT" || role === "FACULTY" || role === "EMPLOYEE"
export const isStaff = (u: User | null) => !!u && !isCampusReporter(u.role)
export const isTech = (u: User | null) => hasRole(u, "TECHNICIAN")
export const isLead = (u: User | null) => hasRole(u, "TEAM_LEAD")
export const isAdmin = (u: User | null) => hasRole(u, "SYSTEM_ADMIN")
