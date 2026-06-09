#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env.render ]]; then
  echo "Fehler: .env.render fehlt."
  echo "  cp .env.render.example .env.render"
  echo "Trage nur RENDER_API_KEY ein (Render → Account Settings → API Keys)."
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env.render
set +a

./scripts/fetch-render-env.sh

set -a
# shellcheck disable=SC1091
source .env
set +a

./gradlew bootRun
