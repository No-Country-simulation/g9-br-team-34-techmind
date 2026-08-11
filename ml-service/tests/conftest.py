"""Configuracion compartida de las pruebas.

Las variables de entorno se fijan a nivel de MODULO, antes de cualquier import
de `app`. No es un detalle de estilo: `app.settings` construye el objeto
Settings en tiempo de import, de modo que cualquier variable definida despues
llegaria tarde y las pruebas correrian contra la ruta de produccion
(/app/models), que en un runner de CI no existe.

pytest importa conftest.py antes que los archivos de prueba, asi que este es el
unico lugar donde se puede hacer.
"""

from __future__ import annotations

import os
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

os.environ["MODEL_PATH"] = str(RAIZ / "models" / "model.joblib")
os.environ["MODEL_SOURCE"] = "local"

import pytest  # noqa: E402  (debe ir despues de fijar el entorno)
from fastapi.testclient import TestClient  # noqa: E402


@pytest.fixture(scope="session")
def cliente() -> TestClient:
    """Cliente HTTP contra la app real.

    Se usa como context manager porque es lo que dispara el `lifespan` de
    FastAPI: sin eso el modelo no se cargaria y todas las pruebas verian un 503.
    """
    from app.main import app

    with TestClient(app) as c:
        yield c
