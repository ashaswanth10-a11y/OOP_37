# CampusOps frontend

React 18 + TypeScript + Vite, Tailwind v4, TanStack Query, React Flow (outage map), Recharts.

## Running

Start the Spring Boot backend first (it listens on `:8080`), then:

```
npm install
npm run dev
```

Open http://localhost:5173. The dev server proxies `/api` (including the `/api/stream` server-sent events) to `http://localhost:8080`, so nothing else needs configuring locally.

The login page has one quick-login button per role (student, technician, team lead, admin, IT manager). They all use the seeded password `Campus@123`.

```
npm run build     # tsc -b && vite build, output in dist/
npm run preview   # serve dist/ on :4173 (no proxy, set VITE_API_URL)
```

## Env vars

| Var | Default | What it does |
| --- | --- | --- |
| `VITE_API_URL` | empty (same origin) | Backend origin for a deployed build, e.g. `https://campusops-api.example.com`. No trailing slash. The app calls `${VITE_API_URL}/api/...` and opens the event stream at `${VITE_API_URL}/api/stream?access_token=...`. |

Put it in `.env.local` or pass it at build time: `VITE_API_URL=https://... npm run build`. The backend must allow CORS from the frontend origin when they're on different hosts.

## Gotchas

- QR stickers encode `${window.location.origin}/report?asset=TAG`, so print them from the deployed URL, not from localhost, or phones won't reach them.
- Voice input uses the browser's Web Speech API (`en-IN`). It works in Chrome and Edge; the mic button is hidden where it isn't supported (Firefox).
- Demo time warp moves the server clock. Admins and team leads read the offset from `/api/admin/settings`; other roles estimate it from an open incident's SLA, so their countdowns can be a minute off right after a warp until they open an incident.
- The PWA manifest and a no-op service worker are in `public/`. The worker is only registered in production builds.
