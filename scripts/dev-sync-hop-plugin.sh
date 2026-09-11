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
VERSION="$(mvn -U -B -ntp -q -DforceStdout help:evaluate -Dexpression=project.version)"
mvn -U -B -ntp -DskipTests package

rm -rf "${PLUGIN_DIR}"
ZIP_PATH="${REPO_DIR}/assemblies/assemblies-hop-geoprocessing-suite/target/hop-geoprocessing-plugin-${VERSION}.zip"
if [[ ! -f "${ZIP_PATH}" ]]; then
  echo "Plugin ZIP not found: ${ZIP_PATH}" >&2
  exit 1
fi
unzip -o "${ZIP_PATH}" -d "${HOP_HOME_ARG}"

echo "Installed hop-geoprocessing into ${HOP_HOME_ARG}"
