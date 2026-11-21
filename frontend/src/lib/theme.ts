import { useEffect, useState } from "react"

const KEY = "campusops.theme"

export function applyStoredTheme() {
  let dark = false
  try {
    dark = localStorage.getItem(KEY) === "dark"
  } catch {
    dark = false
  }
  document.documentElement.classList.toggle("dark", dark)
}

export function useTheme() {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains("dark"))
  useEffect(() => {
    document.documentElement.classList.toggle("dark", dark)
    try {
      localStorage.setItem(KEY, dark ? "dark" : "light")
    } catch {
      // private mode, theme just won't persist
    }
  }, [dark])
  return [dark, () => setDark((d) => !d)] as const
}
