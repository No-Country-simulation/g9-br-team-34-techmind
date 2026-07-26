# Contrato REST/JSON — Backend ↔ Servicio de Inferencia

**Ticket:** TM-006
**Versión:** 1.1
**Estado:** Contenido acordado con Ciencia de Datos — pendiente de revisión formal del data-analyst (regreso de viaje)
**Referencia:** Requerimientos_TechMind.docx §6.14, §7.1 · Arquitectura_TechMind.docx §6

> Este documento es un **acuerdo entre dos áreas**, no una decisión unilateral de Backend.
> Las tres decisiones bloqueantes (D-01, D-02, D-03) ya fueron respondidas por Ciencia de Datos (ver sección 0).

---

## 0. Decisiones acordadas con Ciencia de Datos

Estas tres preguntas fueron respondidas por el equipo de Ciencia de Datos. Quedan pendientes
solo de revisión formal del documento (el data-analyst lo revisa a su regreso de viaje).

| # | Pregunta | Respuesta |
|---|----------|-----------|
| D-01 | ¿Qué enfoque produce el modelo? | **Modelo de clasificación.** Produce una `categoria` y su `probabilidad` de confianza. |
| D-02 | ¿Qué contiene `informacion_adicional`? | **Palabras clave** extraídas del texto (las más repetidas). Cantidad configurable de 1 a 20. Es una lista de strings. |
| D-03 | ¿Cuál es el conjunto cerrado de categorías? | **7 categorías:** Backend, Base de Datos, DevOps, Frontend, Machine Learning, Mobile, Seguridad. |

**Implicancias confirmadas para los DTOs:**

- Al ser clasificación, el campo `probabilidad` existe y es válido en `ContenidoResponseDTO`.
- `informacion_adicional` es `List<String>`, tal como está implementado.
- Las 7 categorías son la fuente para `GET /api/v1/categorias` (TM-038) y para los tests.

---

## 1. Convención de nombres de campos (JSON)

La documentación de origen (§6.8) mezcla `informacion_adicional` (snake_case) y
`fechaProcesamiento` (camelCase) en la misma respuesta. Es una inconsistencia y debe
resolverse aquí.

**Decisión adoptada:**

- **API pública (Backend → cliente/frontend):** `camelCase`, salvo `informacion_adicional`,
  que se mantiene en snake_case mediante `@JsonProperty` por compatibilidad con el ejemplo
  literal del brief del hackathon (los evaluadores comparan contra ese ejemplo).
- **Contrato interno (Backend → servicio Python):** `snake_case` completo, por ser la
  convención natural de Python (PEP 8).

**Alternativa descartada:** camelCase total en la API pública. Descartada porque el enunciado
del hackathon muestra explícitamente `informacion_adicional` en su ejemplo de salida y no
conviene divergir de un artefacto que los jurados van a mirar directamente.

> Si el equipo decide lo contrario, cambiar aquí y quitar el `@JsonProperty`
> de `ContenidoResponseDTO` y `ModeloPredictResponseDTO`.

---

## 2. Endpoint del servicio de inferencia

| Atributo | Valor |
|---|---|
| Método | `POST` |
| Ruta | `/predict` |
| URL base | Externalizada en `application.yml` → `techmind.inferencia.base-url` |
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| Autenticación | Ninguna (servicio no expuesto a internet — §6.18) |
| Exposición | Solo accesible desde el backend (red interna / security list de OCI) |

---

## 3. Request: Backend → Servicio de Inferencia

```json
{
  "titulo": "Introducción a Spring Boot",
  "texto": "En este contenido se presentan los conceptos básicos para la creación de APIs REST utilizando Java y Spring Boot."
}
```

| Campo | Tipo | Obligatorio | Notas |
|---|---|---|---|
| `titulo` | string | Sí | Ya validado por el backend (no vacío, ≤ 200 chars) |
| `texto` | string | Sí | Ya validado por el backend (20–10 000 chars) |

**El servicio de inferencia no revalida reglas de negocio.** Asume que el backend ya validó
(§7.1). Su única responsabilidad es predecir.

---

## 4. Response exitosa: Servicio de Inferencia → Backend

**HTTP 200 OK**

```json
{
  "categoria": "Backend",
  "probabilidad": 0.89,
  "informacion_adicional": ["Java", "Spring Boot", "API REST"]
}
```

