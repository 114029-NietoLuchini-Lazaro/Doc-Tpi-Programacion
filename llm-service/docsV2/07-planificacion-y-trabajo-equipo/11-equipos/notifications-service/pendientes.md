# Notifications-service — pendientes (EP-08 moderación)

> Contexto: [`moderacion-pendientes-chat-service.md`](../../../contracts/moderacion-pendientes-chat-service.md) §4 ·
> historia [LLM-S12-H02 (h04)](../../09-epicas-historias-tareas-sprints/historias/ep-08/h04.md), CA5.

`llm-service` ya notifica cuando un docente resuelve un incidente como `REVERSED`, con dos canales reales:
`POST` por el Gateway a `/api/notifications/v1/moderation-events` (ruta **supuesta**, configurable con
`NOTIFICATIONS_MODERATION_EVENTS_PATH`) y evento Kafka `MESSAGE-UNBLOCKED` (topic `moderation-events`, key
`courseId`). Con `NOTIFICATIONS_ENABLED=false` corre en modo mock (solo log).

## 🟡 Acuerdo cruzado — a confirmar con el equipo de notificaciones

| # | Pregunta |
|---|---|
| N1 | ¿La ruta y el payload actuales (`eventId`, `messageId`, `incidentId`, `courseId`, `userId`, `resolvedBy`, `resolution`, `resolutionReason`) son los que esperan, o prefieren solo Kafka? |
| N2 | ¿Qué texto/plantilla reciben los alumnos para `REVERSED` y `CONFIRMED`? Hoy solo se notifica `REVERSED`. |
| N3 | Política ante fallo: hoy es *best effort* (se loguea y no se revierte la resolución). ¿Alcanza? |
