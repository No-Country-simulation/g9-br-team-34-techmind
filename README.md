# TechMind

**Organización inteligente de contenido técnico mediante Ciencia de Datos.**

TechMind recibe contenido técnico —texto plano, PDF, DOCX o una URL— y devuelve una categoría temática, un puntaje de confianza y las palabras clave más relevantes, todo a través de una API REST en JSON. El objetivo es transformar documentación dispersa en una base de conocimiento clasificada y consultable.


[![CI](https://github.com/No-Country-simulation/g9-br-team-34-techmind/actions/workflows/ci.yml/badge.svg)](https://github.com/No-Country-simulation/g9-br-team-34-techmind/actions/workflows/ci.yml)
[![CD - Despliegue en OCI](https://github.com/No-Country-simulation/g9-br-team-34-techmind/actions/workflows/cd.yml/badge.svg)](https://github.com/No-Country-simulation/g9-br-team-34-techmind/actions/workflows/cd.yml)

---

## Tabla de contenidos

- [Qué hace](#qué-hace)
- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Puesta en marcha (local)](#puesta-en-marcha-local)
- [Comandos frecuentes](#comandos-frecuentes)
- [API REST](#api-rest)
- [Despliegue](#despliegue)
- [Documentación adicional](#documentación-adicional)
- [Estado del MVP](#estado-del-mvp)
- [Licencia](#licencia)

---

## Qué hace

Un cliente envía contenido técnico —como texto directo, un archivo PDF/DOCX o una URL— y TechMind:

1. Extrae y limpia el texto (si la entrada es un archivo o una URL).
2. Lo clasifica en una de las **7 categorías** que predice el modelo entrenado (Backend, Bases de Datos, Ciencia de Datos, DevOps, Frontend, Moviles, Seguridad).
3. Extrae sus palabras clave más representativas.
4. Persiste el resultado y lo expone vía API REST, con búsqueda, filtros, paginación y contenidos relacionados.

```json
// POST /api/v1/contenidos
{
  "titulo": "Introducción a Spring Boot",
  "texto": "En este contenido se presentan los conceptos básicos para la creación de APIs REST utilizando Java y Spring Boot."
}
```

```json
// 201 Created
{
  "id": "0f2a1c3e-...",
  "titulo": "Introducción a Spring Boot",
  "categoria": "Backend",
  "probabilidad": 0.89,
  "informacion_adicional": ["Java", "Spring Boot", "API REST"],
  "fechaProcesamiento": "2026-08-14T10:32:00Z"
}
```

## Arquitectura

Dos servicios con una responsabilidad cada uno, orquestados con Docker Compose y desplegados en Oracle Cloud Infrastructure (OCI):

```
                         ┌──────────────────────┐
   Cliente / Frontend ──▶│  backend (Java 17)    │
   (PDF, DOCX, URL,      │  Spring Boot 3        │
    texto directo)       │  API REST pública      │
                         │  Persistencia (H2)     │
                         └──────────┬─────────────┘
                                    │ POST /predict
                                    │ (red interna, sin
                                    │  exposición externa)
                                    ▼
                         ┌──────────────────────┐
                         │  ml-service (Python)   │
                         │  FastAPI + scikit-learn│
                         │  Clasificación + kw    │
                         └──────────┬─────────────┘
                                    │ descarga el modelo
                                    │ al arrancar (prod)
                                    ▼
                         ┌──────────────────────┐
                         │  OCI Object Storage    │
                         │  model.joblib          │
                         └──────────────────────┘
```

**Por qué dos servicios y no uno monolítico:** el equipo de Ciencia de Datos trabaja en Python (scikit-learn) y el equipo de Backend en Java (Spring Boot). Separar el modelo detrás de una API HTTP interna permite que ambos equipos iteren de forma independiente —reentrenar el modelo no requiere recompilar el backend, y viceversa— mientras comparten un [contrato REST/JSON versionado y acordado explícitamente](docs/contrato-backend-ds.md).

El `backend` es el único servicio expuesto a Internet. El `ml-service` solo es alcanzable desde la red interna de Docker Compose; en producción no publica ningún puerto.

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| **Backend / API REST** | Java 17, Spring Boot 3.3, Spring Data JPA, Bean Validation, springdoc-openapi (Swagger UI) |
| **Persistencia** | H2 (archivo, con volumen persistente en producción) |
| **Servicio de inferencia** | Python 3.11, FastAPI, scikit-learn (TF-IDF + Regresión Logística), Pydantic |
| **Extracción de archivos** | Apache PDFBox, Apache POI (DOCX), jsoup (HTML/URL), Gemini API (limpieza y fallback) |
| **Ciencia de Datos** | pandas, notebooks Jupyter (EDA, modelado, métricas) |
| **Frontend** | HTML/CSS/JS (cliente de demostración que consume la API) |
| **Infraestructura** | Docker, Docker Compose v2.24+, Terraform, Oracle Cloud Infrastructure (OCI) |
| **CI/CD** | GitHub Actions (integración continua, despliegue continuo, monitoreo de salud) |
| **Proxy / HTTPS** | Caddy (opcional, certificados automáticos vía Let's Encrypt) |

## Estructura del repositorio

```
.
├── backend/                    API REST en Spring Boot (Java 17)
│   ├── src/main/java/...       controladores, servicios, DTOs, entidades, seguridad
│   ├── src/test/java/...       pruebas unitarias y de integración
│   ├── pom.xml
│   └── Dockerfile              build multi-etapa: maven -> jre-jammy
│
├── ml-service/                 Servicio de inferencia en FastAPI (Python 3.11)
│   ├── app/                    API HTTP que sirve el modelo (main, model, schemas, settings)
│   ├── train/                  script de entrenamiento y dataset semilla
│   ├── tests/                  pruebas de contrato HTTP
│   └── Dockerfile              build multi-etapa: entrena en build, sirve en runtime
│
├── data-science/                Exploración y modelado (fuera del pipeline productivo)
│   ├── notebooks/               EDA, entrenamiento y métricas en Jupyter
│   ├── data/                    datasets versionados (v2, v3, v4)
│   ├── models/                  artefactos entrenados por el equipo de Ciencia de Datos
│   └── API/                     prototipo original del servicio de inferencia
│
├── frontend/                    Cliente de demostración (HTML/CSS/JS, consume la API)
│
├── infra/terraform/             Infraestructura como código para OCI (VCN, Compute, IAM, Storage)
│
├── docs/
│   ├── contrato-backend-ds.md   contrato REST/JSON acordado entre Backend y Ciencia de Datos
│   ├── pruebas-aceptacion-e2e.md batería de pruebas de aceptación end-to-end
│   └── devops/
│       ├── despliegue-oci.md    guía completa de despliegue y runbook operativo
│       └── informe-devops.md    informe de decisiones de infraestructura
│
├── scripts/
│   ├── provision-vm.sh          deja lista la VM de OCI (se ejecuta una vez)
│   ├── configurar-github.sh     carga secrets/variables del CD en GitHub (idempotente)
│   ├── smoke-test.sh            verifica un sistema levantado, de punta a punta
│   ├── backup-datos.sh          respaldo de la base de datos hacia Object Storage
│   └── aceptacion/               batería de pruebas de aceptación (runner + evidencia)
│
├── .github/workflows/
│   ├── ci.yml                    compilación, pruebas e imágenes en cada push/PR
│   ├── cd.yml                    entrenamiento, publicación y despliegue automático en OCI
│   └── monitoreo.yml             chequeo de salud periódico en producción
│
├── caddy/Caddyfile               proxy inverso opcional con HTTPS automático
├── docker-compose.yml            orquestación para desarrollo local
├── docker-compose.prod.yml       sobrescritura para producción en OCI
├── Makefile                      atajos de desarrollo y operación
└── .env.example                  plantilla de variables de entorno
```

> Documentación específica de cada servicio: [`backend/README.md`](backend/README.md) y [`ml-service/README.md`](ml-service/README.md).

## Puesta en marcha (local)

**Requisitos:** Docker con Compose v2.24 o superior. No hace falta tener Java, Python ni Maven instalados en la máquina — todo corre en contenedores.

```bash
git clone https://github.com/No-Country-simulation/g9-br-team-34-techmind.git
cd g9-br-team-34-techmind

make env      # crea el .env a partir de .env.example
make up       # construye y levanta backend + ml-service
```

Al terminar quedan disponibles:

| Servicio | URL |
|---|---|
| API REST | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Salud de la API | http://localhost:8080/actuator/health |
| Documentación del modelo (OpenAPI) | http://localhost:8000/docs |
| Salud del modelo | http://localhost:8000/health |

Para comprobar que todo funciona de punta a punta con los ejemplos del brief del hackathon:

```bash
make smoke
```

> El puerto `8000` del `ml-service` se publica **solo en desarrollo**, para poder interrogar el modelo directamente con `curl`. En producción es estrictamente interno: solo lo alcanza el backend por la red privada del compose.

### Sin Docker (desarrollo de un solo servicio)

Si estás trabajando exclusivamente en un servicio, cada uno documenta su arranque nativo (sin contenedores) en su propio README:

- Backend Java con Maven → [`backend/README.md`](backend/README.md)
- Servicio de inferencia con Python/uvicorn → [`ml-service/README.md`](ml-service/README.md)

## Comandos frecuentes

`make` sin argumentos lista todos los comandos disponibles. Los más usados:

| Comando | Qué hace |
|---|---|
| `make up` / `make down` | Levanta / detiene el sistema |
| `make logs` | Sigue los logs de ambos servicios |
| `make logs-backend` / `make logs-ml` | Logs de un servicio en particular |
| `make ps` | Estado de los contenedores |
| `make test` | Pruebas de backend y ml-service |
| `make train` | Reentrena el modelo en local |
| `make lint` | Analiza el código Python (ruff) |
| `make shell-backend` / `make shell-ml` | Abre una shell dentro de un contenedor |
| `make rebuild` | Reconstruye ignorando la caché de Docker |
| `make clean` / `make clean-all` | Limpia artefactos locales (el segundo borra también la base de datos) |

## API REST

Todos los endpoints viven bajo `/api/v1`. La referencia interactiva completa (con esquemas, ejemplos y prueba en vivo) está en Swagger UI una vez levantado el sistema: **http://localhost:8080/swagger-ui/index.html**.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/contenidos` | Clasifica y persiste un contenido enviado como texto |
| `POST` | `/api/v1/contenidos/archivo` | Ídem, extrayendo texto de un PDF o DOCX subido |
| `POST` | `/api/v1/contenidos/url` | Ídem, extrayendo texto de una URL (ej. una consulta de foro) |
| `POST` | `/api/v1/contenidos/lote` | Procesa un CSV (`titulo,texto`) en lote |
| `GET` | `/api/v1/contenidos/{id}` | Obtiene un contenido ya procesado |
| `GET` | `/api/v1/contenidos` | Lista contenidos, con filtro por categoría, búsqueda por palabra clave y paginación |
| `GET` | `/api/v1/contenidos/{id}/relacionados` | Contenidos relacionados a uno dado (misma categoría + palabras clave en común) |
| `DELETE` | `/api/v1/contenidos/{id}` | Elimina un contenido procesado |
| `GET` | `/api/v1/categorias` | Lista las categorías presentes en el repositorio, con el conteo de contenidos procesados por cada una |
| `GET` | `/actuator/health` | Estado de salud de la API (usado por Docker y por el CD para decidir un rollback) |

Todas las respuestas de error siguen un formato uniforme (`ErrorResponseDTO`), con el código HTTP correspondiente (`400`, `404`, `413`, `422`, `503`, etc.) y el detalle de los campos inválidos cuando aplica. El detalle completo del contrato —incluyendo el conjunto de categorías y las reglas de validación— está en [`docs/contrato-backend-ds.md`](docs/contrato-backend-ds.md).

## Despliegue

El despliegue en Oracle Cloud Infrastructure es **automático**: cada merge a `main` dispara el workflow de CD ([`.github/workflows/cd.yml`](.github/workflows/cd.yml)), que:

1. Entrena el modelo y publica el artefacto en OCI Object Storage.
2. Construye y publica las imágenes de `backend` y `ml-service` en OCIR (OCI Container Registry).
3. Actualiza los contenedores en la VM de producción vía SSH.
4. Si los healthchecks no pasan, **revierte automáticamente** a la versión anterior.

La VM (`VM.Standard.A1.Flex`, capa Always Free) corre únicamente contenedores ya construidos —nunca compila código— y el `ml-service` descarga el modelo desde Object Storage en lugar de llevarlo embebido en la imagen, usando autenticación `instance_principal` (sin claves privadas en disco).

La guía completa —aprovisionamiento con Terraform, secrets de GitHub Actions, primer despliegue, HTTPS opcional con Caddy y runbook de operación e incidentes— está en **[`docs/devops/despliegue-oci.md`](docs/devops/despliegue-oci.md)**.

## Documentación adicional

| Documento | Contenido |
|---|---|
| [`backend/README.md`](backend/README.md) | Arquitectura interna del backend, capas, configuración, cómo correr sin Docker |
| [`ml-service/README.md`](ml-service/README.md) | Servicio de inferencia, entrenamiento del modelo, contrato HTTP, cómo correr sin Docker |
| [`docs/contrato-backend-ds.md`](docs/contrato-backend-ds.md) | Contrato REST/JSON acordado entre Backend y Ciencia de Datos: campos, categorías, convenciones de nombres |
| [`docs/pruebas-aceptacion-e2e.md`](docs/pruebas-aceptacion-e2e.md) | Batería de pruebas de aceptación end-to-end y defectos corregidos |
| [`docs/devops/despliegue-oci.md`](docs/devops/despliegue-oci.md) | Guía de despliegue en OCI y runbook operativo |
| [`docs/devops/informe-devops.md`](docs/devops/informe-devops.md) | Decisiones de infraestructura y su justificación |
| [`infra/terraform/README.md`](infra/terraform/README.md) | Infraestructura como código: recursos de OCI provisionados |

## Estado del MVP

El proyecto implementa un pipeline completo y operativo:

- ✅ Ingesta de contenido por texto, archivo (PDF/DOCX) o URL
- ✅ Clasificación temática en 7 categorías con puntaje de confianza
- ✅ Extracción de palabras clave (TF-IDF)
- ✅ Persistencia, consulta individual y listado con filtros, búsqueda y paginación
- ✅ Contenidos relacionados por similitud de categoría y palabras clave
- ✅ Procesamiento por lote (CSV)
- ✅ Resumen de categorías con conteo de contenidos procesados
- ✅ Despliegue automatizado en OCI con CI/CD, rollback automático y monitoreo de salud
- ✅ 37/37 casos de la batería de pruebas de aceptación end-to-end en verde ([detalle](docs/pruebas-aceptacion-e2e.md))

