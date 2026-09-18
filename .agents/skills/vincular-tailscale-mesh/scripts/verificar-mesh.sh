#!/bin/sh
# Verifica el vinculo de un micro a la mesh Tailscale.
#
# Uso:
#   SIDECAR=mesh-x MICRO=tpi-x SERVICE_ID=X-SERVICE SERVER_HOST=<fqdn> \
#   SEG=x APP_PORT=8086 MGMT_PORT=8087 sh verificar-mesh.sh
#
# Opcionales: EUREKA_PORT=8761 GATEWAY_PORT=8080 EDGE_PORT=80 SKIP_DIRECT=1
#
# CONTROLES POSITIVOS (cada negativo necesita uno del MISMO contexto):
#   - Dentro del micro (tag:micros, alcanza :8761 y :8080): control = Eureka.
#     Valida que :8082 y :8081 esten cerrados.
#   - En el host (persona, alcanza el borde :80): control = nginx.
#     Valida que el acceso directo al micro este bloqueado.
# Si el control falla, los negativos de ese contexto son SKIP, nunca OK.
#
# Notas:
#  - Requiere Docker en el host. No imprime secretos. Solo lectura.
#  - En Windows correr desde PowerShell (no WSL): el curl.exe del host si tiene
#    la ruta del tailnet. Si el host no esta en la tailnet, SKIP_DIRECT=1.
#  - Si se define TS_SOCKET para el sidecar, el healthcheck del compose debe
#    usar --socket=<path> explicito.
set -u

SIDECAR="${SIDECAR:?falta SIDECAR (contenedor del sidecar, ej. mesh-x)}"
MICRO="${MICRO:?falta MICRO (contenedor del micro, ej. tpi-x)}"
SERVICE_ID="${SERVICE_ID:?falta SERVICE_ID (tal como queda en Eureka, ej. X-SERVICE)}"
SERVER_HOST="${SERVER_HOST:?falta SERVER_HOST (FQDN MagicDNS de la plataforma)}"
SEG="${SEG:?falta SEG (segmento de la ruta, ej. x)}"
APP_PORT="${APP_PORT:?falta APP_PORT (puerto de la app)}"
MGMT_PORT="${MGMT_PORT:?falta MGMT_PORT (puerto de management, app+1)}"
EUREKA_PORT="${EUREKA_PORT:-8761}"
GATEWAY_PORT="${GATEWAY_PORT:-8080}"
EDGE_PORT="${EDGE_PORT:-80}"
SKIP_DIRECT="${SKIP_DIRECT:-0}"

ok()   { echo "OK    $1"; }
fail() { echo "FALLO $1"; }
warn() { echo "WARN  $1"; }
skip() { echo "SKIP  $1"; }

# RESPONDIO si hay una linea HTTP (el servicio contesta); BLOQUEADO si no.
# OJO: nada de -q junto a -S (en busybox -q puede suprimir los headers).
micro_http() {
  docker exec "$MICRO" sh -c "wget -S -O- -T 6 '$1' 2>&1 | grep -q 'HTTP/' && echo RESPONDIO || echo BLOQUEADO" 2>/dev/null
}
host_http() {
  if curl -s -D - -o /dev/null --connect-timeout 4 "$1" 2>/dev/null | grep -q '^HTTP/'; then
    echo RESPONDIO
  else
    echo BLOQUEADO
  fi
}

TMPD="$(mktemp -d)"
trap 'rm -rf "$TMPD"' EXIT INT TERM

echo "== [1] sidecar: estado del nodo =="
docker exec "$SIDECAR" tailscale version 2>/dev/null | head -1
docker exec "$SIDECAR" tailscale status 2>/dev/null | head -6
IP="$(docker exec "$SIDECAR" tailscale ip -4 2>/dev/null | head -1)"
if [ -n "$IP" ]; then ok "ip tailnet = $IP"; else fail "sin IP tailnet (¿logueado? ¿TS_AUTHKEY?)"; fi

echo "== [2] micro: resolv.conf =="
RC="$(docker exec "$MICRO" cat /etc/resolv.conf 2>&1)"
case "$RC" in
  *100.100.100.100*) ok "usa MagicDNS" ;;
  *) fail "no usa MagicDNS (montar resolv.conf:ro)" ;;
esac
case "$RC" in
  *127.0.0.11*) warn "ademas usa Docker DNS: verificar que servicios propios Y la plataforma resuelvan" ;;
esac

echo "== [3] micro: resuelve $SERVER_HOST =="
if docker exec "$MICRO" nslookup "$SERVER_HOST" >/dev/null 2>&1; then
  ok "MagicDNS resuelve"
else
  fail "NXDOMAIN (¿resolv.conf? ¿MagicDNS deshabilitado?)"
fi

echo "== [4] micro: control positivo de su contexto (Eureka :$EUREKA_PORT) =="
MICRO_CONTROL=0
if micro_http "http://$SERVER_HOST:$EUREKA_PORT/eureka/apps" | grep -q RESPONDIO; then
  MICRO_CONTROL=1; ok "el micro alcanza el registro (control OK)"
