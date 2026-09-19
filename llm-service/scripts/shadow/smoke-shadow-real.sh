#!/usr/bin/env bash
# Smoke test del shadow deploy con llm-service REAL (dos instancias) y el comparador.
#
# Fase 1: misma versión en real y sombra -> todas las respuestas deben coincidir.
# Fase 2: la sombra se relanza con otro umbral de spam -> el comparador debe detectar la diferencia.
#
# Uso: scripts/shadow/smoke-shadow-real.sh        (la primera vez compila la imagen: varios minutos)
set -euo pipefail

cd "$(dirname "$0")"
project="llm-shadow-smoke"
compose=(docker compose -p "$project" -f compose.smoke.yaml)
proxy_port=18090
log="$(mktemp -t shadow-smoke).jsonl"
proxy_pid=""

cleanup() {
  [[ -n "$proxy_pid" ]] && kill "$proxy_pid" 2>/dev/null || true
  "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -f "$log"
}
trap cleanup EXIT

pass=0; fail=0
check() { # check "descripcion" "esperado" "obtenido"
  if [[ "$2" == "$3" ]]; then echo "  OK   $1"; pass=$((pass+1))
  else echo "  FAIL $1 (esperado='$2' obtenido='$3')"; fail=$((fail+1)); fi
}
jq_py() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }

# Identidad como la inyecta el gateway: servicio de confianza con el scope de moderación.
call() { # call <id> <texto> [sin-scope]
  local scopes="moderation:decide"; [[ "${3:-}" == "sin-scope" ]] && scopes="otro:scope"
  curl -s -o /tmp/shadow-smoke-body -w '%{http_code}' -X POST "http://localhost:$proxy_port/moderation/v1/decisions" \
    -H 'Content-Type: application/json' -H 'X-Principal-Type: service' \
    -H 'X-Service-Id: chat-service' -H "X-Service-Scopes: $scopes" \
    -d "{\"message_id\":\"$1\",\"course_id\":\"curso-1\",\"sender_id\":\"alumno-1\",\"sender_role\":\"STUDENT\",\"text\":\"$2\"}"
}
start_proxy() {
  python3 shadow_compare.py --primary http://localhost:18086 --shadow http://localhost:18087 \
    --listen "$proxy_port" --log "$log" --ignore latency_ms,incident_id,timestamp,requestId,traceId >/dev/null 2>&1 &
  proxy_pid=$!
  for _ in $(seq 1 30); do curl -s "http://localhost:$proxy_port/__summary" >/dev/null 2>&1 && return; sleep 0.2; done
  echo "el comparador no arrancó"; exit 1
}
wait_compared() { # espera a que el log tenga $1 comparaciones
  for _ in $(seq 1 50); do
    [[ "$(wc -l < "$log" 2>/dev/null | tr -d ' ')" -ge "$1" ]] && return; sleep 0.2
  done
}
show_diffs() { python3 -c "
import json
for l in open('$log'):
    r=json.loads(l)
    if r['result']!='match': print('       ', r['path'], r['result'], r['diffs'])"; }
results() { python3 -c "
import json,sys
print(','.join(json.loads(l)['result'] for l in open('$log')))"; }

run_id="smoke-$(date +%s)"

echo "Levantando real + sombra (la primera vez compila la imagen)..."
"${compose[@]}" up -d --build --wait >/dev/null
start_proxy

echo "1) Misma versión: las respuestas deben coincidir"
: > "$log"
check "mensaje limpio -> 200"        "200" "$(call "$run_id-1" 'Hola, tengo una duda sobre el TP')"
check "cliente ve una decisión ALLOW" "ALLOW" "$(jq_py 'd["decision"]' < /tmp/shadow-smoke-body)"
check "mensaje con 1 URL -> 200"     "200" "$(call "$run_id-2" 'Mirá esto http://ejemplo.com/a')"
check "spam con 4 URLs -> 200"       "200" "$(call "$run_id-3" 'http://a.com http://b.com http://c.com http://d.com')"
check "sin scope -> 401"             "401" "$(call "$run_id-4" 'hola' sin-scope)"
wait_compared 4
check "4 comparaciones registradas"  "4" "$(wc -l < "$log" | tr -d ' ')"
check "todas coinciden"              "match,match,match,match" "$(results)"
show_diffs   # no imprime nada si todo coincide
check "la sombra respondió en todas las comparaciones" "0" "$(python3 -c "
import json; print(sum(1 for l in open('$log') if json.loads(l)['shadow_status'] is None))")"

echo "2) Sombra con otro umbral de spam: el comparador debe detectar la diferencia"
kill "$proxy_pid"; proxy_pid=""
SHADOW_SPAM_MAX_URLS=1 "${compose[@]}" up -d --force-recreate --wait llm-shadow >/dev/null
start_proxy
: > "$log"
check "mensaje con 1 URL -> 200 (el cliente ve al real)" "200" "$(call "$run_id-5" 'Mirá esto http://ejemplo.com/a')"
check "cliente sigue viendo ALLOW" "ALLOW" "$(jq_py 'd["decision"]' < /tmp/shadow-smoke-body)"
wait_compared 1
check "comparador marca diferencia" "body_mismatch" "$(results)"
check "la diferencia es el campo decision" "1" "$(python3 -c "
import json
d=json.loads(open('$log').readline())['diffs']
print(1 if any(x.startswith('\$.decision') for x in d) else 0)")"

echo
echo "Resultado: $pass OK, $fail FAIL"
[[ "$fail" -eq 0 ]]
