# Entradas de la configuracion.
#
# Las que no tienen `default` son obligatorias: Terraform se niega a aplicar sin
# ellas en vez de inventar un valor. Se completan en terraform.tfvars.

# ---------------------------------------------------------------------------
# Credenciales (las mismas que despues van a los secrets de GitHub)
# ---------------------------------------------------------------------------

variable "tenancy_ocid" {
  description = "OCID del tenancy. Consola > perfil > Tenancy."
  type        = string
}

variable "user_ocid" {
  description = "OCID del usuario IAM que ejecuta Terraform."
  type        = string
}

variable "fingerprint" {
  description = "Huella de la API key (formato a1:b2:c3:...)."
  type        = string
}

variable "ruta_clave_api" {
  description = "Ruta al .pem de la API key descargado de la consola."
  type        = string
}

variable "region" {
  description = "Region del tenancy, por ejemplo sa-saopaulo-1."
  type        = string
}

variable "compartment_ocid" {
  description = <<-TEXTO
    Compartment donde se crean los recursos. Si se deja vacio se usa el tenancy
    (el compartment raiz), que es lo mas simple para un proyecto de hackathon.
  TEXTO
  type        = string
  default     = ""
}

# ---------------------------------------------------------------------------
# Acceso a la maquina
# ---------------------------------------------------------------------------

variable "ruta_clave_publica_ssh" {
  description = <<-TEXTO
    Ruta a la clave SSH PUBLICA (.pub) que quedara autorizada en la VM.
    Generala antes con:
      ssh-keygen -t ed25519 -C "techmind-deploy" -f ~/.ssh/techmind_deploy -N ""
  TEXTO
  type        = string
  default     = "~/.ssh/techmind_deploy.pub"
}

variable "cidr_ssh_permitido" {
  description = <<-TEXTO
    Desde donde se permite SSH. Por defecto abierto, porque el despliegue entra
    desde los runners de GitHub y sus rangos de IP cambian constantemente.
    Quien protege el acceso es la clave, no el filtro de red.
    Si el despliegue se hiciera desde una IP fija, conviene acotarlo.
  TEXTO
  type        = string
  default     = "0.0.0.0/0"
}

# ---------------------------------------------------------------------------
# Red
# ---------------------------------------------------------------------------

variable "cidr_vcn" {
  description = "Rango de la red virtual."
  type        = string
  default     = "10.0.0.0/16"
}

variable "cidr_subred" {
  description = "Rango de la subred publica."
  type        = string
  default     = "10.0.0.0/24"
}

variable "puerto_api" {
  description = "Puerto en el que el backend atiende publicamente."
  type        = number
  default     = 8080
}

variable "habilitar_https" {
  description = <<-TEXTO
    Abre tambien los puertos 80 y 443. Solo hace falta si se agrega un proxy
    inverso con certificado (Caddy). El 80 no es opcional en ese caso:
    Let's Encrypt lo necesita para validar el dominio.
  TEXTO
  type        = bool
  default     = false
}

# ---------------------------------------------------------------------------
# Instancia
# ---------------------------------------------------------------------------

variable "shape" {
  description = <<-TEXTO
    Forma de la instancia. VM.Standard.A1.Flex es la Always Free de Ampere (ARM).
    Si no hay capacidad, la alternativa gratuita es VM.Standard.E2.1.Micro (x86),
    que ademas exige cambiar TARGET_PLATFORM a linux/amd64 y bajar los limites de
    memoria del compose. Ver el apendice del runbook.
  TEXTO
  type        = string
  default     = "VM.Standard.A1.Flex"
}

variable "ocpus" {
  description = "Nucleos. La cuota Always Free de Ampere permite hasta 4 en total."
  type        = number
  default     = 2
}

variable "memoria_gb" {
  description = "Memoria en GB. La cuota Always Free permite hasta 24 en total."
  type        = number
  default     = 12
}

variable "disco_gb" {
  description = "Tamano del volumen de arranque. El minimo de OCI es 50."
  type        = number
  default     = 50
}

variable "indice_dominio_disponibilidad" {
  description = <<-TEXTO
    Cual de los Availability Domains usar, empezando en 0.
    Es la primera perilla que hay que mover ante un "Out of host capacity":
    la capacidad de Ampere varia entre dominios de la misma region.
  TEXTO
  type        = number
  default     = 0
}

# ---------------------------------------------------------------------------
# Almacenamiento e identidad
# ---------------------------------------------------------------------------

variable "nombre_bucket" {
  description = "Bucket donde la canalizacion publica el modelo entrenado."
  type        = string
  default     = "techmind-models"
}

variable "nombre_bucket_respaldos" {
  description = <<-TEXTO
    Bucket donde la VM sube los respaldos de la base. Separado del de modelos a
    proposito: la maquina puede escribir aqui, pero nunca alli.
  TEXTO
  type        = string
  default     = "techmind-backups"
}

variable "dias_retencion_respaldos" {
  description = <<-TEXTO
    Dias que se conserva cada respaldo antes de que OCI lo borre solo. Sin un
    limite, un respaldo diario llena los 20 GB de la capa gratuita.
  TEXTO
  type        = number
  default     = 14
}

variable "dominio_identidad" {
  description = <<-TEXTO
    Nombre del identity domain, si el tenancy usa esa funcion (los creados a
    partir de 2023 la usan). Afecta COMO se escribe el grupo dinamico dentro de
    la policy: con dominio va 'Default'/'techmind-instances', sin dominio va
    techmind-instances a secas. Dejar vacio si no aplica.
  TEXTO
  type        = string
  default     = ""
}

variable "prefijo" {
  description = "Prefijo de los nombres de todos los recursos creados."
  type        = string
  default     = "techmind"
}