else
  fail "el micro NO alcanza el registro: sin control, los negativos del micro van a SKIP"
fi

echo "== [5] registro: instancias, IP anunciada y duplicados =="
APP="$(docker exec "$MICRO" wget -qO- -T 8 "http://$SERVER_HOST:$EUREKA_PORT/eureka/apps/$SERVICE_ID" 2>/dev/null)"
if echo "$APP" | grep -q "<hostName>"; then
  # Aplana el XML (puede venir en una sola linea) y extrae listas en orden.
  # grep -o: una coincidencia por linea; luego se emparejan ip:puerto.
  FLAT="$(printf '%s' "$APP" | tr -d '\n')"
  printf '%s\n' "$FLAT" | grep -o '<ipAddr>[^<]*' | sed 's/^<ipAddr>//' > "$TMPD/ips"
  printf '%s\n' "$FLAT" | grep -o '<port enabled="[^"]*">[^<]*' | sed 's/.*>//' > "$TMPD/ports"
  paste -d: "$TMPD/ips" "$TMPD/ports" > "$TMPD/pairs"
  N="$(wc -l < "$TMPD/ips" | tr -d ' ')"
  DUP="$(sort "$TMPD/pairs" | uniq -d | tr '\n' ' ')"
  OURS="$(grep -F "$IP:" "$TMPD/pairs" 2>/dev/null | wc -l | tr -d ' ')"
  echo "  instancias=$N pares=[$(tr '\n' ' ' < "$TMPD/pairs")] actual=$IP"
  if [ -n "$DUP" ]; then
    fail "ip:puerto DUPLICADO en Eureka: $DUP (registro huerfano o instance-id compartido)"
  elif [ "$OURS" -eq 0 ]; then
    fail "ninguna instancia anuncia la IP actual ($IP): IP vieja o preferred-networks"
  elif [ "$N" -gt "$OURS" ]; then
    warn "hay $N instancias y $OURS con la IP actual: replicas legitimas u huerfanas sin expirar"
  else
    ok "registro OK (una instancia, IP actual)"
  fi
else
  warn "no aparece /apps/$SERVICE_ID (¿serviceId correcto? ¿micro arrancado?)"
fi

echo "== [6] cerrado para tag:micros: :8082 y :8081 (negativos con control) =="
if [ "$MICRO_CONTROL" -ne 1 ]; then
  skip ":8082 y :8081 (sin control del micro)"
else
  R="$(micro_http "http://$SERVER_HOST:8082/.well-known/jwks.json")"
  if [ "$R" = "RESPONDIO" ]; then
    fail "SEGURIDAD: :8082 responde al micro (forward a users-service abierto, saltea el gateway)"
  else
    ok ":8082 no responde"
  fi
  R="$(micro_http "http://$SERVER_HOST:8081/actuator/health")"
  if [ "$R" = "RESPONDIO" ]; then
    fail "SEGURIDAD: :8081 (management del gateway) responde al micro"
  else
    ok ":8081 no responde"
  fi
fi

echo "== [7] ingreso real: ruta publica POR EL GATEWAY =="
if docker exec "$MICRO" wget -qO- -T 10 "http://$SERVER_HOST:$GATEWAY_PORT/api/$SEG/public/ping" >/dev/null 2>&1; then
  ok "gateway rutea /api/$SEG/public/ping"
else
  warn "sin 200 (404 = falta allowlist; 503 = gateway sin ruta de salida / ACL / IP vieja)"
fi

echo "== [8] JWKS por el gateway (desde el micro) =="
R="$(micro_http "http://$SERVER_HOST:$GATEWAY_PORT/.well-known/jwks.json")"
if [ "$R" = "RESPONDIO" ]; then
  ok "JWKS responde por el gateway"
else
  warn "JWKS no responde por el gateway: desactivar el job, NO abrir :8082"
fi

echo "== [9] ACL: acceso directo al micro desde el host (negativo con control) =="
if [ "$SKIP_DIRECT" = "1" ]; then
  skip "salteado (SKIP_DIRECT=1)"
else
  R="$(host_http "http://$SERVER_HOST:$EDGE_PORT/")"
  if [ "$R" != "RESPONDIO" ]; then
    skip "control del host no responde (¿sin tailnet en este host? ¿serve sin re-aplicar?)"
  elif [ -z "$IP" ]; then
    skip "sin IP del sidecar"
  else
    set +e
    curl -s -o /dev/null --connect-timeout 4 "http://$IP:$APP_PORT/" 2>/dev/null
    RC=$?
    set -e 2>/dev/null || true
    case "$RC" in
      0)  fail "el micro responde DIRECTO desde este host: no hay ACL" ;;
      28) ok "acceso directo al micro bloqueado (timeout por ACL)" ;;
      7)  warn "connection refused: puerto cerrado o app caida (no distingue ACL)" ;;
      *)  warn "resultado inconcluso (curl=$RC)" ;;
    esac
  fi
fi

echo "== fin =="
