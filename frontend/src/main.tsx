import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { BrowserRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import "@xyflow/react/dist/style.css"
import "./index.css"
import App from "./App.tsx"
import { AuthProvider } from "./lib/auth"
import { ToastProvider } from "./lib/toast"
import { LiveProvider } from "./lib/live"
import { applyStoredTheme } from "./lib/theme"
import { syncServerClock } from "./lib/clock"
import { ApiError } from "./lib/api"
import ServerBanner from "./components/ServerBanner"

applyStoredTheme()
syncServerClock()

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15000,
      refetchOnWindowFocus: false,
      retry: (count, err) => !(err instanceof ApiError && err.status >= 400 && err.status < 500) && count < 2,
    },
  },
})

if ("serviceWorker" in navigator && import.meta.env.PROD) {
  navigator.serviceWorker.register("/sw.js").catch(() => {})
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <ServerBanner />
        <ToastProvider>
          <AuthProvider>
            <LiveProvider>
              <App />
            </LiveProvider>
          </AuthProvider>
        </ToastProvider>
      </QueryClientProvider>
    </BrowserRouter>
  </StrictMode>,
)
