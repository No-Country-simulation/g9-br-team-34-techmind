# TechMind · Servicio de Inferencia

Servicio interno en FastAPI que expone el modelo de clasificación de contenido técnico por HTTP. Lo consume exclusivamente el [backend](../backend/README.md); en producción no publica ningún puerto al exterior.

> Visión general del proyecto completo y arquitectura de dos servicios: [README del repositorio](../README.md).

---

## Tabla de contenidos

- [Qué hace](#qué-hace)
- [Endpoints](#endpoints)
- [Correr sin Docker](#correr-sin-docker)
- [Entrenar el modelo](#entrenar-el-modelo)
- [Configuración](#configuración)
- [Origen del modelo: local vs. OCI](#origen-del-modelo-local-vs-oci)
- [Pruebas](#pruebas)
- [Relación con `data-science/`](#relación-con-data-science)
- [Decisiones de diseño](#decisiones-de-diseño)

---

## Qué hace

Recibe un título y un texto, y devuelve:

- **`categoria`** — una de las **7 categorías** que predice el modelo (backend, base de datos, devops, frontend, machine learning, mobile, seguridad), en minúsculas tal como las entrena Ciencia de Datos. El modelo es el que el equipo de Ciencia de Datos entrena en su notebook (`data-science/`): dos vectorizadores TF-IDF (título y texto) + Regresión Logística.
- **`probabilidad`** — confianza de la predicción, en `[0.0, 1.0]`.
- **`informacion_adicional`** — las palabras clave con mayor peso TF-IDF dentro del texto (hasta `MAX_KEYWORDS`, default 5), lematizadas como en el entrenamiento.

Este servicio **no valida reglas de negocio** más allá de las cotas de longitud del propio esquema: asume que el backend ya validó la entrada. Tampoco entrena en tiempo de ejecución — el modelo se reempaqueta una vez, en tiempo de build de la imagen, y se sirve como artefacto estático (ver [Entrenar el modelo](#entrenar-el-modelo)).

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
  "categoria": "backend",
  "probabilidad": 0.89,
  "informacion_adicional": ["java", "spring boot", "rest"]
}
```

Si el modelo no pudo cargarse al arrancar, responde `503` con la causa exacta del fallo en el `detail` — nunca `500`: el servicio está bien escrito, lo que falta es el artefacto.

**`GET /health`**

```json
{
  "status": "ok",
  "modelo_cargado": true,
  "categorias": ["backend", "base de datos", "devops", "frontend", "machine learning", "mobile", "seguridad"],
  "origen_modelo": "local:///app/models/model.joblib"
}
```

`status` es exactamente lo que lee el `HEALTHCHECK` de Docker (y, a través de él, la condición `service_healthy` que usa Compose para decidir cuándo arrancar el backend). Solo es `"ok"` si el modelo está realmente cargado en memoria — un servicio "arriba" pero sin modelo se reporta `"degraded"` con `503`, en vez de fingir que puede predecir.

## Correr sin Docker

Requiere Python 3.11+.

```bash
cd ml-service
python -m venv .venv && source .venv/bin/activate    # o el equivalente en tu shell
pip install -r requirements-dev.txt

python -m train.train        # genera models/model.joblib (necesario la primera vez)
uvicorn app.main:app --reload --port 8000
```

`requirements-dev.txt` incluye `requirements.txt` más las dependencias de pruebas y lint (`pytest`, `httpx`, `ruff`) — nunca viajan a la imagen de producción, que solo instala `requirements.txt`.

## Entrenar el modelo

```bash
python -m train.train
```

Este script **no entrena**: toma los artefactos que entrega el equipo de Ciencia de Datos en `data-science/API/` (`modelo_clasificador.joblib`, `tfidf_titulo.joblib`, `tfidf_texto.joblib`), los reempaqueta en un único archivo con el contrato que lee `app/model.py` y evalúa al modelo sobre el dataset versionado (`data-science/data/v4.json`). Genera dos archivos en `models/`:

- **`model.joblib`** — el artefacto que carga el servicio en producción.
- **`metrics.json`** — métricas de la corrida (para CI y para el informe del equipo de Ciencia de Datos).

Para que el preprocesado en servir coincida con el del entrenamiento, el servicio usa la **misma limpieza que el notebook** (lematización spaCy en español, lista de excepciones de términos técnicos y normalizaciones) — ver `app/preprocess.py`, transposición literal de la celda de preprocesado del notebook. Por eso `requirements.txt` incluye `spacy` y el modelo `es_core_news_sm`; sin el mismo lematizador, las predicciones en producción se degradarían silenciosamente.

**El único contrato que debe respetarse** para que `app/model.py` pueda cargarlo es la forma del artefacto serializado: un diccionario con las claves `modelo`, `vect_titulo`, `vect_texto`, `peso_titulo` y `categorias`. Si Ciencia de Datos reentrena y cambia el formato de entrega, el punto de adaptación es `train/train.py` — el resto del servicio no tiene por qué enterarse.

> El reempaquetado en el build de la imagen corre con `CALCULAR_METRICAS=0`: las métricas solo las generan CI y el CD (son quienes suben `metrics.json`); el build solo necesita el artefacto servible, y evaluar el dataset completo encarecería cada build (especialmente emulado en el CD).

## Configuración

Todas las variables entran por entorno (`app/settings.py`, vía `pydantic-settings`) — la misma imagen Docker corre en local, CI y la VM de OCI sin reconstruirse; cambiar de entorno es cambiar variables, nunca reconstruir.

| Variable | Descripción | Default |
|---|---|---|
| `HOST` / `PORT` | Bind del servidor | `0.0.0.0` / `8000` |
| `LOG_LEVEL` | Nivel de logging | `info` |
| `MODEL_SOURCE` | `local` o `oci` — ver [siguiente sección](#origen-del-modelo-local-vs-oci) | `local` |
| `MODEL_PATH` | Ruta del artefacto `.joblib` | `/app/models/model.joblib` |
| `MAX_KEYWORDS` | Máximo de palabras clave devueltas en `informacion_adicional` | `5` |
| `OCI_AUTH_METHOD` | `config_file` (dev) o `instance_principal` (producción, sin credenciales en disco) | `config_file` |
| `OCI_NAMESPACE` / `OCI_REGION` | Namespace y región del tenancy (si se omiten, el SDK los resuelve) | *(vacío)* |
| `OCI_BUCKET_NAME` / `OCI_OBJECT_NAME` | Ubicación del modelo en Object Storage | `techmind-models` / `model.joblib` |
| `OCI_CONFIG_FILE` / `OCI_CONFIG_PROFILE` | Ruta y perfil de `~/.oci/config` (solo con auth `config_file`) | `~/.oci/config` / `DEFAULT` |

## Origen del modelo: local vs. OCI

- **`MODEL_SOURCE=local`** (desarrollo, `docker-compose.yml`) — el modelo se reempaqueta en tiempo de build a partir de los artefactos de `data-science/` y viaja dentro de la propia imagen Docker. Quien clona el repositorio levanta todo con un solo comando, sin necesitar credenciales de nube.
- **`MODEL_SOURCE=oci`** (producción, `docker-compose.prod.yml`) — el contenedor descarga `model.joblib` desde OCI Object Storage al arrancar, autenticándose como la propia instancia de OCI (`instance_principal`, sin claves privadas en disco). Si Object Storage no responde pero el volumen conserva una copia previa válida, el servicio arranca igual con esa copia en vez de quedar caído — un incidente transitorio de OCI no debe tumbar el servicio si ya hay un modelo utilizable en disco. Ese origen efectivo (recién descargado vs. copia local de respaldo) queda reflejado en el campo `origen_modelo` de `/health`, precisamente para poder distinguir ambos casos en producción.

## Pruebas

```bash
pytest -v
```

Requiere que exista `models/model.joblib` (correr `python -m train.train` antes si hace falta — `make test-ml` ya lo hace por vos). Las pruebas verifican el **contrato HTTP**: códigos de estado, forma del JSON, validación de entrada — deliberadamente **no** afirman qué categoría concreta debería predecirse para un texto dado, porque eso haría que la suite fallara cada vez que Ciencia de Datos reentrena el modelo, que es exactamente el momento en que no conviene que el pipeline de CI se rompa. La calidad de las predicciones se mide aparte, en `models/metrics.json`.

## Relación con `data-science/`

El repositorio tiene dos carpetas relacionadas con Ciencia de Datos y conviene no confundirlas:

- **[`data-science/`](../data-science/)** — el espacio de trabajo del equipo de Ciencia de Datos: notebooks de EDA, modelado y métricas, datasets versionados (`v2`, `v3`, `v4`) y los **artefactos entrenados** en `data-science/API/`.
- **`ml-service/`** (este directorio) — el servicio productivo que sirve ese modelo por HTTP. `train/train.py` reempaqueta los artefactos de `data-science/API/` en el formato que lee `app/model.py`; **no reproduce el trabajo de exploración ni reentrena**.

El flujo real es: el notebook de Ciencia de Datos entrena y deja `data-science/API/*.joblib` → `train.py` los reempaqueta en `model.joblib` → el CD lo publica en OCI Object Storage → el contenedor lo descarga al arrancar (`MODEL_SOURCE=oci`). Lo que se sirve en producción es, literalmente, el modelo del notebook — no una copia de un dataset semilla.

## Decisiones de diseño

- **Un solo worker de uvicorn** (`--workers 1`, ver `Dockerfile`). El modelo se carga entero en memoria por proceso, y la VM `Always Free` de OCI tiene RAM acotada; para más capacidad conviene escalar con más réplicas del contenedor antes que con más workers por proceso.
- **El contenedor de producción nunca entrena.** `train/` no se copia a la imagen final — reduce superficie de ataque y tamaño, y evita que el tiempo de arranque dependa del tamaño del dataset.
- **Un fallo al cargar el modelo no tumba el proceso.** Si `cargar_modelo()` lanza una excepción, el servicio queda arriba mostrando `/health` en `503` con la causa exacta, en vez de reiniciarse en bucle y enterrar el motivo real entre reinicios sucesivos.
- **Las cotas de validación del esquema (`PredictRequest`) duplican las del backend** (`ContenidoRequestDTO`) a propósito: el servicio es alcanzable dentro de la red del compose por sí mismo y no debe asumir que otra capa ya validó por él.

Para el detalle línea por línea de cada decisión, `app/`, `train/` y `Dockerfile` están documentados con docstrings y comentarios extensos — es la fuente de verdad más granular por encima de este README.
