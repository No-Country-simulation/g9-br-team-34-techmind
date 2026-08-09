# Versiones fijadas.
#
# Tanto Terraform como el proveedor se acotan a proposito: un `terraform apply`
# que se comporta distinto segun quien lo ejecute deja de ser infraestructura
# reproducible, que es justamente lo unico que justifica usar Terraform en vez
# de la consola web.

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }
}

# Autenticacion por API key: las mismas cuatro credenciales que despues van a
# los secrets de GitHub. Se reutilizan a proposito, para no multiplicar
# identidades que despues nadie recuerda por que existen.
provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.ruta_clave_api
  region           = var.region
}
