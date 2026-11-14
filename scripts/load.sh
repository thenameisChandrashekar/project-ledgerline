#!/usr/bin/env bash
# Load test: posts N newly generated files (incl. recycled statements) with C concurrent clients.
# Run against a clean database and Redis (docker compose down -v && docker compose up -d), service running.
# Usage: scripts/load.sh [baseUrl] [count] [concurrency] [recycled]
set -euo pipefail
cd "$(dirname "$0")/../backend"
JAR=$(ls target/ledgerline-*.jar 2>/dev/null | head -1 || true)
[ -n "$JAR" ] || { mvn -q -DskipTests package; JAR=$(ls target/ledgerline-*.jar | head -1); }
java -cp "$JAR" -Dloader.main=com.azentio.ledgerline.tools.LoadRunner \
  org.springframework.boot.loader.launch.PropertiesLauncher "${1:-http://localhost:8080}" "${2:-1000}" "${3:-10}" "${4:-50}"
