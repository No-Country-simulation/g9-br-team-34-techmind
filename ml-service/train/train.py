"""Entrenamiento y serializacion del modelo de clasificacion de contenido tecnico.

    Uso:  python -m train.train   (desde ml-service/)

Produce dos archivos en models/:
    model.joblib   -> artefacto que carga el servicio en produccion
    metrics.json   -> metricas de la ultima corrida, para CI y para el informe

ALCANCE: este script es un ANDAMIO DE DEVOPS, no el entregable de Ciencia de
Datos. Existe para que la canalizacion completa (entrenar -> serializar ->
publicar en Object Storage -> servir en un contenedor) se pueda ejecutar y
demostrar de punta a punta desde el primer dia, sin quedar bloqueada esperando
al notebook.

El equipo de Ciencia de Datos deberia reemplazar dataset.csv y, si lo necesita,
este mismo script. El unico contrato que debe respetarse es el formato del
artefacto serializado (ver ARTEFACTO mas abajo), porque es lo que app/model.py
sabe leer.
"""

from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path

import joblib
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report
from sklearn.model_selection import StratifiedKFold, cross_val_score, train_test_split
from sklearn.pipeline import Pipeline

# --- Rutas, relativas a este archivo y no al directorio de trabajo ---
# Asi el script funciona igual invocado desde ml-service/, desde la raiz del
# repositorio o desde un paso de CI, que es donde mas suele romperse esto.
RAIZ = Path(__file__).resolve().parent.parent
DATASET = RAIZ / "train" / "dataset.csv"
DIRECTORIO_MODELOS = RAIZ / "train" / "artifacts"
ARTEFACTO = DIRECTORIO_MODELOS / "model.joblib"
METRICAS = DIRECTORIO_MODELOS / "metrics.json"

# Semilla fija: dos corridas sobre el mismo dataset deben producir el mismo
# modelo. Sin esto, una metrica que baja entre commits no se puede distinguir de
# ruido aleatorio.
SEMILLA = 42

# Palabras vacias del espanol. Se listan aca en vez de depender de NLTK para no
# arrastrar una descarga de corpus en tiempo de build de la imagen.
STOP_WORDS_ES = [
    "a", "al", "algo", "algunas", "algunos", "ante", "antes", "como", "con",
    "contra", "cual", "cuando", "de", "del", "desde", "donde", "durante", "e",
    "el", "ella", "ellas", "ellos", "en", "entre", "era", "es", "esta", "estan",
    "este", "esto", "estos", "ha", "hasta", "hay", "la", "las", "le", "les",
    "lo", "los", "mas", "me", "mi", "mientras", "muy", "no", "nos", "o", "otra",
    "otras", "otro", "otros", "para", "pero", "por", "porque", "que", "se",
    "sea", "segun", "ser", "si", "sin", "sobre", "son", "su", "sus", "tambien",
    "te", "tiene", "todo", "todos", "tras", "un", "una", "uno", "unos", "y",
    "ya",
]


def cargar_datos() -> pd.DataFrame:
    """Lee el dataset y verifica que sirva para entrenar."""
    if not DATASET.exists():
        raise FileNotFoundError(f"No existe el dataset en {DATASET}")

    df = pd.read_csv(DATASET)

    columnas_esperadas = {"titulo", "texto", "categoria"}
    faltantes = columnas_esperadas - set(df.columns)
    if faltantes:
        raise ValueError(f"Al dataset le faltan columnas: {sorted(faltantes)}")

    # Filas incompletas o duplicadas ensucian las metricas: una fila repetida
    # que cae en entrenamiento y en prueba a la vez infla la exactitud.
    antes = len(df)
    df = df.dropna(subset=["titulo", "texto", "categoria"])
    df = df.drop_duplicates(subset=["titulo", "texto"])
    if len(df) < antes:
        print(f"  Descartadas {antes - len(df)} filas nulas o duplicadas")

    # `train_test_split` estratificado exige al menos 2 ejemplos por clase.
    conteo = df["categoria"].value_counts()
    escasas = conteo[conteo < 2]
    if not escasas.empty:
        raise ValueError(
            f"Estas categorias tienen menos de 2 ejemplos y no permiten "
            f"division estratificada: {escasas.to_dict()}"
        )

    return df


