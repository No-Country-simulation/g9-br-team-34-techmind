#!/usr/bin/env bash
#
# Carga en GitHub los secrets, variables y el environment que necesita el
# workflow de CD.
#
#   ./scripts/configurar-github.sh
#
# Reemplaza catorce formularios de la interfaz web, que es donde mas facil es
# pegar un valor en el campo equivocado o dejar un salto de linea de mas en una
# clave privada.
#
# Es idempotente: volver a ejecutarlo sobrescribe los valores. Podes dejar
# cualquier campo vacio para no tocarlo.
#
# Los secretos se leen SIN eco en pantalla y se pasan a `gh` por stdin, asi que
# no quedan en el historial de la terminal ni en ningun archivo temporal.
#
# Requisitos: gh (brew install gh) y una sesion iniciada (gh auth login).
# De donde sale cada valor: docs/devops/despliegue-oci.md

set -uo pipefail

VERDE='\033[0;32m'; ROJO='\033[0;31m'; AZUL='\033[0;34m'
AMARILLO='\033[0;33m'; GRIS='\033[0;90m'; FIN='\033[0m'

titulo() { printf "\n${AZUL}== %s ==${FIN}\n" "$1"; }
ok()     { printf "  ${VERDE}OK${FIN}   %s\n" "$1"; }
error()  { printf "  ${ROJO}FALLA${FIN} %s\n" "$1"; }
saltado(){ printf "  ${GRIS}--${FIN}   %s (vacio, sin cambios)\n" "$1"; }
nota()   { printf "       ${GRIS}%s${FIN}\n" "$1"; }

# --------------------------------------------------------------------------
# Comprobaciones previas
# --------------------------------------------------------------------------

if ! command -v gh > /dev/null 2>&1; then
  printf "${ROJO}Falta gh.${FIN} Instalalo con:  brew install gh\n"
  exit 1
fi

if ! gh auth status > /dev/null 2>&1; then
  printf "${ROJO}gh no tiene sesion iniciada.${FIN} Ejecuta:  gh auth login\n"
  exit 1
fi

# Determinar el repositorio. Se intenta en tres pasos, de mas a menos comodo.
REPO="${GH_REPO:-}"

# 1. Preguntarle a gh. Requiere que `git` funcione, porque gh deduce el
#    repositorio leyendo el remoto con git.
if [ -z "$REPO" ]; then
  REPO=$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null)
fi

# 2. Leer .git/config directamente. Esto NO necesita git, y es lo que salva el
#    caso de una maquina donde el git del sistema esta roto (por ejemplo, un Mac
#    con la licencia de Xcode sin aceptar) pero se commitea con GitHub Desktop,
#    que trae su propio git embebido.
if [ -z "$REPO" ] && [ -f .git/config ]; then
  REPO=$(grep -A3 'remote "origin"' .git/config \
         | grep -oE '[:/][^:/]+/[^/]+\.git' \
         | head -1 \
         | sed 's|^[:/]||; s|\.git$||')
fi

if [ -z "$REPO" ]; then
  printf "${ROJO}No se pudo determinar el repositorio.${FIN}\n"
  printf "Ejecuta el script desde la raiz del repo, o definilo a mano:\n"
  printf "  export GH_REPO=owner/repo\n"
  exit 1
fi

# Todas las llamadas posteriores usan --repo "$REPO" de forma explicita, asi que
# a partir de aca el script ya no depende de git para nada.
export GH_REPO="$REPO"

printf "\n  Configuracion de GitHub Actions para ${AZUL}%s${FIN}\n" "$REPO"
printf "  ${GRIS}Dejar un campo vacio = no tocarlo.${FIN}\n"

# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------

# Variable normal: se escribe a la vista, porque no es secreta.
pedir_variable() {
  local nombre="$1" ayuda="$2" ejemplo="$3" valor
  printf "\n  ${AMARILLO}%s${FIN}  ${GRIS}%s${FIN}\n" "$nombre" "$ayuda"
  printf "  ejemplo: ${GRIS}%s${FIN}\n  > " "$ejemplo"
  read -r valor

  if [ -z "$valor" ]; then saltado "$nombre"; return; fi

  if gh variable set "$nombre" --body "$valor" --repo "$REPO" 2>/dev/null; then
    ok "$nombre = $valor"
  else
    error "$nombre no se pudo escribir"
  fi
}

# Secreto de una linea: se lee sin eco.
pedir_secreto() {
  local nombre="$1" ayuda="$2" valor
  printf "\n  ${AMARILLO}%s${FIN}  ${GRIS}%s${FIN}\n  > " "$nombre" "$ayuda"
  read -rs valor
  printf "\n"

  if [ -z "$valor" ]; then saltado "$nombre"; return; fi

  if printf '%s' "$valor" | gh secret set "$nombre" --repo "$REPO" 2>/dev/null; then
    ok "$nombre cargado (${#valor} caracteres)"
  else
    error "$nombre no se pudo escribir"
  fi
}

# Secreto que vive en un archivo (las dos claves privadas). Se pide la RUTA y no
# el contenido: pegar una clave multilinea en una terminal es la forma mas
# comun de que llegue cortada o sin el salto de linea final, y entonces falla en
# el despliegue con un error que no menciona la clave por ningun lado.
pedir_secreto_archivo() {
  local nombre="$1" ayuda="$2" ruta
  printf "\n  ${AMARILLO}%s${FIN}  ${GRIS}%s${FIN}\n" "$nombre" "$ayuda"
  printf "  ruta del archivo > "
  read -r ruta
  ruta="${ruta/#\~/$HOME}"

  if [ -z "$ruta" ]; then saltado "$nombre"; return; fi

  if [ ! -f "$ruta" ]; then
    error "$nombre: no existe el archivo $ruta"
    return
  fi

  if ! grep -q "BEGIN" "$ruta"; then
    error "$nombre: $ruta no parece una clave privada (no dice BEGIN)"
    nota "Ojo: la clave PUBLICA (.pub) no sirve, hace falta la privada."
    return
  fi

  if gh secret set "$nombre" --repo "$REPO" < "$ruta" 2>/dev/null; then
    ok "$nombre cargado desde $ruta"
  else
    error "$nombre no se pudo escribir"
  fi
}

