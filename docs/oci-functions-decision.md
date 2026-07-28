# TM-015 - Evaluación de OCI Functions para preprocesamiento

## Objetivo

Evaluar la viabilidad de utilizar Oracle Cloud Infrastructure (OCI) Functions para realizar tareas de preprocesamiento dentro del proyecto TechMind.

---

## ¿Qué es OCI Functions?

OCI Functions es un servicio serverless de Oracle Cloud Infrastructure que permite ejecutar funciones bajo demanda sin administrar servidores. Las funciones pueden ejecutarse mediante eventos, API Gateway u otros servicios de OCI.

---

## Posibles usos en TechMind

Durante la investigación se identificaron los siguientes posibles escenarios:

- Validación de archivos antes del procesamiento.
- Conversión de formatos.
- Limpieza y normalización de datos.
- Procesamiento asíncrono de tareas pesadas.
- Automatización basada en eventos.

---

## Ventajas

- Escalabilidad automática.
- Pago por uso.
- No requiere administrar servidores.
- Integración con otros servicios de Oracle Cloud.
- Adecuado para procesos event-driven.

---

## Desventajas

- Incrementa la complejidad de la arquitectura.
- Requiere configuración adicional.
- Agrega dependencia del entorno OCI.
- No aporta beneficios relevantes para el flujo síncrono principal del Sprint 1.

---

## Recomendación

Para el Sprint 1 se recomienda **no utilizar OCI Functions como parte del flujo principal**.

La comunicación síncrona entre el backend y el servicio de inferencia es suficiente para cumplir los requerimientos actuales.

OCI Functions podrá evaluarse nuevamente en futuras versiones para tareas asíncronas o procesos de preprocesamiento independientes.

---

## Impacto

Esta decisión **no bloquea el desarrollo** del flujo principal del proyecto y mantiene una arquitectura más simple para la primera versión.

---

## Conclusión

Se recomienda posponer la incorporación de OCI Functions hasta que exista una necesidad concreta de procesamiento asíncrono o basado en eventos.