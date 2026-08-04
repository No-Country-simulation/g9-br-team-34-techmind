"""Carga del modelo serializado y ejecucion de la inferencia.

Responsabilidad de este modulo: tomar el artefacto `.joblib` que produce
`train/train.py` (o el que entregue el equipo de Ciencia de Datos) y exponerlo
como un objeto con un metodo `predict`. No entrena, no conoce el dataset.

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

from .settings import settings

logger = logging.getLogger(__name__)

# Claves del diccionario que `train.py` serializa. Se nombran una sola vez aca
# para que el contrato del artefacto no quede repetido en dos archivos.
KEY_PIPELINE = "pipeline"
KEY_CATEGORIAS = "categorias"

# Nombres de los pasos dentro del Pipeline de scikit-learn.
STEP_TFIDF = "tfidf"
STEP_CLF = "clf"


@dataclass
class InferenceResult:
    categoria: str
    probabilidad: float
    palabras_clave: list[str]


class ClassifierModel:
    """Envuelve el pipeline entrenado y le da una interfaz estable.

    El resto del servicio habla con esta clase y no con scikit-learn. Si manana
    Ciencia de Datos cambia TF-IDF + Regresion Logistica por embeddings, el
    cambio queda contenido aca dentro.
    """

    def __init__(self, pipeline, categorias: list[str], origen: str) -> None:
        self._pipeline = pipeline
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

        El titulo se concatena al texto en lugar de ignorarse porque suele ser
        la senal mas densa del documento: "Tutorial de Docker" aporta mas por
        palabra que cualquier parrafo del cuerpo.
        """
        documento = f"{titulo}. {texto}"

        categoria, probabilidad = self._clasificar(documento)
        palabras_clave = self._extraer_palabras_clave(documento)

        return InferenceResult(
            categoria=categoria,
            probabilidad=probabilidad,
            palabras_clave=palabras_clave,
        )

    def _clasificar(self, documento: str) -> tuple[str, float]:
        probabilidades = self._pipeline.predict_proba([documento])[0]
        indice = int(np.argmax(probabilidades))

        # `classes_` viene del clasificador y no de `self._categorias`: es el
        # orden real de las columnas de `predict_proba`. Usar la otra lista
        # asumiria que ambos ordenes coinciden, y un dia no coincidirian.
        categoria = str(self._pipeline.named_steps[STEP_CLF].classes_[indice])
        probabilidad = round(float(probabilidades[indice]), 4)

        return categoria, probabilidad

    def _extraer_palabras_clave(self, documento: str) -> list[str]:
        """Devuelve los terminos con mayor peso TF-IDF dentro del documento.

        Se reutiliza el vectorizador ya entrenado en vez de contar frecuencias:
        TF-IDF ya descarta lo que aparece en todos los documentos del corpus, de
        modo que "utilizando" o "contenido" no compiten con "spring boot".
        """
        vectorizador = self._pipeline.named_steps[STEP_TFIDF]
        vector = vectorizador.transform([documento])

        # La matriz es dispersa: solo iteramos sobre los terminos presentes en
        # este documento, no sobre todo el vocabulario.
        fila = vector.tocoo()
        if fila.nnz == 0:
            return []

        nombres = vectorizador.get_feature_names_out()
        pares = sorted(
            zip(fila.col, fila.data, strict=False),
            key=lambda par: par[1],
            reverse=True,
        )

        return [str(nombres[indice]) for indice, _ in pares[: settings.max_keywords]]


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

    pipeline = artefacto[KEY_PIPELINE]
    categorias = list(artefacto[KEY_CATEGORIAS])

    logger.info("Modelo cargado desde %s con %d categorias", origen, len(categorias))

    return ClassifierModel(pipeline=pipeline, categorias=categorias, origen=origen)
