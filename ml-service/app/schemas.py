"""Contrato HTTP del servicio de inferencia.

Estos modelos son el espejo exacto de los DTO de cliente del backend Java
(`ModelPredictClientRequestDto` / `ModelPredictClientResponseDto`). Si un campo
cambia aca, cambia alla: son las dos mitades del mismo contrato interno.

Los nombres van en snake_case por convencion de Python (PEP 8); el backend ya
mapea `informacion_adicional` explicitamente, asi que no hay friccion.
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class PredictRequest(BaseModel):
    """Entrada de POST /predict.

    Las cotas replican las de `ContenidoRequestDTO` del backend. Duplicarlas no
    es redundancia: el servicio de inferencia es alcanzable por si mismo dentro
    de la red del compose, y un servicio no debe confiar en que alguien mas ya
    valido por el.
    """

    titulo: str = Field(min_length=1, max_length=200)
    texto: str = Field(min_length=20, max_length=10_000)

    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "titulo": "Introduccion a Spring Boot",
                    "texto": (
                        "En este contenido se presentan los conceptos basicos "
                        "para la creacion de APIs REST utilizando Java y Spring Boot."
                    ),
                }
            ]
        }
    }


class PredictResponse(BaseModel):
    """Salida de POST /predict."""

    categoria: str
    probabilidad: float = Field(ge=0.0, le=1.0)
    informacion_adicional: list[str] = Field(default_factory=list)


class HealthResponse(BaseModel):
    """Salida de GET /health.

    `status` es lo que lee el HEALTHCHECK de Docker y, a traves de el, la
    condicion `service_healthy` del compose. Que devuelva "ok" solo cuando el
    modelo esta realmente cargado es lo que impide que el backend arranque
    apuntando a un servicio que aun no puede responder nada util.
    """

    status: str
    modelo_cargado: bool
    categorias: list[str] = Field(default_factory=list)
    origen_modelo: str
