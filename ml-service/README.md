# TechMind · Servicio de Inferencia

Servicio interno en FastAPI que expone el modelo de clasificación de contenido técnico por HTTP. Lo consume exclusivamente el [backend](../backend/README.md); en producción no publica ningún puerto al exterior.

> Visión general del proyecto completo y arquitectura de dos servicios: [README del repositorio](../README.md).

---

## Tabla de contenidos

- [Qué hace](#qué-hace)
- [Endpoints](#endpoints)
- [Correr sin Docker](#correr-sin-docker)
- [Artefacto del modelo](#artefacto-del-modelo)
- [Configuración](#configuración)
- [Origen del modelo: local vs. OCI](#origen-del-modelo-local-vs-oci)
- [Pruebas](#pruebas)
- [Relación con `data-science/`](#relación-con-data-science)
- [Decisiones de diseño](#decisiones-de-diseño)

---

## Qué hace

Recibe un título y un texto, y devuelve:

- **`categoria`** — una de las **7 categorías** que predice el modelo entrenado (Backend, Base de Datos, DevOps, Frontend, Machine Learning, Mobile, Seguridad), combinando dos vectorizadores TF-IDF (título y texto, con el título x0.5) y una Regresión Logística.
- **`probabilidad`** — confianza de la predicción, en `[0.0, 1.0]`.
- **`informacion_adicional`** — las palabras clave con mayor peso TF-IDF dentro del texto (hasta `MAX_KEYWORDS`, default 5).

Este servicio **no valida reglas de negocio** más allá de las cotas de longitud del propio esquema: asume que el backend ya validó la entrada. Tampoco entrena en tiempo de ejecución — el modelo se entrena una vez, en tiempo de build de la imagen, y se sirve como artefacto estático (ver [Entrenar el modelo](#entrenar-el-modelo)).

## Endpoints

Documentación interactiva (OpenAPI/Swagger) en `http://localhost:8000/docs` una vez levantado el servicio.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/predict` | Clasifica un contenido y extrae sus palabras clave |
| `GET` | `/health` | Estado del servicio y del modelo cargado |

**`POST /predict`**

```json
// Request
{
  "titulo": "Introducción a Spring Boot",
  "texto": "En este contenido se presentan los conceptos básicos para la creación de APIs REST utilizando Java y Spring Boot."
}
```

```json
// 200 OK
{
  "categoria": "Backend",
  "probabilidad": 0.89,
  "informacion_adicional": ["Java", "Spring Boot", "API REST"]
}
```

Si el modelo no pudo cargarse al arrancar, responde `503` con la causa exacta del fallo en el `detail` — nunca `500`: el servicio está bien escrito, lo que falta es el artefacto.

**`GET /health`**

```json
{
  "status": "ok",
  "modelo_cargado": true,
  "categorias": ["backend", "base de datos", "devops", "frontend", "machine learning", "mobile", "seguridad"],
  "origen_modelo": "local:///app/models"
}
```

`status` es exactamente lo que lee el `HEALTHCHECK` de Docker (y, a través de él, la condición `service_healthy` que usa Compose para decidir cuándo arrancar el backend). Solo es `"ok"` si el modelo está realmente cargado en memoria — un servicio "arriba" pero sin modelo se reporta `"degraded"` con `503`, en vez de fingir que puede predecir.

## Correr sin Docker

Requiere Python 3.11+.

```bash
cd ml-service
python -m venv .venv && source .venv/bin/activate    # o el equivalente en tu shell
pip install -r requirements-dev.txt
python -m spacy download es_core_news_sm

# El servicio necesita los 3 .joblib en models/ (ver "Artefacto del modelo"
# abajo). Si no están, copialos desde data-science/models/:
#   cp ../data-science/models/*.joblib models/
uvicorn app.main:app --reload --port 8000
```

`requirements-dev.txt` incluye `requirements.txt` más las dependencias de pruebas y lint (`pytest`, `httpx`, `ruff`) — nunca viajan a la imagen de producción, que solo instala `requirements.txt`.

## Artefacto del modelo

`app/model.py` no carga un `Pipeline` único: carga **tres archivos por separado** desde `MODEL_DIR` (`models/` en local, `/app/models` en el contenedor):

- **`modelo_clasificador.joblib`** — el `LogisticRegression` ya entrenado (`.classes_` es la fuente de verdad de las categorías, no se guarda una lista aparte).
- **`tfidf_titulo.joblib`** / **`tfidf_texto.joblib`** — los dos `TfidfVectorizer`, uno por campo, entrenados por separado.

**El contrato que debe respetarse** para que `app/model.py` pueda cargarlos es la forma de estas tres piezas, sus nombres de archivo (configurables por `MODEL_FILE_*`) y el hecho de que ambos vectorizadores se combinan en inferencia con el título pesado `PESO_TITULO` veces (`app/model.py`, función `predict`) antes de `hstack`. Este es el artefacto real que entrega el equipo de Ciencia de Datos — no un `Pipeline` único — y vive versionado en `ml-service/models/`, copiado desde [`data-science/models/`](../data-science/models/).

> `train/train.py` sigue existiendo como andamio de referencia de DevOps (pipeline de un solo `TfidfVectorizer` + `LogisticRegression`, entrenado sobre un dataset semilla), pero **ya no participa del build de la imagen**: el Dockerfile copia directamente los `.joblib` de `models/` en vez de entrenar. Si el equipo de Ciencia de Datos cambia de arquitectura (por ejemplo, a un único vectorizador), hay que actualizar `app/model.py` para que seguir sirviendo, no solo `train.py`.

## Configuración

Todas las variables entran por entorno (`app/settings.py`, vía `pydantic-settings`) — la misma imagen Docker corre en local, CI y la VM de OCI sin reconstruirse; cambiar de entorno es cambiar variables, nunca reconstruir.

| Variable | Descripción | Default |
|---|---|---|
| `HOST` / `PORT` | Bind del servidor | `0.0.0.0` / `8000` |
| `LOG_LEVEL` | Nivel de logging | `info` |
| `MODEL_SOURCE` | `local` o `oci` — ver [siguiente sección](#origen-del-modelo-local-vs-oci) | `local` |
| `MODEL_DIR` | Carpeta donde viven los 3 `.joblib` | `/app/models` |
| `MODEL_FILE_CLASIFICADOR` / `MODEL_FILE_TFIDF_TITULO` / `MODEL_FILE_TFIDF_TEXTO` | Nombres de archivo dentro de `MODEL_DIR` | `modelo_clasificador.joblib` / `tfidf_titulo.joblib` / `tfidf_texto.joblib` |
| `PESO_TITULO` | Peso del vector TF-IDF del título frente al del texto antes de concatenar | `0.5` |
| `MAX_KEYWORDS` | Máximo de palabras clave devueltas en `informacion_adicional` | `5` |
| `OCI_AUTH_METHOD` | `config_file` (dev) o `instance_principal` (producción, sin credenciales en disco) | `config_file` |
| `OCI_NAMESPACE` / `OCI_REGION` | Namespace y región del tenancy (si se omiten, el SDK los resuelve) | *(vacío)* |
| `OCI_BUCKET_NAME` | Bucket donde viven los 3 objetos del modelo | `techmind-models` |
| `OCI_OBJECT_CLASIFICADOR` / `OCI_OBJECT_TFIDF_TITULO` / `OCI_OBJECT_TFIDF_TEXTO` | Nombres de los 3 objetos dentro del bucket | mismos nombres que `MODEL_FILE_*` |
| `OCI_CONFIG_FILE` / `OCI_CONFIG_PROFILE` | Ruta y perfil de `~/.oci/config` (solo con auth `config_file`) | `~/.oci/config` / `DEFAULT` |

## Origen del modelo: local vs. OCI

- **`MODEL_SOURCE=local`** (desarrollo, `docker-compose.yml`) — los 3 `.joblib` viajan dentro de la propia imagen Docker (`ml-service/models/`, versionados en el repo). Quien clona el repositorio levanta todo con un solo comando, sin necesitar credenciales de nube.
- **`MODEL_SOURCE=oci`** (producción, `docker-compose.prod.yml`) — el contenedor descarga los 3 objetos desde OCI Object Storage al arrancar, autenticándose como la propia instancia de OCI (`instance_principal`, sin claves privadas en disco). Si Object Storage no responde pero el volumen conserva una copia previa válida de las tres piezas, el servicio arranca igual con esa copia en vez de quedar caído. Si falta alguna de las tres, no arranca sano. Ese origen efectivo (recién descargado vs. copia local de respaldo) queda reflejado en el campo `origen_modelo` de `/health`, precisamente para poder distinguir ambos casos en producción.

## Pruebas

```bash
pytest -v
```

Requiere que existan los 3 `.joblib` en `models/` — cópialos desde `data-science/models/` si hace falta (`make test-ml` verifica que estén antes de correr). Las pruebas verifican el **contrato HTTP**: códigos de estado, forma del JSON, validación de entrada — deliberadamente **no** afirman qué categoría concreta debería predecirse para un texto dado, porque eso haría que la suite fallara cada vez que Ciencia de Datos reentrena el modelo, que es exactamente el momento en que no conviene que el pipeline de CI se rompa.

## Relación con `data-science/`

El repositorio tiene dos carpetas relacionadas con Ciencia de Datos y conviene no confundirlas:

- **[`data-science/`](../data-science/)** — el espacio de trabajo exploratorio del equipo de Ciencia de Datos: notebooks de EDA, modelado y métricas, datasets versionados (`v2`, `v3`, `v4`) y los artefactos que produjeron.
- **`ml-service/`** (este directorio) — el servicio productivo que sirve un modelo entrenado por HTTP. Consume el contrato de artefacto descrito arriba; no reproduce el trabajo de exploración.

## Decisiones de diseño

- **Un solo worker de uvicorn** (`--workers 1`, ver `Dockerfile`). El modelo se carga entero en memoria por proceso, y la VM `Always Free` de OCI tiene RAM acotada; para más capacidad conviene escalar con más réplicas del contenedor antes que con más workers por proceso.
- **El contenedor de producción nunca entrena.** `train/` no se copia a la imagen final — reduce superficie de ataque y tamaño, y evita que el tiempo de arranque dependa del tamaño del dataset.
- **Un fallo al cargar el modelo no tumba el proceso.** Si `cargar_modelo()` lanza una excepción, el servicio queda arriba mostrando `/health` en `503` con la causa exacta, en vez de reiniciarse en bucle y enterrar el motivo real entre reinicios sucesivos.
- **Las cotas de validación del esquema (`PredictRequest`) duplican las del backend** (`ContenidoRequestDTO`) a propósito: el servicio es alcanzable dentro de la red del compose por sí mismo y no debe asumir que otra capa ya validó por él.

Para el detalle línea por línea de cada decisión, `app/`, `train/` y `Dockerfile` están documentados con docstrings y comentarios extensos — es la fuente de verdad más granular por encima de este README.
