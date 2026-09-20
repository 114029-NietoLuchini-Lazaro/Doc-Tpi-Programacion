---
name: vincular-tailscale-mesh
description: Vincula un microservicio dockerizado a la mesh Tailscale de la plataforma (sidecar kernel + network_mode service, MagicDNS, registro auto-detectando la IP del tailnet, ACLs con tags) para que el api-gateway lo alcance desde otra máquina. Incluye compose/resolv.conf/ACL, verificación desde el gateway y anti-patrones.
---

# Vincular microservicio a la mesh Tailscale

> **Versión: 1 (borrador revisado).** Origen: prueba `ping-service` — ver
> `resumen-tailscale.html` junto a esta carpeta.
>
> Esta skill aplica **exactamente los cambios que quedaron funcionando**
> (registro `UP` + gateway `200` + ida y vuelta OK), más las correcciones de
> seguridad del review: **cierre con ACLs** y **JWKS por el gateway**. Los
> templates de `references/` son la config final, no una propuesta teórica.
>
> Alcance: micro dockerizado en una **máquina distinta** a la del stack de
> plataforma (`tpi-compose`). Si corre en la **misma** máquina, no aplica:
> alcanza con unirse a la red externa `tpi-platform`.
>
> Fuera de alcance: allowlist del gateway, scopes y filtros de identidad →
> skill `integrar-micro-gateway`. El **lado plataforma** (lo que hay que pedir
> y verificar del otro extremo) → `references/lado-plataforma.md`.

La mesh no es una red docker: es una red L3 privada (WireGuard) entre nodos.
El micro **no habla Tailscale**: hereda el namespace de un sidecar que sí lo
habla, vía `network_mode: "service:<sidecar>"`.

## Contrato de direcciones y puertos

Un solo contrato, sin ambigüedad:

| Qué | Puerto | Quién lo usa |
|---|---|---|
| Borde de plataforma (nginx vía `tailscale serve`) | **80** | personas: `curl`, navegador |
| api-gateway | **8080** | micros para R2 (token + llamadas) |
| Eureka | **8761** | registro de los micros |
| App del micro | **\<APP_PORT\>** | el gateway lo rutea por tailnet |
| Management del micro | **\<APP_PORT+1\>** | healthcheck |

Todo lo anterior se direcciona por **MagicDNS** (`<TS_SERVER_HOST>`), nunca por
IP. La única IP que se fija es, opcionalmente, la propia (ver registro).

## Paso 1 — Detectar (sin preguntar)

1. **¿Dónde corre el micro?** Misma máquina que la plataforma → no seguir.
2. **`docker-compose.y*ml`** → anotar `ports:` publicados (no agregar nuevos),
   `network_mode:` y el nombre del contenedor del micro.
3. **¿Tiene servicios propios en docker?** (mysql, redis…) → afecta el resolver
   (ver `references/resolv.conf`, caso B).
4. **Dockerfile** → ¿la imagen final trae `wget`/`curl`? Sin eso no hay
   healthcheck.
5. **Registro** → Spring Cloud Eureka: no hace falta pasar la IP por entorno,
   se auto-detecta con `preferred-networks` (ver Paso 3).
6. **Host** → `/dev/net/tun` + `NET_ADMIN`. En la prueba funcionó sobre Docker
   Desktop (Windows/WSL2): verificar, no asumir que falla.

## Paso 2 — Preguntar (solo lo no detectado)

| Variable | Pregunta | Default | Dónde cae |
|---|---|---|---|
| `serviceId` | ¿Nombre del micro? | `<carpeta>-service` | `TS_HOSTNAME`, registro, allowlist |
| `TS_AUTHKEY` | Auth key del tailnet **con tag** | — (obligatoria; efímera) | `.env` |
| `TS_SERVER_HOST` | Nombre MagicDNS del nodo plataforma | `tpi-plataforma.tail767776.ts.net` | `EUREKA_URL`/`GATEWAY_URL` |
| `APP_PORT` | Puerto de la app | el que ya use | healthcheck, registro |
| tag ACL | ¿`tag:micros` (o el esquema del tailnet)? | `tag:micros` | `tagOwners` + ACL |

