#!/usr/bin/env bash
# Post every generated loan file to the API and print band counts. Usage: scripts/post-files.sh [dir] [baseUrl]
set -euo pipefail
DIR="${1:-data/generated/files}"
URL="${2:-http://localhost:8080}"
for f in "$DIR"/*.json; do
  curl -s -X POST "$URL/api/loan-files" -H 'Content-Type: application/json' --data @"$f" \
    | grep -o '"band":"[A-Z]*"' || echo '"band":"ERROR"'
done | sort | uniq -c
