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
    model_path: Path = Path("/app/models/model.joblib")

    # --- OCI Object Storage (solo si model_source="oci") ---
    # auth_method:
    #   config_file        -> ~/.oci/config (desarrollo en la maquina del equipo)
    #   instance_principal -> sin credenciales, la VM se autentica sola (produccion)
    oci_auth_method: Literal["config_file", "instance_principal"] = "config_file"
    oci_namespace: str = ""
    oci_bucket_name: str = "techmind-models"
    oci_region: str = ""
    oci_object_name: str = "model.joblib"
    oci_config_file: str = "~/.oci/config"
    oci_config_profile: str = "DEFAULT"

    # --- Comportamiento del modelo ---
    # Cuantas palabras clave devolver como maximo en `informacion_adicional`.
    max_keywords: int = 5


settings = Settings()
