#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -z "${RENDER_API_KEY:-}" && -f .env.render ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env.render
  set +a
fi

: "${RENDER_API_KEY:?RENDER_API_KEY fehlt. Trage ihn in .env.render ein (Render → Account Settings → API Keys).}"

API_BASE="https://api.render.com/v1"

auth_header() {
  curl -sS \
    -H "Authorization: Bearer ${RENDER_API_KEY}" \
    -H "Accept: application/json" \
    "$@"
}

if ! command -v jq >/dev/null 2>&1; then
  echo "Fehler: jq ist erforderlich (z. B. brew install jq)."
  exit 1
fi

list_web_services() {
  auth_header "${API_BASE}/services?limit=100" \
    | jq '[.[] | select(.service.type == "web_service") | .service | {name, id}]'
}

list_postgres_instances() {
  auth_header "${API_BASE}/postgres?limit=100" \
    | jq '[.[] | .postgres | {name, id, databaseName}]'
}

find_service_id() {
  local service_name="${1:-}"
  auth_header "${API_BASE}/services?limit=100" \
    | jq -r --arg name "$service_name" '
        [.[] 
         | select(.service.type == "web_service")
         | select((.service.name | ascii_downcase) == ($name | ascii_downcase))
         | .service.id][0] // empty'
}

find_postgres_id() {
  local db_name="${1:-}"
  auth_header "${API_BASE}/postgres?limit=100" \
    | jq -r --arg name "$db_name" '
        [.[] 
         | select(
             (.postgres.name | ascii_downcase) == ($name | ascii_downcase)
             or (.postgres.databaseName | ascii_downcase) == ($name | ascii_downcase)
             or (.postgres.id | ascii_downcase) == ($name | ascii_downcase)
             or ((.postgres.id | split("-")[1] // "") | ascii_downcase) == ($name | ascii_downcase)
           )
         | .postgres.id][0] // empty'
}

fetch_database_url_from_service() {
  local service_id="$1"
  local response
  response="$(auth_header "${API_BASE}/services/${service_id}/env-vars/DATABASE_URL")"
  if echo "$response" | jq -e '.message' >/dev/null 2>&1; then
    echo ""
    return 0
  fi
  echo "$response" | jq -r '.envVar.value // .value // empty'
}

SERVICE_NAME="${RENDER_SERVICE_NAME:-}"
DB_NAME="${RENDER_DB_NAME:-}"

if [[ -z "$SERVICE_NAME" ]]; then
  SERVICE_NAME="$(list_web_services | jq -r 'if length == 1 then .[0].name else empty end')"
fi

if [[ -z "$DB_NAME" ]]; then
  DB_NAME="$(list_postgres_instances | jq -r 'if length == 1 then .[0].name else empty end')"
fi

if [[ "$SERVICE_NAME" == dpg-* ]]; then
  echo "Hinweis: '${SERVICE_NAME}' ist eine PostgreSQL-ID, kein Web-Service-Name."
  DB_NAME="${DB_NAME:-$SERVICE_NAME}"
  SERVICE_NAME="$(list_web_services | jq -r 'if length == 1 then .[0].name else empty end')"
fi

DATABASE_URL=""
SERVICE_ID=""

if [[ -n "$SERVICE_NAME" ]]; then
  SERVICE_ID="$(find_service_id "$SERVICE_NAME")"
  if [[ -n "$SERVICE_ID" ]]; then
    DATABASE_URL="$(fetch_database_url_from_service "$SERVICE_ID")"
  fi
fi

if [[ -z "$DATABASE_URL" && -n "$DB_NAME" ]]; then
  POSTGRES_ID="$(find_postgres_id "$DB_NAME")"
  if [[ -n "$POSTGRES_ID" ]]; then
    DATABASE_URL="$(auth_header "${API_BASE}/postgres/${POSTGRES_ID}" \
      | jq -r '.postgres.connectionInfo.externalConnectionString // empty')"
  fi
fi

normalize_database_url() {
  local url="$1"
  local region="$2"
  python3 - "$url" "$region" <<'PY'
import sys
from urllib.parse import urlparse, urlunparse

url, region = sys.argv[1], sys.argv[2]
parsed = urlparse(url)
host = parsed.hostname
port = parsed.port or 5432

if host and host.startswith("dpg-") and "." not in host:
    host = f"{host}.{region}-postgres.render.com"
    port = 5432

userinfo = ""
if parsed.username:
    userinfo = parsed.username
    if parsed.password:
        userinfo += f":{parsed.password}"

netloc = f"{userinfo}@{host}:{port}" if userinfo else f"{host}:{port}"
query = parsed.query or ("sslmode=require" if "render.com" in host else "")
normalized = urlunparse((parsed.scheme, netloc, parsed.path, "", query, ""))
print(normalized)
PY
}

if [[ -n "$DATABASE_URL" ]]; then
  POSTGRES_ID="$(python3 -c 'import sys; from urllib.parse import urlparse; print(urlparse(sys.argv[1]).hostname or "")' "$DATABASE_URL")"
  if [[ "$POSTGRES_ID" == dpg-* ]]; then
    REGION="$(auth_header "${API_BASE}/postgres/${POSTGRES_ID}" | jq -r '.region // .postgres.region // "frankfurt"')"
    DATABASE_URL="$(normalize_database_url "$DATABASE_URL" "$REGION")"
  fi
fi

if [[ -z "$DATABASE_URL" ]]; then
  echo "DATABASE_URL konnte nicht von Render geladen werden."
  echo
  echo "Gefundene Web Services:"
  list_web_services | jq -r '.[] | "  - \(.name) (\(.id))"'
  echo
  echo "Gefundene PostgreSQL-Instanzen:"
  list_postgres_instances | jq -r '.[] | "  - \(.name) [DB: \(.databaseName)] (\(.id))"'
  echo
  echo "Ursache:"
  echo "  Die PostgreSQL-Instanz ist noch nicht mit dem Web Service verknüpft."
  echo "  Render stellt DATABASE_URL erst bereit, wenn die Verknüpfung existiert."
  echo
  echo "Einmalig im Render-Dashboard:"
  echo "  1. webtech-greenie-Chat-backend → Environment"
  echo "  2. Add Environment Variable → Add from Database"
  echo "  3. webtech-Greenie-Chat-Data auswählen, Key: DATABASE_URL"
  echo "  4. Save and deploy"
  echo
  echo "Danach erneut: ./run-local.sh"
  echo
  echo "Optional in .env.render (nur falls Auto-Erkennung fehlschlägt):"
  echo "  RENDER_SERVICE_NAME=webtech-greenie-Chat-backend"
  echo "  RENDER_DB_NAME=webtech-Greenie-Chat-Data"
  exit 1
fi

cat > .env <<EOF
# Automatisch von Render geladen ($(date -u +%Y-%m-%dT%H:%M:%SZ))
DATABASE_URL=${DATABASE_URL}
EOF

echo ".env wurde mit DATABASE_URL von Render aktualisiert."
