# 06 — Operación, calidad y pruebas

Esta carpeta indica **cómo sabemos que el servicio funciona correctamente y cómo se sostiene en ejecución**. Se consulta durante el desarrollo, no solo antes de la demo o el deploy.

## Qué vas a encontrar

1. [Operación e ingeniería](01-operacion-e-ingenieria.md): colas, caché, proveedores, observabilidad, degradación, runbooks y experiencia ante fallas.
2. [Convenciones de cobertura](02-convenciones-de-cobertura.md): qué probar y cómo interpretar cobertura y calidad.
3. [Matriz de pruebas de infraestructura](03-matriz-de-pruebas-de-infraestructura.md): pruebas necesarias para componentes y dependencias de plataforma.
4. [Estado de implementación](04-estado-de-implementacion/README.md): evidencia de código y diferencia entre el objetivo documentado y lo que ya existe.
5. [Operación y pruebas V2](../05-operacion-y-pruebas.md): referencia breve de criterios verificables del MVP.

## Cómo leerla

Leé `01` para conocer el comportamiento operativo de cualquier capacidad. Según el cambio, usá `02` y `03` para diseñar pruebas antes de implementar. Consultá `04` al estimar o afirmar que algo está terminado: es el lugar que evita confundir planificación, contratos propuestos y código real.

## Uso correcto

Una funcionalidad no queda terminada por compilar ni por responder en local. Debe respetar contratos, seguridad, pruebas aplicables, observabilidad y recuperación. Si agrega asincronía o proveedor, actualizá también métricas, health/readiness y runbook según corresponda.
