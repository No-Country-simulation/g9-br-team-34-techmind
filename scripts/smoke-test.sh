#!/usr/bin/env bash
#
# Prueba de humo de TechMind.
#
#   ./scripts/smoke-test.sh                      # contra localhost (desarrollo)
#   ./scripts/smoke-test.sh 140.238.1.2          # contra la VM de OCI
#   BASE_ML=http://mi-vm:8000 ./scripts/smoke-test.sh
#   TECHMIND_API_KEY=<clave> ./scripts/smoke-test.sh 140.238.1.2
#
# Ejecuta los tres ejemplos de uso que exige el brief del hackathon contra un
# sistema ya levantado. Sirve tanto para verificar el entorno local como para
# comprobar un despliegue en OCI recien hecho.
#
# DOS TOPOLOGIAS, UN SOLO SCRIPT
#
# En desarrollo el ml-service publica el puerto 8000 y se puede interrogar
# directamente. En produccion NO lo publica a proposito: es un servicio interno,
# alcanzable solo desde la red privada del compose. Por eso el script detecta
# con que topologia esta hablando y ajusta lo que comprueba:
#
#   modo completo    -> el 8000 responde. Se prueba el ml-service directamente
#                       Y el camino a traves de la API.
#   modo produccion  -> el 8000 no responde pero la API si. Se prueba todo a
#                       traves de la API publica, que es el unico camino que
#                       existe alli. No es un fallo: es el diseno.
#
# Sin esta distincion, correrlo contra OCI reportaba cuatro fallos garantizados
# por comprobar un puerto que debe estar cerrado.
#
# Devuelve 0 si todo responde como debe, 1 si algo falla.

set -uo pipefail
# Nota: no se usa `set -e`. Se quiere que TODAS las comprobaciones se ejecuten y
# se reporten juntas; abortar en la primera obligaria a arreglar y relanzar una
# y otra vez para descubrir los problemas de a uno.

HOST="${1:-localhost}"
BASE_ML="${BASE_ML:-http://${HOST}:8000}"
BASE_API="${BASE_API:-http://${HOST}:8080}"

VERDE='\033[0;32m'; ROJO='\033[0;31m'; AZUL='\033[0;34m'
AMARILLO='\033[0;33m'; GRIS='\033[0;90m'; FIN='\033[0m'

fallos=0
pendientes=0

titulo()  { printf "\n${AZUL}== %s ==${FIN}\n" "$1"; }
correcto(){ printf "  ${VERDE}OK${FIN}   %s\n" "$1"; }
fallo()   { printf "  ${ROJO}FALLA${FIN} %s\n" "$1"; fallos=$((fallos + 1)); }
detalle() { printf "       ${GRIS}%s${FIN}\n" "$1"; }
omitido() { printf "  ${GRIS}OMIT${FIN} %s\n" "$1"; }

# Estado intermedio: algo que todavia no esta hecho pero que NO es un error.
# Se cuenta aparte para poder informarlo sin que el script devuelva fallo: una
# pieza que aun no existe no es lo mismo que una pieza rota.
aviso_pendiente() { printf "  ${AMARILLO}PEND${FIN} %s\n" "$1"; pendientes=$((pendientes + 1)); }

# Formatea JSON con jq si esta disponible; si no, lo imprime tal cual. No se
# exige jq porque no viene instalado por defecto en la VM de OCI.
formatear() {
  if command -v jq > /dev/null 2>&1; then jq -C .; else cat; fi
}

# --------------------------------------------------------------------------
titulo "Disponibilidad de los servicios"

ml_accesible=0
api_accesible=0

if respuesta_ml=$(curl --silent --fail --max-time 10 "${BASE_ML}/health" 2>/dev/null); then
  ml_accesible=1
  if echo "$respuesta_ml" | grep -q '"modelo_cargado":true'; then
    correcto "ml-service responde y tiene el modelo cargado"
    detalle "$(echo "$respuesta_ml" | grep -o '"categorias":\[[^]]*\]')"

    # `origen_modelo` distingue de donde salio el artefacto. En produccion,
    # `local-fallback` significa que Object Storage no respondio y el servicio
    # arranco con la ultima copia buena del volumen: funciona, pero el modelo
    # puede estar desactualizado y alguien tiene que enterarse.
    if echo "$respuesta_ml" | grep -q '"origen_modelo":"local-fallback'; then
      aviso_pendiente "El modelo se cargo desde la copia local de respaldo, no desde Object Storage"
      detalle "Revisar credenciales/policy de OCI: docker compose logs ml-service"
    fi
  else
    fallo "ml-service responde pero el modelo NO esta cargado"
    detalle "$respuesta_ml"
  fi
