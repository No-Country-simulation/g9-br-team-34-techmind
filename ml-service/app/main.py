"""Servicio de inferencia TechMind (FastAPI).

Expone el modelo de Ciencia de Datos por HTTP para que el backend Java lo
consuma. Es un servicio interno: en el despliegue de OCI no publica ningun
puerto al exterior, solo es alcanzable desde la red privada del compose.

Endpoints:
    GET  /health   -> estado del servicio y del modelo (lo lee Docker)
    POST /predict  -> clasificacion + palabras clave de un contenido tecnico
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, status
from fastapi.responses import JSONResponse

from .model import ClassifierModel, cargar_modelo
from .schemas import HealthResponse, PredictRequest, PredictResponse
from .settings import settings

logging.basicConfig(
    level=settings.log_level.upper(),
    format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s",
)
logger = logging.getLogger("techmind.ml")

# Estado del proceso. Queda en `None` si el modelo no pudo cargarse; el resto
# del archivo trata ese caso explicitamente en vez de asumir que existe.
_modelo: Optional[ClassifierModel] = None
_error_carga: Optional[str] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Carga el modelo una vez, al arrancar el proceso.

    Un fallo de carga NO tumba el contenedor a proposito. Si el proceso muriera,
    Docker lo reiniciaria en bucle y el motivo real quedaria enterrado entre
    reinicios; asi en cambio el servicio queda arriba y marcado como no sano, y
    `curl /health` responde con la causa exacta. El compose no arranca el backend
    hasta que este healthcheck pase, de modo que un modelo roto nunca llega a
    recibir trafico.
    """
    global _modelo, _error_carga

    try:
        _modelo = cargar_modelo()
        _error_carga = None
    except Exception as exc:  # noqa: BLE001 - se reporta por /health, no se traga
        _modelo = None
        _error_carga = f"{type(exc).__name__}: {exc}"
        logger.error("No se pudo cargar el modelo: %s", _error_carga)

    yield

    _modelo = None


app = FastAPI(
    title="TechMind - Servicio de Inferencia",
    description=(
        "Clasificacion tematica y extraccion de palabras clave de contenido "
        "tecnico. Consumido por la API REST de TechMind."
    ),
    version="1.0.0",
    lifespan=lifespan,
)


@app.get("/health", response_model=HealthResponse, tags=["operacion"])
def health() -> JSONResponse:
    """Estado del servicio.

    Devuelve 200 solo si hay modelo cargado. El HEALTHCHECK de la imagen depende
    de ese codigo, asi que un 503 aca es lo que mantiene al backend esperando en
    lugar de arrancar contra un servicio que no puede predecir.
    """
    cargado = _modelo is not None

    cuerpo = HealthResponse(
        status="ok" if cargado else "degraded",
        modelo_cargado=cargado,
        categorias=_modelo.categorias if _modelo else [],
        origen_modelo=_modelo.origen if _modelo else (_error_carga or "desconocido"),
    )

    return JSONResponse(
        status_code=status.HTTP_200_OK if cargado else status.HTTP_503_SERVICE_UNAVAILABLE,
        content=cuerpo.model_dump(),
    )


@app.post("/predict", response_model=PredictResponse, tags=["inferencia"])
def predict(peticion: PredictRequest) -> PredictResponse:
    """Clasifica un contenido tecnico y extrae sus palabras clave.

    La validacion del cuerpo la hace Pydantic antes de entrar aca: un payload
    malformado ya devolvio 422 y esta funcion nunca llego a ejecutarse.
    """
    if _modelo is None:
        # 503 y no 500: el servicio esta bien escrito, lo que falta es el
        # artefacto. La diferencia importa para quien lee los logs a las 3 AM.
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Modelo no disponible. Causa: {_error_carga or 'desconocida'}",
        )

    resultado = _modelo.predict(titulo=peticion.titulo, texto=peticion.texto)

    return PredictResponse(
        categoria=resultado.categoria,
        probabilidad=resultado.probabilidad,
        informacion_adicional=resultado.palabras_clave,
    )
