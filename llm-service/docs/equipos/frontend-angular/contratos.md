# Front End — Angular — contratos

> Este documento es el contrato completo y vigente con Front End — `18` ya no repite este
> detalle, solo indexa hacia acá. También incorpora
> [06 §6/§7](../../06-operacion-e-ingenieria.md#6-qué-ve-el-usuario-cuando-algo-falla) (estados
> de error y contrato del componente) y
> [15-sincronizacion-arquitectura-y-despliegue.md](../../15-sincronizacion-arquitectura-y-despliegue.md).

## Relación

No hay un endpoint que Front End consuma directo de nosotros — es una relación de UI: ellos
construyen las pantallas que exponen lo que nuestros endpoints/eventos devuelven. Todo lo que
falta de este lado está en [`pendientes.md`](pendientes.md).

## Estados que las pantallas tienen que poder mostrar

No es un endpoint nuevo — son los valores de enum que ya salen de los contratos de otros
equipos y que las pantallas (ver [`pendientes.md`](pendientes.md)) tienen que renderizar sin
inventar un estado nuevo:

| Pantalla | Enum a mostrar | Fuente |
|---|---|---|
| 1 — Chat del tutor | `completed \| blocked \| unavailable` | [`tema-05-desafios-practicos/contratos.md`](../tema-05-desafios-practicos/contratos.md) |
| 2 — Estado del evaluador | `queued \| running \| completed \| failed` (job) + `score_pendiente_diferido` | [`tema-03-motor-de-desafios/contratos.md`](../tema-03-motor-de-desafios/contratos.md) |
| 5 — Panel de moderación | `severidad: alta \| media \| baja` + `degradacion: prefiltro_solamente \| diferido` | [`tema-11-chat/contratos.md`](../tema-11-chat/contratos.md) |

El caso más flojo hoy es el 1: no hay copy ni pantalla acordada para `unavailable` — ver
[`tema-05-desafios-practicos/pendientes.md`](../tema-05-desafios-practicos/pendientes.md).

## Qué ve el alumno cuando algo falla (copiado de [06 §6](../../06-operacion-e-ingenieria.md#6-qué-ve-el-usuario-cuando-algo-falla))

Nunca lo escribimos antes, y son las pantallas que más se improvisan.

| Situación | Qué ve el alumno | Requerimiento |
|---|---|---|
| **Tutor caído** | *"La asistencia no está disponible en este momento. Podés resolver y entregar igual: **esto no afecta tu puntaje**."* | RF-IA-27 — score neutro |
| **Guardarraíl bloqueó la respuesta** | Se regenera en silencio. **El alumno no se entera** | RF-IA-20 |
| **Jailbreak detectado** | Mensaje genérico de rechazo, **siempre el mismo**, sin explicar el motivo | RF-IA-10 |
| **Cuota agotada** | *"Alcanzaste el límite de N consultas para este desafío."* | RF-IA-22 |
| **Evaluación diferida** | *"Tu entrega fue registrada. El puntaje de uso de IA se calculará en breve."* | RF-IA-27 |
| **Mensaje de chat bloqueado** | *"Tu mensaje no se envió por las normas de convivencia."* Sin detalle | RF-CHT-12 |

> **El mensaje de rechazo por jailbreak se diseña una sola vez y se usa para todos los casos.**
> Si varía según el motivo, el alumno aprende a mapear el detector probando.

## El componente Angular del tutor: estados y contrato (copiado de [06 §7](../../06-operacion-e-ingenieria.md#7-el-componente-angular-estados-y-contrato))

Lo construyen ustedes y no lo habíamos especificado.

### Los siete estados

| Estado | Qué muestra |
|---|---|
| `inactivo` | Caja de texto vacía |
| `enviando` | El mensaje del alumno ya visible, spinner |
| **`pensando`** | **El estado que más importa.** Como no hay streaming (RF-IA-20), es la única señal de que algo pasa |
| `respondido` | La respuesta completa |
| `bloqueado` | Mensaje genérico |
| `sin_servicio` | El aviso de RF-IA-27 |
| `cuota_agotada` | El límite alcanzado |

**El estado `pensando` es el que define la experiencia.** Sin streaming, el alumno mira una
pantalla quieta hasta 2 segundos. Poner un indicador con progreso aparente —"buscando en el
material del curso…", "preparando una pista…"— es la diferencia entre que se sienta roto o vivo.

### Cuatro reglas del componente

1. **Llama al API Gateway**, no a nuestro servicio directo. Es regla no negociable de la cátedra.
2. **No conoce el nombre del modelo.** Ni lo muestra ni lo recibe.
3. **Envía el `desafio_id` y el código actual**; el `alumno_id` y el `curso_cohorte_id` los deriva
   el servidor de la sesión — **nunca del cliente**.
4. **Muestra el contador de cuota restante** (RF-IA-22): que el alumno sepa cuántas consultas le
   quedan cambia cómo las usa, y eso es pedagógicamente deseable.

## Nota aparte, no es contrato

`15-sincronizacion-arquitectura-y-despliegue.md` sincroniza con la unidad curricular de Front
End (Programación IV) — nginx como gateway, rolling update, etc. **No define contratos ni
requisitos nuestros**, es contenido pedagógico compartido que además nutre el glosario de
infraestructura (`11-glosario-y-metadata.md`).
