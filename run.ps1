# Starts CampusOps (backend + frontend) with one command on Windows.
# Double-click run.cmd, or run:  powershell -ExecutionPolicy Bypass -File run.ps1
# Needs Java 21+ and Node.js 20+. No Docker or database install needed (it uses a built-in H2 database).

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Stop-WithMessage($text) {
    Write-Host $text -ForegroundColor Red
    Read-Host "Press Enter to close"
    exit 1
}

function Test-Health {
    try {
        (Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 "http://localhost:8080/actuator/health").StatusCode -eq 200
    } catch {
        $false
    }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Stop-WithMessage "Java is not installed. Install Java 21 (for example Temurin 21 from https://adoptium.net), then open a new terminal."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Stop-WithMessage "Node.js is not installed. Install Node.js 20 or newer from https://nodejs.org, then open a new terminal."
}
# java -version prints to stderr, so read it through cmd
$javaLine = cmd /c "java -version 2>&1" | Select-Object -First 1
$javaMajor = [int]([regex]::Match($javaLine, '"(\d+)').Groups[1].Value)
if ($javaMajor -lt 21) { Stop-WithMessage "Found Java $javaMajor. CampusOps needs Java 21 or newer." }
$nodeMajor = [int]((node -v) -replace '^v(\d+).*', '$1')
if ($nodeMajor -lt 20) { Stop-WithMessage "Found Node.js $nodeMajor. CampusOps needs Node.js 20 or newer." }

if (-not (Test-Path "$root\frontend\node_modules")) {
    Write-Host "Installing frontend packages (first run only)..." -ForegroundColor Cyan
    npm --prefix "$root\frontend" ci
    if ($LASTEXITCODE -ne 0) { Stop-WithMessage "npm install failed. Check your internet connection and try again." }
}

$backend = $null
if (Test-Health) {
    Write-Host "Backend already running on http://localhost:8080" -ForegroundColor Green
} else {
    Write-Host "Starting the backend (the first run downloads dependencies and can take a few minutes)." -ForegroundColor Cyan
    Write-Host "Backend log: $root\backend.log"
    $backend = Start-Process -FilePath "$root\backend\mvnw.cmd" -ArgumentList "-q", "spring-boot:run" `
        -WorkingDirectory "$root\backend" -RedirectStandardOutput "$root\backend.log" `
        -RedirectStandardError "$root\backend-error.log" -NoNewWindow -PassThru
    $waited = 0
    while (-not (Test-Health)) {
        if ($backend.HasExited) { Stop-WithMessage "The backend stopped. See backend.log and backend-error.log." }
        if ($waited -ge 900) { Stop-WithMessage "The backend did not start within 15 minutes. See backend.log." }
        Start-Sleep -Seconds 2
        $waited += 2
        Write-Host "." -NoNewline
    }
    Write-Host ""
    Write-Host "Backend is up." -ForegroundColor Green
}

Write-Host "Starting the frontend. The browser opens at http://localhost:5173 . Press Ctrl+C to stop everything." -ForegroundColor Cyan
Write-Host "Demo logins: aarav, ravi, priya, kiran, anil @hyderabad.bits-pilani.ac.in, password Campus@123"
try {
    npm --prefix "$root\frontend" run dev -- --open
} finally {
    if ($backend -and -not $backend.HasExited) {
        Write-Host "Stopping the backend..."
        # mvnw starts java as a child process, so stop the whole tree
        taskkill /PID $backend.Id /T /F | Out-Null
    }
}
