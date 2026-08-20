# Servicio de inferencia TechMind - imagen de produccion.
#
# Construccion en dos etapas. La etapa `builder` instala las dependencias; la
# etapa final se queda solo con el entorno virtual, el codigo del servicio y
# los artefactos del modelo que entrega Ciencia de Datos.
#
# NO se entrena en tiempo de build: los tres .joblib (clasificador +
# vectorizadores de titulo y texto) son el entregable real de Ciencia de
# Datos y viven versionados en `models/`, tal cual los produjo el equipo. El
# contenedor de produccion solo los sirve, nunca los genera -- asi dos
# replicas del mismo servicio garantizan servir exactamente el mismo modelo.
# `train/` sigue existiendo como andamio de referencia (ver su README) pero
# ya no participa del build.

# ---------------------------------------------------------------------------
# Etapa 1: dependencias
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
# reconstruir (scikit-learn, numpy y scipy pesan cientos de MB) y de esta forma
# Docker solo la rehace cuando cambian las dependencias, no cuando cambia una
# linea de codigo.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
RUN python -m spacy download es_core_news_sm

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
COPY --chown=techmind:techmind models/ /app/models/
COPY --chown=techmind:techmind app/ /app/app/

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
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --workers 1"]
