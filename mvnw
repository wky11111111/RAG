#!/usr/bin/env sh

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_REPO="$SCRIPT_DIR/.mvn/repository"

if command -v mvn >/dev/null 2>&1; then
  exec mvn -Dmaven.repo.local="$PROJECT_REPO" "$@"
fi

echo "Maven executable not found. Please install Maven and rerun." >&2
exit 1
