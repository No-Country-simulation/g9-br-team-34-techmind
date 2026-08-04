# Ejecución local

Cómo levantar TechMind en tu máquina.

---

## Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Docker Desktop / Engine | 24+ | Todo |
| Docker Compose | **2.24+** | Las etiquetas `!reset` de `docker-compose.prod.yml` |
| Java (JDK) | 17 | Solo para ejecutar el backend fuera de Docker |
| Python | 3.11 | Solo para entrenar o probar el `ml-service` fuera de Docker |
| Make | cualquiera | Opcional, son atajos |

Con Docker y Compose alcanza. Java y Python solo hacen falta si quieres trabajar
en un servicio sin contenedor.

```bash
docker compose version   # debe dar 2.24 o superior
```

---

## Arranque rápido

```bash
make env    # crea .env a partir de .env.example
make up     # construye y levanta todo
make smoke  # verifica con los 3 ejemplos del brief
```

Sin `make`:

```bash
cp .env.example .env
docker compose up --build --detach --wait
./scripts/smoke-test.sh
```

La primera construcción tarda varios minutos: descarga Spring Boot y
scikit-learn. Las siguientes usan caché y son cuestión de segundos.

### Qué queda levantado

| URL | Qué es |
|---|---|
| <http://localhost:8080> | API REST (backend Java) |
| <http://localhost:8080/swagger-ui/index.html> | Documentación interactiva de la API |
| <http://localhost:8000/docs> | Documentación del servicio de inferencia |
| <http://localhost:8000/health> | Estado del modelo |

En desarrollo el puerto 8000 se publica para poder probar el modelo directamente
sin pasar por el backend. En producción no se publica.

---

## Probar el modelo a mano

```bash
curl -X POST http://localhost:8000/predict \
  -H 'Content-Type: application/json' \
  -d '{
    "titulo": "Introducción a Spring Boot",
    "texto": "En este contenido se presentan los conceptos básicos para la creación de APIs REST utilizando Java y Spring Boot."
  }'
```

```json
{
  "categoria": "Backend",
  "probabilidad": 0.7421,
  "informacion_adicional": ["spring boot", "apis rest", "java", "creacion", "conceptos"]
}
```

Los valores exactos dependen del modelo entrenado; la forma es siempre esa.

---

## Comandos disponibles

```bash
make            # lista todos los comandos con su descripción
```

| Comando | Qué hace |
|---|---|
| `make up` | Levanta el sistema completo |
| `make down` | Lo detiene (conserva los datos) |
| `make logs` | Sigue los logs de ambos servicios |
| `make logs-ml` | Solo los del servicio de inferencia |
| `make ps` | Estado de los contenedores |
| `make rebuild` | Reconstruye ignorando la caché |
| `make test` | Todas las pruebas |
| `make lint` | Linter de Python |
| `make train` | Reentrena el modelo en local |
| `make smoke` | Prueba el sistema levantado |
| `make clean` | Limpia artefactos (conserva la base de datos) |
| `make clean-all` | Limpia todo, **incluida la base de datos** |

---

## Trabajar en un servicio sin contenedor

### Solo el `ml-service`

```bash
cd ml-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt

python -m train.train        # genera models/model.joblib

MODEL_PATH=$(pwd)/models/model.joblib uvicorn app.main:app --reload --port 8000
```

`MODEL_PATH` es necesario: por defecto apunta a `/app/models/model.joblib`, que
es la ruta *dentro* del contenedor.

Con `--reload`, uvicorn recarga al guardar. Ojo: recargar vuelve a ejecutar el
`lifespan`, es decir, recarga el modelo entero.

### Solo el backend

```bash
cd backend
./mvnw spring-boot:run
```

Si quieres que use el `ml-service` que corre en Docker:

```bash
docker compose up -d ml-service    # solo ese servicio
cd backend
INFERENCE_SERVICE_URL=http://localhost:8000 ./mvnw spring-boot:run
```

Aquí sí es `localhost`, porque el backend corre fuera de Docker y el puerto 8000
está publicado en la máquina. Dentro de la red del compose sería
`http://ml-service:8000`.

---

## Entrenar con otro dataset

El dataset semilla (`ml-service/train/dataset.csv`) es un **andamio de DevOps**
para que la canalización funcione de punta a punta desde el principio. El equipo
de Ciencia de Datos lo reemplaza por el suyo.

Formato: CSV con columnas `titulo`, `texto`, `categoria`. Mínimo 2 ejemplos por
categoría (lo exige la división estratificada).

```bash
# 1. Reemplazar ml-service/train/dataset.csv
# 2. Reentrenar
make train
# 3. Reconstruir la imagen para que el modelo nuevo entre en el contenedor
make rebuild && make up
```

El paso 3 es necesario porque el modelo se entrena durante el build de la
imagen. Sin reconstruir, el contenedor sigue sirviendo el modelo anterior.

---

## Problemas frecuentes

### `port is already allocated`

Algo ocupa el 8080 o el 8000. Cámbialo en el `.env`:

```bash
BACKEND_PORT=9090
ML_SERVICE_PORT=9000
```

### El backend se queda esperando y no arranca

Es lo esperado: `depends_on: condition: service_healthy` lo hace esperar a que el
`ml-service` cargue el modelo. Si nunca arranca:

```bash
docker compose logs ml-service
curl http://localhost:8000/health
```

El campo `origen_modelo` de `/health` trae el error exacto.

### `unsupported Compose file tag: !reset`

Docker Compose es anterior a la 2.24. Actualiza Docker Desktop. Solo afecta a
`docker-compose.prod.yml`; el de desarrollo funciona igual.

### Las pruebas fallan con `FileNotFoundError: model.joblib`

Falta entrenar antes de probar:

```bash
cd ml-service && python -m train.train && pytest
```

En CI esto ya va en el orden correcto.
