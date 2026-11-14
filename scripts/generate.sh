#!/usr/bin/env bash
# Generate synthetic loan files + labels.csv sidecar. Usage: scripts/generate.sh [count] [outDir] [seed]
set -euo pipefail
cd "$(dirname "$0")/../backend"
JAR=$(ls target/ledgerline-*.jar 2>/dev/null | head -1 || true)
[ -n "$JAR" ] || { mvn -q -DskipTests package; JAR=$(ls target/ledgerline-*.jar | head -1); }
java -cp "$JAR" -Dloader.main=com.azentio.ledgerline.tools.GenerateCli \
  org.springframework.boot.loader.launch.PropertiesLauncher "${1:-300}" "${2:-../data/generated}" "${3:-42}"
