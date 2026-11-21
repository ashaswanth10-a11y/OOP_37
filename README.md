# CampusOps

Intelligent IT Service Management and Incident Resolution for BITS Pilani, Hyderabad Campus.
CS F213 Object-Oriented Programming project, Semester I 2026-2027, group [Group No.].

CampusOps takes an IT incident through **report, classify, prioritise, assign, investigate, resolve, verify and close**, with SLA
clocks, automatic escalation, duplicate detection, "Me too" confirmations and root-cause correlation on the campus service
dependency graph. Students report from the web (with voice input or by scanning a QR sticker) or from Telegram; technicians work
a live queue; team leads and admins see dashboards, an outage map and the full audit trail.

## Feature highlights

- **Role-specific apps** for Employee/Student, Technician, Team Lead, System Administrator and IT Manager, plus a public `/status` page.
- **Lifecycle as a State pattern**: NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, AWAITING_INFO, RESOLVED, VERIFIED, CLOSED, REOPENED, CANCELLED. Illegal moves (NEW to CLOSED) are rejected with 409.
- **Priority strategies** (switchable at runtime): weighted score `impact x urgency x criticality x reach` (default) or an ITIL rule matrix.
- **Five assignment strategies**: skill-based (default), load-based, round robin, experience-based, smart (weighted).
- **SLA management**: campus-default and per-service policies, warnings at 75 %, breach records, escalation Technician, Team Lead, System Administrator, IT Manager.
- **Intelligence**: category suggestion (Anthropic Claude, then Google Gemini, then an offline keyword classifier), TF-IDF duplicate detection, "Me too" crowd confirmation that can raise priority, smart technician recommendation, SLA-risk prediction, root-cause correlation (for example Core Switch B behind four hostel Wi-Fi outages), suggested fixes from past resolutions.
- **Notifications** over in-app (live via Server-Sent Events), simulated email outbox and Telegram.
- **Audit trail** with a replay slider, dashboards, outage map (React Flow), search with 11 filters, QR sticker sheet, demo control panel (time warp and scenarios), PWA.

## Architecture

```
Browser (React + TypeScript PWA) --HTTPS/JWT--> nginx (prod) / Vite proxy (dev)
                                                   |
                                    Spring Boot 4 modular monolith (Java 21)
   controllers -> services -> domain (Incident + state objects, SlaTracker) + strategies
                       |                    |
            IncidentEventPublisher --> AuditListener | NotificationListener | RealtimeListener (SSE)
                       |
  adapters: Claude (Anthropic SDK), Google Gemini (REST), Telegram Bot API, EmailGateway (simulated)
                       |
             Spring Data JPA --> H2 file DB (local) / PostgreSQL 16 (Docker)
```

Full diagrams (use case, five class diagrams, state, sequence, component, ER) are in [`docs/uml`](docs/uml).

## Quick start

Requirements: Java 21, Node 22 (or just Docker).

```bash
# backend on http://localhost:8080 (H2 file database in backend/data/, seeded on first start)
cd backend && ./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# frontend on http://localhost:5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

Docker (PostgreSQL + backend + nginx frontend on http://localhost:3000):

```bash
docker compose up --build
```

To start again from a clean demo campus, stop the backend and delete `backend/data/`.

- API documentation: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Demo accounts

Password `Campus@123` for everyone, emails are `<login>@hyderabad.bits-pilani.ac.in`.

| Role | Logins |
|------|--------|
| Employee / student / faculty | aarav, diya, kabir, ananya, rohan, lakshmi, sneha |
| Technician | ravi, imran (Network Ops); arjun, meghana (Apps & Data); suresh, farah (Desktop Support) |
| Team lead | priya (Network Ops), vikram (Apps & Data) |
| System administrator | kiran |
| IT manager | anil |

Demo controls (admin, `/admin/demo`): scenarios **Core Switch B outage**, **Payment portal down**, **Fest traffic surge (Atmos)**, and time warp (+5, +15, +60 minutes) to show SLA breaches and escalation on camera.

## Configuration

Copy `.env.example` to `.env` in the repo root (never commit it). Everything is optional for a local demo.

| Variable | Purpose |
|----------|---------|
| `GEMINI_API_KEY` | Google Gemini API key (free tier, Google AI Studio): enables AI category suggestion. |
| `GEMINI_MODEL` | Gemini model, default `gemini-3.5-flash-lite` (commented out in `.env.example`). |
| `ANTHROPIC_API_KEY` | Claude API key: tried before Gemini when set. |
| `CAMPUSOPS_AI_MODEL` | Claude model, default `claude-opus-5-5`. |
| `TELEGRAM_BOT_TOKEN` | Token from @BotFather: enables the Telegram bot and channel. |
| `JWT_SECRET` | Token signing key, at least 32 characters (commented out in `.env.example`; a development default is used locally). Set it for any shared deployment. |
| `ALLOWED_DOMAIN`, `CORS_ORIGINS`, `DEMO_ENABLED`, `SEED_ENABLED`, `PORT` | Registration domain, allowed origins, demo controls, seeding, port. |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection, used with `SPRING_PROFILES_ACTIVE=postgres`. |

Without any AI key the keyword classifier is used; without a Telegram token the bot is simply off.

## Tests

```bash
cd backend && ./mvnw test
```

43 JUnit 5 tests (TC01-TC42 plus a context-load test): unit tests for the state machine, SLA tracker, priority and assignment
strategies, text intelligence and AI adapter fallback; integration tests through the REST API for the three demo workflows,
security, SLA escalation (including an idempotent scheduled sweep), "Me too" and the fest-load correlation. CI (`.github/workflows/ci.yml`) runs `./mvnw verify` and the
frontend build on every push.

## Project structure

```
backend/                 Spring Boot app, package in.bits.campusops
  user/ auth/            user hierarchy, UserFactory, JWT security
  incident/              Incident aggregate, State pattern, IncidentPolicy, service, search
  catalog/               services + dependency graph, categories, assets, SLA policies
  priority/ assignment/  Strategy pattern implementations
  sla/                   SlaMonitor, EscalationService, SlaViolation
  intelligence/ ai/      TF-IDF similarity, SLA risk, triage chain, Claude + Gemini adapters
  problem/               CorrelationEngine, ProblemRecord, outage map, public status
  events/ audit/ notification/ realtime/ telegram/   Observer, channels, SSE, Telegram bot
  report/ settings/ demo/ seed/ common/              dashboards, runtime settings, demo, seed data, exceptions
frontend/                React + TypeScript + Vite PWA
deploy/                  nginx config and frontend Dockerfile
docs/                    report, UML, video script
docker-compose.yml       PostgreSQL + backend + frontend
```

## Documentation

- Project report (PDF): [`docs/report/CampusOps_Report.pdf`](docs/report/CampusOps_Report.pdf) (source: `docs/report/report.html`)
- UML diagrams (PlantUML sources, SVG, PNG): [`docs/uml`](docs/uml)
- Demo video script: [`docs/VIDEO_SCRIPT.md`](docs/VIDEO_SCRIPT.md)

## Team

| Member | Area |
|--------|------|
| [Member 1 name, ID] | UI / UX |
| [Member 2 name, ID] | Backend / APIs |
| [Member 3 name, ID] | Domain model / OOP |
| [Member 4 name, ID] | Database / persistence |
| [Member 5 name, ID] | Testing / DevOps / intelligent features |