def construir_pipeline() -> Pipeline:
    """Arma el pipeline TF-IDF + Regresion Logistica.

    Va como Pipeline y no como dos objetos sueltos porque el vectorizador tiene
    que viajar dentro del mismo artefacto que el clasificador: si se guardaran
    por separado, cualquier desajuste entre ambos daria predicciones silenciosa
    y sutilmente equivocadas en lugar de un error.
    """
    return Pipeline(
        [
            (
                "tfidf",
                TfidfVectorizer(
                    # Bigramas ademas de palabras sueltas: "spring boot" y
                    # "base de datos" significan mas juntos que separados.
                    ngram_range=(1, 2),
                    min_df=1,
                    max_df=0.85,
                    sublinear_tf=True,
                    strip_accents="unicode",
                    lowercase=True,
                    stop_words=STOP_WORDS_ES,
                ),
            ),
            (
                "clf",
                LogisticRegression(
                    max_iter=1000,
                    # El dataset semilla esta balanceado, pero el del equipo de
                    # Ciencia de Datos probablemente no lo este.
                    class_weight="balanced",
                    random_state=SEMILLA,
                ),
            ),
        ]
    )


def main() -> None:
    print("== Entrenamiento del clasificador TechMind ==\n")

    df = cargar_datos()
    print(f"Dataset: {len(df)} documentos, {df['categoria'].nunique()} categorias")
    for categoria, cantidad in df["categoria"].value_counts().items():
        print(f"  - {categoria}: {cantidad}")

    # El titulo se concatena al texto igual que en app/model.py. Entrenar sobre
    # una composicion distinta a la que se usa al predecir es la forma mas
    # comun de que un modelo rinda peor en produccion que en el notebook.
    X = df["titulo"] + ". " + df["texto"]
    y = df["categoria"]

    X_entreno, X_prueba, y_entreno, y_prueba = train_test_split(
        X, y, test_size=0.25, random_state=SEMILLA, stratify=y
    )

    pipeline = construir_pipeline()
    pipeline.fit(X_entreno, y_entreno)

    exactitud = float(pipeline.score(X_prueba, y_prueba))
    print(f"\nExactitud sobre el conjunto de prueba: {exactitud:.3f}\n")

    reporte = classification_report(
        y_prueba, pipeline.predict(X_prueba), output_dict=True, zero_division=0
    )
    print(classification_report(y_prueba, pipeline.predict(X_prueba), zero_division=0))

    # Con un dataset chico, una sola division puede dar un numero engañoso por
    # puro azar. La validacion cruzada da una lectura mas honesta.
    n_splits = min(5, int(df["categoria"].value_counts().min()))
    if n_splits >= 2:
        cv = StratifiedKFold(n_splits=n_splits, shuffle=True, random_state=SEMILLA)
        puntajes = cross_val_score(construir_pipeline(), X, y, cv=cv, scoring="f1_macro")
        f1_cv, f1_std = float(puntajes.mean()), float(puntajes.std())
        print(f"F1 macro (validacion cruzada, {n_splits} particiones): "
              f"{f1_cv:.3f} +/- {f1_std:.3f}")
    else:
        f1_cv, f1_std = None, None

    # --- Serializacion ---
    #
    # ARTEFACTO: diccionario con estas claves. app/model.py lee 'pipeline' y
    # 'categorias'; el resto es metadato para poder rastrear, ante una duda en
    # produccion, que modelo exacto esta sirviendo.
    DIRECTORIO_MODELOS.mkdir(parents=True, exist_ok=True)

    # El modelo final se reentrena con TODOS los datos: la division en prueba
    # servia para medir, y ya midio. Descartar ese 25% al desplegar seria tirar
    # informacion util sin ninguna razon.
    modelo_final = construir_pipeline()
    modelo_final.fit(X, y)

    joblib.dump(
        {
            "pipeline": modelo_final,
            "categorias": sorted(y.unique().tolist()),
            "entrenado_en": datetime.now(UTC).isoformat(),
            "n_documentos": len(df),
            "exactitud_prueba": exactitud,
        },
        ARTEFACTO,
        compress=3,
    )

    METRICAS.write_text(
        json.dumps(
            {
                "exactitud_prueba": round(exactitud, 4),
                "f1_macro_cv": round(f1_cv, 4) if f1_cv is not None else None,
                "f1_macro_cv_std": round(f1_std, 4) if f1_std is not None else None,
                "n_documentos": len(df),
                "categorias": sorted(y.unique().tolist()),
                "entrenado_en": datetime.now(UTC).isoformat(),
                "reporte_por_clase": reporte,
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    tamano_kb = ARTEFACTO.stat().st_size / 1024
    print(f"\nModelo serializado en {ARTEFACTO} ({tamano_kb:.1f} KB)")
    print(f"Metricas escritas en {METRICAS}")


if __name__ == "__main__":
    main()
