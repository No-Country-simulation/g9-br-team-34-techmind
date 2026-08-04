# G9-LATAM-Team-34-TechMind

Solución para la organización inteligente de contenido técnico mediante técnicas de Ciencia de Datos. La aplicación procesa documentos técnicos, identifica información relevante y expone los resultados a través de una API REST en formato JSON, integrándose con Oracle Cloud Infrastructure (OCI).

---

## Descripción

El proyecto tiene como objetivo facilitar la organización, consulta y reutilización de contenido técnico, permitiendo transformar grandes volúmenes de información en una base de conocimiento estructurada.

La solución recibe contenido técnico como entrada y aplica técnicas de procesamiento de texto y Ciencia de Datos para generar información enriquecida que puede ser consumida por otras aplicaciones.

Entre las capacidades que puede ofrecer la solución se encuentran:

- Clasificación temática del contenido.
- Extracción de información relevante.
- Identificación de palabras clave.
- Agrupación de documentos similares.
- Recomendación de contenidos relacionados.
- Organización automática de bases de conocimiento.

Todos los resultados son entregados mediante una API REST utilizando formato JSON.

---

## Arquitectura

```
                 Documento Técnico
                        │
                        ▼
                 API REST (Backend)
                        │
                        ▼
           Modelo de Ciencia de Datos
                        │
                        ▼
        Clasificación / Procesamiento
                        │
                        ▼
                 Respuesta JSON
                        │
                        ▼
              Aplicaciones Cliente
```

---

## Tecnologías

### Ciencia de Datos

- Python
- Pandas
- Scikit-learn
- TF-IDF
- Técnicas de similitud textual

### Backend

- API REST
- JSON

### Infraestructura

- Docker y Docker Compose
- GitHub Actions (CI/CD)
- Oracle Cloud Infrastructure (OCI)

Servicios de OCI en uso:

| Servicio | Para que |
|---|---|
| **Compute** (VM.Standard.A1.Flex, Always Free) | ejecuta los contenedores |
| **Object Storage** | almacena el modelo entrenado y sus metricas |
| **Container Registry (OCIR)** | almacena las imagenes publicadas por CI |
| **IAM** (Dynamic Group + Policy) | permite a la VM leer el modelo sin secretos en disco |

---

## Estructura del proyecto

```
.
├── backend/                    API REST en Spring Boot (Java 17)
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile              build multi-etapa: maven -> jre-alpine
│
├── ml-service/                 Servicio de inferencia en FastAPI (Python 3.11)
│   ├── app/                    API HTTP que sirve el modelo
│   ├── train/                  entrenamiento y dataset
│   ├── tests/
│   └── Dockerfile              build multi-etapa: entrena y empaqueta
│
├── scripts/
│   ├── provision-vm.sh         deja lista la VM de OCI (se ejecuta una vez)
│   └── smoke-test.sh           verifica un sistema levantado
│
├── docs/devops/
│   └── despliegue-oci.md       guia completa de despliegue y runbook
│
├── .github/workflows/
│   ├── ci.yml                  pruebas, imagenes y prueba de humo
│   └── cd.yml                  despliegue automatico en OCI
│
├── docker-compose.yml          orquestacion para desarrollo local
├── docker-compose.prod.yml     sobrescritura para produccion en OCI
├── Makefile                    atajos de desarrollo y operacion
└── .env.example                plantilla de variables de entorno
```

---

## Puesta en marcha (local)

Requisitos: **Docker** con Compose v2.24 o superior. Nada mas: ni Java, ni
Python, ni Maven instalados en la maquina.

```bash
make env      # crea el .env a partir de .env.example
make up       # construye y levanta backend + ml-service
```

Al terminar quedan disponibles:

| Servicio | URL |
|---|---|
| API REST | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Salud de la API | http://localhost:8080/actuator/health |
| Documentacion del modelo | http://localhost:8000/docs |
| Salud del modelo | http://localhost:8000/health |

Para comprobar que todo funciona de punta a punta:

```bash
make smoke
```

### Comandos frecuentes

`make` sin argumentos lista todos. Los mas usados:

| Comando | Que hace |
|---|---|
| `make up` / `make down` | levanta / detiene el sistema |
| `make logs` | sigue los logs de ambos servicios |
| `make ps` | estado de los contenedores |
| `make test` | pruebas de backend y ml-service |
| `make train` | reentrena el modelo en local |
| `make rebuild` | reconstruye ignorando la cache de Docker |
| `make clean-all` | borra tambien los volumenes (se pierde la base) |

El puerto 8000 se publica **solo en desarrollo**, para poder interrogar el modelo
directamente con `curl`. En produccion el ml-service es interno: solo lo alcanza
el backend por la red privada del compose.

---

## Despliegue

El despliegue en Oracle Cloud Infrastructure es automatico: cada merge a `main`
dispara el workflow de CD, que entrena y publica el modelo en Object Storage,
construye y sube las imagenes a OCIR, y actualiza los contenedores en la VM por
SSH. Si los healthchecks no pasan, **revierte solo** a la version anterior.

La guia completa —recursos de OCI, permisos, secrets, primer despliegue y
runbook de operacion— esta en **[docs/devops/despliegue-oci.md](docs/devops/despliegue-oci.md)**.

---

## Flujo de funcionamiento

1. El cliente envía un contenido técnico a la API.
2. La API valida la solicitud.
3. El modelo procesa el texto.
4. Se identifica información relevante.
5. La API devuelve los resultados en formato JSON.

---

## Endpoint principal

### POST /contenido

Procesa un contenido técnico y devuelve la información obtenida por el modelo.

### Solicitud

```json
{
    "titulo": "Introducción a Spring Boot",
    "texto": "En este contenido se presentan los conceptos básicos para la creación de APIs REST utilizando Java y Spring Boot."
}
```

### Respuesta

```json
{
    "categoria": "Backend",
    "probabilidad": 0.89,
    "informacion_adicional": [
        "Java",
        "Spring Boot",
        "API REST"
    ]
}
```

La estructura de la respuesta puede variar según el enfoque implementado por el equipo.

---

## Instalación

```bash
git clone https://github.com/No-Country-simulation/g9-br-team-34-techmind.git
cd g9-br-team-34-techmind
make env && make up
```

Ver [Puesta en marcha (local)](#puesta-en-marcha-local) para el detalle.

---

## Ejemplo de uso

### Clasificación de contenido

Entrada

```
Tutorial de Docker
```

Salida

```json
{
    "categoria": "DevOps"
}
```

---
### Organización de contenido

Entrada

```
Documentación técnica
```

Salida

```json
{
    "categoria": "...",
    "probabilidad": "...",
    "informacion_adicional": [...]
}
```

---

## Componentes del proyecto

### Notebook de Ciencia de Datos

Incluye:

- Exploración y limpieza de datos (EDA).
- Procesamiento de texto.
- Transformación de datos.
- Entrenamiento del modelo.
- Evaluación.
- Serialización del modelo.

---

### API REST

Incluye:

- Recepción de contenido.
- Procesamiento mediante el modelo.
- Respuesta JSON.
- Validación de entrada.
- Manejo de errores.

---

## Alcance del MVP

El proyecto implementa un servicio capaz de:

- Recibir contenido técnico.
- Procesarlo mediante un modelo de Ciencia de Datos.
- Generar información enriquecida.
- Exponer los resultados mediante una API REST.

---

## Licencia

Este proyecto fue desarrollado con fines académicos para el Hackathon, siguiendo los requisitos establecidos en la propuesta del desafío.
