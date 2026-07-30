#!/usr/bin/env bash
# ============================================================
# CodeForge AI — Cloud Deployment Script for Linux / VPS
# Usage: ./scripts/deploy.sh
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "\033[36m========================================\033[0m"
echo -e "\033[36m  CodeForge AI — Production Deployment  \033[0m"
echo -e "\033[36m========================================\033[0m"

# 1. Check for .env file
if [ ! -f "$ROOT_DIR/.env" ]; then
  echo -e "\033[33m[1/4] .env file not found. Copying from .env.example...\033[0m"
  cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"
  echo -e "\033[31mPlease edit $ROOT_DIR/.env and add your actual API keys, then re-run this script.\033[0m"
  exit 1
else
  echo -e "\033[32m[1/4] .env file found ✓\033[0m"
fi

# 2. Build & start containers
echo -e "\033[33m[2/4] Building and launching production containers...\033[0m"
cd "$ROOT_DIR/docker"
docker compose -f docker-compose.prod.yml --env-file "$ROOT_DIR/.env" up -d --build

# 3. Create MinIO bucket
echo -e "\033[33m[3/4] Ensuring MinIO bucket 'codeforge-repos' exists...\033[0m"
sleep 5
docker exec codeforge-minio-prod sh -c \
  "mc alias set local http://localhost:9000 minioadmin minioadmin123 --quiet 2>/dev/null; mc mb --ignore-existing local/codeforge-repos" || true

# 4. Status summary
echo -e "\033[32m[4/4] Deployment complete! ✓\033[0m"
echo -e "\033[36m========================================\033[0m"
echo -e "API Gateway running at: http://<your-server-ip>:8085"
echo -e "Check running containers: docker ps"
echo -e "\033[36m========================================\033[0m"
