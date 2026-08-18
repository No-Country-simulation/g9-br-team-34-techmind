"""Carga del modelo serializado y ejecucion de la inferencia.

Responsabilidad de este modulo: tomar el artefacto `.joblib` que produce
`train/train.py` (reempaquetado de los archivos que entrega Ciencia de Datos en
`data-science/API/`) y exponerlo como un objeto con un metodo `predict`. No
entrena, no conoce el dataset.

Esa separacion es deliberada: el contenedor de produccion nunca debe entrenar.
Entrenar en el arranque haria que el tiempo de despliegue dependiera del tamano
del dataset y que dos replicas del mismo servicio pudieran servir modelos
distintos.
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from pathlib import Path

import joblib
import numpy as np
from scipy.sparse import hstack

from .preprocess import limpiar_texto
from .settings import settings

logger = logging.getLogger(__name__)

# Claves del diccionario que `train/train.py` serializa. Se nombran una sola vez
# aca para que el contrato del artefacto no quede repetido en dos archivos.
KEY_MODELO = "modelo"
KEY_VECT_TITULO = "vect_titulo"
KEY_VECT_TEXTO = "vect_texto"
KEY_PESO_TITULO = "peso_titulo"
KEY_CATEGORIAS = "categorias"


@dataclass
class InferenceResult:
    categoria: str
    probabilidad: float
    palabras_clave: list[str]


class ClassifierModel:
    """Envuelve los artefactos de Ciencia de Datos y les da una interfaz estable.

    El resto del servicio habla con esta clase y no con scikit-learn. Si manana
    Ciencia de Datos cambia TF-IDF + Regresion Logistica por embeddings, el
    cambio queda contenido aca dentro.
    """

    def __init__(
        self,
        modelo,
        vect_titulo,
        vect_texto,
        peso_titulo: float,
        categorias: list[str],
        origen: str,
    ) -> None:
        self._modelo = modelo
        self._vect_titulo = vect_titulo
        self._vect_texto = vect_texto
        self._peso_titulo = peso_titulo
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

        El titulo y el texto se limpian por separado (misma limpieza que en el
        entrenamiento) y se vectorizan con sus propios TF-IDF, ponderando el del
        titulo por `peso_titulo` y apilando ambos. Ese orden es el que vio el
        clasificador al entrenarse; cambiarlo cambiaria el significado de cada
        columna de la matriz y degradaria las predicciones.
        """
        vector_titulo = self._vect_titulo.transform([limpiar_texto(titulo)]) * self._peso_titulo
        vector_texto = self._vect_texto.transform([limpiar_texto(texto)])
        vector = hstack([vector_titulo, vector_texto])

        probabilidades = self._modelo.predict_proba(vector)[0]
        indice = int(np.argmax(probabilidades))

        # `classes_` viene del clasificador y no de `self._categorias`: es el
        # orden real de las columnas de `predict_proba`. Usar la otra lista
        # asumiria que ambos ordenes coinciden, y un dia no coincidirian.
        categoria = str(self._modelo.classes_[indice])
        probabilidad = round(float(probabilidades[indice]), 4)
        palabras_clave = self._extraer_palabras_clave(vector)

        return InferenceResult(
            categoria=categoria,
            probabilidad=probabilidad,
            palabras_clave=palabras_clave,
        )

    def _extraer_palabras_clave(self, vector) -> list[str]:
        """Devuelve los terminos con mayor peso TF-IDF dentro del documento.

        Replica `predecir_json_separado` del notebook: los nombres de las
        features son la concatenacion del vocabulario del titulo y del texto (en
        ese orden, el mismo con el que se armo la matriz apilada), se ordenan
        por peso y se toman los `settings.max_keywords` con peso positivo.
        """
        nombres = list(self._vect_titulo.get_feature_names_out()) + list(
            self._vect_texto.get_feature_names_out()
        )

        vector_denso = vector.toarray()[0]
        indices_top = vector_denso.argsort()[::-1][: settings.max_keywords]

        return [str(nombres[i]) for i in indices_top if vector_denso[i] > 0]