# --------------------------------------------------------------------------
titulo "Variables (visibles en la interfaz, no son secretas)"

pedir_variable OCI_REGION \
  "Region del tenancy" "sa-saopaulo-1"

pedir_variable OCI_NAMESPACE \
  "Namespace del Object Storage. Se obtiene con: oci os ns get" "axxxxxxxxxxx"

pedir_variable OCI_BUCKET_NAME \
  "Bucket donde se publica el modelo" "techmind-models"

pedir_variable OCIR_REGISTRY \
  "Registro de contenedores segun la region (gru/scl/bog/iad/phx)" "gru.ocir.io"

pedir_variable OCI_HOST \
  "IP publica de la instancia de Compute" "140.238.1.2"

pedir_variable OCI_SSH_USER \
  "Usuario SSH: opc en Oracle Linux, ubuntu en Ubuntu" "opc"

pedir_variable TARGET_PLATFORM \
  "Arquitectura de la VM: linux/arm64 en Ampere A1, linux/amd64 en E2.1.Micro" \
  "linux/arm64"

# --------------------------------------------------------------------------
titulo "Secrets"

pedir_secreto OCI_CLI_USER \
  "OCID del usuario IAM (empieza con ocid1.user.oc1..)"

pedir_secreto OCI_CLI_TENANCY \
  "OCID del tenancy (empieza con ocid1.tenancy.oc1..)"

pedir_secreto OCI_CLI_FINGERPRINT \
  "Huella de la API key (formato a1:b2:c3:...)"

pedir_secreto_archivo OCI_CLI_KEY_CONTENT \
  "Clave privada de la API key de OCI, el .pem que descargaste"

pedir_secreto OCIR_USERNAME \
  "Usuario de OCIR: <namespace>/<usuario> o <namespace>/<dominio>/<usuario>"

pedir_secreto OCIR_AUTH_TOKEN \
  "Auth Token de OCIR. NO es la contrasena de la consola"

pedir_secreto_archivo OCI_SSH_PRIVATE_KEY \
  "Clave SSH privada de despliegue (ej: ~/.ssh/techmind_deploy, SIN el .pub)"

# --------------------------------------------------------------------------
titulo "Environment de produccion"

# cd.yml declara `environment: produccion`. Si el environment no existe, el job
# de despliegue no arranca. La interfaz no lo crea solo.
if gh api -X PUT "repos/${REPO}/environments/produccion" > /dev/null 2>&1; then
  ok "environment 'produccion' creado o ya existente"
  nota "Para exigir aprobacion manual antes de tocar produccion:"
  nota "Settings > Environments > produccion > Required reviewers"
else
  error "no se pudo crear el environment 'produccion'"
  nota "Crealo a mano: Settings > Environments > New environment > produccion"
fi

# --------------------------------------------------------------------------
titulo "Estado final"

printf "\n  ${AZUL}Variables cargadas:${FIN}\n"
gh variable list --repo "$REPO" 2>/dev/null | sed 's/^/    /' || printf "    (no se pudieron listar)\n"

printf "\n  ${AZUL}Secrets cargados:${FIN}\n"
gh secret list --repo "$REPO" 2>/dev/null | sed 's/^/    /' || printf "    (no se pudieron listar)\n"

# Comprueba que no falte ninguno de los catorce. Descubrirlo aca es gratis;
# descubrirlo a mitad del primer despliegue cuesta una corrida fallida.
faltantes=0
variables_actuales=$(gh variable list --repo "$REPO" --json name --jq '.[].name' 2>/dev/null)
secrets_actuales=$(gh secret list --repo "$REPO" --json name --jq '.[].name' 2>/dev/null)

printf "\n"
for v in OCI_REGION OCI_NAMESPACE OCI_BUCKET_NAME OCIR_REGISTRY OCI_HOST OCI_SSH_USER; do
  echo "$variables_actuales" | grep -qx "$v" || { error "falta la variable $v"; faltantes=$((faltantes + 1)); }
done
for s in OCI_CLI_USER OCI_CLI_TENANCY OCI_CLI_FINGERPRINT OCI_CLI_KEY_CONTENT \
         OCIR_USERNAME OCIR_AUTH_TOKEN OCI_SSH_PRIVATE_KEY; do
  echo "$secrets_actuales" | grep -qx "$s" || { error "falta el secret $s"; faltantes=$((faltantes + 1)); }
done

# TARGET_PLATFORM no se cuenta: cd.yml tiene `vars.TARGET_PLATFORM || 'linux/arm64'`,
# asi que su ausencia es un valor por defecto, no un error.

if [ "$faltantes" -eq 0 ]; then
  printf "\n${VERDE}  Todo listo. Ya podes lanzar el workflow de CD.${FIN}\n"
  printf "  ${GRIS}Actions > CD - Despliegue en OCI > Run workflow${FIN}\n\n"
  exit 0
else
  printf "\n${AMARILLO}  Faltan %d valor(es). Volve a ejecutar el script para completarlos.${FIN}\n\n" "$faltantes"
  exit 1
fi
