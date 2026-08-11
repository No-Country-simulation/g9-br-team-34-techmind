# Salidas.
#
# No son solo informativas: son exactamente los valores que despues hay que
# cargar como variables de GitHub y en el .env de la VM. Que salgan de aca
# elimina la copia manual desde cinco pantallas distintas de la consola, que es
# donde se cuela la mayoria de los errores de configuracion.

locals {
  # OCIR usa la clave corta de la region, no su nombre completo.
  # Si tu region no esta en la tabla, la clave figura en:
  # Consola > Regions, o en la documentacion de "Availability by region".
  claves_region = {
    "sa-saopaulo-1"   = "gru"
    "sa-vinhedo-1"    = "vcp"
    "sa-santiago-1"   = "scl"
    "sa-valparaiso-1" = "vap"
    "sa-bogota-1"     = "bog"
    "mx-queretaro-1"  = "qro"
    "mx-monterrey-1"  = "mty"
    "us-ashburn-1"    = "iad"
    "us-phoenix-1"    = "phx"
    "us-sanjose-1"    = "sjc"
    "us-chicago-1"    = "ord"
    "ca-toronto-1"    = "yyz"
    "ca-montreal-1"   = "yul"
    "eu-madrid-1"     = "mad"
    "eu-frankfurt-1"  = "fra"
    "uk-london-1"     = "lhr"
  }

  clave_region = lookup(local.claves_region, var.region, "REVISAR")

  registro_ocir = local.clave_region != "REVISAR" ? "${local.clave_region}.ocir.io" : "REVISAR-clave-de-region.ocir.io"

  ip_publica = oci_core_instance.principal.public_ip
}

# ---------------------------------------------------------------------------
# Lo que hay que cargar como VARIABLES en GitHub
# ---------------------------------------------------------------------------

output "github_OCI_HOST" {
  description = "Variable OCI_HOST: IP publica de la instancia."
  value       = local.ip_publica
}

output "github_OCI_NAMESPACE" {
  description = "Variable OCI_NAMESPACE: namespace de Object Storage del tenancy."
  value       = data.oci_objectstorage_namespace.principal.namespace
}

output "github_OCI_REGION" {
  description = "Variable OCI_REGION."
  value       = var.region
}

output "github_OCI_BUCKET_NAME" {
  description = "Variable OCI_BUCKET_NAME."
  value       = oci_objectstorage_bucket.modelos.name
}

output "bucket_respaldos" {
  description = "Bucket de respaldos. Va en el .env de la VM como OCI_BUCKET_RESPALDOS."
  value       = oci_objectstorage_bucket.respaldos.name
}

output "github_OCIR_REGISTRY" {
  description = "Variable OCIR_REGISTRY, derivada de la region."
  value       = local.registro_ocir
}

output "github_OCI_SSH_USER" {
  description = "Variable OCI_SSH_USER. En Oracle Linux siempre es opc."
  value       = "opc"
}

output "github_TARGET_PLATFORM" {
  description = "Variable TARGET_PLATFORM, derivada de la forma de la instancia."
  value       = length(regexall("A1", var.shape)) > 0 ? "linux/arm64" : "linux/amd64"
}

# ---------------------------------------------------------------------------
# Referencias utiles
# ---------------------------------------------------------------------------

output "instancia_ocid" {
  description = "OCID de la instancia, por si hace falta acotar el grupo dinamico a esta sola maquina."
  value       = oci_core_instance.principal.id
}

output "url_swagger" {
  description = "Donde queda la documentacion de la API una vez desplegada."
  value       = "http://${local.ip_publica}:${var.puerto_api}/swagger-ui/index.html"
}

output "url_salud" {
  description = "Endpoint que consulta el healthcheck y la verificacion del despliegue."
  value       = "http://${local.ip_publica}:${var.puerto_api}/actuator/health"
}

output "dominio_sslip" {
  description = <<-TEXTO
    Dominio gratuito que ya resuelve a esta IP, sin registrar nada.
    Sirve para poner HTTPS con Let's Encrypt sin comprar un dominio.
    Requiere habilitar_https = true y un proxy inverso.
  TEXTO
  value       = "techmind.${replace(local.ip_publica, ".", "-")}.sslip.io"
}

# ---------------------------------------------------------------------------
# Los siguientes pasos, ya con los valores puestos
# ---------------------------------------------------------------------------

output "siguientes_pasos" {
  description = "Que hacer despues de este apply, con los comandos ya armados."
  value       = <<-TEXTO

    La infraestructura esta creada. Faltan tres cosas, en este orden.

    1) Preparar la maquina

       scp -i ~/.ssh/techmind_deploy ../../scripts/provision-vm.sh opc@${local.ip_publica}:~
       ssh -i ~/.ssh/techmind_deploy opc@${local.ip_publica} 'bash provision-vm.sh'

       Cerra y reabri la sesion SSH al terminar, para que el usuario tome el
       grupo docker.

    2) Completar el .env de la VM

       ssh -i ~/.ssh/techmind_deploy opc@${local.ip_publica}
       vi /opt/techmind/.env

       OCIR_REGISTRY=${local.registro_ocir}
       OCI_NAMESPACE=${data.oci_objectstorage_namespace.principal.namespace}
       OCI_REGION=${var.region}
       OCI_BUCKET_NAME=${oci_objectstorage_bucket.modelos.name}

       DB_PASSWORD y TECHMIND_API_KEY se generan DENTRO de la VM con:
         openssl rand -base64 24
         openssl rand -hex 32

       Cuidado: DB_PASSWORD se elige UNA vez. H2 crea la base con esa
       contrasena en el primer arranque y cambiarla despues obliga a borrar
       el volumen.

    3) Cargar la configuracion en GitHub

       ../../scripts/configurar-github.sh

       O a mano, las variables ya resueltas:
         OCI_HOST        = ${local.ip_publica}
         OCI_NAMESPACE   = ${data.oci_objectstorage_namespace.principal.namespace}
         OCI_REGION      = ${var.region}
         OCI_BUCKET_NAME = ${oci_objectstorage_bucket.modelos.name}
         OCIR_REGISTRY   = ${local.registro_ocir}
         OCI_SSH_USER    = opc
         TARGET_PLATFORM = ${length(regexall("A1", var.shape)) > 0 ? "linux/arm64" : "linux/amd64"}

       Los siete secrets NO salen de aca: son credenciales, no infraestructura.
       Estan en docs/devops/despliegue-oci.md, parte 5.

    Despues: Actions > CD - Despliegue en OCI > Run workflow.

    Para verificar cuando termine:
       bash ../../scripts/smoke-test.sh ${local.ip_publica}

  TEXTO
}
