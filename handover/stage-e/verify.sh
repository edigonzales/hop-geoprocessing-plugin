#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
shasum -a 256 -c SHA256SUMS
python3 -m json.tool manifest.json >/dev/null
python3 -m json.tool e2e-results.json >/dev/null
python3 -m json.tool benchmark.json >/dev/null
echo "Stage-E handover artifacts verified."