def _descargar_desde_oci(destino: Path) -> None:
    """Baja el artefacto del modelo desde OCI Object Storage.

    Se importa `oci` aca dentro y no arriba del archivo a proposito: cuando
    `model_source=local` el SDK no hace falta, y un import de nivel de modulo
    haria que un problema de credenciales rompiera el arranque de un servicio
    que ni siquiera iba a usar la nube.
    """
    import oci  # noqa: PLC0415  (import diferido, ver docstring)

    logger.info(
        "Descargando modelo desde OCI Object Storage "
        "(bucket=%s, objeto=%s, auth=%s)",
        settings.oci_bucket_name,
        settings.oci_object_name,
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
        object_name=settings.oci_object_name,
    )

    destino.parent.mkdir(parents=True, exist_ok=True)

    # Se escribe primero a un archivo temporal y despues se renombra. Si la
    # descarga se corta a la mitad, lo que queda es un `.tmp` incompleto y no un
    # `model.joblib` corrupto que el proximo arranque intentaria cargar.
    temporal = destino.with_suffix(destino.suffix + ".tmp")
    with open(temporal, "wb") as archivo:
        for fragmento in respuesta.data.raw.stream(1024 * 1024, decode_content=False):
            archivo.write(fragmento)
    temporal.replace(destino)

    logger.info("Modelo descargado en %s (%d bytes)", destino, destino.stat().st_size)


def cargar_modelo() -> ClassifierModel:
    """Deja el modelo listo para servir. Lanza si no lo consigue.

    Se llama una sola vez, en el arranque. Esta funcion solo carga y propaga el
    error; la politica de que hacer ante un fallo la decide `main.py`.
    """
    destino = settings.model_path

    if settings.model_source == "oci":
        try:
            _descargar_desde_oci(destino)
            origen = f"oci://{settings.oci_bucket_name}/{settings.oci_object_name}"
        except Exception as exc:  # noqa: BLE001 - cualquier fallo de red/credenciales
            # Si Object Storage no responde pero el volumen conserva la ultima
            # copia buena, el servicio arranca con ella en vez de quedarse sin
            # modelo. Un incidente de OCI, un token vencido o una policy mal
            # puesta no deberian dejar la API caida cuando el artefacto que hace
            # falta ya esta en disco.
            #
            # Si NO hay copia previa no hay nada que servir, y entonces si
            # conviene propagar: /health devolvera 503 con la causa exacta y el
            # backend no llegara a arrancar contra un servicio inutil.
            if not destino.exists():
                raise

            logger.warning(
                "Fallo la descarga desde Object Storage (%s: %s). "
                "Se usa la copia local previa de %s.",
                type(exc).__name__,
                exc,
                destino,
            )
            # El origen lo reporta /health: quien mire el estado tiene que poder
            # distinguir "modelo recien bajado" de "modelo viejo porque OCI no
            # respondio", que son dos situaciones muy distintas.
            origen = f"local-fallback://{destino}"
    else:
        origen = f"local://{destino}"

    if not destino.exists():
        raise FileNotFoundError(
            f"No se encontro el modelo en {destino}. "
            "Ejecuta 'python -m train.train' para generarlo, "
            "o define MODEL_SOURCE=oci para descargarlo de Object Storage."
        )

    artefacto = joblib.load(destino)

    modelo = artefacto[KEY_MODELO]
    vect_titulo = artefacto[KEY_VECT_TITULO]
    vect_texto = artefacto[KEY_VECT_TEXTO]
    peso_titulo = float(artefacto.get(KEY_PESO_TITULO, 0.5))
    categorias = list(artefacto.get(KEY_CATEGORIAS) or modelo.classes_)

    logger.info(
        "Modelo cargado desde %s con %d categorias", origen, len(categorias)
    )

    return ClassifierModel(
        modelo=modelo,
        vect_titulo=vect_titulo,
        vect_texto=vect_texto,
        peso_titulo=peso_titulo,
        categorias=categorias,
        origen=origen,
    )