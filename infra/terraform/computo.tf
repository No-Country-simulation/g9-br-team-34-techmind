# La maquina virtual donde corren los contenedores.

data "oci_identity_availability_domains" "disponibles" {
  compartment_id = var.tenancy_ocid
}

# La imagen se busca por caracteristicas y no por OCID fijo. Un OCID de imagen
# es distinto en cada region y ademas queda obsoleto cuando Oracle publica una
# version nueva; buscarla asi hace que la configuracion sirva en cualquier region
# sin editar nada.
#
# Filtrar por `shape` es lo que garantiza que la imagen sea compatible con la
# arquitectura: en Ampere devuelve builds aarch64, en x86 devuelve amd64.
data "oci_core_images" "oracle_linux" {
  compartment_id           = local.compartment
  operating_system         = "Oracle Linux"
  operating_system_version = "9"
  shape                    = var.shape

  sort_by    = "TIMECREATED"
  sort_order = "DESC"
}

locals {
  dominio_disponibilidad = data.oci_identity_availability_domains.disponibles.availability_domains[
    var.indice_dominio_disponibilidad
  ].name

  # `A1.Flex` y las demas formas flexibles exigen shape_config; las fijas, como
  # E2.1.Micro, lo rechazan. Este condicional permite cambiar de una a otra sin
  # tener que editar el recurso.
  es_flexible = length(regexall("Flex", var.shape)) > 0
}

resource "oci_core_instance" "principal" {
  availability_domain = local.dominio_disponibilidad
  compartment_id      = local.compartment
  display_name        = "${var.prefijo}-vm"
  shape               = var.shape

  dynamic "shape_config" {
    for_each = local.es_flexible ? [1] : []

    content {
      ocpus         = var.ocpus
      memory_in_gbs = var.memoria_gb
    }
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.publica.id
    assign_public_ip = true
    display_name     = "${var.prefijo}-vnic"
    hostname_label   = replace(var.prefijo, "-", "")
  }

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.oracle_linux.images[0].id
    boot_volume_size_in_gbs = var.disco_gb
  }

  metadata = {
    ssh_authorized_keys = file(pathexpand(var.ruta_clave_publica_ssh))
  }

  # La IP publica es efimera por defecto y CAMBIA si la instancia se detiene y
  # arranca. Eso invalidaria la variable OCI_HOST de GitHub y el despliegue
  # empezaria a fallar por una razon que no tiene nada que ver con el codigo.
  # `preserve_boot_volume` no lo evita; lo que lo evita es no destruir la
  # instancia, y por eso esta el lifecycle de abajo.
  lifecycle {
    ignore_changes = [
      # Oracle publica imagenes nuevas seguido. Sin esto, un `terraform apply`
      # rutinario propondria RECREAR la maquina para actualizar la imagen, con
      # perdida de la base de datos y cambio de IP incluidos.
      source_details[0].source_id,
    ]
  }

  freeform_tags = local.etiquetas
}
