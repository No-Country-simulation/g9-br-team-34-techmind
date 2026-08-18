# Object Storage: donde la canalizacion publica el modelo entrenado.

data "oci_objectstorage_namespace" "principal" {
  compartment_id = var.tenancy_ocid
}

resource "oci_objectstorage_bucket" "modelos" {
  compartment_id = local.compartment
  namespace      = data.oci_objectstorage_namespace.principal.namespace
  name           = var.nombre_bucket

  # Privado. El acceso se resuelve con identidad (ver identidad.tf), no
  # exponiendo el bucket: un bucket publico es la forma mas rapida de que
  # alguien descargue el modelo y consuma la cuota de salida de la cuenta.
  access_type = "NoPublicAccess"

  storage_tier = "Standard"

  freeform_tags = local.etiquetas
}

# ---------------------------------------------------------------------------
# Bucket de respaldos
# ---------------------------------------------------------------------------
#
# Va SEPARADO del de modelos a proposito. La VM necesita poder ESCRIBIR aca
# —es quien genera los respaldos— pero debe seguir sin poder escribir en el
# bucket de modelos, que es donde publica unicamente la canalizacion de CD.
#
# Un solo bucket con permiso de escritura obligaria a darle a la maquina la
# capacidad de sobrescribir el modelo en produccion, que es exactamente lo que
# no queremos: si alguien compromete el contenedor, no debe poder envenenar el
# artefacto que sirve el sistema.
resource "oci_objectstorage_bucket" "respaldos" {
  compartment_id = local.compartment
  namespace      = data.oci_objectstorage_namespace.principal.namespace
  name           = var.nombre_bucket_respaldos

  access_type  = "NoPublicAccess"
  storage_tier = "Standard"

  # Los respaldos viejos se borran solos. Sin esto, un respaldo diario acumula
  # archivos para siempre y termina comiendose los 20 GB de la capa gratuita.
  retention_rules {
    display_name = "descartar-respaldos-viejos"

    duration {
      time_amount = var.dias_retencion_respaldos
      time_unit   = "DAYS"
    }
  }

  freeform_tags = local.etiquetas
}
