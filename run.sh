#!/usr/bin/env bash
# Starts CampusOps (backend + frontend) with one command on macOS / Linux:  ./run.sh
# Needs Java 21+ and Node.js 20+. No Docker or database install needed (it uses a built-in H2 database).
set -euo pipefail
cd "$(dirname "$0")"

fail() { echo "$1" >&2; exit 1; }
healthy() { curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; }

command -v java >/dev/null || fail "Java is not installed. Install Java 21 (for example Temurin 21 from https://adoptium.net)."
command -v node >/dev/null || fail "Node.js is not installed. Install Node.js 20 or newer from https://nodejs.org."
command -v curl >/dev/null || fail "curl is needed to check when the backend is up."
java_major=$(java -version 2>&1 | head -1 | sed -E 's/[^"]*"([0-9]+).*/\1/')
[ "$java_major" -ge 21 ] || fail "Found Java $java_major. CampusOps needs Java 21 or newer."
node_major=$(node -v | sed -E 's/^v([0-9]+).*/\1/')
[ "$node_major" -ge 20 ] || fail "Found Node.js $node_major. CampusOps needs Node.js 20 or newer."

if [ ! -d frontend/node_modules ]; then
  echo "Installing frontend packages (first run only)..."
  npm --prefix frontend ci
fi

backend_pid=""
cleanup() {
  if [ -n "$backend_pid" ]; then
    echo "Stopping the backend..."
    # the backend runs in its own process group, so this also stops the java child
    kill -- "-$backend_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if healthy; then
  echo "Backend already running on http://localhost:8080"
else
  echo "Starting the backend (the first run downloads dependencies and can take a few minutes). Log: backend.log"
  chmod +x backend/mvnw
  set -m
  (cd backend && exec ./mvnw -q spring-boot:run) > backend.log 2>&1 &
  backend_pid=$!
  set +m
  waited=0
  until healthy; do
    kill -0 "$backend_pid" 2>/dev/null || fail "The backend stopped. See backend.log."
    [ "$waited" -lt 900 ] || fail "The backend did not start within 15 minutes. See backend.log."
    sleep 2; waited=$((waited + 2)); printf "."
  done
  echo; echo "Backend is up."
fi

echo "Starting the frontend. The browser opens at http://localhost:5173 . Press Ctrl+C to stop everything."
echo "Demo logins: aarav, ravi, priya, kiran, anil @hyderabad.bits-pilani.ac.in, password Campus@123"
npm --prefix frontend run dev -- --open
