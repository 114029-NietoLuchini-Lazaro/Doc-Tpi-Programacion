#!/usr/bin/env bash
# Verifica que el mirror de nginx duplique el tráfico a la sombra sin afectar al real.
# Uso: scripts/shadow/test-shadow-mirror.sh
set -euo pipefail

cd "$(dirname "$0")"
project="llm-shadow-test"
compose=(docker compose -p "$project" -f compose.test.yaml)
url="http://localhost:18080"

cleanup() { "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1; }
trap cleanup EXIT

pass=0; fail=0
check() { # check "descripcion" "esperado" "obtenido"
  if [[ "$2" == "$3" ]]; then echo "  OK   $1"; pass=$((pass+1))
  else echo "  FAIL $1 (esperado='$2' obtenido='$3')"; fail=$((fail+1)); fi
}
mock() { "${compose[@]}" exec -T "$1" wget -qO- "http://127.0.0.1:8086$2" --post-data="" 2>/dev/null; }
stats() { "${compose[@]}" exec -T "$1" wget -qO- http://127.0.0.1:8086/__stats; }
jq_py() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
reset() { mock mock-primary /__reset >/dev/null; mock mock-shadow /__reset >/dev/null; mock mock-shadow "/__mode?m=ok" >/dev/null; }
wait_shadow() { # espera hasta 3s a que la sombra reciba $1 requests
  for _ in $(seq 1 15); do
    [[ "$(stats mock-shadow | jq_py 'len(d["received"])')" -ge "$1" ]] && return; sleep 0.2
  done
}

echo "Levantando entorno de prueba..."
"${compose[@]}" up -d --build --wait >/dev/null

echo "1) GET: el cliente recibe la respuesta del real y la sombra recibe copia"
reset
resp=$(curl -s "$url/api/llm/v1/ping?x=1" -H "Authorization: Bearer tok")
check "cliente ve served_by=primary" "primary" "$(echo "$resp" | jq_py 'd["served_by"]')"
wait_shadow 1
check "real recibió 1 request" "1" "$(stats mock-primary | jq_py 'len(d["received"])')"
check "sombra recibió 1 request" "1" "$(stats mock-shadow | jq_py 'len(d["received"])')"
check "sombra recibió misma ruta+query" "/api/llm/v1/ping?x=1" "$(stats mock-shadow | jq_py 'd["received"][0]["path"]')"
check "sombra recibió Authorization" "Bearer tok" "$(stats mock-shadow | jq_py 'd["received"][0]["authorization"]')"
check "sombra marcada con X-Shadow-Request" "1" "$(stats mock-shadow | jq_py 'd["received"][0]["shadow"]')"
check "real NO lleva X-Shadow-Request" "None" "$(stats mock-primary | jq_py 'd["received"][0]["shadow"]')"

echo "2) POST: el body se replica"
reset
curl -s -X POST "$url/api/llm/v1/moderation" -H 'Content-Type: application/json' -d '{"text":"hola"}' >/dev/null
wait_shadow 1
check "real recibió el body" '{"text":"hola"}' "$(stats mock-primary | jq_py 'd["received"][0]["body"]')"
check "sombra recibió el body" '{"text":"hola"}' "$(stats mock-shadow | jq_py 'd["received"][0]["body"]')"

echo "3) Sombra devuelve 500: el cliente no se entera"
reset; mock mock-shadow "/__mode?m=error" >/dev/null
code=$(curl -s -o /dev/null -w '%{http_code}' "$url/api/llm/v1/ping")
check "cliente recibe 200" "200" "$code"

echo "4) Sombra lenta (5s): el cliente no espera"
reset; mock mock-shadow "/__mode?m=slow" >/dev/null
t=$(curl -s -o /dev/null -w '%{time_total}' "$url/api/llm/v1/ping")
check "cliente responde en <1s (t=${t}s)" "1" "$(python3 -c "print(1 if $t < 1 else 0)")"

echo "5) Sombra caída: el cliente no se entera"
reset; "${compose[@]}" stop mock-shadow >/dev/null
code=$(curl -s -o /dev/null -w '%{http_code}' "$url/api/llm/v1/ping")
t=$(curl -s -o /dev/null -w '%{time_total}' "$url/api/llm/v1/ping")
check "cliente recibe 200 con sombra caída" "200" "$code"
check "cliente responde en <1s (t=${t}s)" "1" "$(python3 -c "print(1 if $t < 1 else 0)")"

echo "6) /_shadow no es accesible desde afuera"
code=$(curl -s -o /dev/null -w '%{http_code}' "$url/_shadow")
check "GET /_shadow devuelve 404 (interno)" "404" "$code"

echo
echo "Resultado: $pass OK, $fail FAIL"
[[ "$fail" -eq 0 ]]
