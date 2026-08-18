# TechMind · Backend

API REST en Spring Boot que recibe contenido técnico, delega la clasificación al [servicio de inferencia](../ml-service/README.md) y persiste el resultado. Es el único componente del sistema expuesto a Internet.

> Visión general del proyecto completo, arquitectura de dos servicios y arranque con Docker: [README del repositorio](../README.md).

---

## Tabla de contenidos

- [Arquitectura interna](#arquitectura-interna)
- [Modelo de dominio](#modelo-de-dominio)
- [Contrato de la API](#contrato-de-la-api)
- [Manejo de errores](#manejo-de-errores)
- [Configuración](#configuración)
- [Correr sin Docker](#correr-sin-docker)
- [Pruebas](#pruebas)
- [Integración con OCI Object Storage](#integración-con-oci-object-storage)
- [Extracción de archivos y URLs](#extracción-de-archivos-y-urls)
- [Decisiones de diseño](#decisiones-de-diseño)

---

## Arquitectura interna

El backend sigue una separación por capas convencional en Spring Boot, con una regla estricta: **los controladores no contienen lógica de negocio**, solo traducen HTTP ↔ DTOs y delegan en la capa de servicio.

```
com.api.techmind_g9_team34.api_techmind
├── controller/       Capa web. Traduce HTTP <-> DTOs. Sin lógica de negocio.
├── service/          Lógica de negocio. Interfaces + implementaciones en service/impl.
│   └── parser/        Extracción de texto desde PDF, DOCX y HTML (jsoup).
├── client/           Clientes HTTP salientes (servicio de inferencia, Gemini).
│   └── mock/           Implementaciones mock para pruebas y desarrollo aislado.
├── model/            Entidades JPA (persistencia).
├── repository/       Acceso a datos (Spring Data JPA + Specifications).
│   └── projection/     Proyecciones de solo lectura (ej. conteo por categoría).
├── dto/
│   ├── request/        Entrada de la API pública.
│   ├── response/        Salida de la API pública.
│   └── client/           Contrato interno con el servicio de inferencia.
├── mapper/            Traducción entre entidades, DTOs públicos y DTOs de cliente.
├── exception/         Excepciones de dominio + manejador global (@ControllerAdvice).
├── config/            Configuración de Spring: CORS, OCI, Gemini, WebClient, paginación.
├── security/          (placeholder — ver Decisiones de diseño)
└── util/
```

**Por qué esta separación:** el modelo de dominio (`ContenidoAnalizado`) y el contrato público (`ContenidoResponseDTO`) son deliberadamente distintos, aunque hoy se parezcan. La entidad modela lo que el sistema necesita persistir; el DTO modela lo que el contrato con el cliente promete. Cambiar uno no debería forzar a cambiar el otro — el `mapper/` es la capa que absorbe esa diferencia.

## Modelo de dominio

La entidad central es `ContenidoAnalizado`: representa un contenido técnico ya procesado por el modelo de inferencia.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `UUID` | Generado por Hibernate (`GenerationType.UUID`), no autoincremental |
| `titulo` | `String` (≤ 200) | Validado en el DTO de entrada |
| `texto` | `String` (≤ 10 000) | Se persiste completo — lo necesita el cálculo de "relacionados" y permite reprocesar sin volver a pedirlo al cliente |
| `categoria` | `String` | La categoría predicha por el modelo (ver [contrato](../docs/contrato-backend-ds.md)). Se guarda como `String` y no `enum` para no acoplar un despliegue de backend a cada cambio de categorías |
| `probabilidad` | `Double` | Confianza de la clasificación, en `[0.0, 1.0]` |
| `palabrasClave` | `List<String>` | `@ElementCollection` en tabla aparte (`contenido_palabras_clave`), para poder buscar por palabra clave directamente en SQL |
| `fechaProcesamiento` | `Instant` | Asignado por Hibernate al insertar (`@CreationTimestamp`); no se actualiza — un reproceso es un registro nuevo |

La igualdad (`equals`/`hashCode`) se basa exclusivamente en `id`, siguiendo el patrón recomendado para entidades JPA (evita romperse dentro de colecciones tipo `HashSet` entre el estado transitorio y el persistido).

## Contrato de la API

Referencia interactiva completa (con Swagger UI) en `http://localhost:8080/swagger-ui/index.html` una vez levantado el servicio.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/contenidos` | Clasifica y persiste contenido enviado como texto |
| `POST` | `/api/v1/contenidos/archivo` | Extrae texto de un PDF/DOCX subido, lo limpia vía Gemini y sigue el mismo flujo |
| `POST` | `/api/v1/contenidos/url` | Extrae texto de una URL, lo limpia vía Gemini y sigue el mismo flujo |
| `POST` | `/api/v1/contenidos/lote` | Procesa un CSV (`titulo,texto`, máx. `techmind.csv.max-rows` filas) |
| `GET` | `/api/v1/contenidos/{id}` | Obtiene un contenido por id |
| `GET` | `/api/v1/contenidos` | Lista paginada, con filtro opcional `categoria` y búsqueda opcional `palabraClave` |
| `GET` | `/api/v1/contenidos/{id}/relacionados` | Contenidos relacionados (misma categoría + palabras clave compartidas), `limite` opcional (1–20, default 5) |
| `DELETE` | `/api/v1/contenidos/{id}` | Elimina un contenido |
| `GET` | `/api/v1/categorias` | Las categorías presentes en el repositorio, con el conteo de contenidos procesados |
| `GET` | `/actuator/health` | Salud de la API (único endpoint de Actuator expuesto) |

**Convención de nombres:** la API pública usa `camelCase`, con la única excepción de `informacion_adicional` (en `snake_case`), que se mantiene así por decisión explícita para coincidir con el ejemplo literal del brief del hackathon. El contrato interno con el servicio de inferencia usa `snake_case` completo (convención Python/PEP 8). Detalle completo en [`docs/contrato-backend-ds.md`](../docs/contrato-backend-ds.md).

**Paginación:** `GET /api/v1/contenidos` valida `page`, `size` (máximo 50, default 20) y `sort` contra una lista blanca de propiedades ordenables (`fechaProcesamiento`, `titulo`, `categoria`). Un valor fuera de rango responde `400`, no lo trunca silenciosamente — con la única excepción de `limite` en `/relacionados`, que si supera 20 se acota a 20 en lugar de rechazarse.

## Manejo de errores

Centralizado en `GlobalExceptionHandler` (`@ControllerAdvice`). Toda respuesta de error usa el mismo formato (`ErrorResponseDTO`), con el código HTTP correspondiente:

| Situación | HTTP |
|---|---|
| Validación de campos (`@Valid`) | `400` |
| Parámetro con formato inválido (ej. `id` que no es UUID) | `400` |
| `Content-Type` no soportado | `400` |
| Falta el campo `archivo` en una petición multipart | `400` |
| Contenido no encontrado | `404` |
| Recurso/ruta inexistente (ej. `favicon.ico`) | `404` |
| Archivo o CSV no procesable | `422` |
| Error de extracción (PDF/DOCX/URL) | `422` |
| Archivo supera el tamaño máximo | `413` |
| Servicio de inferencia no disponible | `503` |
| Cualquier otra excepción no controlada | `500` (con log del stack trace; el cliente solo recibe un mensaje genérico) |

## Configuración

La configuración vive en `application.properties` (base) más un perfil (`application-dev.properties` / `application-prod.properties`), seleccionado con `spring.profiles.active`. Todo lo que cambia entre entornos entra por variable de entorno — nunca hay que reconstruir la imagen para cambiar de entorno.

| Variable | Descripción | Default (dev) |
|---|---|---|
| `SERVER_PORT` | Puerto HTTP | `8080` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos por CORS | `http://localhost:3000` |
| `INFERENCE_SERVICE_URL` | URL base del `ml-service` | `http://localhost:8000` |
| `INFERENCE_SERVICE_TIMEOUT_MS` | Timeout del cliente hacia el `ml-service` | `5000` |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales H2 | `sa` / *(vacío)* |
| `TECHMIND_API_KEY` | Clave que exigirá el filtro de autenticación (ver [Decisiones de diseño](#decisiones-de-diseño)) | *(vacío)* |
| `OCI_ENABLED` | Habilita la integración con OCI Object Storage | `false` en dev, `true` en prod |
| `OCI_AUTH_METHOD` | `config_file` (dev) o `env_vars` (prod/CI) | `config_file` |
| `GEMINI_API_KEY` | Clave de la API de Gemini (extracción/limpieza) | *(vacío)* |
| `techmind.csv.max-rows` | Máximo de filas aceptadas en `/contenidos/lote` | `100` |

`/actuator/health` **debe** quedar accesible sin autenticación una vez que se implemente el filtro de seguridad: es lo que usan el `HEALTHCHECK` de Docker y el workflow de CD para decidir si un despliegue es válido o hay que revertirlo.

## Correr sin Docker

Requiere JDK 17 y Maven (o usar el wrapper incluido `./mvnw`).

```bash
cd backend
./mvnw spring-boot:run
```

Por defecto arranca con el perfil `dev`: base H2 en archivo (`./data/techmind-dev`), consola H2 habilitada en `/h2-console`, integración OCI deshabilitada. El servicio de inferencia se espera en `http://localhost:8000` — levantarlo por separado siguiendo [`ml-service/README.md`](../ml-service/README.md), o usar el perfil de pruebas con el cliente mock (ver [Pruebas](#pruebas)).

## Pruebas

```bash
./mvnw test
```

La suite cubre controladores, servicios, mapper, repositorio, el resolutor de paginación, el manejador global de excepciones y el cliente HTTP hacia el servicio de inferencia. Para pruebas que no dependen de un `ml-service` real, el perfil `mock` sustituye `RestModeloInferenciaClient` por `MockModeloInferenciaClient` (ver `client/mock/`).

## Integración con OCI Object Storage

`OciStorageConfig` soporta dos métodos de autenticación, seleccionados con `techmind.oci.auth-method`:

- **`config_file`** — lee `~/.oci/config` (uso en desarrollo local, con credenciales personales).
- **`env_vars`** — construye las credenciales desde variables de entorno, aceptando la clave privada como contenido Base64 (`OCI_PRIVATE_KEY_CONTENT`, recomendado para Docker/CI) o como ruta a un archivo montado (`OCI_PRIVATE_KEY_PATH`).

En producción, el propio backend no necesita esto activo para operar — es el `ml-service` quien descarga el modelo desde Object Storage. Este cliente existe para verificación y para el endpoint temporal de prueba `POST /api/test/oci` (`OciTestController`), activo solo si `techmind.oci.enabled=true`.

## Extracción de archivos y URLs

`ExtraccionArchivoService` unifica tres fuentes de entrada bajo el mismo contrato (`ContenidoRequestDTO`):

- **PDF** → Apache PDFBox (`PdfParserService`)
- **DOCX** → Apache POI (`DocxParserService`)
- **URL / HTML** → jsoup (`HtmlParserService`)

El texto extraído por los parsers determinísticos pasa siempre por un paso de limpieza vía **Gemini API** antes de entrar al pipeline de clasificación; Gemini también actúa como *fallback* cuando un parser falla en extraer contenido utilizable. La configuración de Gemini (modelo, timeout, API key) vive en `GeminiConfig`.

## Decisiones de diseño

Algunas decisiones documentadas directamente en el código (Javadoc) y que vale la pena tener presentes al extender el backend:

- **`security/AuthenticationFilter.java` y `config/SecurityConfig.java` son placeholders.** La autenticación por API key (`TECHMIND_API_KEY`) está prevista en la configuración pero el filtro aún no está implementado. Al implementarlo, `/actuator/health` debe quedar explícitamente en `permitAll()`.
- **`ddl-auto=update`, no `validate`.** El volumen de H2 en producción arranca vacío en el primer despliegue; `validate` exigiría que las tablas ya existieran. Para un proyecto de mayor recorrido, correspondería migrar a Flyway o Liquibase con migraciones versionadas.
- **`schema.sql` + `spring.sql.init.mode=always` en el perfil prod.** El esquema se crea también de forma declarativa e idempotente (`CREATE TABLE IF NOT EXISTS`, más los índices), complementando al `ddl-auto=update`. Conviven sin conflicto: el script garantiza el esquema deseado desde el primer arranque y Hibernate solo sincroniza diferencias.
- **Un solo commit por ticket, DTOs como `record`.** Los DTOs de request/response son `record` inmutables — un DTO de entrada no debe poder mutarse una vez deserializado.
- **`RestModeloInferenciaClient` reintenta una vez** ante fallos de conexión (no ante errores HTTP del servicio) antes de propagar `ModeloServiceException`, que el `GlobalExceptionHandler` traduce a `503`.

Para el detalle completo de cada decisión (por qué un `Specification` y no `@Query` derivado, por qué `@EntityGraph` en el repositorio, por qué las palabras clave usan TF-IDF y no un modelo aparte, etc.), el código está documentado con Javadoc extenso clase por clase — es la fuente de verdad más granular por encima de este README.
