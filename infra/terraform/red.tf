# Red virtual: VCN, salida a internet, subred publica y reglas de firewall.
#
# Es el equivalente en codigo del "VCN Wizard" de la consola, con una diferencia
# que importa: aca se ve exactamente que se creo y por que, y volver a levantarlo
# desde cero es un comando en vez de veinte clics.

resource "oci_core_vcn" "principal" {
  compartment_id = local.compartment
  display_name   = "${var.prefijo}-vcn"
  cidr_blocks    = [var.cidr_vcn]

  # El label se usa para el DNS interno de la VCN. Solo admite letras y numeros.
  dns_label = replace(var.prefijo, "-", "")

  freeform_tags = local.etiquetas
}

# Sin Internet Gateway la subred no tiene salida, y una VM sin salida no puede
# ni bajar imagenes de Docker ni ser alcanzada desde fuera.
resource "oci_core_internet_gateway" "salida" {
  compartment_id = local.compartment
  vcn_id         = oci_core_vcn.principal.id
  display_name   = "${var.prefijo}-igw"
  enabled        = true

  freeform_tags = local.etiquetas
}

resource "oci_core_route_table" "publica" {
  compartment_id = local.compartment
  vcn_id         = oci_core_vcn.principal.id
  display_name   = "${var.prefijo}-rt-publica"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.salida.id
  }

  freeform_tags = local.etiquetas
}

# ---------------------------------------------------------------------------
# Reglas de firewall de la red
# ---------------------------------------------------------------------------
#
# OJO: esta es solo la PRIMERA de las dos capas de firewall que tiene OCI. La
# segunda es la del sistema operativo dentro de la VM, y la abre
# scripts/provision-vm.sh. Hay que abrir las dos; olvidar la segunda es la causa
# numero uno de "el contenedor corre pero no puedo entrar desde fuera".

resource "oci_core_security_list" "publica" {
  compartment_id = local.compartment
  vcn_id         = oci_core_vcn.principal.id
  display_name   = "${var.prefijo}-sl-publica"

  # Salida sin restricciones: la VM necesita bajar imagenes del registro y
  # hablar con Object Storage.
  egress_security_rules {
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    protocol         = "all"
  }

  # SSH: por aca entra el despliegue desde GitHub Actions.
  ingress_security_rules {
    source      = var.cidr_ssh_permitido
    source_type = "CIDR_BLOCK"
    protocol    = "6" # TCP
    description = "SSH para el despliegue"

    tcp_options {
      min = 22
      max = 22
    }
  }

  # La API publica. Es el unico puerto de aplicacion que se abre: el servicio de
  # inferencia queda deliberadamente fuera, alcanzable solo por la red interna
  # de Docker.
  ingress_security_rules {
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    protocol    = "6"
    description = "API REST publica"

    tcp_options {
      min = var.puerto_api
      max = var.puerto_api
    }
  }

  # HTTP y HTTPS, solo si se va a poner un proxy inverso con certificado.
  # `dynamic` con una lista vacia no genera ninguna regla cuando esta apagado.
  dynamic "ingress_security_rules" {
    for_each = var.habilitar_https ? [80, 443] : []

    content {
      source      = "0.0.0.0/0"
      source_type = "CIDR_BLOCK"
      protocol    = "6"
      description = ingress_security_rules.value == 80 ? "HTTP (validacion de Let's Encrypt y redireccion)" : "HTTPS"

      tcp_options {
        min = ingress_security_rules.value
        max = ingress_security_rules.value
      }
    }
  }

  freeform_tags = local.etiquetas
}

resource "oci_core_subnet" "publica" {
  compartment_id = local.compartment
  vcn_id         = oci_core_vcn.principal.id
  display_name   = "${var.prefijo}-subred-publica"
  cidr_block     = var.cidr_subred
  dns_label      = "publica"

  route_table_id    = oci_core_route_table.publica.id
  security_list_ids = [oci_core_security_list.publica.id]

  # Una subred publica es exactamente esto: la que NO prohibe direcciones IP
  # publicas. Si estuviera en true, la instancia no podria tener IP publica por
  # mas que se la pidieras.
  prohibit_public_ip_on_vnic = false

  freeform_tags = local.etiquetas
}
