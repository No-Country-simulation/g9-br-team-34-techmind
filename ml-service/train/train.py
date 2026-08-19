"""Reempaquetado y evaluacion del modelo que entrega Ciencia de Datos.

    Uso:  python -m train.train   (desde ml-service/)

Este script NO entrena. Toma los artefactos que produce el equipo de Ciencia de
Datos en `data-science/API/` (modelo_clasificador.joblib, tfidf_titulo.joblib y
tfidf_texto.joblib, generados por su notebook) y los reempaqueta en UN solo
archivo con el contrato que sabe leer `app/model.py`, mas las metricas del
ultimo dataset versionado.

Produce dos archivos en models/:
    model.joblib   -> artefacto que carga el servicio en produccion
    metrics.json   -> metricas de la ultima corrida, para CI y para el informe

ANTECEDENTE: este script nacio como andamio de DevOps (entrenaba un dataset
sintetico de 56 filas para demostrar la canalizacion de punta a punta). El
modelo definitivo lo entrena el equipo de Ciencia de Datos en su notebook, de
modo que aqui ya no se entrena: se sirve exactamente lo que ellos entregan.
"""

from __future__ import annotations

import json
import os
from datetime import UTC, datetime
from pathlib import Path

import joblib
import pandas as pd
from scipy.sparse import hstack
from sklearn.metrics import accuracy_score, classification_report, f1_score

from app.model import (
    KEY_CATEGORIAS,
    KEY_MODELO,
    KEY_PESO_TITULO,
    KEY_VECT_TEXTO,
    KEY_VECT_TITULO,
)
from app.preprocess import limpiar_texto

# --- Rutas, relativas a este archivo y no al directorio de trabajo ---
# Asi el script funciona igual invocado desde ml-service/, desde la raiz del
# repositorio o desde un paso de CI, que es donde mas suele romperse esto.
#
# DS_MODEL_DIR / DS_DATASET admiten override por entorno: en el build de la
# imagen Docker las rutas relativas no aplican (el codigo vive en /build/), y el
# Dockerfile las fija explicitamente.
RAIZ = Path(__file__).resolve().parent.parent
DS_MODEL_DIR = Path(
    os.environ.get("DS_MODEL_DIR", RAIZ.parent / "data-science" / "API")
)
DS_DATASET = Path(
    os.environ.get("DS_DATASET", RAIZ.parent / "data-science" / "data" / "v4.json")
)
CALCULAR_METRICAS = os.environ.get("CALCULAR_METRICAS", "1") != "0"

DIRECTORIO_MODELOS = RAIZ / "models"
ARTEFACTO = DIRECTORIO_MODELOS / "model.joblib"
METRICAS = DIRECTORIO_MODELOS / "metrics.json"

# Nombre de cada pieza dentro de data-science/API/.
ARTEFACTOS_DS = {
    "modelo_clasificador.joblib": KEY_MODELO,
    "tfidf_titulo.joblib": KEY_VECT_TITULO,
    "tfidf_texto.joblib": KEY_VECT_TEXTO,
}

# El notebook entrena con peso_titulo=0.5 (celdas de vectorizacion y de
# validacion cruzada). Es el mismo valor que aplica `app/model.py` al predecir.
PESO_TITULO = 0.5


def cargar_artefactos() -> dict:
    """Carga las tres piezas que entrega Ciencia de Datos y las valida."""
    faltantes = [nombre for nombre in ARTEFACTOS_DS if not (DS_MODEL_DIR / nombre).exists()]
    if faltantes:
        raise FileNotFoundError(
            f"No estan los artefactos de Ciencia de Datos en {DS_MODEL_DIR}: "
            f"{sorted(faltantes)}. Se generan con el notebook en "
            "data-science/notebooks/Notebook_EDA_Modelado_Metricas.ipynb."
        )

    return {clave: joblib.load(DS_MODEL_DIR / nombre) for nombre, clave in ARTEFACTOS_DS.items()}


