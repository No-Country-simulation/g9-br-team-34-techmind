# Identidad de la maquina: el mecanismo que permite que la VM lea el modelo
# SIN tener ninguna credencial guardada en disco.
#
# Como funciona: el grupo dinamico define "que instancias son estas", y la
# policy define "que pueden hacer". Cuando el contenedor pide el objeto, el SDK
# de OCI le pide un token al servicio de metadatos de la propia instancia. No
# hay clave privada que rotar, que expire, ni que se pueda filtrar si alguien
# entra a la maquina.
#
# Es la diferencia entre este proyecto y los que dejan un .pem en /home.

locals {
  nombre_grupo_dinamico = "${var.prefijo}-instancias"

  # Los tenancy con identity domains exigen calificar el grupo con el dominio
  # dentro de la policy. Sin dominio, va el nombre pelado. Escribirlo mal no da
  # error al crear la policy: simplemente no aplica a nadie, y el sintoma
  # aparece despues como un NotAuthenticated del ml-service al arrancar.
  grupo_en_policy = var.dominio_identidad != "" ? "'${var.dominio_identidad}'/'${local.nombre_grupo_dinamico}'" : local.nombre_grupo_dinamico
}

# Los grupos dinamicos SIEMPRE viven en el tenancy, nunca en un compartment
# hijo. Es una restriccion de OCI, no una eleccion.
resource "oci_identity_dynamic_group" "instancias" {
  compartment_id = var.tenancy_ocid
  name           = local.nombre_grupo_dinamico
  description    = "Instancias de TechMind autorizadas a leer el modelo de Object Storage"

  matching_rule = "ALL {instance.compartment.id = '${local.compartment}'}"

  freeform_tags = local.etiquetas
}

resource "oci_identity_policy" "lectura_modelos" {
  compartment_id = local.compartment
  name           = "${var.prefijo}-lectura-modelos"
  description    = "Permite a las instancias de TechMind leer el modelo publicado"

  # Solo `read`, y solo sobre ESE bucket.
  #
  # La VM no debe poder escribir: quien publica modelos es la canalizacion de
  # CD, no el servidor. Si un dia alguien compromete el contenedor, lo maximo
  # que consigue es leer un archivo que de todas formas ya esta sirviendo.
  statements = [
    "Allow dynamic-group ${local.grupo_en_policy} to read objects in compartment id ${local.compartment} where target.bucket.name = '${oci_objectstorage_bucket.modelos.name}'",

    # Sobre el bucket de respaldos si puede escribir, pero solo ahi y solo con
    # los verbos que necesita: subir un objeto nuevo y listar lo que hay.
    # `manage` le permitiria tambien BORRAR respaldos, que es justo lo que no
    # queremos que pueda hacer una maquina comprometida.
    "Allow dynamic-group ${local.grupo_en_policy} to use objects in compartment id ${local.compartment} where all { target.bucket.name = '${oci_objectstorage_bucket.respaldos.name}', any { request.permission = 'OBJECT_CREATE', request.permission = 'OBJECT_INSPECT' } }"
  ]

  freeform_tags = local.etiquetas
}