| Campo | Tipo | Obligatorio | Notas |
|---|---|---|---|
| `categoria` | string | Sí | Una de las 7 de D-03: Backend, Base de Datos, DevOps, Frontend, Machine Learning, Mobile, Seguridad |
| `probabilidad` | number | Sí | Rango [0.0, 1.0]. Confianza de la clasificación |
| `informacion_adicional` | array de string | Sí | Palabras clave extraídas del texto (1 a 20). Puede venir vacío `[]`, nunca `null` |

**Regla:** ante ausencia de datos, el servicio devuelve array vacío, no `null`.
Evita `NullPointerException` en el mapeo del backend.

---

## 5. Response de error: Servicio de Inferencia → Backend

```json
{
  "error": "texto_vacio_tras_limpieza",
  "detalle": "El texto no contiene tokens útiles luego del preprocesamiento."
}
```

| HTTP | Código `error` | Significado | Traducción del backend al cliente |
|---|---|---|---|
| 400 | `entrada_invalida` | Payload malformado | 503 (es un bug de contrato, no del cliente) |
| 422 | `texto_vacio_tras_limpieza` | Válido pero no procesable | **422** `ProcesamientoException` (TM-033) |
| 500 | `error_interno_modelo` | Fallo al ejecutar el modelo | **503** `ModeloServiceException` (TM-032) |
| 503 | `modelo_no_cargado` | El modelo aún no terminó de bajar de OCI | **503** `ModeloServiceException` (TM-032) |

**Regla del backend:** ningún error del servicio de inferencia se propaga como 500 genérico
al cliente final (§6.11).

---

## 6. Timeouts y reintentos

| Parámetro | Valor | Justificación |
|---|---|---|
| Timeout de conexión | 1 s | Falla rápido si el servicio está caído |
| Timeout de respuesta | **2,5 s** | RNF-01 exige respuesta total < 3 s |
| Reintentos | **1**, solo ante conexión rechazada o HTTP 5xx | — |
| Reintento ante timeout | **NO** | Duplicaría el tiempo y garantizaría incumplir RNF-01 |

> **Corrección respecto de §6.14:** el documento de requerimientos sugiere timeout de 5 s
> con reintento. Combinado da ~10 s en el peor caso, contra un RNF-01 de 3 s. Los valores
> de esta tabla son los que rigen. Escalar a TM-058 (verificación de rendimiento).

---

## 7. Health check del servicio de inferencia

| Atributo | Valor |
|---|---|
| Método / Ruta | `GET /health` |
| Respuesta 200 | `{"estado": "ok", "modelo_cargado": true}` |
| Respuesta 503 | `{"estado": "degradado", "modelo_cargado": false}` |

Usado por TM-043 (pruebas de conectividad end-to-end) y para diagnosticar en la demo.

---

## 8. Versionado del contrato

- Cualquier cambio en este documento incrementa la versión y se comunica en el canal del equipo.
- **Cambio incompatible** (renombrar/eliminar un campo, cambiar un tipo): requiere PR con
  revisión de Backend **y** de Ciencia de Datos.
- **Cambio compatible** (agregar un campo opcional): basta con avisar.
- El backend ignora campos desconocidos en la respuesta (`FAIL_ON_UNKNOWN_PROPERTIES = false`),
  de modo que DS puede agregar campos sin romper el backend.

---

## 9. Ejemplos de referencia (para el mock de TM-019 y los tests)

### 9.1 Caso feliz

```
POST /predict
{"titulo":"Introducción a Spring Boot","texto":"En este contenido se presentan los conceptos básicos para la creación de APIs REST utilizando Java y Spring Boot."}

200 OK
{"categoria":"Backend","probabilidad":0.89,"informacion_adicional":["Java","Spring Boot","API REST"]}
```

### 9.2 Contenido no procesable

```
POST /predict
{"titulo":"Prueba","texto":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}

422 Unprocessable Entity
{"error":"texto_vacio_tras_limpieza","detalle":"El texto no contiene tokens útiles luego del preprocesamiento."}
```

### 9.3 Modelo aún no disponible

```
POST /predict

503 Service Unavailable
{"error":"modelo_no_cargado","detalle":"Descarga del artefacto desde OCI Object Storage en curso."}
```

---

## 10. Firma

| Área | Responsable | Fecha | Estado |
|---|---|---|---|
| Backend | _(pendiente)_ | | |
| Ciencia de Datos | _(pendiente)_ | | |
