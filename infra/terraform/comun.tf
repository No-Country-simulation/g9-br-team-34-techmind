# Valores compartidos por el resto de los archivos.

locals {
  # Si no se indica compartment, se usa el tenancy (el compartment raiz), que es
  # lo mas simple para un proyecto de este tamano.
  compartment = var.compartment_ocid != "" ? var.compartment_ocid : var.tenancy_ocid

  # Todos los recursos quedan etiquetados. En una cuenta compartida es lo que
  # permite responder "¿esto que es y quien lo creo?" seis meses despues, y
  # borrar todo lo del proyecto sin dudar de si algo mas depende de eso.
  etiquetas = {
    proyecto = "techmind"
    gestor   = "terraform"
    equipo   = "g9-team-34"
    ambiente = "produccion"
  }
}
