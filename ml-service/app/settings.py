"""Configuracion del servicio de inferencia, leida desde variables de entorno.

Toda la configuracion entra por entorno y no por archivos versionados: es lo que
permite que la misma imagen Docker corra en local, en CI y en la VM de OCI sin
reconstruirla. Cambiar de entorno debe ser cambiar variables, nunca reconstruir.
"""

from __future__ import annotations

from pathlib import Path
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Configuracion del servicio. Se instancia una sola vez al arrancar."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        # Sin este prefijo, `region` o `namespace` chocarian con variables del
        # sistema que nada tienen que ver con nosotros.
        env_prefix="",
    )

    # --- Servidor ---
    host: str = "0.0.0.0"
    port: int = 8000
    log_level: str = "info"

    # --- Modelo ---
    # "local" -> se usa el archivo que ya esta en la imagen o en el volumen.
    # "oci"   -> se descarga desde Object Storage al arrancar el contenedor.
    #
    # El default es "local" para que el servicio arranque sin credenciales de
    # nube: quien clona el repo debe poder levantarlo con un solo comando.
    model_source: Literal["local", "oci"] = "local"

    # El artefacto real que entrega Ciencia de Datos NO es un unico .joblib:
    # son tres archivos independientes (clasificador + dos vectorizadores TF-IDF,
    # uno por titulo y otro por texto). Se referencian por directorio + nombre de
    # archivo en vez de por ruta completa para que MODEL_DIR sea el unico valor
    # que cambia entre local/CI/OCI.
    model_dir: Path = Path("/app/models")
    model_file_clasificador: str = "modelo_clasificador.joblib"
    model_file_tfidf_titulo: str = "tfidf_titulo.joblib"
    model_file_tfidf_texto: str = "tfidf_texto.joblib"

    # Peso relativo del titulo frente al texto al combinar ambos vectores
    # TF-IDF antes de clasificar. Viene del entrenamiento de Ciencia de Datos:
    # cambiarlo aca sin reentrenar el modelo desalinea inferencia de entrenamiento.
    peso_titulo: float = 2.0

    # --- OCI Object Storage (solo si model_source="oci") ---
    # auth_method:
    #   config_file        -> ~/.oci/config (desarrollo en la maquina del equipo)
    #   instance_principal -> sin credenciales, la VM se autentica sola (produccion)
    oci_auth_method: Literal["config_file", "instance_principal"] = "config_file"
    oci_namespace: str = ""
    oci_bucket_name: str = "techmind-models"
    oci_region: str = ""

    # Nombres de los tres objetos dentro del bucket. Por defecto iguales a los
    # nombres de archivo locales, para que el equipo de Ciencia de Datos pueda
    # subir el mismo artefacto que produce sin renombrar nada.
    oci_object_clasificador: str = "modelo_clasificador.joblib"
    oci_object_tfidf_titulo: str = "tfidf_titulo.joblib"
    oci_object_tfidf_texto: str = "tfidf_texto.joblib"

    oci_config_file: str = "~/.oci/config"
    oci_config_profile: str = "DEFAULT"

    # --- Comportamiento del modelo ---
    # Cuantas palabras clave devolver como maximo en `informacion_adicional`.
    max_keywords: int = 5


settings = Settings()
