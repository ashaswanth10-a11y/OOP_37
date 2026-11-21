# CampusOps demo video script

**File name required by the brief:** `[GroupNo]_OOP_S1_2025-2026_Project` (for example `7_OOP_S1_2025-2026_Project.mp4`; the brief writes the year as 2025-2026, keep it exactly).
**Target length:** 18 minutes (the brief allows 15–20). **Deadline:** upload with the report on or before 22 Nov 2026.
**Speakers:** Member 1 ... Member 5 (replace with names). Every member introduces themselves on camera or by voice and explains at least one module, as the brief requires.

## Before recording (10 minutes, off camera)

1. Start everything with `run.cmd` (Windows) or `./run.sh` (macOS/Linux). Keep the ticket IDs visible as you record.
2. To demonstrate Gemini classification, set `GEMINI_API_KEY` in your local `.env`. The app can also classify reports using local keyword matching.
3. Open four browser profiles (or one normal + three private windows), all at `http://localhost:5173`, and log in (password `Campus@123`, domain `@hyderabad.bits-pilani.ac.in`):
   - A: **aarav** (student, Gautam Bhavan)
   - B: **ravi** (technician, Network Ops) - keep a second tab for **arjun** (Apps & Data)
   - C: **priya** (team lead, Network Ops)
   - D: **kiran** (system admin) - open the Overview dashboard
4. Print or open the QR sticker sheet (`/admin/assets/print`) and keep the phone camera ready.
5. Record at 1080p, browser zoom 110 %, notifications muted. Record each segment separately and join them.

Note on numbers: the seeded campus has INC-1001 to INC-1048. New incidents in the demo start at INC-1049. If you re-seed, numbers stay the same; if you do not, read the number off the screen instead.

## Slides

`docs/slides/CampusOps_Slides.pptx` (PDF copy next to it). Put member names and the group number on slides 1 and 12 and in the footer.

| Segment | Slides |
|---|---|
| 0 Introduction | 1 title, 2 the problem, 3 roles |
| 3 Correlation (optional recap) | 9 root cause, 10 known outage |
| 4 OOP design | 4 architecture, 5 State pattern, 6 design patterns, 7 OOP principles |
| 5 Intelligence | 8 intelligence |
| 8 Tests | 11 testing and coverage |
| 9 Close | 12 thank you |

## Timeline

| # | Time | Segment | Speaker |
|---|------|---------|---------|
| 0 | 0:00-1:30 | Team intro and the problem | All, led by Member 1 |
| 1 | 1:30-4:30 | Workflow 1: report, prioritise, assign, notify | Member 1 (UI) + Member 3 (domain) |
| 2 | 4:30-7:30 | Workflow 2: investigate, SLA, resolve, notify | Member 2 (backend) |
| 3 | 7:30-11:00 | Workflow 3: duplicates, "Me too", correlation, escalation | Member 5 (intelligence) |
| 4 | 11:00-13:30 | OOP design walk-through | Member 3 |
| 5 | 13:30-14:30 | Intelligent features recap and AI integrations | Member 5 |
| 6 | 14:30-15:30 | QR sticker, public status page | Member 2 + Member 1 |
| 7 | 15:30-16:45 | Admin configuration and database | Member 4 |
| 8 | 16:45-17:45 | Tests and CI | Member 5 |
| 9 | 17:45-18:15 | Close | Member 1 |
| + | optional | Fest-load scenario (if time allows, or cut segment 7 short) | Member 4 |

---

## Segment 0 - Introduction (0:00-1:30)

**Show:** title slide or the CampusOps login page, then the public status page `/status`.

- **Member 1:** "We are group [Group No.]. I am [Member 1 name, ID]; I built the user interface."
- **Members 2-5**, one line each: name, ID and module (Backend and APIs; Domain model and OOP; Database and persistence; Testing, DevOps and intelligent features).
- **Member 1 (problem, about 40 s):** "On our campus, IT problems are reported in WhatsApp groups, on the Shoutbox and by word of mouth. There is no ticket number, no owner and no deadline. During Atmos the academic-block internet collapses and fifty people post 'same here'. Nobody can tell whether that is fifty problems or one. CampusOps gives every problem a number, a priority, an SLA clock, an owner and a history, and it notices when many complaints are one root cause."

## Segment 1 - Workflow 1: student reports, system prioritises, technician assigned, notification (1:30-4:30)

**Window A (aarav):**
1. Click **Report a problem**. Choose service **Hostel Wi-Fi: Gautam Bhavan**.
2. Click **Speak instead** and dictate (or type): title *"Wi-Fi keeps dropping on 2nd floor"*, description *"The Wi-Fi in Gautam 2nd floor disconnects every few minutes and video calls fail."* Point out "Dictated by voice".
3. While typing, show the live preview panel on the right: suggested category **Network & Wi-Fi** with confidence and matched words, similar open incidents, and known fixes from past incidents (for example the Gautam "Wi-Fi slow" fix).
4. Set impact **Medium**, urgency **High**, affected users **40**, submit.
- **Member 1:** "The form is the same on a phone; the app can be installed as a PWA."

