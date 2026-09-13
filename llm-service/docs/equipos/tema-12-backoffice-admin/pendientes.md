# Tema 12 — Backoffice / ADMIN — pendientes

> Este documento es la fuente completa de lo pendiente con Tema 12. Antecedentes:
> [17 I-15](../../17-mapa-de-integracion.md#8-lo-que-estos-diagramas-dejaron-a-la-vista),
> [04-funciones-de-ia.md](../../04-funciones-de-ia.md) líneas 889-900.

## 🔴 Cruzado — I-15: quién construye la pantalla del golden set

Actualmente sin dueño claro — cuatro documentos le dan cuatro dueños distintos. Es el ítem que
destraba el plazo más largo del proyecto.

**Decide:** Product Owner, con Front End, Tema 07 y Tema 12 en la sala — ver
[`docs/entregas/sesion-integracion-agenda.md`](../../entregas/sesion-integracion-agenda.md) ítem 7 y
[`product-owner/pendientes.md`](../product-owner/pendientes.md).

## 🟡 Nuevo — distinguir "no corrió" de "corrió y no pasó"

Al documentar resiliencia en `contratos.md` aparece que no hay evento para "la calibración
falló por infraestructura" (proveedor caído, timeout) — solo existe
`calibracion_fuera_de_tolerancia`, que es un resultado de PAR-14, no un error técnico. Tema 12
hoy no tiene forma de diferenciar ambos casos desde el evento.

## 🟡 Debate abierto — quién construye la pantalla de calibración

Tema 12 dice "gestión del proveedor LLM, exclusiva de ADMIN"; Tema 07 tiene "configuración
centralizada" como opción propia. Doc 04 recomienda que la construya **Tema 07**, porque
"nadie más entiende la rúbrica ni por qué existen los dos bloqueos" (un docente no ve puntajes
de otro curso, ni el puntaje del modelo antes de puntuar manualmente).

**Recomendación de doc 08 B-6:** Tema 12 dueño de la **pantalla y el dato** del proveedor;
Tema 07 dueño de **aplicarlo** en cada llamada. Acordar el contrato de lectura que Tema 12
necesita (estado de calibración, deriva, costo por curso) temprano.

## 🟡 Nuevo — límites de IA por alumno vs. privacidad del panel agregado

Si Tema 12 pasa a ser dueño de la pantalla que configura, por alumno y por día, la cantidad de
usos y la cantidad de tokens (`PUT /api/v1/operations/quotas/student/{studentId}`, ver
[`contratos.md`](contratos.md) y [08 P-12](../../08-decisiones-y-pendientes.md)), esa pantalla
necesita identificar al alumno. Esto convive con una restricción ya escrita en la misma épica: el
panel agregado de costos y fallas (`LLM-S09-H01`) prohíbe explícitamente exponer `studentId`
(su CA6, [`historias/ep-07/h01.md`](../../historias/ep-07/h01.md)) por privacidad.

Son dos recursos distintos (uno agregado y de solo lectura, el otro puntual y de escritura), pero
falta que **Product Owner + DPO** confirmen: quién puede ver/limitar a qué alumno, si hace falta
un registro de auditoría adicional por tratarse de un dato que identifica a un alumno, y si esto
cambia algo del contrato de datos ya cerrado en doc 07.
