"""Carga del modelo serializado y ejecucion de la inferencia.

Responsabilidad de este modulo: tomar los artefactos `.joblib` que entrega el
equipo de Ciencia de Datos y exponerlos como un objeto con un metodo
`predict`. No entrena, no conoce el dataset.

El artefacto real de Ciencia de Datos NO es un unico Pipeline de scikit-learn:
son tres piezas serializadas por separado -- `modelo_clasificador.joblib`
(un `LogisticRegression` ya entrenado) y dos `TfidfVectorizer`
(`tfidf_titulo.joblib`, `tfidf_texto.joblib`) -- que se combinan a mano en
tiempo de inferencia, replicando exactamente el preprocesamiento y el peso de
titulo que Ciencia de Datos uso al entrenar. Este modulo es el unico lugar del
servicio que conoce ese detalle: el resto habla con `ClassifierModel.predict`.

Esa separacion es deliberada: el contenedor de produccion nunca debe entrenar.
Entrenar en el arranque haria que el tiempo de despliegue dependiera del tamano
del dataset y que dos replicas del mismo servicio pudieran servir modelos
distintos.
"""

from __future__ import annotations

import logging
import os
import re
from dataclasses import dataclass
from pathlib import Path

import joblib
import numpy as np
from scipy.sparse import hstack

from .settings import settings

logger = logging.getLogger(__name__)

# Normalizaciones de texto identicas a las que aplico Ciencia de Datos antes de
# vectorizar. Si esto se desalinea del preprocesamiento usado al entrenar, el
# modelo predice sobre tokens que nunca vio -- silenciosamente peor, sin error.
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


def limpiar_texto(texto: str) -> str:
    """Normaliza un texto exactamente como lo hizo Ciencia de Datos al entrenar."""
    texto = texto.lower()
    for patron, reemplazo in NORMALIZACIONES.items():
        texto = re.sub(patron, reemplazo, texto)
    texto = re.sub(r"http\S+|www\.\S+", " ", texto)
    texto = re.sub(r"[^\w\sáéíóúñü]", " ", texto)
    return re.sub(r"\s+", " ", texto).strip()


@dataclass
class InferenceResult:
    categoria: str
    probabilidad: float
    palabras_clave: list[str]


class ClassifierModel:
    """Envuelve el clasificador y los dos vectorizadores TF-IDF entrenados.

    El resto del servicio habla con esta clase y no con scikit-learn
    directamente. Si manana Ciencia de Datos cambia de arquitectura, el cambio
    queda contenido aca dentro.
    """

    def __init__(
        self,
        modelo,
        vect_titulo,
        vect_texto,
        categorias: list[str],
        origen: str,
    ) -> None:
        self._modelo = modelo
        self._vect_titulo = vect_titulo
        self._vect_texto = vect_texto
        self._categorias = categorias
        self._origen = origen

    @property
    def categorias(self) -> list[str]:
        return list(self._categorias)

    @property
    def origen(self) -> str:
        return self._origen

    def predict(self, titulo: str, texto: str) -> InferenceResult:
        """Clasifica un contenido y extrae sus palabras clave.

        Replica el pipeline de inferencia de Ciencia de Datos: titulo y texto
        se limpian y vectorizan por separado, el vector del titulo se pesa
        `PESO_TITULO` veces (el titulo suele ser la senal mas densa del
        documento) y ambos se concatenan antes de clasificar.
        """
        titulo_limpio = limpiar_texto(titulo)
        texto_limpio = limpiar_texto(texto)

        vt = self._vect_titulo.transform([titulo_limpio]) * settings.peso_titulo
        vx = self._vect_texto.transform([texto_limpio])
        vector = hstack([vt, vx])

        categoria, probabilidad = self._clasificar(vector)
        palabras_clave = self._extraer_palabras_clave(vector)

        return InferenceResult(
            categoria=categoria,
            probabilidad=probabilidad,
            palabras_clave=palabras_clave,
        )

    def _clasificar(self, vector) -> tuple[str, float]:
        probabilidades = self._modelo.predict_proba(vector)[0]
        indice = int(np.argmax(probabilidades))

        # `classes_` viene del clasificador y no de `self._categorias`: es el
        # orden real de las columnas de `predict_proba`. Usar la otra lista
        # asumiria que ambos ordenes coinciden, y un dia no coincidirian.
        categoria = str(self._modelo.classes_[indice])
        probabilidad = round(float(probabilidades[indice]), 4)

        return categoria, probabilidad

    def _extraer_palabras_clave(self, vector) -> list[str]:
        """Devuelve los terminos con mayor peso TF-IDF dentro del documento.

        Combina el vocabulario de ambos vectorizadores (titulo + texto) en el
        mismo orden en que se concatenaron los vectores con `hstack`.
        """
        nombres = list(self._vect_titulo.get_feature_names_out()) + list(
            self._vect_texto.get_feature_names_out()
        )
        fila = vector.toarray()[0]
        if not fila.any():
            return []

        indices = fila.argsort()[::-1]
        return [
            str(nombres[i]) for i in indices[: settings.max_keywords] if fila[i] > 0
        ]


# Las tres piezas del artefacto: nombre de atributo en Settings para la ruta
# local, nombre de atributo en Settings para el objeto OCI. Se recorren juntas
# para no repetir la misma logica de descarga/carga tres veces.
_ARCHIVOS_MODELO = (
    ("model_file_clasificador", "oci_object_clasificador"),
    ("model_file_tfidf_titulo", "oci_object_tfidf_titulo"),
    ("model_file_tfidf_texto", "oci_object_tfidf_texto"),
)


