# NO hacer — anti-patrones, con el error textual

Complemento de la sección «Lo que NO hacer» de `SKILL.md`. Agrupado por área.
Si algo de esta lista aparece en un compose propuesto, **frenar y corregir
antes de levantar**.

## Seguridad (lo más grave)

### 1. Exponer un servicio interno a la tailnet (ej. `users-service` :8082)
- **Qué pasa**: se saltea el gateway. Toda la API de users queda accesible y,
  como el micro destino confía en `X-User-Roles`, cualquier nodo del tailnet
  puede inventar `ADMIN` y actuar como admin. Sin token, sin password, sin 2FA.
- **Síntoma**: `wget http://<plataforma>:8082/...` responde desde cualquier
  nodo.
- **En su lugar**: el JWKS va por el gateway
  (`http://<TS_SERVER_HOST>:8080/.well-known/jwks.json`). Confirmar la ruta con
  `curl` antes; si no existe, pedirla o desactivar el job JWKS en el micro
  remoto. **Nunca** un forward/publish de `:8082`.

### 2. Dejar la tailnet sin ACL
- **Qué pasa**: la tailnet por defecto acepta todo. "Sin `ports:`" no protege:
  cualquier laptop llega a `<ip-micro>:<APP_PORT>`.
- **Síntoma**: desde otro nodo, `nc -z <ip-micro> <APP_PORT>` **funciona**.
- **En su lugar**: `references/acl-ejemplo.hujson` con tags, deny por defecto,
  y verificación negativa obligatoria.

## Red del micro (compose)

### 3. `extra_hosts:` junto a `network_mode: "service:<sidecar>"`
- **Error**: `conflicting options: custom host-to-IP mapping and the network mode`
- **Trampa**: `docker compose config` lo valida igual; falla al crear.
- **En su lugar**: direccionar por MagicDNS o IP directa en las URLs.

### 4. `dns:` junto a `network_mode: "service:<sidecar>"`
- **Error**: `conflicting options: dns and the network mode`
- **En su lugar**: bind-mount `./resolv.conf:/etc/resolv.conf:ro`.

### 5. `networks:` en el servicio con namespace compartido
- **Causa**: `network_mode: "service:..."` y `networks:` son excluyentes.
- **En su lugar**: la conectividad la da el namespace del sidecar.

### 6. `ports:` publicados en el micro
- **Riesgo**: con puerto publicado, un `X-User-Roles: ADMIN` inventado entra
  sin credenciales. Los headers solo son confiables si **solo** el gateway
  puede hablarle al micro.
- **En su lugar**: sin `ports:`; la tailnet + ACL son el transporte.

### 7. Levantar la app fuera del compose "para probar"
- **Error**: `Application failed to start: Port <APP_PORT> was already in use`
- **Causa**: correr la imagen sin `network_mode` la pone en otra red y arranca
  la app de nuevo.
- **En su lugar**: `docker run --rm --entrypoint /bin/sh --network "container:<sidecar>" ...`
  (nunca el ENTRYPOINT de la app).

## Sidecar Tailscale

### 8. `TS_USERSPACE` en su default (`true`)
- **Síntoma**: el nodo figura en la tailnet pero el micro no llega a nada.
- **Causa**: sin `tailscale0`; el proxy SOCKS solo sirve al sidecar.
- **En su lugar**: `TS_USERSPACE=false` + tun + `NET_ADMIN`/`NET_RAW`.

### 9. `service_started` en `depends_on`
- **Síntoma**: el micro arranca antes de que `tailscale0` esté lista;
  registro/JWKS fallan al inicio (el cliente reintenta, pero ensucia).
- **En su lugar**: healthcheck en el sidecar + `condition: service_healthy`.

### 10. Reusar `TS_HOSTNAME` sin verificar
- **Síntoma**: el nodo aparece como `nombre-1` en MagicDNS.
- **Causa**: la identidad efímera anterior todavía reserva el nombre.
- **En su lugar**: nombre único; con el registro por IP el sufijo no molesta.

### 11. Auth key sin tag
- **Síntoma**: el nodo queda fuera del esquema de ACL (no matchea
  `tag:micros`), y las reglas no lo cubren.
- **En su lugar**: generar la auth key con el tag (`tag:micros`) o pasar
  `TS_EXTRA_ARGS=--advertise-tags=tag:micros`.

### 12. `TS_AUTHKEY` en el compose o con el placeholder del `.env.example`
- **Error**: `Received error: invalid key: API key does not exist`; nodo
  logged out (`tailscale status` sin peers).
- **En su lugar**: key real, tagged, por `.env` gitignoreado; rotarla al cerrar.

### 13. Fijar `image: tailscale/tailscale:latest`
- **Riesgo**: un pull nuevo cambia comportamiento sin que nadie lo pida.
- **En su lugar**: fijar la versión probada (ej. `v1.102.3`).

## DNS

### 14. Confiar en el resolver de Docker (`127.0.0.11`)
- **Síntoma**: `nslookup <TS_SERVER_HOST>` → `NXDOMAIN`.
- **En su lugar**: `resolv.conf` con `nameserver 100.100.100.100`.

### 15. Montar solo MagicDNS cuando el micro usa servicios propios por nombre
- **Síntoma**: el micro no resuelve `mysql`/`redis` y no arranca.
- **Causa**: se pisó `/etc/resolv.conf` y se perdió `127.0.0.11`.
- **En su lugar**: caso B de `references/resolv.conf` (ambos resolvers) y
  **verificar** los dos nombres; si la cadena NXDOMAIN los pisa, direccionar
  los propios por IP/alias o la plataforma por IP.

