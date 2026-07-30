# ============================================================
#  CodeForge AI — Stop All Local Services
# ============================================================

$Root = $PSScriptRoot | Split-Path -Parent

Write-Host ""
Write-Host "[1/2] Stopping Docker infrastructure..." -ForegroundColor Yellow
Push-Location "$Root\docker"
docker compose down
Pop-Location

Write-Host "[2/2] Killing any Spring Boot processes on ports 8081-8085..." -ForegroundColor Yellow
$ports = @(8081, 8082, 8083, 8085)
foreach ($port in $ports) {
    $pid = (netstat -ano | Select-String ":$port " | Where-Object { $_ -match "LISTENING" } | ForEach-Object { ($_ -split "\s+")[-1] }) | Select-Object -First 1
    if ($pid) {
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        Write-Host "  Stopped process on port $port (PID: $pid)" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "All services stopped." -ForegroundColor Cyan
Write-Host ""
