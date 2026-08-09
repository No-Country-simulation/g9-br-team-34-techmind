#!/usr/bin/env bash
#
# Respaldo de la base de datos de TechMind hacia OCI Object Storage.
#
# Se ejecuta DENTRO de la VM. `provision-vm.sh` lo instala en un cron diario,
# pero tambien sirve a mano antes de cualquier cambio riesgoso:
#
#   bash /opt/techmind/backup-datos.sh
#
# Sube a un bucket SEPARADO del de modelos. La instancia tiene permiso de
# escritura solo alli; sobre el bucket de modelos sigue siendo de solo lectura,
# de modo que una maquina comprometida no puede envenenar el artefacto que
# sirve el sistema.
#
# Restaurar: ver docs/devops/despliegue-oci.md, parte 7.

set -euo pipefail

DIRECTORIO_APP="${DIRECTORIO_APP:-/opt/techmind}"
VOLUMEN="${VOLUMEN:-techmind_backend-data}"

# El bucket y la region se leen del mismo .env que usa el compose, para que no
# haya dos lugares donde configurar lo mismo y puedan quedar desincronizados.
if [ -f "${DIRECTORIO_APP}/.env" ]; then
  # shellcheck disable=SC1090,SC1091
  set -a; . "${DIRECTORIO_APP}/.env"; set +a
fi

BUCKET="${OCI_BUCKET_RESPALDOS:-techmind-backups}"
RETENCION_LOCAL_DIAS="${RETENCION_LOCAL_DIAS:-3}"

DIRECTORIO_TEMPORAL="${DIRECTORIO_APP}/respaldos"
MARCA=$(date -u +%Y%m%dT%H%M%SZ)
ARCHIVO="techmind-datos-${MARCA}.tar.gz"
RUTA_LOCAL="${DIRECTORIO_TEMPORAL}/${ARCHIVO}"

registrar() { printf '%s  %s\n' "$(date -u +'%Y-%m-%d %H:%M:%SZ')" "$1"; }

mkdir -p "$DIRECTORIO_TEMPORAL"

# --------------------------------------------------------------------------
registrar "Verificando que el volumen exista"

if ! docker volume inspect "$VOLUMEN" > /dev/null 2>&1; then
  registrar "ERROR: no existe el volumen ${VOLUMEN}."
  registrar "Los volumenes disponibles son:"
  docker volume ls --format '  {{.Name}}'
  exit 1
fi

# --------------------------------------------------------------------------
registrar "Preparando las imagenes auxiliares"

# Las imagenes se descargan ANTES de pausar el backend, y esto no es un detalle
# de eficiencia. Un `docker pull` dentro de la ventana de pausa la estira de un
# segundo a un minuto, y durante ese rato el healthcheck del contenedor pausado
# falla una vez tras otra: si acumula suficientes fallos, Docker lo marca como
# unhealthy y cualquier monitoreo empieza a avisar de una caida que no existe.
#
# Se comprobo en la practica: con el pull dentro de la pausa, el backend quedaba
# unhealthy. Con el pull afuera, la pausa dura menos que un solo intervalo de
# healthcheck y no se nota.
docker image inspect alpine:3 > /dev/null 2>&1 || docker pull --quiet alpine:3 > /dev/null
docker image inspect ghcr.io/oracle/oci-cli:latest > /dev/null 2>&1 || \
  docker pull --quiet ghcr.io/oracle/oci-cli:latest > /dev/null

# --------------------------------------------------------------------------
registrar "Comprimiendo el volumen ${VOLUMEN}"

# H2 con AUTO_SERVER escribe de forma continua. Copiar los archivos mientras el
# backend esta escribiendo puede capturar un estado a medias, asi que se pausa
# el contenedor los pocos segundos que dura el tar.
#
# Se usa `pause` y no `stop`: congela los procesos sin cerrar conexiones ni
# disparar el reinicio del healthcheck. El servicio queda inaccesible un
# instante, lo cual es aceptable de madrugada y es la unica forma de obtener una
# copia consistente sin montar replicacion.
CONTENEDOR_BACKEND="techmind-backend"
pausado=0

if docker ps --format '{{.Names}}' | grep -qx "$CONTENEDOR_BACKEND"; then
  docker pause "$CONTENEDOR_BACKEND" > /dev/null
  pausado=1
  registrar "Backend pausado durante la copia"
fi

# `trap` garantiza que el contenedor se reanude aunque el tar falle o alguien
# interrumpa el script. Sin esto, un fallo a mitad de camino dejaria la API
# congelada indefinidamente, que seria bastante peor que no tener respaldo.
reanudar() {
  if [ "$pausado" -eq 1 ]; then
    docker unpause "$CONTENEDOR_BACKEND" > /dev/null 2>&1 || true
    registrar "Backend reanudado"
    pausado=0
  fi
}
trap reanudar EXIT

docker run --rm \
  -v "${VOLUMEN}:/datos:ro" \
  -v "${DIRECTORIO_TEMPORAL}:/respaldo" \
  alpine:3 \
  tar czf "/respaldo/${ARCHIVO}" -C /datos .

reanudar
trap - EXIT

TAMANO=$(du -h "$RUTA_LOCAL" | cut -f1)
registrar "Copia local lista: ${ARCHIVO} (${TAMANO})"

# --------------------------------------------------------------------------
registrar "Subiendo a Object Storage (bucket ${BUCKET})"

# Se usa la CLI de OCI en contenedor para no instalar Python ni el SDK en la VM.
# --auth instance_principal: la maquina se autentica con su propia identidad,
# igual que hace el ml-service para bajar el modelo. Cero credenciales en disco.
if docker run --rm \
  -v "${DIRECTORIO_TEMPORAL}:/respaldo:ro" \
  --network host \
  ghcr.io/oracle/oci-cli:latest \
  os object put \
  --auth instance_principal \
  --bucket-name "$BUCKET" \
  --file "/respaldo/${ARCHIVO}" \
  --name "datos/${ARCHIVO}" \
  --no-multipart \
  > /dev/null 2>&1; then
  registrar "Subido como datos/${ARCHIVO}"
else
  registrar "ERROR: fallo la subida."
  registrar "Causas mas probables, en orden:"
  registrar "  1. Falta la policy de escritura sobre el bucket ${BUCKET}"
  registrar "  2. El bucket no existe (correr terraform apply)"
  registrar "  3. El dynamic group no incluye a esta instancia"
  registrar "La copia local quedo en ${RUTA_LOCAL}, no se perdio nada."
  exit 1
fi

# --------------------------------------------------------------------------
registrar "Limpiando copias locales de mas de ${RETENCION_LOCAL_DIAS} dias"

# Las copias viejas se borran solo en la VM. En Object Storage las descarta la
# regla de retencion del bucket, que definimos en Terraform.
find "$DIRECTORIO_TEMPORAL" -name 'techmind-datos-*.tar.gz' \
  -mtime "+${RETENCION_LOCAL_DIAS}" -delete 2>/dev/null || true

registrar "Respaldo terminado"
