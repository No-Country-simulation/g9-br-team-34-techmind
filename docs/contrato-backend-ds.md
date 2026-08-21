# Contrato REST/JSON — Backend ↔ Servicio de Inferencia

**Ticket:** TM-006
**Versión:** 1.0
**Estado:** ACORDADO — aprobado por Backend y Ciencia de Datos
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
| URL base | Externalizada en `application.properties` → `techmind.inference-service.base-url` |
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

> **Discrepancia con el código actual:** `application.properties` ya define
> `techmind.inference-service.timeout-ms=5000` (5 s). Ese valor incumple el RNF-01
> (respuesta total < 3 s) y debe reducirse a 2500 ms antes de TM-031 (integración real).
> Escalar a TM-058 (verificación de rendimiento).
>
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

| Área             | Responsable    | Fecha       | Estado     |
| Backend          | Esteban        | 2026-07-28  | Aprobado   |
| Ciencia de Datos | [Luigi Huánuco ,Sofia Alferez, JorgeM ] | 2026-07-28  | Aprobado   |

---

## 11. Contrato del endpoint de métricas (S5-14)

A diferencia del resto del documento, esta sección describe un contrato
**Backend → cliente**, no Backend ↔ Servicio de Inferencia. Se documenta acá por
pedido del criterio de aceptación de S5-14, para que todos los contratos del
proyecto queden en un solo archivo.

### 11.1 Endpoint

| Atributo | Valor |
|---|---|
| Método / Ruta | `GET /api/v1/metricas` |
| Parámetros | ninguno |
| `Accept` | `application/json` |
| Respuesta | siempre `200 OK` |

Con la base vacía devuelve el tablero en ceros y las listas vacías, no un 404:
un repositorio sin contenidos es un estado válido del producto.

### 11.2 Respuesta

```json
{
  "totalContenidos": 42,
  "totalCategorias": 5,
  "confianzaMedia": 0.83,
  "longitudMediaTexto": 3210,
  "palabrasClavePorContenido": 4.2,
  "palabrasClaveUnicas": 87,
  "totalRelaciones": 63,
  "confianzaPorCategoria": [
    { "categoria": "mobile", "confianzaMedia": 0.61, "cantidad": 4 },
    { "categoria": "backend", "confianzaMedia": 0.91, "cantidad": 18 }
  ],
  "distribucionConfianza": [
    { "etiqueta": "Menos de 50%", "desde": 0.0,  "hasta": 0.5,  "cantidad": 2 },
    { "etiqueta": "50% a 70%",    "desde": 0.5,  "hasta": 0.7,  "cantidad": 6 },
    { "etiqueta": "70% a 85%",    "desde": 0.7,  "hasta": 0.85, "cantidad": 14 },
    { "etiqueta": "85% o más",    "desde": 0.85, "hasta": 1.01, "cantidad": 20 }
  ],
  "procesadosPorDia": [
    { "fecha": "2026-08-09", "cantidad": 12 },
    { "fecha": "2026-08-10", "cantidad": 30 }
  ],
  "palabrasClaveTop": [
    { "palabraClave": "java", "cantidad": 15 },
    { "palabraClave": "api rest", "cantidad": 11 }
  ],
  "calculadoEn": "2026-08-11T03:20:00Z"
}
```

### 11.3 Campos

| Campo | Tipo | Notas |
|---|---|---|
| `totalContenidos` | number | Contenidos analizados |
| `totalCategorias` | number | Categorías distintas con al menos un contenido |
| `confianzaMedia` | number | Rango [0, 1], dos decimales |
| `longitudMediaTexto` | number | Caracteres, redondeado |
| `palabrasClavePorContenido` | number | Promedio, dos decimales |
| `palabrasClaveUnicas` | number | Términos distintos, comparados en minúsculas |
| `totalRelaciones` | number | Pares de contenidos relacionados; cada par cuenta una vez |
| `confianzaPorCategoria` | array | Ordenado de menor a mayor confianza |
| `distribucionConfianza` | array | Siempre 4 tramos, incluso con la base vacía |
| `procesadosPorDia` | array | `fecha` en `yyyy-MM-dd`, UTC, orden ascendente |
| `palabrasClaveTop` | array | Máximo 12, orden descendente por frecuencia |
| `calculadoEn` | string | ISO-8601 en UTC |

