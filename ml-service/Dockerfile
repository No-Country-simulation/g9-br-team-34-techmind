# Servicio de inferencia TechMind - imagen de produccion.
#
# Construccion en dos etapas. La etapa `builder` instala las dependencias y
# reempaqueta el modelo de Ciencia de Datos; la etapa final se queda solo con el
# entorno virtual, el codigo del servicio y el artefacto servible.
#
# CONTEXTO: esta imagen se construye desde la RAIZ del repositorio (no desde
# ml-service/) porque el reempaquetado necesita los artefactos de
# data-science/API/. El `.dockerignore` de la raiz excluye todo lo demas del
# contexto. En docker-compose.yml y en los workflows se indica
# `context: .` + `dockerfile: ml-service/Dockerfile`; el backend sigue usando su
# propio contexto.
#
# El reempaquetado ocurre en tiempo de BUILD y no en tiempo de ARRANQUE a
# proposito: el arranque del contenedor debe ser rapido y deterministico, y dos
# replicas del mismo servicio tienen que servir exactamente el mismo modelo.

# ---------------------------------------------------------------------------
# Etapa 1: dependencias + reempaquetado del modelo
# ---------------------------------------------------------------------------
FROM python:3.11-slim AS builder

# PYTHONDONTWRITEBYTECODE: los .pyc no aportan nada en una imagen inmutable.
# PIP_NO_CACHE_DIR: la cache de pip solo agrega peso a la capa.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /build

# El entorno virtual permite copiar TODAS las dependencias a la etapa final con
# un solo COPY, sin arrastrar pip, setuptools ni las cabeceras de compilacion.
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# requirements.txt se copia solo, antes que el codigo. Es la capa mas cara de
# reconstruir (scikit-learn, numpy, spacy y scipy pesan cientos de MB) y de esta
# forma Docker solo la rehace cuando cambian las dependencias, no cuando cambia
# una linea de codigo.
COPY ml-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ml-service/app/ ./app/
COPY ml-service/train/ ./train/

# Artefactos del modelo que entrega Ciencia de Datos (un .joblib por pieza) y el
# dataset versionado. Se copian bajo /build/data-science/ para replicar la
# estructura del repositorio; el Dockerfile fija DS_MODEL_DIR/DS_DATASET porque
# dentro de /build las rutas relativas al codigo no apuntan ahi.
COPY data-science/API/ ./data-science/API/
COPY data-science/data/ ./data-science/data/

# Genera /build/models/model.joblib a partir de los artefactos de DS.
#
# CALCULAR_METRICAS=0: la evaluacion sobre el dataset completo lematiza miles de
# documentos y solo la necesitan CI y el CD (son quienes suben metrics.json). El
# build solo necesita el artefacto servible; evaluar aca duplicaria ese trabajo
# y, al construirse la imagen emulada en el CD, sumaria minutos a cada build.
ENV DS_MODEL_DIR=/build/data-science/API \
    DS_DATASET=/build/data-science/data/v4.json \
    CALCULAR_METRICAS=0
RUN python -m train.train

# ---------------------------------------------------------------------------
# Etapa 2: imagen final
# ---------------------------------------------------------------------------
FROM python:3.11-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:$PATH"

# PYTHONUNBUFFERED es lo que hace que los logs aparezcan en `docker logs` al
# instante. Sin esta variable Python almacena la salida en un buffer y los logs
# de un contenedor que se cuelga se pierden justo cuando mas se necesitan.

# Usuario sin privilegios. Un proceso que no necesita ser root no debe correr
# como root: si alguien logra ejecutar codigo dentro del contenedor, la
# diferencia entre uid 1000 y uid 0 es la diferencia entre un incidente
# contenido y uno que no lo esta.
RUN groupadd --system --gid 1000 techmind \
    && useradd --system --uid 1000 --gid techmind --create-home techmind

WORKDIR /app

COPY --from=builder /opt/venv /opt/venv
COPY --from=builder --chown=techmind:techmind /build/models/ /app/models/
COPY --chown=techmind:techmind ml-service/app/ /app/app/

# `train/` NO se copia: la imagen de produccion no entrena, y todo lo que no
# entra reduce la superficie de ataque y el tamano final.

USER techmind

EXPOSE 8000

# Healthcheck via urllib porque la imagen slim no trae curl ni wget, y
# instalarlos solo para esto agregaria paquetes a la imagen de produccion.
# /health devuelve 503 si el modelo no cargo; urlopen lanza excepcion ante un
# 503, el proceso termina con codigo distinto de cero y Docker marca el
# contenedor como unhealthy. Que es exactamente lo que queremos.
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health', timeout=4)" || exit 1

# Un solo worker: el modelo se carga entero en memoria por proceso y la VM
# Always Free de OCI tiene RAM acotada. Si hiciera falta mas capacidad, conviene
# escalar con mas replicas del contenedor antes que con mas workers.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
