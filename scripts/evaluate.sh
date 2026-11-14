#!/usr/bin/env bash
# Run generated files through the rules offline and print the per-pattern table. Usage: scripts/evaluate.sh [count] [seed]
set -euo pipefail
cd "$(dirname "$0")/../backend"
JAR=$(ls target/ledgerline-*.jar 2>/dev/null | head -1 || true)
[ -n "$JAR" ] || { mvn -q -DskipTests package; JAR=$(ls target/ledgerline-*.jar | head -1); }
java -cp "$JAR" -Dloader.main=com.azentio.ledgerline.tools.DatasetEvaluator \
  org.springframework.boot.loader.launch.PropertiesLauncher "${1:-300}" "${2:-42}"
