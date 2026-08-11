# TM-052 — Pruebas de aceptación end-to-end del MVP

> **Issue:** [#53](https://github.com/No-Country-simulation/g9-br-team-34-techmind/issues/53)
> **Sprint:** 4 · **Tipo:** Test · **Prioridad:** Critical

## 1. Objetivo

Ejecutar y documentar pruebas de aceptación end-to-end sobre todas las
funcionalidades obligatorias (RF-01 a RF-06) y las opcionales efectivamente
implementadas (RF-09 lote, RF-10 relacionados, RF-11 persistencia, RF-12
categorías), corrigiendo cualquier defecto encontrado antes del cierre del
sprint.

## 2. Alcance y mapeo de requerimientos

| Requerimiento | Endpoint | Casos |
|---|---|---|
| RF-01/RF-02 (ingesta y clasificación) | `POST /api/v1/contenidos` | 1–8 |
| RF-11 (persistencia + consulta individual) | `GET /api/v1/contenidos/{id}` | 19–21 |
| RF-06/RF-11 (listado, filtro, búsqueda, paginación) | `GET /api/v1/contenidos` | 9–18 |
| RF-10 (contenidos relacionados) | `GET /api/v1/contenidos/{id}/relacionados` | 22–25 |
| RF-12 (resumen por categoría) | `GET /api/v1/categorias` | 26 |
| RF-09 (procesamiento por lote CSV) | `POST /api/v1/contenidos/lote` | 27–33 |
| RF-06 (eliminación) | `DELETE /api/v1/contenidos/{id}` | 34–36 |
| RNF (salud del servicio) | `GET /actuator/health` | 37 |

## 3. Entorno de ejecución

- **Backend** (Spring Boot): jar `api-techmind-0.0.1-SNAPSHOT.jar` en `localhost:8080`,
  perfil `dev`, H2 en memoria.
- **Servicio de inferencia real** (ml-service, FastAPI): `localhost:8000`,
  modelo cargado desde `ml-service/models/model.joblib`.
- **Batería:** `scripts/aceptacion/runner.sh`. Evidencia de un run completo
  verificado: `scripts/aceptacion/evidencia.txt`.

## 4. Resultado: 37/37 casos OK

Batería de 37 casos; el resumen de códigos HTTP obtenidos:

| HTTP | Cantidad | Casos |
|---|---|---|
| 201 Created | 3 | 1–3 |
| 200 OK | 12 | 9–10, 14–16, 19, 22–23, 26–28, 37 |
| 204 No Content | 1 | 34 |
| 400 Bad Request | 17 | 4–8, 11–13, 17–18, 21, 25, 29–32, 36 |
| 404 Not Found | 3 | 20, 24, 35 |
| 413 Payload Too Large | 1 | 33 |

Todas las respuestas usan `ErrorResponseDTO` con formato uniforme.

## 5. Defectos encontrados y corregidos

El estado final de la batería se alcanzó tras corregir cuatro defectos reales
(dos en el runner y dos en el backend). Los defectos de backend:

### 5.1 Parsee de CSV conservaba las comillas literales

`procesarLote` partía cada línea con `String.split(",")`, de modo que los
valores `"titulo","texto"` enviados por un cliente (formato CSV estándar con
comillas) llegaban al modelo con las comillas incluidas, degradando la
clasificación (el título quedaba como `"Introduccion a Docker"` en lugar de
`Introduccion a Docker`).

**Corrección:** `ContenidoServiceImpl#parsearLineaCsv` — parseo de campos con
soporte de comillas dobles (`""` como escape). Se usa tanto para el encabezado
como para las filas.

### 5.2 Faltaba validación de extensión `.csv`

Un archivo con extensión `.txt` pero columnas correctas se procesaba
normalmente (200) en lugar de rechazarse con 400 como exige la Sección 4.4 de la
especificación.

**Corrección:** al inicio de `procesarLote` se valida `getOriginalFilename()`
(extensión `.csv`); si no cumple, `ValidacionException` → 400 «El archivo debe
ser un CSV (extensión .csv).».

### 5.3 Errores de multipart y Content-Type devolvían 500

`MultipartException` / `MissingServletRequestPartException` /
`MissingServletRequestParameterException` (lote sin archivo o petición no
multipart) y `HttpMediaTypeNotSupportedException` (POST JSON enviado como
`text/plain`) caían en el catch-all y devolvían 500 con stack trace.

**Corrección:** dos handlers nuevos en `GlobalExceptionHandler`:

- `{MultipartException, MissingServletRequestPartException,
  MissingServletRequestParameterException}` → **400** «La solicitud debe incluir
  el campo 'archivo' en formato multipart/form-data.».
- `HttpMediaTypeNotSupportedException` → **400** «El Content-Type de la
  solicitud debe ser application/json.» (la especificación, Sección 4.1, exige
  400 y no 415).

### 5.4 Defectos del runner (harness), no del backend

- `Content-Type: application/json` quedaba activo al ejecutar los casos
  multipart, inyectando un header incorrecto junto con `-F` y provocando
  `MultipartException` en todos los casos del lote (enmascaraba defectos
  reales).
- El reset de headers en cada caso impedía reproducir correctamente el estado
  transitorio.

## 6. Verificación de regresión

- Suite unitaria del backend (JUnit + MockMvc): **74 tests, 0 fallos, 0 errores**.
- Batería E2E completa reproducida con el runner del repositorio:
  **37/37 casos conforme a la especificación**.

## 7. Cómo reproducir

```bash
# 1. Servicios arriba
cd ml-service && uvicorn app:app --port 8000 &      # servicio de inferencia
cd backend && java -jar target/api-techmind-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev --server.port=8080 &

# 2. Batería de aceptación
scripts/aceptacion/runner.sh
# Evidencia en /tmp/aceptacion-evidencia.txt
```