Mostrar los valores resueltos antes de escribir.

## Paso 3 — Generar

| Plantilla | Destino |
|---|---|
| `references/compose-sidecar.yml` | compose del equipo |
| `references/resolv.conf` | raíz del repo, montado en el micro |
| `references/eureka-tailnet.yml` | `application.yml` (Spring Eureka) |
| `references/acl-ejemplo.hujson` | política del tailnet (coordinar con plataforma) |

### Reglas duras

1. **El micro no publica puertos.** Sin `ports:`, los headers `X-*` del gateway
   son confiables. Publicar rompe la confianza y viola el spec.
2. **El micro no lleva `networks:`, `dns:` ni `extra_hosts:`** con
   `network_mode: "service:<sidecar>"`. El daemon rechaza las dos últimas
   (`conflicting options`) aunque `docker compose config` las acepte.
3. **Sidecar en kernel mode** (`TS_USERSPACE=false`) + `/dev/net/tun` +
   `NET_ADMIN`/`NET_RAW` + volumen de estado + **healthcheck** propio, y el
   micro con `depends_on: {condition: service_healthy}` (con `service_started`
   el micro puede arrancar antes de que `tailscale0` esté lista).
4. **DNS del micro → MagicDNS** por bind-mount de `/etc/resolv.conf:ro`. Si el
   micro resuelve servicios propios por nombre, seguir el caso B y **verificar
   ambos** antes de dar por bueno el resolver.
5. **Infraestructura por MagicDNS** (estable ante IPs efímeras). La IP del
   nodo plataforma NUNCA se fija en el `.env`.
6. **Registro sin IP por entorno**: `spring.cloud.inetutils.preferred-networks:
   ["100."]` + `prefer-ip-address=true` + `instance-id` fijo. **No admite CIDR**
   (`100.64.0.0/10` no matchea nada y Spring vuelve a la `172.x` → 503): cada
   valor se prueba como regex (`matches`) o prefijo (`startsWith`). Verificado
   en spring-cloud-commons 5.0.3.
7. **El JWKS va por el gateway**, nunca por un puerto de servicio expuesto a la
   tailnet. **Prohibido** cualquier forward/publish de `users-service` (:8082):
   saltea el gateway y con `X-User-Roles: ADMIN` inventado cualquier nodo de la
   tailnet actúa como admin. Confirmar la ruta antes de usarla:
   `curl http://<TS_SERVER_HOST>:8080/.well-known/jwks.json`; si no existe,
   pedirla a plataforma o **desactivar el job JWKS** en el micro remoto.
8. **ACLs con tags son obligatorias.** La tailnet por defecto deja pasar todo:
   sin ACL, "sin `ports:`" no protege — cualquier laptop llega a
   `<ip-micro>:<APP_PORT>` y puede spoofear headers. Aplicar
   `references/acl-ejemplo.hujson`: en Tailscale lo que no está en `acls` queda
   **denegado** (no se escriben reglas de `deny`: el editor las rechaza), y la
   política lleva un bloque **`tests`** que la consola ejecuta al guardar (si
   uno falla, no deja aplicar). Las auth keys se generan **con el tag** y quien
   las crea debe figurar en `tagOwners`; los nodos existentes **no** toman el
   tag solos — hay que re-autenticarlos.
9. **El gateway necesita ruta de salida al tailnet** (namespace compartido con
   su sidecar o Tailscale de host). Sin esto, `503` aunque el registro esté
   `UP`.
10. **Auth key tagged** (`tag:micros`), por entorno y rotada al cerrar. Nunca al
    repo. Una key tagged hace que el nodo quede gobernado por las ACL.
11. **Réplicas**: cada una necesita **su propio sidecar** (su namespace y su IP).
    `docker compose up --scale` **no** sirve: las réplicas comparten el netns
    del sidecar y chocan en el puerto. Y el `instance-id` debe ser único por
    instancia (`APP_INSTANCE_ID`): dos instancias con el mismo id **se pisan en
    Eureka** aunque tengan IP distinta. Con eso, nunca hay un `ip:puerto`
    repetido: Tailscale le da una IP propia a cada nodo.

### Lo que NO hacer

Detalle con el error textual en `references/no-hacer.md`.