def evaluar(modelo, vect_titulo, vect_texto, df: pd.DataFrame) -> dict:
    """Mide al modelo sobre el dataset versionado usando la misma limpieza que
    `app/model.py` usa al predecir (la del notebook)."""
    df = df.copy()
    df["titulo_limpio"] = df["titulo"].apply(limpiar_texto)
    df["descripcion_limpia"] = df["descripcion"].apply(limpiar_texto)

    X_titulo = vect_titulo.transform(df["titulo_limpio"]) * PESO_TITULO
    X_texto = vect_texto.transform(df["descripcion_limpia"])
    X = hstack([X_titulo, X_texto])

    y = df["categoria"]
    y_pred = modelo.predict(X)

    print(classification_report(y, y_pred, zero_division=0))

    return {
        "exactitud": float(accuracy_score(y, y_pred)),
        "f1_macro": float(f1_score(y, y_pred, average="macro", zero_division=0)),
        "n_documentos": len(df),
        "reporte_por_clase": classification_report(
            y, y_pred, output_dict=True, zero_division=0
        ),
    }


def main() -> None:
    print("== Reempaquetado del modelo de Ciencia de Datos ==\n")

    artefactos = cargar_artefactos()
    modelo = artefactos[KEY_MODELO]
    vect_titulo = artefactos[KEY_VECT_TITULO]
    vect_texto = artefactos[KEY_VECT_TEXTO]
    categorias = sorted(modelo.classes_.tolist())

    print(f"Artefactos cargados desde {DS_MODEL_DIR}")
    print(f"Categorias del modelo: {', '.join(categorias)}")

    # --- Serializacion ---
    # ARTEFACTO: diccionario con las claves que lee app/model.py (definidas ahi,
    # para que el contrato no quede repetido en dos archivos). El resto es
    # metadato para poder rastrear, ante una duda en produccion, que modelo
    # exacto esta sirviendo.
    DIRECTORIO_MODELOS.mkdir(parents=True, exist_ok=True)

    joblib.dump(
        {
            KEY_MODELO: modelo,
            KEY_VECT_TITULO: vect_titulo,
            KEY_VECT_TEXTO: vect_texto,
            KEY_PESO_TITULO: PESO_TITULO,
            KEY_CATEGORIAS: categorias,
            "entrenado_por": "data-science/notebooks/Notebook_EDA_Modelado_Metricas.ipynb",
            "artefactos_origen": {
                nombre: str(DS_MODEL_DIR / nombre) for nombre in ARTEFACTOS_DS
            },
            "reempaquetado_en": datetime.now(UTC).isoformat(),
        },
        ARTEFACTO,
        compress=3,
    )

    tamano_kb = ARTEFACTO.stat().st_size / 1024
    print(f"\nModelo reempaquetado en {ARTEFACTO} ({tamano_kb:.1f} KB)")

    # --- Metricas ---
    if not CALCULAR_METRICAS:
        print("Metricas omitidas (CALCULAR_METRICAS=0).")
        return

    if not DS_DATASET.exists():
        raise FileNotFoundError(
            f"No existe el dataset para evaluar en {DS_DATASET}. "
            "Defina DS_DATASET o verifique data-science/data/."
        )

    df = pd.read_json(DS_DATASET)
    print(f"\nEvaluando sobre {DS_DATASET} ({len(df)} documentos)")

    metricas = evaluar(modelo, vect_titulo, vect_texto, df)
    exactitud = metricas["exactitud"]
    f1_macro = metricas["f1_macro"]

    print(f"\nExactitud sobre el dataset: {exactitud:.3f}")
    print(f"F1 macro sobre el dataset: {f1_macro:.3f}\n")

    METRICAS.write_text(
        json.dumps(
            {
                # Se mantienen los nombres historicos para que los resumenes de
                # CI/CD y el informe sigan leyendo lo mismo que antes.
                "exactitud_prueba": round(exactitud, 4),
                "f1_macro": round(f1_macro, 4),
                "n_documentos": metricas["n_documentos"],
                "categorias": categorias,
                "entrenado_en": datetime.now(UTC).isoformat(),
                "reporte_por_clase": metricas["reporte_por_clase"],
                "dataset_evaluado": str(DS_DATASET),
                "peso_titulo": PESO_TITULO,
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    print(f"Metricas escritas en {METRICAS}")


if __name__ == "__main__":
    main()