**Incident page (still aarav):**
5. Show status **ASSIGNED**, the priority with its rationale line ("MEDIUM impact (2) x HIGH urgency (3) x criticality 4 ... = P2"), SLA deadlines, assigned technician (a Network Ops technician), and the lifecycle bar.
- **Member 3:** "Behind this one click, IncidentService created the incident through IncidentFactory, the triage chain picked a category, the WeightedScoreStrategy calculated the priority, CatalogService found the SLA policy, the active assignment strategy picked a technician with the NETWORK skill and the lowest load, and every step was published as an event."

**Window B (the assigned technician, normally ravi):**
6. Show the bell: a new notification "New incident INC-1049 (P2)" arrived live without refreshing (Server-Sent Events).
7. Open **My queue**: the incident is at the top with its SLA countdown.
- **Member 1:** "CampusOps sends live in-app alerts and records copies for email in the admin outbox."

**Window D (kiran):** open **Email outbox** for two seconds to show the email copy.

## Segment 2 - Workflow 2: technician investigates, SLA monitored, resolved, user notified (4:30-7:30)

**Window B (technician):**
1. On INC-1049 click **Acknowledge** (response time stamped, response SLA met), then **Start work**.
2. Add an **internal note**: *"AP on 2F rebooting every 5 min, checking PoE."* Point out it is internal.
3. Click **Ask reporter**: *"Does it happen on mobile data too, or only on Wi-Fi?"* Status becomes AWAITING_INFO.

**Window A (aarav):** the bell shows "We need more information". Open the incident, click **Send the info**: *"Only on Wi-Fi."* Status returns to IN_PROGRESS. Note that aarav cannot see the internal note.

**Window B:** show the **Suggested fixes** panel and the **Recommended technicians** panel (Smart strategy with reasons). Then **Resolve** ("What fixed it?") with summary *"Replaced the failing PoE injector for the 2F access point"*, root cause *"PoE injector failure"*, code FIXED.
- **Member 2:** "The REST controller only validates and delegates. The state object for RESOLVED refuses to enter without a resolution summary, and entering it stops the resolution clock. The SLA monitor runs every 15 seconds in the background and would have warned at 75 % of the window."

**Window A:** notification "INC-1049 resolved". Click **Confirm fix**, rate **5 stars**, comment *"Works now"*. Status VERIFIED.
**Window B:** click **Close**. Status CLOSED.
4. On the incident page drag the **replay slider** from start to end: priority, assignee and SLA state at each moment, built from the audit snapshots.
- **Member 2:** "Every row of this timeline is an AuditRecord written in the same transaction as the change, so the history can never disagree with the data."

## Segment 3 - Workflow 3: many incidents, duplicates and related incidents, correlation and escalation (7:30-11:00)

**Window A (student), then Window D (admin):**
1. As students, submit three reports for the Fee Payment Portal with similar titles describing a failed fee payment. Choose high impact and critical urgency. Confirm a separate report when prompted so the admin can compare related tickets.
2. Open the second and third incidents: show "Possible duplicate of INC-1050" (the first payment incident on a fresh seed; read the number off the screen) with the score and reason ("78% text match, same service, reported 0 min ago").
- **Member 5:** "Duplicate detection uses TF-IDF with synonym folding: unavailable, failing and unable all become the token 'fail', payment and transaction become 'payment'. Score is 0.6 text similarity plus 0.25 for the same service plus 0.15 for recency."

**"Me too" moment, Window A (aarav):**
3. Start a new report on **Fee Payment Portal** and type *"Payment page not opening"*. The preview lists the open payment incident. Click **Me too, I have this problem** instead of submitting.
4. Open that incident: the "said me too" count went up and affected users increased.
- **Member 5:** "Instead of a fourth duplicate, the student confirms the existing one. It is the up-vote idea from SU Connect, but here the vote feeds the priority formula: more affected people can raise the priority automatically, and each person counts once."

**Correlation, Window D:**
5. Use the student report form to submit Wi-Fi outages for the Gautam, Krishna, Vyas and Meera hostel services within the correlation window. Use the affected hostel as the service on each report.
6. Open **Outage map**: Core Switch B is marked as the root cause, the four hostel Wi-Fi services are red. Open **Problems**: "Suspected Core Switch B (Hostels) failure" with the four incidents and the analysis text.
- **Member 5:** "The correlation engine walks the service dependency graph. All four hostels depend on Core Switch B, the captive portal, the Sophos firewall and the uplink. Core Switch B is the most specific shared dependency, so it is the likely root cause. A fifth Wi-Fi report would be attached to the same problem straight away."

**Known outage, Window A (aarav):** open **Report a problem**. A red "Known outage" strip is already at the top. Choose **Hostel Wi-Fi: Ram Bhavan** (nobody has reported Ram yet): the form says "There’s a known outage for this service" because Ram also hangs off Core Switch B. Click **Me too** instead of filing.
- **Member 5:** "We stop duplicates before they are filed. The form knows everything downstream of the failed switch, so even a hostel that hasn't complained yet is told it's a known outage."

