#!/usr/bin/env bash
#
# Prueba de humo de TechMind.
#
#   ./scripts/smoke-test.sh                      # contra localhost
#   ./scripts/smoke-test.sh 10.0.0.5             # contra una IP concreta
#   BASE_ML=http://mi-vm:8000 ./scripts/smoke-test.sh
#
# Ejecuta los tres ejemplos de uso que exige el brief del hackathon contra un
# sistema ya levantado. Sirve tanto para verificar el entorno local como para
# comprobar un despliegue en OCI recien hecho.
#
# Devuelve 0 si todo responde como debe, 1 si algo falla.

set -uo pipefail
# Nota: no se usa `set -e`. Se quiere que TODAS las comprobaciones se ejecuten y
# se reporten juntas; abortar en la primera obligaria a arreglar y relanzar una
# y otra vez para descubrir los problemas de a uno.

HOST="${1:-localhost}"
BASE_ML="${BASE_ML:-http://${HOST}:8000}"
BASE_API="${BASE_API:-http://${HOST}:8080}"

VERDE='\033[0;32m'; ROJO='\033[0;31m'; AZUL='\033[0;34m'; GRIS='\033[0;90m'; FIN='\033[0m'

fallos=0

titulo()  { printf "\n${AZUL}== %s ==${FIN}\n" "$1"; }
correcto(){ printf "  ${VERDE}OK${FIN}   %s\n" "$1"; }
fallo()   { printf "  ${ROJO}FALLA${FIN} %s\n" "$1"; fallos=$((fallos + 1)); }
detalle() { printf "       ${GRIS}%s${FIN}\n" "$1"; }

# Formatea JSON con jq si esta disponible; si no, lo imprime tal cual. No se
# exige jq porque no viene instalado por defecto en la VM de OCI.
formatear() {
  if command -v jq > /dev/null 2>&1; then jq -C .; else cat; fi
}

# --------------------------------------------------------------------------
titulo "Disponibilidad de los servicios"

if respuesta=$(curl --silent --fail --max-time 10 "${BASE_ML}/health" 2>/dev/null); then
  if echo "$respuesta" | grep -q '"modelo_cargado":true'; then
    correcto "ml-service responde y tiene el modelo cargado"
    categorias=$(echo "$respuesta" | grep -o '"categorias":\[[^]]*\]')
    detalle "$categorias"
  else
    fallo "ml-service responde pero el modelo NO esta cargado"
    detalle "$respuesta"
  fi
else
  fallo "ml-service no responde en ${BASE_ML}/health"
  detalle "Revisar: docker compose logs ml-service"
fi

if curl --silent --fail --max-time 10 "${BASE_API}/v3/api-docs" > /dev/null 2>&1; then
  correcto "backend responde en ${BASE_API}"
else
  fallo "backend no responde en ${BASE_API}/v3/api-docs"
  detalle "Revisar: docker compose logs backend"
fi

# --------------------------------------------------------------------------
titulo "Ejemplos de uso del brief"

# Cada ejemplo: nombre, titulo y texto.
probar_ejemplo() {
  local nombre="$1" titulo_doc="$2" texto_doc="$3"

  local cuerpo
  cuerpo=$(printf '{"titulo":%s,"texto":%s}' \
    "$(printf '%s' "$titulo_doc" | sed 's/"/\\"/g; s/^/"/; s/$/"/')" \
    "$(printf '%s' "$texto_doc"  | sed 's/"/\\"/g; s/^/"/; s/$/"/')")

  local salida
  if salida=$(curl --silent --fail --max-time 15 \
        -X POST "${BASE_ML}/predict" \
        -H 'Content-Type: application/json' \
        -d "$cuerpo" 2>/dev/null); then

    # Se verifica la FORMA de la respuesta, no la categoria concreta: el modelo
    # se reentrena y una asercion sobre su salida convertiria cada mejora del
    # equipo de Ciencia de Datos en un falso fallo.
    if echo "$salida" | grep -q '"categoria"' \
       && echo "$salida" | grep -q '"probabilidad"' \
       && echo "$salida" | grep -q '"informacion_adicional"'; then
      correcto "$nombre"
      echo "$salida" | formatear | sed 's/^/       /'
    else
      fallo "$nombre - la respuesta no tiene la forma esperada"
      detalle "$salida"
    fi
  else
    fallo "$nombre - la peticion no obtuvo respuesta"
  fi
}

probar_ejemplo "Ejemplo 1: Backend" \
  "Introduccion a Spring Boot" \
  "En este contenido se presentan los conceptos basicos para la creacion de APIs REST utilizando Java y Spring Boot."

probar_ejemplo "Ejemplo 2: DevOps" \
  "Tutorial de Docker" \
  "Introduccion a la contenerizacion de aplicaciones con Docker, escritura de un Dockerfile, construccion de imagenes y ejecucion de contenedores con volumenes y redes."

probar_ejemplo "Ejemplo 3: Ciencia de Datos" \
  "Analisis exploratorio de datos con Pandas" \
  "Carga, limpieza y exploracion de conjuntos de datos tabulares usando Pandas, tratamiento de valores nulos, agrupaciones y calculo de estadisticas descriptivas."

# --------------------------------------------------------------------------
titulo "Validacion de entradas"

codigo=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 \
  -X POST "${BASE_ML}/predict" \
  -H 'Content-Type: application/json' \
  -d '{"titulo":"Sin texto"}' 2>/dev/null)

if [ "$codigo" = "422" ]; then
  correcto "Un cuerpo incompleto devuelve 422 (y no 500)"
else
  fallo "Un cuerpo incompleto devolvio ${codigo}, se esperaba 422"
fi

# --------------------------------------------------------------------------
printf "\n"
if [ "$fallos" -eq 0 ]; then
  printf "${VERDE}Todas las comprobaciones pasaron.${FIN}\n\n"
  exit 0
else
  printf "${ROJO}%d comprobacion(es) fallaron.${FIN}\n\n" "$fallos"
  exit 1
fi
