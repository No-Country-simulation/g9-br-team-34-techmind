"""Preprocesado de texto en espanol, replicando el notebook de Ciencia de Datos.

Este modulo es la mitad de la consistencia entrenar/servir. Reproduce EXACTAMENTE
la limpieza que el equipo de Ciencia de Datos aplico en
`data-science/notebooks/Notebook_EDA_Modelado_Metricas.ipynb` (celda "LIMPIEZA /
PREPROCESAMIENTO EN ESPANOL") antes de entrenar el modelo que aqui se sirve.

Si la prediccion en produccion no aplicara la misma normalizacion, la misma
lista de excepciones y la misma lematizacion de spaCy que se usaron al entrenar,
el texto llegaria al vectorizador con una forma distinta a la que el modelo vio
en el entrenamiento y las predicciones se degradarian sin que nadie lo note.
"""

from __future__ import annotations

import re

import spacy

# Palabras propias del dominio tecnico que NO deben tratarse como stopwords.
# Mismo set que el notebook: "api", "git" o "docker" son senales, no ruido.
EXCEPCIONES = {
    # Backend / lenguajes
    "api", "rest", "soap", "orm", "jwt", "sdk", "cli", "php", "java",
    "cplusplus", "csharp", "fsharp", "dotnet", "nodejs", "go", "rust",
    # Frontend
    "css", "html", "js", "ts", "ux", "ui", "reactjs", "vuejs", "angularjs",
    "seo",
    # Base de datos
    "sql", "nosql",
    # DevOps
    "git", "ci", "cd", "cicd", "aws", "gcp", "k8s", "ssh", "vpn", "dns",
    "ftp", "tcp", "ip", "http", "https", "json", "xml", "yaml", "npm",
    "pip", "venv", "docker",
    # Machine Learning
    "ai", "ml", "nlp", "gpu", "cpu", "ram",
    # Seguridad
    "oauth", "sso",
    # Mobile
    "ios", "apk",
}

# Terminos compuestos que la limpieza de puntuacion destruiria (ej. "c++" -> "c",
# "c#" -> "c", ".net" -> "net") si no se protegen antes. Mismo diccionario que
# el notebook.
NORMALIZACIONES = {
    r"\bc\+\+": "cplusplus",
    r"\bc#": "csharp",
    r"\bf#": "fsharp",
    r"\.net\b": "dotnet",
    r"\bnode\.js\b": "nodejs",
    r"\bvue\.js\b": "vuejs",
    r"\breact\.js\b": "reactjs",
    r"\bangular\.js\b": "angularjs",
    r"\bci\s*/\s*cd\b": "cicd",
}

# El modelo se carga una sola vez por proceso (los ~12 MB del pipeline quedan en
# RAM). Se hace de forma diferida para que los modulos que solo importan las
# constantes no paguen la carga; quien llama a `limpiar_texto` si la necesita.
_nlp = None


def _obtener_nlp():
    """Carga el pipeline de spaCy en espanol, una sola vez por proceso."""
    global _nlp

    if _nlp is None:
        # Sin NER ni parser: para lematizar solo hacen falta el tokenizer y los
        # atributos de cada token, y desactivar lo demas hace el paso mas rapido.
        _nlp = spacy.load("es_core_news_sm", disable=["ner", "parser"])

    return _nlp


def limpiar_texto(texto: str) -> str:
    """Normaliza, quita stopwords y lematiza. Pensado para alimentar TF-IDF.

    Es una transposicion literal de la celda del notebook citada arriba. Cambiar
    algo aqui SIN cambiar el notebook (o al reves) reabre la inconsistencia
    entrenar/servir que este modulo existe para cerrar.
    """
    texto = texto.lower()

    # Protege terminos compuestos ANTES de quitar simbolos como + # .
    for patron, reemplazo in NORMALIZACIONES.items():
        texto = re.sub(patron, reemplazo, texto)

    texto = re.sub(r"http\S+|www\.\S+", " ", texto)      # URLs
    texto = re.sub(r"[^\w\sáéíóúñü]", " ", texto)          # puntuacion (conserva tildes/ñ)
    texto = re.sub(r"\d+", " ", texto)                     # numeros
    texto = re.sub(r"\s+", " ", texto).strip()

    doc = _obtener_nlp()(texto)
    tokens = [
        tok.lemma_ for tok in doc
        if (not tok.is_stop or tok.text in EXCEPCIONES)
        and not tok.is_punct
        and len(tok.text) > 1
    ]

    return " ".join(tokens)