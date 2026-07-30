# ============================================================
#  CodeForge AI — MinIO Bucket Setup Script
#  Run AFTER docker compose is up and MinIO is healthy
#  Requires: mc (MinIO Client) installed, OR uses Docker exec
# ============================================================

Write-Host ""
Write-Host "Setting up MinIO bucket 'codeforge-repos'..." -ForegroundColor Yellow

# Try using mc (MinIO Client) if installed
if (Get-Command mc -ErrorAction SilentlyContinue) {
    mc alias set local http://localhost:9000 minioadmin minioadmin123 --quiet
    mc mb --ignore-existing local/codeforge-repos
    Write-Host "  Bucket 'codeforge-repos' created via mc ✓" -ForegroundColor Green
} else {
    # Fallback: use docker exec with mc inside the minio container
    Write-Host "  'mc' not found locally — using Docker exec fallback..." -ForegroundColor DarkGray
    docker exec codeforge-minio sh -c `
        "mc alias set local http://localhost:9000 minioadmin minioadmin123 --quiet 2>/dev/null; mc mb --ignore-existing local/codeforge-repos"
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  Bucket 'codeforge-repos' created via docker exec ✓" -ForegroundColor Green
    } else {
        Write-Host "  Could not create bucket automatically." -ForegroundColor Yellow
        Write-Host "  → Open http://localhost:9001, login with minioadmin/minioadmin123" -ForegroundColor Yellow
        Write-Host "    and manually create a bucket named 'codeforge-repos'" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "MinIO setup complete." -ForegroundColor Cyan
Write-Host "  Console: http://localhost:9001" -ForegroundColor White
Write-Host "  Access Key: minioadmin" -ForegroundColor White
Write-Host "  Secret Key: minioadmin123" -ForegroundColor White
Write-Host ""