def _descargar_desde_oci(destino: Path, objeto: str) -> None:
    """Baja un objeto puntual del artefacto desde OCI Object Storage.

    Se importa `oci` aca dentro y no arriba del archivo a proposito: cuando
    `model_source=local` el SDK no hace falta, y un import de nivel de modulo
    haria que un problema de credenciales rompiera el arranque de un servicio
    que ni siquiera iba a usar la nube.
    """
    import oci  # noqa: PLC0415  (import diferido, ver docstring)

    logger.info(
        "Descargando %s desde OCI Object Storage (bucket=%s, auth=%s)",
        objeto,
        settings.oci_bucket_name,
        settings.oci_auth_method,
    )

    if settings.oci_auth_method == "instance_principal":
        # En la VM de OCI no hay claves en disco: la instancia se autentica con
        # su propia identidad. Es el metodo correcto en produccion porque no hay
        # ningun secreto que rotar ni que se pueda filtrar.
        signer = oci.auth.signers.InstancePrincipalsSecurityTokenSigner()
        cliente = oci.object_storage.ObjectStorageClient(config={}, signer=signer)
    else:
        config = oci.config.from_file(
            file_location=os.path.expanduser(settings.oci_config_file),
            profile_name=settings.oci_config_profile,
        )
        cliente = oci.object_storage.ObjectStorageClient(config)

    namespace = settings.oci_namespace or cliente.get_namespace().data

    respuesta = cliente.get_object(
        namespace_name=namespace,
        bucket_name=settings.oci_bucket_name,
        object_name=objeto,
    )

    destino.parent.mkdir(parents=True, exist_ok=True)

    # Se escribe primero a un archivo temporal y despues se renombra. Si la
    # descarga se corta a la mitad, lo que queda es un `.tmp` incompleto y no un
    # artefacto corrupto que el proximo arranque intentaria cargar.
    temporal = destino.with_suffix(destino.suffix + ".tmp")
    with open(temporal, "wb") as archivo:
        for fragmento in respuesta.data.raw.stream(1024 * 1024, decode_content=False):
            archivo.write(fragmento)
    temporal.replace(destino)

    logger.info("%s descargado en %s (%d bytes)", objeto, destino, destino.stat().st_size)


def _resolver_ruta(atributo_archivo: str) -> Path:
    return settings.model_dir / getattr(settings, atributo_archivo)


def cargar_modelo() -> ClassifierModel:
    """Deja el modelo listo para servir. Lanza si no lo consigue.

    Se llama una sola vez, en el arranque. Esta funcion solo carga y propaga el
    error; la politica de que hacer ante un fallo la decide `main.py`.

    Carga las TRES piezas del artefacto (clasificador + 2 vectorizadores). Si
    cualquiera de las tres falta o no puede descargarse, el modelo completo se
    considera no disponible: una combinacion parcial (por ejemplo, el
    clasificador nuevo con un vectorizador viejo) prediria de forma incorrecta
    y silenciosa, que es peor que no predecir.
    """
    rutas: dict[str, Path] = {
        atributo_archivo: _resolver_ruta(atributo_archivo)
        for atributo_archivo, _ in _ARCHIVOS_MODELO
    }

    if settings.model_source == "oci":
        try:
            for atributo_archivo, atributo_objeto in _ARCHIVOS_MODELO:
                _descargar_desde_oci(
                    rutas[atributo_archivo], getattr(settings, atributo_objeto)
                )
            origen = f"oci://{settings.oci_bucket_name}/"
        except Exception as exc:  # noqa: BLE001 - cualquier fallo de red/credenciales
            # Si Object Storage no responde pero el volumen conserva la ultima
            # copia buena de las TRES piezas, el servicio arranca con ella en
            # vez de quedarse sin modelo. Un incidente de OCI, un token vencido
            # o una policy mal puesta no deberian dejar la API caida cuando el
            # artefacto que hace falta ya esta en disco.
            #
            # Si falta alguna pieza no hay nada seguro que servir, y entonces
            # si conviene propagar: /health devolvera 503 con la causa exacta.
            if not all(ruta.exists() for ruta in rutas.values()):
                raise

            logger.warning(
                "Fallo la descarga desde Object Storage (%s: %s). "
                "Se usa la copia local previa en %s.",
                type(exc).__name__,
                exc,
                settings.model_dir,
            )
            origen = f"local-fallback://{settings.model_dir}"
    else:
        origen = f"local://{settings.model_dir}"

    faltantes = [ruta for ruta in rutas.values() if not ruta.exists()]
    if faltantes:
        raise FileNotFoundError(
            f"Faltan artefactos del modelo: {[str(r) for r in faltantes]}. "
            "Copia los .joblib que entrega Ciencia de Datos a MODEL_DIR, "
            "o define MODEL_SOURCE=oci para descargarlos de Object Storage."
        )

    modelo = joblib.load(rutas["model_file_clasificador"])
    vect_titulo = joblib.load(rutas["model_file_tfidf_titulo"])
    vect_texto = joblib.load(rutas["model_file_tfidf_texto"])

    # No hay archivo de categorias aparte: `classes_` del clasificador ya
    # entrenado es la fuente de verdad, igual que en la inferencia.
    categorias = sorted(str(c) for c in modelo.classes_)

    logger.info("Modelo cargado desde %s con %d categorias", origen, len(categorias))

    return ClassifierModel(
        modelo=modelo,
        vect_titulo=vect_titulo,
        vect_texto=vect_texto,
        categorias=categorias,
        origen=origen,
    )
