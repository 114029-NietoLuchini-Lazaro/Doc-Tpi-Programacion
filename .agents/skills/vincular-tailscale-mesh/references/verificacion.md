# Verificación del vínculo a la mesh

## Regla de oro

El **ingreso** (plataforma → micro) se prueba **por el gateway**. Una conexión
directa a la IP del micro desde el mismo namespace del micro **no prueba nada**
(el paquete nunca sale a la tailnet ni cruza la ACL) y da `200` siempre.
Desde **otro nodo**, con ACL aplicada, la conexión directa debe **fallar**.

## Control positivo obligatorio (por contexto)

Toda prueba negativa va precedida por un control positivo **del mismo contexto**:
el puerto del control tiene que ser uno que ese nodo tenga permitido por la ACL.

| Desde dónde corre | Control positivo | Negativos que valida |
|---|---|---|
| Contenedor del micro (`tag:micros`) | `tpi-plataforma:8761` | `:8082` y `:8081` |
| Host en PowerShell (persona) | `tpi-plataforma:80` | directo a `<micro>:<APP_PORT>` |

Si el control falla, el negativo es **SKIP**, nunca `OK`: un intento que se cae
por la razón equivocada no prueba que la ACL funcione. El control del micro es
además el mejor canario: si falla, el micro tampoco puede registrarse.

## Chequeos (en orden)

| # | Chequeo | Esperado |
|---|---|---|
| 1 | `docker exec <sidecar> tailscale status` + `tailscale ip -4` | nodo `online`, `BackendState` Running, IP `100.x` |
| 2 | `docker exec <micro> cat /etc/resolv.conf` | `100.100.100.100`; caso B: además `127.0.0.11` y **ambos** `nslookup` OK |
| 3 | `docker exec <micro> nslookup <TS_SERVER_HOST>` | resuelve a la IP del nodo plataforma |
| 4 | **Control del micro**: `docker exec <micro> wget ... :8761/eureka/apps` | responde |
| 5 | `.../eureka/apps/<SERVICE_ID>` | sin pares `ip:puerto` duplicados, y **alguna** instancia anuncia la IP actual. Varias instancias con IPs distintas → WARN (réplicas u huérfanas) |
| 6 | **Negativos del micro** (con control): `:8082` y `:8081` | **no responden** |
| 7 | `GET http://<TS_SERVER_HOST>:8080/api/<seg>/public/...` | `200` + `X-Request-Id` — la prueba real de ingreso vía gateway |
| 8 | `curl http://<TS_SERVER_HOST>:8080/.well-known/jwks.json` desde el micro | `200`, o job JWKS desactivado |
| 9 | **Control del host** (`:80`) + **negativo**: directo a `<ip-micro>:<APP_PORT>` | control responde y el directo **falla** (timeout) |

## Pruebas negativas (obligatorias)

1. Ruta pública con `X-User-Roles: ADMIN` inventado → 200 y el rol **no**
   aparece en la respuesta.
2. Ruta privada sin identidad → 401.
3. Token de servicio con `audience` de otro destino → 403.
4. `:8082` y `:8081` desde el micro → no responden (control `:8761` previo).
5. Conexión directa al micro desde el host → bloqueada (control `:80` previo).

## Criterio de "terminado"

- Sidecar `Running`, **sin pares `ip:puerto` duplicados** en Eureka y **alguna
  instancia anunciando la IP actual**. Varias instancias con IPs distintas es
  WARN (réplicas legítimas u huérfanas sin expirar), no error.
- El gateway rutea y devuelve 200 con `X-Request-Id` correlacionable.
- Los negativos fallan **con sus controles positivos pasando**.

## Suposición sobre réplicas

El script asume que **una instancia por `serviceId`** salvo que se vean varias
IPs. Si el equipo va a escalar, cada réplica necesita **su propio sidecar** (su
propio namespace y su IP) y un `instance-id` único (`APP_INSTANCE_ID`): con eso
nunca hay dos instancias con el mismo `ip:puerto` y el chequeo 5 las trata como
WARN. `docker compose up --scale` **no** sirve con este patrón (ver
`no-hacer.md`, réplicas).