## Registro en Eureka

### 16. Pasar la IP del tailnet por entorno (`TS_PING_IP`) con `${...:?}`
- **Error**: el compose no levanta **nada** (ni el sidecar que daría la IP),
  porque valida el archivo entero antes de crear.
- **En su lugar**: `spring.cloud.inetutils.preferred-networks` + `prefer-ip-address`
  + `instance-id` fijo. La IP se detecta sola.

### 17. Usar notación CIDR en `preferred-networks`
- **Síntoma**: Eureka anuncia `ipAddr = 172.x` (bridge docker), inalcanzable,
  aunque la config parezca correcta.
- **Causa**: Spring **no entiende CIDR**. Por cada valor prueba
  `ip.matches(valor)` (regex completa) y `ip.startsWith(valor)` (prefijo).
  `100.64.0.0/10` no cumple ninguna de las dos contra `100.94.20.6`, así que no
  hay IP preferida y vuelve a la elección por defecto (172.x).
  Verificado en spring-cloud-commons 5.0.3 (`InetUtils`).
- **En su lugar**: `preferred-networks: ["100."]`, o la regex exacta del rango
  con comillas simples YAML:
  `'100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\..*'`

### 18. Registrar solo `hostName` corto y esperar que el gateway lo resuelva
- **Síntoma**: registro `UP` pero gateway `503`.
- **Causa**: el gateway no tiene MagicDNS en su contenedor.
- **En su lugar**: registrar la IP del tailnet (la auto-detectada).

### 19. Asumir que registrarse alcanza
- **Síntoma**: `404` (sin allowlist) o `503` (sin ruta de salida del gateway).
- **En su lugar**: ver `references/lado-plataforma.md`.

## Verificación

### 20. Probar el ingreso desde el mismo namespace del micro
- **Qué pasa**: el paquete nunca sale a la tailnet ni cruza la ACL: da `200`
  **siempre**. Falso positivo.
- **En su lugar**: la prueba válida de ingreso es la ruta pública **por el
  gateway**; la directa desde otro nodo debe **fallar** (ACL).

### 21. Probar desde un nodo offline
- **Síntoma**: `HTTP 000` que parece falla del vínculo.
- **En su lugar**: `tailscale status` antes de concluir; probar desde el
  contenedor o desde un nodo `online`.

### 22. Pegarle al health en el puerto de app
- **Síntoma**: `404` en `/<APP_PORT>/actuator/health`.
- **Causa**: el management corre en `APP_PORT + 1`.
- **En su lugar**: healthcheck contra `<APP_PORT+1>/actuator/health/readiness`.

### 23. Prueba negativa sin control positivo (o con el control equivocado)
- **Qué pasa**: si el nodo desde el que se prueba no tiene tailnet (Tailscale no
  instalado, sesión caída, WSL sin ruta), el intento se cae por la razón
  equivocada y se lee como "ACL bloqueó". Falso OK.
- **CasoTrampa**: controlar con `:80` un negativo corrido **desde el micro**.
  Con la ACL puesta, `tag:micros` no tiene `:80`: el control falla siempre y los
  negativos del micro quedan en SKIP para siempre.
- **En su lugar**: control del **mismo contexto** — micro:
  `tpi-plataforma:8761`; host persona: `tpi-plataforma:80`. Si el control falla,
  **SKIP**, nunca `OK`.

### 24. Interpretar mal las múltiples instancias en Eureka
- **Falla real**: un par `ip:puerto` **duplicado** (registro huérfano o
  `instance-id` compartido) o que **ninguna** instancia anuncie la IP actual.
- **NO es falla**: varias instancias con IPs distintas → réplicas legítimas o
  huérfanas que todavía no expiraron. Eso es **WARN**.
- **En su lugar**: contar pares `ip:puerto`, buscar duplicados, exigir que la IP
  actual esté presente y avisar (no fallar) si hay más instancias.

### 25. Escalar con `--scale` en el mismo servicio
- **Qué pasa**: `docker compose up --scale <micro>=2` no crea dos nodos: las
  dos réplicas comparten **el mismo netns del sidecar**, así que tienen la misma
  IP y pelean por el mismo puerto (`address already in use`).
- **Y peor**: con el mismo `instance-id` (o el default), las dos instancias
  **se pisan en Eureka** aunque tuvieran IP distinta: la última registrada
  sobreescribe a la otra.
- **En su lugar**: una **réplica = un servicio del compose + su propio sidecar**
  (su namespace, su IP, su nodo MagicDNS) y `APP_INSTANCE_ID` distinto en cada
  una (`instance-id: ${spring.application.name}:${APP_INSTANCE_ID:${server.port}}`).

## Checklist final antes de levantar

- [ ] Sin forward/publish de servicios internos (`:8082` en particular).
- [ ] ACL con tags aplicada (y pensada la verificación negativa).
- [ ] Micro sin `ports:`, `networks:`, `dns:` ni `extra_hosts:`.
- [ ] Sidecar kernel + healthcheck + `image` fijada + auth key tagged.
- [ ] `resolv.conf` montado; si hay servicios propios, verificados ambos.
- [ ] URLs de infra por FQDN MagicDNS (no IP de plataforma).
- [ ] `preferred-networks` + `instance-id` fijo (sin IP por entorno).
- [ ] JWKS por el gateway (ruta confirmada) o job desactivado.
- [ ] `serviceId` en allowlist y gateway con ruta de salida (coordinar).
- [ ] Plan de cierre: rotar key, quitar forwards, verificar ACL.