| NO hacer | Problema que causó |
|---|---|
| Forward/publish de `users-service` (:8082) a la tailnet | Saltea el gateway: API de users expuesta + spoof de `X-User-Roles` |
| Tailnet sin ACL (todo abierto) | Cualquier nodo llega al micro directo: "sin `ports:`" no alcanza |
| `${TS_PING_IP:?}` por entorno para registrar | Compose valida todo antes de crear: sin la IP no arranca ni el sidecar que la daría |
| `extra_hosts:` / `dns:` con `network_mode: service:` | `conflicting options: ... and the network mode` (el daemon lo rechaza) |
| Dejar el resolver de Docker (`127.0.0.11`) | `NXDOMAIN` al resolver la infraestructura |
| Montar solo MagicDNS con servicios propios por nombre | Rompe la resolución de la BD/redis del micro |
| `TS_USERSPACE=true` (default) | Sin `tailscale0`: compartir namespace no da conectividad |
| Registrar solo `hostName` corto sin DNS en el gateway | `503` con registro `UP` |
| Probar el ingreso desde el mismo namespace del micro | Nunca pasa por el tailnet/ACL: da `OK` siempre (falso positivo) |
| Probar el ingreso desde un nodo offline | `HTTP 000` que parece falla del vínculo |
| Publicar `ports:` del micro | Headers `X-*` spoofeables |
| Levantar la app fuera del compose "para probar" | `Port <APP_PORT> already in use` |
| Placeholder del `.env.example` como `TS_AUTHKEY` | `invalid key: API key does not exist` |
| Commitear `.env`, auth key o `clientSecret` | Credenciales en el repo |
| Pegarle a `/actuator/health` en el puerto de app | `404`: el health vive en management (`APP_PORT+1`) |

## Paso 4 — Verificar

```sh
SIDECAR=mesh-<seg> MICRO=tpi-<seg> SERVICE_ID=<SERVICE-ID> \
  SERVER_HOST=<fqdn> SEG=<seg> APP_PORT=<app> MGMT_PORT=<app+1> \
  sh scripts/verificar-mesh.sh
```

El script chequea: sidecar `Running`, resolver, resolución MagicDNS, registro
(control del micro), pares `ip:puerto` en Eureka (duplicado = falla; varias IPs
distintas = WARN de réplicas), `:8082`/`:8081` cerrados para el micro, la ruta
pública **por el gateway** (la única prueba válida de ingreso), el JWKS por el
gateway, y el acceso directo al micro bloqueado desde el host.

**Antes de cada prueba negativa** el script corre un control positivo **del
mismo contexto**: desde el micro, Eureka `:8761` (el puerto que `tag:micros`
tiene permitido) valida `:8082`/`:8081`; desde el host (persona), el borde `:80`
valida el acceso directo al micro. Un control contra `:80` corrido *desde el
micro* fallaría siempre con la ACL puesta y dejaría los negativos en SKIP para
siempre. Si el control falla, el negativo es **SKIP**, nunca `OK`.

Criterios y pruebas negativas en `references/verificacion.md`.

## Paso 5 — Diagnóstico

| Síntoma | Causa probable |
|---|---|
| `404 route-not-found` | falta el `serviceId` en `GATEWAY_ALLOWLIST` |
| `503 + Retry-After` | el gateway no alcanza ninguna instancia: ruta de salida, ACL, o IP anunciada vieja |
| Registro `UP` pero `503` | es red, no disco |
| `401` | falta identidad o sesión vencida/superada (mirar el `type`) |
| `403 invalid-audience` | token de servicio emitido para otro destino |
| `NXDOMAIN` | falta el bind-mount de `resolv.conf` |
| `invalid key: API key does not exist` | auth key inválida/usada |
| Conexión directa al micro que **funciona** desde otro nodo | no hay ACL: aplicar `acl-ejemplo.hujson` |

## Cierre de la prueba (no olvidar)

1. **Rotar la auth key** efímera.
2. **Quitar cualquier forward/publish** de servicios internos (ej. :8082) que se
   haya agregado para el job JWKS.
3. Verificar que la ACL quede activa (conectar directo desde otro nodo debe
   fallar).
