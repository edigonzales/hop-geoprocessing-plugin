#!/usr/bin/env bash

set -euo pipefail

if [[ $# -gt 1 ]]; then
  echo "usage: $0 [HOP_HOME]" >&2
  exit 1
fi

HOP_HOME_ARG="${1:-${HOP_HOME:-}}"
if [[ -z "${HOP_HOME_ARG}" ]]; then
  echo "HOP_HOME is required" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PLUGIN_DIR="${HOP_HOME_ARG}/plugins/transforms/hop-geoprocessing"

cd "${REPO_DIR}"
mvn -q -DskipTests package

rm -rf "${PLUGIN_DIR}"
unzip -o "${REPO_DIR}/assemblies/assemblies-hop-geoprocessing-suite/target/hop-geoprocessing-plugin-"*.zip -d "${HOP_HOME_ARG}"

echo "Installed hop-geoprocessing into ${HOP_HOME_ARG}"