fi

if curl --silent --fail --max-time 10 "${BASE_API}/actuator/health" > /dev/null 2>&1; then
  api_accesible=1
  correcto "backend responde en ${BASE_API}"
else
  fallo "backend no responde en ${BASE_API}/actuator/health"
  detalle "Revisar: docker compose logs backend"
fi

# --- Decidir el modo ------------------------------------------------------
#
# Que el 8000 no responda solo es aceptable si el backend SI responde: esa
# combinacion es la topologia de produccion. Si no responde ninguno de los dos,
# el sistema esta caido y hay que decirlo.
if [ "$ml_accesible" -eq 1 ]; then
  MODO="completo"
elif [ "$api_accesible" -eq 1 ]; then
  MODO="produccion"
  omitido "ml-service no expone el puerto 8000 (correcto en produccion)"
  detalle "Las pruebas directas al modelo se omiten; se verifica via API publica."
else
  MODO="caido"
  fallo "ml-service no responde en ${BASE_ML}/health"
  detalle "Ni el ml-service ni el backend responden: el sistema no esta levantado."
fi

# --------------------------------------------------------------------------
titulo "Ejemplos de uso del brief"

# Construye el cuerpo JSON escapando las comillas del titulo y del texto.
cuerpo_json() {
  printf '{"titulo":%s,"texto":%s}' \
    "$(printf '%s' "$1" | sed 's/"/\\"/g; s/^/"/; s/$/"/')" \
    "$(printf '%s' "$2" | sed 's/"/\\"/g; s/^/"/; s/$/"/')"
}

# La API key solo hace falta cuando el equipo implemente AuthenticationFilter.
cabecera_auth=()
if [ -n "${TECHMIND_API_KEY:-}" ]; then
  cabecera_auth=(-H "X-API-Key: ${TECHMIND_API_KEY}")
fi

ENDPOINT_API="${BASE_API}/api/v1/contenidos"

# --- Ejemplo contra el ml-service (modo completo) -------------------------
probar_ejemplo_ml() {
  local nombre="$1" titulo_doc="$2" texto_doc="$3"
  local salida

  if salida=$(curl --silent --fail --max-time 15 \
        -X POST "${BASE_ML}/predict" \
        -H 'Content-Type: application/json' \
        -d "$(cuerpo_json "$titulo_doc" "$texto_doc")" 2>/dev/null); then

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

# --- Ejemplo contra la API publica (modo produccion) ----------------------
#
# Mismo tratamiento de codigos que la seccion de integracion: un 404 significa
# que el endpoint todavia no existe, y eso no es un fallo de infraestructura.
probar_ejemplo_api() {
  local nombre="$1" titulo_doc="$2" texto_doc="$3"

  local archivo codigo salida
  archivo=$(mktemp)
  codigo=$(curl --silent --max-time 20 \
    --output "$archivo" --write-out '%{http_code}' \
    -X POST "$ENDPOINT_API" \
    -H 'Content-Type: application/json' \
    ${cabecera_auth[@]+"${cabecera_auth[@]}"} \
    -d "$(cuerpo_json "$titulo_doc" "$texto_doc")" 2>/dev/null)
  salida=$(cat "$archivo")
  rm -f "$archivo"

  case "$codigo" in
    200|201)
      if echo "$salida" | grep -q '"categoria"' \
         && echo "$salida" | grep -q '"informacion_adicional"'; then
        correcto "$nombre (via API)"
        echo "$salida" | formatear | sed 's/^/       /'
      else
        fallo "$nombre - la API respondio ${codigo} con una forma inesperada"
        detalle "$salida"
      fi
      ;;
    404) aviso_pendiente "$nombre - el endpoint todavia no existe (404)" ;;
    401|403)
      aviso_pendiente "$nombre - requiere autenticacion (${codigo})"
      detalle "Reintenta con: TECHMIND_API_KEY=<clave> $0 ${HOST}"
      ;;
    *)
      fallo "$nombre - la API devolvio ${codigo}"
      detalle "$salida"
      ;;
  esac
}