**Ningún campo llega nulo.** Las agregaciones SQL devuelven `null` sobre conjunto
vacío; el servicio las normaliza a cero antes de responder, para que el cliente no
tenga que defenderse de ausencias.

### 11.4 Criterio de `totalRelaciones`

Dos contenidos están relacionados si comparten **categoría** y **al menos una
palabra clave** (comparada en minúsculas). Es el mismo criterio de
`GET /api/v1/contenidos/{id}/relacionados`, de modo que ambos números son
coherentes entre sí.

### 11.5 Fuera de alcance

**Documentos exitosos frente a documentos con error** no se expone: la entidad
sólo se persiste cuando el análisis salió bien, así que no hay estado ni registro
de intentos fallidos del cual derivarlo. Requiere un campo nuevo o una tabla de
auditoría; queda registrado en el spike S5-15 como descartado.

---

## 12. POST /api/v1/contenidos/lote-pdf (S5-09)

Procesa un lote de archivos PDF en una sola operación. Reutiliza la
extracción existente (Apache PDFBox + fallback/limpieza con Gemini,
ver `ExtraccionArchivoService`) y el mismo camino de clasificación que
`POST /api/v1/contenidos` — el lote es, en esencia, N llamadas a ese
mismo flujo, con manejo de errores independiente por archivo.

### Request

```
POST /api/v1/contenidos/lote-pdf
Content-Type: multipart/form-data

archivos: [archivo1.pdf, archivo2.pdf, ...]   (campo repetido, uno por archivo)
```

### Response 200

```json
{
  "totalArchivos": 3,
  "procesadosExitosos": 2,
  "procesadosConError": 1,
  "resultados": [
    {
      "nombreArchivo": "guia-spring-boot.pdf",
      "estado": "PROCESADO",
      "resultado": {
        "id": "...",
        "titulo": "...",
        "categoria": "Backend",
        "probabilidad": 0.89,
        "informacion_adicional": ["Java", "Spring Boot"]
      },
      "mensajeError": null
    },
    {
      "nombreArchivo": "documento-corrupto.pdf",
      "estado": "ERROR",
      "resultado": null,
      "mensajeError": "No se pudo extraer contenido suficiente del archivo"
    }
  ]
}
```

### Procesamiento independiente por archivo

Si un archivo falla (extracción y fallback de Gemini agotados,
archivo no es PDF, o el resultado final no pasa validación), el resto
del lote se sigue procesando igual — no se aborta la operación
completa por un solo archivo problemático. El detalle del error queda
en el campo `mensajeError` de ese archivo específico.

### Límites

| Límite | Valor por defecto | Property |
|---|---|---|
| Archivos por lote | 20 | `techmind.pdf-lote.max-archivos` (env: `PDF_LOTE_MAX_ARCHIVOS`) |
| Tamaño por archivo | 20MB | `spring.servlet.multipart.max-file-size` |
| Tamaño total de la request | 100MB | `spring.servlet.multipart.max-request-size` |

Un lote que exceda el máximo de archivos se rechaza completo con
`400 Bad Request`, sin procesar ninguno — a diferencia del caso de un
archivo individual fallido dentro de un lote válido, que sí se reporta
por separado sin afectar a los demás.

### Errores

| Status | Causa |
|---|---|
| 400 | Lote vacío, o supera el máximo de archivos permitido |
| 200 (con detalle por archivo) | Archivo no es PDF, extracción/clasificación fallida para ese archivo puntual |
