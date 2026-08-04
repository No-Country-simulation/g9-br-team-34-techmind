#!/usr/bin/env bash
#
# Aprovisionamiento de la instancia de OCI Compute para TechMind.
#
# Se ejecuta UNA SOLA VEZ, dentro de la VM recien creada:
#
#   scp scripts/provision-vm.sh opc@<IP>:~
#   ssh opc@<IP> 'bash provision-vm.sh'
#
# Deja la maquina lista para recibir despliegues: Docker instalado, el puerto
# 8080 abierto y /opt/techmind creado con los permisos correctos.
#
# Es idempotente: volver a ejecutarlo no rompe nada, solo salta lo ya hecho.
# Eso importa porque la primera ejecucion casi nunca sale perfecta y hay que
# relanzarlo.
#
# Probado en Oracle Linux 9 (la imagen por defecto de OCI). Para Ubuntu, ver la
# nota al final del archivo.

set -euo pipefail

VERDE='\033[0;32m'; AMARILLO='\033[0;33m'; AZUL='\033[0;34m'; FIN='\033[0m'
paso() { printf "\n${AZUL}==> %s${FIN}\n" "$1"; }
ok()   { printf "${VERDE}    %s${FIN}\n" "$1"; }
aviso(){ printf "${AMARILLO}    %s${FIN}\n" "$1"; }

DIRECTORIO_APP="/opt/techmind"
USUARIO="${SUDO_USER:-$(whoami)}"

if [ "$(id -u)" -eq 0 ] && [ -z "${SUDO_USER:-}" ]; then
  echo "Ejecutar como usuario normal con sudo disponible, no como root directo."
  echo "El script necesita saber a que usuario agregar al grupo docker."
  exit 1
fi

printf "\n  Aprovisionamiento de TechMind\n"
printf "  Usuario: %s | Host: %s | Arquitectura: %s\n" "$USUARIO" "$(hostname)" "$(uname -m)"

# --------------------------------------------------------------------------
paso "Actualizando paquetes del sistema"
sudo dnf update -y -q
ok "Sistema actualizado"

# --------------------------------------------------------------------------
paso "Instalando Docker Engine"

if command -v docker > /dev/null 2>&1; then
  ok "Docker ya estaba instalado: $(docker --version)"
else
  # Se usa el repositorio oficial de Docker y no el paquete `podman-docker` que
  # trae Oracle Linux: necesitamos docker compose v2 con soporte de `!reset`, y
  # el emulador de podman no es equivalente para esto.
  sudo dnf install -y -q dnf-plugins-core
  sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
  sudo dnf install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  ok "Docker instalado: $(docker --version)"
fi

sudo systemctl enable --now docker
ok "Servicio docker activo y habilitado en el arranque"

# --------------------------------------------------------------------------
paso "Verificando la version de Docker Compose"

version_compose=$(docker compose version --short 2>/dev/null || echo "0.0.0")
echo "    Version detectada: ${version_compose}"

# docker-compose.prod.yml usa etiquetas `!reset`, incorporadas en la v2.24.
mayor=$(echo "$version_compose" | cut -d. -f1)
menor=$(echo "$version_compose" | cut -d. -f2)
if [ "$mayor" -gt 2 ] || { [ "$mayor" -eq 2 ] && [ "$menor" -ge 24 ]; }; then
  ok "Compose soporta las etiquetas !reset que usa docker-compose.prod.yml"
else
  aviso "Compose ${version_compose} es anterior a la 2.24."
  aviso "Las etiquetas !reset de docker-compose.prod.yml NO funcionaran."
  aviso "Actualizar con: sudo dnf update docker-compose-plugin"
fi

# --------------------------------------------------------------------------
paso "Agregando ${USUARIO} al grupo docker"

if id -nG "$USUARIO" | grep -qw docker; then
  ok "El usuario ya pertenecia al grupo docker"
else
  sudo usermod -aG docker "$USUARIO"
  ok "Usuario agregado al grupo docker"
  aviso "Cerrar y reabrir la sesion SSH para que el cambio surta efecto."
fi

# Sin esto, el despliegue por SSH desde GitHub Actions fallaria con
# "permission denied on /var/run/docker.sock", que es el error mas comun al
# montar este tipo de canalizacion.

# --------------------------------------------------------------------------
paso "Abriendo el puerto 8080 en el firewall de la instancia"

# OCI tiene DOS capas de firewall y hay que abrir las dos:
#   1. La Security List / NSG de la VCN  -> se configura en la consola web
#   2. El firewall del sistema operativo -> es lo que hace este paso
# Olvidar la segunda es la causa numero uno de "el contenedor corre pero no
# puedo entrar desde fuera".

if command -v firewall-cmd > /dev/null 2>&1 && sudo firewall-cmd --state > /dev/null 2>&1; then
  sudo firewall-cmd --permanent --add-port=8080/tcp > /dev/null
  sudo firewall-cmd --reload > /dev/null
  ok "Puerto 8080/tcp abierto en firewalld"
