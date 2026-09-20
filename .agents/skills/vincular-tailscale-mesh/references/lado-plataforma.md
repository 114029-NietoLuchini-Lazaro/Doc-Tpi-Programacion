# Lado plataforma — lo que hay que armar y verificar del otro extremo

Fue **lo más difícil de la prueba**: el micro puede estar perfecto y el gateway
igual no lo alcanza. Esta es la parte que hay que pedirle/coordinar con quien
administra la tailnet y el stack `tpi-compose`.

## 1. Nodo Tailscale de la plataforma

- Un contenedor `tailscale/tailscale` propio (kernel: `TS_USERSPACE=false`,
  `/dev/net/tun`, `NET_ADMIN`/`NET_RAW`, **volumen de estado**), igual que el
  sidecar del micro.
- Auth key con **`tag:plataforma`** (queda gobernado por la ACL).
- Fijar la versión de imagen (no `:latest`).

### `tailscale serve` en 1.102.3

En la versión probada (`v1.102.3`), `TS_SERVE_CONFIG` declarativo **no se
aplicó** (la consola del contenedor posteaba config vacía). El `serve` lo aplica
un servicio `one-shot` con:

```sh
tailscale serve --bg --http=80 http://localhost:3000
```

**Si el contenedor Tailscale se reinicia, hay que re-correr ese one-shot**
(`docker compose up tailscale-serve`): el `--bg` no sobrevive al restart del
nodo. Anotarlo en el runbook de la plataforma.

## 2. El gateway tiene que poder SALIR al tailnet (el punto clave)

`tailscale serve` resuelve solo el tráfico **entrante**. Para que el gateway
resuelva `lb://<serviceId>` contra la IP de un micro remoto, el contenedor del
api-gateway tiene que **iniciar** conexiones hacia `100.64.0.0/10`.

| Opción | Cómo |
|---|---|
| Aplicada en la prueba | `api-gateway` comparte el namespace del contenedor Tailscale (`network_mode: "service:tpi-tailscale"`), y ese contenedor se conecta a las redes de plataforma (`tpi-edge`, `tpi-platform`, `tpi-data`) para que el gateway siga viendo nginx/users/redis. |
| Alternativa | Tailscale a nivel host: los contenedores bridge salen por ruteo del host. |

**Síntoma si falta**: registro `UP` en Eureka + `503` permanente ("El servicio
'X' no está respondiendo"). No es un problema del micro.

## 3. Qué NO exponer

- `mysql`, `redis`, `kafka`: jamás a la tailnet.
- `users-service` (`:8082`): no. El JWKS va **por el gateway**.
  - La ruta existe en el código del gateway: `SecurityConfig` deja
    `/.well-known/**` en `permitAll`, `PublicRouteMatcher` la trata como pública
    y `application.yml` tiene la ruta estática hacia `lb://users-service`.
  - Falta la confirmación en vivo (`curl`), pero si falla es **red**, no
    configuración del gateway.
- Superficie de la plataforma hacia los equipos, y nada más: **nginx :80**
  (humanos), **eureka :8761** (registro), **gateway :8080** (R2).

## 4. ACL y tags

- Aplicar `references/acl-ejemplo.hujson`. En Tailscale **lo que no está en
  `acls` queda denegado**: no se escriben reglas de `deny` (el editor las
  rechaza). La política trae un bloque **`tests`** que la consola ejecuta al
  guardar.
- **Las llaves primero**: las auth keys se generan **con el tag** y quien las
  crea debe figurar en `tagOwners`. Los nodos que **ya existen no toman el tag
  solos**: hay que re-autenticarlos (borrar estado y levantar de nuevo con la
  key tagged). Activar la ACL sin eso **corta la mesh**.
- **Verificación negativa** (obligatoria, y con control positivo): desde un nodo
  que no sea la plataforma, `nc -z <ip-micro> <APP_PORT>` debe **fallar**;
  `SERVER_HOST:80` desde ese mismo nodo debe **funcionar**. Si el control falla,
  el nodo no tiene tailnet: el resultado es SKIP, no OK.

## 5. Pendientes en el compose central (`tailscale-mesh`)

Al cierre de esta revisión, el compose central **todavía** tiene:

| Pendiente | Detalle |
|---|---|
| Forward de `:8082` | línea 83 — expone users-service a la tailnet |
| Imagen `:latest` | fijar `v1.102.3` |
| Sin volumen de estado | el nodo Tailscale pierde identidad al recrear |

**Orden obligatorio** (no invertir):

1. Con el micro **ya apuntando** `JWKS_URL` al gateway (`:8080`), levantar y
   correr `verificar-mesh.sh`.
2. Confirmar la ruta: `curl http://<SERVER_HOST>:8080/.well-known/jwks.json`.
3. **Recién entonces** sacar el forward de `:8082`.
4. Aplicar la ACL (con sus `tests`) y las keys tagged.

Si se saca el forward antes, el job JWKS del micro falla en el medio.

## 6. Checklist de coordinación

- [ ] Tags y ACL aplicados, con keys re-autenticadas (y probados en negativo +
      control positivo).
- [ ] `serve` del borde aplicado y **re-aplicado tras restart del nodo**.
- [ ] Eureka y gateway alcanzables desde la tailnet.
- [ ] El gateway tiene ruta de **salida** al tailnet.
- [ ] `serviceId` en `GATEWAY_ALLOWLIST`.
- [ ] Ningún forward/publish de servicios internos (ej. `:8082`).
- [ ] Ruta del JWKS por el gateway confirmada con `curl`.