**Escalation, Window D:**
7. Open the P1 payment ticket. Show its actual first-response deadline. Once that time passes without acknowledgement, show the recorded violation and escalation in its timeline.
8. For a short recording, prepare the overdue ticket before filming and show its timestamped history.
- **Member 5:** "Escalation follows Technician, Team Lead, System Administrator, IT Manager. An urgent incident climbs one level for every response window it stays unacknowledged, and never skips a level, so everyone in the chain is told."

**Window C (priya, team lead):** open the Core Switch B problem, click **Resolve problem + all linked incidents** with *"Replaced the failed line card in Core Switch B"*. All four linked incidents move to RESOLVED at once and each reporter is notified.

## Segment 4 - OOP design walk-through (11:00-13:30)

**Show:** `docs/report/CampusOps_Report.pdf` section 4 and 5, or the IDE. Keep each item to 15-20 seconds.

- **Member 3:**
  1. Class diagram 1: `User` is abstract; `Student`, `Technician`, `Administrator`; `TeamLead` *is a* `Technician`, `ItManager` *is an* `Administrator`. `UserFactory` is the only place that builds them.
  2. Class diagram 2: `Incident` is the aggregate root, no setters. `SlaTracker`, `Resolution`, `Feedback` are composed into it; `WorkNote`s are owned; `ProblemRecord` aggregates incidents it does not own.
  3. State pattern: open `IncidentStates.java`, show `ResolvedState` (allowed next, validateEntry, onEnter). "NEW to CLOSED is impossible: the state object rejects it with a 409."
  4. Strategy pattern: `AssignmentStrategy` with five implementations and `PriorityStrategy` with two; open **Settings** in the app and switch the assignment strategy to **Load-based** live.
  5. Observer: `IncidentEventPublisher` and the three listeners (audit, notifications, live updates).
  6. Adapter and Factory: `GeminiCategoryClassifier` / `EmailGateway`; `NotificationChannelFactory`.
  7. Exceptions: `CampusOpsException` hierarchy and `GlobalExceptionHandler` returning a uniform JSON error.

## Segment 5 - Intelligent features recap (13:30-14:30)

- **Member 5** on the **Overview** dashboard: SLA compliance, technician workload, service and category analysis, 14-day trend, the "At risk" list from the SLA risk predictor ("Typical fix 37 min, 117 % used, Arjun Rao has 2 active").
- Mention the triage chain: Gemini if enabled, with local keyword matching as the fallback; show the **Integrations** panel on the Settings page. If a Gemini key is configured, show an incident whose classification says "by GEMINI".

## Segment 6 - QR sticker, public status page (14:30-15:30)

- **Member 1 (QR):** scan the sticker for **LAB-F104-PC07** with the phone camera; the report form opens with service **Lab PCs (D+F Block)**, location and category already filled. Show the **Assets & QR** page and the printable sticker sheet.
- **Member 1 (status page):** log out and open `/status`: campus health per service and known problems without login, so students can check before reporting.

## Segment 7 - Admin configuration and database (15:30-16:45)

- **Member 4** (kiran): **Services** (dependency graph editing, criticality), **SLA policies** (campus defaults and the tighter Fee Portal P1 policy), **Categories** (keywords drive classification; add a category live), **Users** (create a technician, set skills and capacity), **Audit log**, **SLA violations**.
- Show the ER diagram from the report and say: H2 locally, PostgreSQL in Docker; the audit table is append-only; incidents use optimistic locking.

## Segment 8 - Tests and CI (16:45-17:45)

- **Member 5:** run `cd backend && ./mvnw test` (or show a finished run): **50 tests, 0 failures**. Show two names: TC01 "NEW cannot jump straight to CLOSED" and TC35 "An unacknowledged P1 breaches its SLA and is escalated up the chain".
- Show `.github/workflows/ci.yml`: backend `mvnw verify` and frontend `npm run build` on every push.
- Show the test table in the report (section 10) and the traceability matrix (section 12).

## Segment 9 - Close (17:45-18:15)

- **Member 1:** "CampusOps covers the full lifecycle from report to close, with SLA monitoring, escalation, duplicate detection and root-cause correlation, built on State, Strategy, Observer, Factory, Adapter and Repository. Next steps: forward IT grievances from SU Connect, campus SSO and real email. Thank you."

## Optional extra - Fest-load scenario (about 45 s)

- As students, report internet failures for Academic Blocks LAN, Library Wi-Fi and the D+F lab network. As Kiran, open the outage map and inspect the shared dependency.
- "This is modelled on the Atmos complaints: one saturated academic uplink, many different-looking reports."

## Fallbacks during recording

- A step shows an error toast: read it aloud, it is part of the exception handling; redo the step.
- The assigned technician is not ravi: log in as whoever is shown on the incident (password is the same).
- The bell did not update: refresh once; SSE reconnects automatically.