else
  # Las imagenes mas antiguas de OCI usan iptables directamente.
  sudo iptables -I INPUT 5 -p tcp --dport 8080 -j ACCEPT 2>/dev/null || true
  if command -v netfilter-persistent > /dev/null 2>&1; then
    sudo netfilter-persistent save > /dev/null 2>&1 || true
  else
    sudo bash -c 'iptables-save > /etc/iptables/rules.v4' 2>/dev/null || true
  fi
  ok "Puerto 8080/tcp abierto en iptables"
fi

aviso "RECORDATORIO: abrir tambien 8080 en la Security List de la VCN (consola de OCI)."

# --------------------------------------------------------------------------
paso "Creando el directorio de la aplicacion"

sudo mkdir -p "$DIRECTORIO_APP"
sudo chown "${USUARIO}:${USUARIO}" "$DIRECTORIO_APP"
ok "${DIRECTORIO_APP} creado y asignado a ${USUARIO}"

# El .env de produccion se crea vacio con permisos 600. Contiene la contrasena
# de la base de datos y la API key, asi que no debe ser legible por otros
# usuarios de la maquina.
if [ ! -f "${DIRECTORIO_APP}/.env" ]; then
  cat > "${DIRECTORIO_APP}/.env" <<'PLANTILLA'
# TechMind - configuracion de PRODUCCION.
#
# COMPLETAR A MANO. Este archivo nunca se sube al repositorio ni se sobrescribe
# desde la canalizacion de despliegue: solo se actualiza su linea IMAGE_TAG.

# --- OCIR / Object Storage ---
OCIR_REGISTRY=
OCI_NAMESPACE=
OCI_REGION=
OCI_BUCKET_NAME=techmind-models
OCI_MODEL_OBJECT=model.joblib

# --- Version desplegada (la actualiza el workflow de CD) ---
IMAGE_TAG=latest

# --- Backend ---
BACKEND_PORT=8080
DB_USERNAME=techmind
DB_PASSWORD=
TECHMIND_API_KEY=
CORS_ALLOWED_ORIGINS=
INFERENCE_SERVICE_TIMEOUT_MS=8000
PLANTILLA
  chmod 600 "${DIRECTORIO_APP}/.env"
  ok "Plantilla de .env creada en ${DIRECTORIO_APP}/.env (permisos 600)"
  aviso "COMPLETAR sus valores antes del primer despliegue."
else
  ok ".env ya existia, no se toca"
fi

# --------------------------------------------------------------------------
paso "Configurando la rotacion de logs de Docker"

# Segunda linea de defensa: docker-compose.prod.yml ya limita los logs por
# servicio, pero este ajuste protege tambien a cualquier contenedor que alguien
# levante a mano para depurar y luego olvide. En una VM con 47 GB de disco, un
# contenedor con logs sin limite la llena en cuestion de dias.
if [ ! -f /etc/docker/daemon.json ]; then
  sudo mkdir -p /etc/docker
  sudo tee /etc/docker/daemon.json > /dev/null <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
JSON
  sudo systemctl restart docker
  ok "Rotacion de logs configurada globalmente"
else
  ok "/etc/docker/daemon.json ya existia, no se toca"
fi

# --------------------------------------------------------------------------
paso "Verificacion final"

sudo docker run --rm hello-world > /dev/null 2>&1 \
  && ok "Docker ejecuta contenedores correctamente" \
  || aviso "El contenedor de prueba fallo. Revisar: sudo systemctl status docker"

printf "\n${VERDE}  Aprovisionamiento terminado.${FIN}\n\n"
printf "  Siguientes pasos:\n"
printf "    1. Completar %s/.env con los valores reales\n" "$DIRECTORIO_APP"
printf "    2. Abrir el puerto 8080 en la Security List de la VCN (consola de OCI)\n"
printf "    3. Crear el dynamic group y la policy para instance_principal\n"
printf "    4. Cargar los secrets del repositorio en GitHub y lanzar el workflow de CD\n"
printf "\n  Detalle completo en docs/devops/despliegue-oci.md\n\n"

# --------------------------------------------------------------------------
# NOTA PARA UBUNTU
#
# Si la instancia usa Ubuntu en lugar de Oracle Linux, sustituir los bloques de
# `dnf` por:
#
#   sudo apt-get update
#   sudo apt-get install -y ca-certificates curl gnupg
#   sudo install -m 0755 -d /etc/apt/keyrings
#   curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
#     | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
#   echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
#     https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
#     | sudo tee /etc/apt/sources.list.d/docker.list
#   sudo apt-get update
#   sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
#     docker-buildx-plugin docker-compose-plugin
#
# En Ubuntu el usuario por defecto es `ubuntu` y no `opc`.
