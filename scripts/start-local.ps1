# ============================================================
#  CodeForge AI — Local Development Startup Script
#  Run from the project root: d:\codeforge-ai
# ============================================================

$Root = $PSScriptRoot | Split-Path -Parent

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  CodeForge AI - Local Dev Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ── Step 1: Start Docker infrastructure ─────────────────────
Write-Host "[1/4] Starting Docker infrastructure..." -ForegroundColor Yellow
Push-Location "$Root\docker"
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "WARNING: docker compose exited with errors (see above)." -ForegroundColor Yellow
    Write-Host "         Checking if critical containers are up anyway..." -ForegroundColor Yellow
    $running = docker ps --format "{{.Names}}" 2>$null
    $required = @("codeforge-postgres", "codeforge-minio", "codeforge-redis")
    $missing = $required | Where-Object { $running -notcontains $_ }
    if ($missing.Count -gt 0) {
        Write-Host "ERROR: Required containers not running: $($missing -join ', ')" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Write-Host "  Core containers are up — continuing despite warning." -ForegroundColor Green
}
Pop-Location

# ── Step 2: Wait for services to be healthy ─────────────────
Write-Host ""
Write-Host "[2/4] Waiting for services to become healthy..." -ForegroundColor Yellow

$services = @("codeforge-postgres", "codeforge-minio", "codeforge-redis")
foreach ($svc in $services) {
    Write-Host "  Waiting for $svc..." -NoNewline
    $maxWait = 60  # seconds
    $waited = 0
    do {
        Start-Sleep -Seconds 3
        $waited += 3
        $status = docker inspect --format='{{.State.Health.Status}}' $svc 2>$null
    } while ($status -ne "healthy" -and $waited -lt $maxWait)

    if ($status -eq "healthy") {
        Write-Host " ✓" -ForegroundColor Green
    } else {
        Write-Host " ✗ (may still be starting — check: docker ps)" -ForegroundColor Yellow
    }
}

# ── Step 3: Build Maven project ─────────────────────────────
Write-Host ""
Write-Host "[3/4] Building all Maven modules (skip tests for speed)..." -ForegroundColor Yellow
Push-Location $Root
mvn clean install -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Maven build failed." -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location
Write-Host "  Build complete ✓" -ForegroundColor Green

# ── Step 4: Launch all services in separate windows ─────────
Write-Host ""
Write-Host "[4/4] Starting Spring Boot services in separate windows..." -ForegroundColor Yellow

$services_to_start = @(
    @{ Name = "auth-service";      Dir = "auth-service";      Port = 8081 },
    @{ Name = "project-manager";   Dir = "project-manager";   Port = 8082 },
    @{ Name = "ingestion-service"; Dir = "ingestion-service"; Port = 8083 },
    @{ Name = "api-gateway";       Dir = "api-gateway";       Port = 8085 }
)

foreach ($svc in $services_to_start) {
    $svcDir = Join-Path $Root $svc.Dir
    $title = "CodeForge: $($svc.Name) [:$($svc.Port)]"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", `
        "cd '$svcDir'; Write-Host 'Starting $($svc.Name)...' -ForegroundColor Cyan; mvn spring-boot:run" `
        -WindowStyle Normal
    Write-Host "  Started $($svc.Name) on port $($svc.Port) ✓" -ForegroundColor Green
    Start-Sleep -Seconds 2  # stagger startups to reduce DB connection race
}

# ── Summary ─────────────────────────────────────────────────
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Services starting up!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  API Gateway     → http://localhost:8085" -ForegroundColor White
Write-Host "  Auth Service    → http://localhost:8081" -ForegroundColor White
Write-Host "  Project Manager → http://localhost:8082" -ForegroundColor White
Write-Host "  Ingestion Svc   → http://localhost:8083" -ForegroundColor White
Write-Host ""
Write-Host "  MinIO Console   → http://localhost:9001  (minioadmin / minioadmin123)" -ForegroundColor DarkGray
Write-Host "  Qdrant UI       → http://localhost:6333/dashboard" -ForegroundColor DarkGray
Write-Host "  Grafana         → http://localhost:3000  (admin / admin123)" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Allow ~30-60s for all Spring Boot services to fully start." -ForegroundColor Yellow
Write-Host ""