probar_ejemplo() {
  if [ "$MODO" = "completo" ]; then
    probar_ejemplo_ml "$@"
  elif [ "$MODO" = "produccion" ]; then
    probar_ejemplo_api "$@"
  else
    fallo "$1 - no se puede probar, el sistema no responde"
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
# En modo produccion los ejemplos de arriba YA recorrieron este camino, asi que
# repetirlo solo duplicaria la salida.
if [ "$MODO" = "completo" ]; then
  titulo "Integracion extremo a extremo (backend -> ml-service)"

  # Esta es la comprobacion que detecta el fallo de integracion mas probable del
  # proyecto: que el backend llame a una ruta del servicio de inferencia distinta
  # de /predict. Si eso pasa, todo lo demas se ve perfecto —ambos contenedores
  # arrancan, los healthchecks pasan— y la API devuelve 500 solo cuando alguien la
  # usa de verdad.
  #
  # Se distingue entre "todavia no implementado" y "implementado pero roto":
  #   404  -> el endpoint aun no existe. Se informa, NO se falla.
  #   2xx  -> funciona. Se valida la forma de la respuesta.
  #   5xx  -> el endpoint existe pero la integracion esta rota. FALLA.

  cuerpo_prueba=$(cuerpo_json \
    "Introduccion a Spring Boot" \
    "En este contenido se presentan los conceptos basicos para la creacion de APIs REST utilizando Java y Spring Boot.")

  archivo_respuesta=$(mktemp)
  codigo_api=$(curl --silent --max-time 20 \
    --output "$archivo_respuesta" --write-out '%{http_code}' \
    -X POST "$ENDPOINT_API" \
    -H 'Content-Type: application/json' \
    ${cabecera_auth[@]+"${cabecera_auth[@]}"} \
    -d "$cuerpo_prueba" 2>/dev/null)

  respuesta_api=$(cat "$archivo_respuesta")
  rm -f "$archivo_respuesta"

  case "$codigo_api" in
    200|201)
      if echo "$respuesta_api" | grep -q '"categoria"' \
         && echo "$respuesta_api" | grep -q '"informacion_adicional"'; then
        correcto "POST /api/v1/contenidos responde con la forma correcta"
        echo "$respuesta_api" | formatear | sed 's/^/       /'
      else
        fallo "POST /api/v1/contenidos devolvio ${codigo_api} pero con una forma inesperada"
        detalle "Se esperaban las claves: categoria, probabilidad, informacion_adicional"
        detalle "$respuesta_api"
      fi
      ;;
    404)
      aviso_pendiente "POST /api/v1/contenidos todavia no existe (404)"
      detalle "Normal mientras el equipo de backend no implemente ContenidoController."
      detalle "El resto de la infraestructura no depende de esto."
      ;;
    401|403)
      aviso_pendiente "POST /api/v1/contenidos requiere autenticacion (${codigo_api})"
      detalle "Reintenta exportando la clave: TECHMIND_API_KEY=<clave> $0 ${HOST}"
      ;;
    5*)
      fallo "POST /api/v1/contenidos devolvio ${codigo_api}: el endpoint existe pero la integracion esta rota"
      detalle "Causa mas probable: ModeloInferenciaClient no esta llamando a POST /predict."
      detalle "Debe usar la URL de INFERENCE_SERVICE_URL y la ruta /predict, no otra."
      detalle "$respuesta_api"
      detalle "Revisar tambien: docker compose logs backend"
      ;;
    000)
      fallo "El backend no acepto la conexion en ${ENDPOINT_API}"
      ;;
    *)
      fallo "POST /api/v1/contenidos devolvio un codigo inesperado: ${codigo_api}"
      detalle "$respuesta_api"
      ;;
  esac
fi

# --------------------------------------------------------------------------
titulo "Validacion de entradas"

if [ "$MODO" = "completo" ]; then
  codigo=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 \
    -X POST "${BASE_ML}/predict" \
    -H 'Content-Type: application/json' \
    -d '{"titulo":"Sin texto"}' 2>/dev/null)

  if [ "$codigo" = "422" ]; then
    correcto "Un cuerpo incompleto devuelve 422 (y no 500)"
  else
    fallo "Un cuerpo incompleto devolvio ${codigo}, se esperaba 422"
  fi
else
  omitido "Validacion directa del ml-service (requiere el puerto 8000)"
fi

# --------------------------------------------------------------------------
printf "\n"
printf "${GRIS}Modo: %s | Host: %s${FIN}\n" "$MODO" "$HOST"

if [ "$pendientes" -gt 0 ]; then
  printf "${AMARILLO}%d comprobacion(es) pendientes (piezas aun no implementadas).${FIN}\n" "$pendientes"
fi

if [ "$fallos" -eq 0 ]; then
  printf "${VERDE}Todas las comprobaciones pasaron.${FIN}\n\n"
  exit 0
else
  printf "${ROJO}%d comprobacion(es) fallaron.${FIN}\n\n" "$fallos"
  exit 1
fi
