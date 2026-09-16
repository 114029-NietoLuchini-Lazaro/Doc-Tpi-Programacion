#!/usr/bin/env bash
set -euo pipefail

project_name="llm-s1-smoke"
mode="${1:-}"

cleanup() { docker compose -p "$project_name" down --volumes --remove-orphans; }
trap cleanup EXIT

if [[ "$mode" == "--cold" ]]; then
  echo "[T7] Modo --cold: eliminando imágenes/volúmenes previos de '$project_name' antes de medir."
  docker compose -p "$project_name" down --rmi all --volumes --remove-orphans || true
fi

mvn -q -DskipTests package

start_ts=$(date +%s)
docker compose -p "$project_name" up --build --wait
end_ts=$(date +%s)
elapsed=$((end_ts - start_ts))

docker compose -p "$project_name" exec -T llm-service wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"'
echo "Docker Compose S1 smoke test: OK"
if [[ "$mode" == "--cold" ]]; then
  echo "[T7] Tiempo de arranque en frío (docker compose up --build --wait): ${elapsed}s"
else
  echo "[T7] Tiempo de arranque (docker compose up --build --wait): ${elapsed}s (usar --cold para medir en frío)"
fi
