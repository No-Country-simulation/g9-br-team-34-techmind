"""Pruebas del contrato HTTP del servicio de inferencia.

Lo que se verifica aca es el CONTRATO, no la calidad del modelo: que los
codigos de estado sean los correctos, que la forma del JSON sea la que el
backend Java espera y que la validacion rechace lo que tiene que rechazar.

La calidad de las predicciones se mide en train/train.py y se reporta en
models/metrics.json. Afirmar en una prueba que "Tutorial de Docker" es DevOps
haria que la suite fallara cada vez que Ciencia de Datos reentrena, que es
justamente cuando NO queremos que el pipeline se detenga.
"""

from __future__ import annotations

import pytest

TEXTO_VALIDO = (
    "En este contenido se presentan los conceptos basicos para la creacion "
    "de APIs REST utilizando Java y Spring Boot."
)


def test_health_reporta_modelo_cargado(cliente):
    respuesta = cliente.get("/health")

    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["status"] == "ok"
    assert cuerpo["modelo_cargado"] is True
    assert len(cuerpo["categorias"]) > 0


def test_predict_devuelve_la_forma_que_espera_el_backend(cliente):
    """El backend Java deserializa exactamente estas tres claves."""
    respuesta = cliente.post(
        "/predict",
        json={"titulo": "Introduccion a Spring Boot", "texto": TEXTO_VALIDO},
    )

    assert respuesta.status_code == 200
    cuerpo = respuesta.json()

    assert set(cuerpo) == {"categoria", "probabilidad", "informacion_adicional"}
    assert isinstance(cuerpo["categoria"], str) and cuerpo["categoria"]
    assert 0.0 <= cuerpo["probabilidad"] <= 1.0
    assert isinstance(cuerpo["informacion_adicional"], list)
    assert all(isinstance(p, str) for p in cuerpo["informacion_adicional"])


def test_la_categoria_predicha_pertenece_al_catalogo(cliente):
    """Ninguna prediccion puede caer fuera de las categorias del modelo.

    Si esto fallara, el backend recibiria una categoria que no sabe manejar.
    """
    categorias = cliente.get("/health").json()["categorias"]

    respuesta = cliente.post(
        "/predict",
        json={"titulo": "Tutorial de Docker", "texto": TEXTO_VALIDO},
    )

    assert respuesta.json()["categoria"] in categorias


@pytest.mark.parametrize(
    ("payload", "motivo"),
    [
        ({"texto": TEXTO_VALIDO}, "falta el titulo"),
        ({"titulo": "Solo titulo"}, "falta el texto"),
        ({"titulo": "", "texto": TEXTO_VALIDO}, "titulo vacio"),
        ({"titulo": "Valido", "texto": "corto"}, "texto por debajo del minimo"),
        ({"titulo": "x" * 201, "texto": TEXTO_VALIDO}, "titulo por encima del maximo"),
    ],
)
def test_entradas_invalidas_devuelven_422(cliente, payload, motivo):
    """FastAPI responde 422 ante un cuerpo que no cumple el esquema.

    Importa que sea 422 y no 500: el backend debe poder distinguir "me mandaron
    algo mal" de "el servicio de inferencia se rompio".
    """
    respuesta = cliente.post("/predict", json=payload)

    assert respuesta.status_code == 422, motivo


def test_texto_en_el_limite_superior_es_aceptado(cliente):
    """10000 caracteres es el maximo del contrato, no uno menos."""
    respuesta = cliente.post(
        "/predict",
        json={"titulo": "Contenido extenso", "texto": "a" * 10_000},
    )

    assert respuesta.status_code == 200
