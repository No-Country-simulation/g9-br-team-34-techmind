#!/usr/bin/env bash
# Batería de pruebas de aceptación end-to-end del MVP (TM-052).
#
# Ejecuta los 37 casos contra la API real (localhost:8080) con el servicio de
# inferencia real (localhost:8000). Cada caso vuelca request -> HTTP -> body.
#
# Uso:
#   scripts/aceptacion/runner.sh                    # asume 8080 y 8000 locales
#   BASE_URL=... scripts/aceptacion/runner.sh       # apunta a otro host
#
# Los fixtures grandes (lote_grande.csv > 5 MB y lote_muchas_filas.csv > 100
# filas) se generan al vuelo para no inflar el repositorio.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE="${BASE_URL:-http://127.0.0.1:8080/api/v1/contenidos}"
OUT="${OUT:-/tmp/aceptacion-evidencia.txt}"
: > "$OUT"

# --- Fixtures calculados: se generan si no existen --------------------------
if [ ! -f "$DIR/lote_grande.csv" ]; then
  python3 - "$DIR/lote_grande.csv" <<'PY'
import sys
with open(sys.argv[1], 'w', encoding='utf-8') as f:
    f.write('titulo,texto\n')
    for i in range(5):
        f.write(f'"Titulo {i}","' + ('x' * 1_500_000) + '"\n')
PY
fi

if [ ! -f "$DIR/lote_muchas_filas.csv" ]; then
  python3 - "$DIR/lote_muchas_filas.csv" <<'PY'
import sys
with open(sys.argv[1], 'w', encoding='utf-8') as f:
    f.write('titulo,texto\n')
    for i in range(130):
        f.write(f'"Titulo fila {i}","Este es el texto de la fila numero {i} con la longitud suficiente para validar el procesamiento por lote de contenidos."\n')
PY
fi

run() {
  local num="$1" desc="$2" file="$3"
  echo "====================" >> "$OUT"
  echo "CASO $num — $desc" >> "$OUT"
  echo "REQ: ${METHOD:-GET} ${URL}" >> "$OUT"
  local probe
  if [ -n "$file" ]; then
    probe=$(curl -s --max-time 40 -w "\n----HTTP:%{http_code}----\n" -X "${METHOD:-POST}" "$URL" -F "$file" 2>&1)
  else
    probe=$(curl -s --max-time 40 -w "\n----HTTP:%{http_code}----\n" -X "${METHOD:-GET}" "$URL" ${H[@]:+"${H[@]}"} -d "${BODY:-}" 2>&1)
  fi
  echo "RES:" >> "$OUT"
  echo "$probe" >> "$OUT"
  echo "" >> "$OUT"
}

# ---------- A. POST /api/v1/contenidos ----------
H=( -H "Content-Type: application/json" ); URL="$BASE"; METHOD=POST
BODY='{"titulo":"Introduccion a Spring Boot","texto":"En este contenido se presentan los conceptos basicos para la creacion de APIs REST utilizando Java y Spring Boot para construir servicios web eficientes."}'
run 1 "POST /contenidos exito (Spring Boot)" ""

BODY='{"titulo":"Tutorial de Docker","texto":"Tutorial practico de contenedorizacion de aplicaciones con Docker: Dockerfile, imagenes, volumenes, redes y orquestacion basica con docker compose."}'
run 2 "POST /contenidos exito (DevOps/Docker)" ""

BODY='{"titulo":"Analisis exploratorio de datos con Pandas","texto":"Carga, limpieza y exploracion de conjuntos de datos tabulares usando Pandas: tratamiento de valores nulos, agrupaciones y metricas descriptivas del dataset."}'
run 3 "POST /contenidos exito (Ciencia de Datos)" ""

BODY='{"titulo":"   ","texto":"Este texto es lo bastante largo como para pasar la validacion de longitud minima del sistema."}'
run 4 "POST /contenidos titulo vacio -> 400" ""

BODY=$(python3 -c "print('{\"titulo\":\"'+'a'*201+'\",\"texto\":\"Este texto supera la validacion y deberia ser rechazado por el sistema de pruebas de aceptacion.\"}')")
run 5 "POST /contenidos titulo >200 chars -> 400" ""

BODY='{"titulo":"Texto corto","texto":"muy corto"}'
run 6 "POST /contenidos texto <20 chars -> 400" ""

BODY=$(python3 -c "print('{\"titulo\":\"Texto largo\",\"texto\":\"'+'y'*10001+'\"}')")
run 7 "POST /contenidos texto >10000 chars -> 400" ""

H=( -H "Content-Type: text/plain" ); BODY='{"titulo":"x","texto":"contenido invalido con contenido type incorrecto para el endpoint."}'
run 8 "POST /contenidos content-type incorrecto -> 400" ""
H=( -H "Content-Type: application/json" )

# ---------- B. GET /api/v1/contenidos (listado/filtro/busqueda/paginacion) ----------
URL="$BASE"; METHOD=GET
run 9 "GET /contenidos listado" ""
URL="$BASE?page=0&size=2"; run 10 "GET /contenidos paginado page=0&size=2" ""
URL="$BASE?size=51"; run 11 "GET /contenidos size>50 -> 400" ""
URL="$BASE?size=abc"; run 12 "GET /contenidos size no numerico -> 400" ""
URL="$BASE?page=-1"; run 13 "GET /contenidos page negativo -> 400" ""
URL="$BASE?categoria=Backend"; run 14 "GET /contenidos filtro categoria=Backend" ""
URL="$BASE?palabraClave=spring"; run 15 "GET /contenidos busqueda palabraClave=spring" ""
URL="$BASE?categoria=Backend&palabraClave=spring&page=0&size=20&sort=titulo,asc"; run 16 "GET /contenidos combinado categoria+palabraClave+paginacion+sort" ""
URL="$BASE?sort=probabilidad,desc"; run 17 "GET /contenidos sort no permitido -> 400" ""
URL="$BASE?sort=titulo,pepe"; run 18 "GET /contenidos sort direccion invalida -> 400" ""

# ---------- C. GET /api/v1/contenidos/{id} ----------
ID=$(curl -s -X POST "$BASE" -H "Content-Type: application/json" -d '{"titulo":"Contenido para consulta","texto":"Contenido tecnico creado para verificar la consulta individual por identificador en el endpoint de recuperacion."}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
URL="$BASE/$ID"; run 19 "GET /contenidos/{id} exito" ""
URL="$BASE/00000000-0000-0000-0000-000000000000"; run 20 "GET /contenidos/{id} inexistente -> 404" ""
URL="$BASE/no-es-uuid"; run 21 "GET /contenidos/{id} id invalido -> 400" ""

# ---------- D. GET /api/v1/contenidos/{id}/relacionados ----------
URL="$BASE/$ID/relacionados"; run 22 "GET /contenidos/{id}/relacionados" ""
URL="$BASE/$ID/relacionados?limite=3"; run 23 "GET /contenidos/{id}/relacionados limite=3" ""
URL="$BASE/00000000-0000-0000-0000-000000000000/relacionados"; run 24 "GET /relacionados base inexistente -> 404" ""
URL="$BASE/no-es-uuid/relacionados"; run 25 "GET /relacionados id invalido -> 400" ""

# ---------- E. GET /api/v1/categorias ----------
URL="http://127.0.0.1:8080/api/v1/categorias"; run 26 "GET /categorias exito" ""

# ---------- F. POST /api/v1/contenidos/lote ----------
URL="$BASE/lote"; METHOD=POST
run 27 "POST /lote CSV valido" "archivo=@$DIR/lote_valido.csv"
run 28 "POST /lote CSV con fila invalida (error por fila)" "archivo=@$DIR/lote_error_parcial.csv"
run 29 "POST /lote sin archivo -> 400" ""
run 30 "POST /lote archivo no .csv -> 400" "archivo=@$DIR/lote_extension.txt"
run 31 "POST /lote CSV header invalido -> 400" "archivo=@$DIR/lote_sin_header.csv"
run 32 "POST /lote CSV mas de 100 filas -> 400" "archivo=@$DIR/lote_muchas_filas.csv"
run 33 "POST /lote archivo >5MB -> 413" "archivo=@$DIR/lote_grande.csv"

# ---------- G. DELETE /api/v1/contenidos/{id} ----------
DID=$(curl -s -X POST "$BASE" -H "Content-Type: application/json" -d '{"titulo":"Contenido a eliminar","texto":"Contenido tecnico creado unicamente para verificar la operacion de borrado por identificador."}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
URL="$BASE/$DID"; METHOD=DELETE
run 34 "DELETE /contenidos/{id} exito -> 204" ""
URL="$BASE/$DID"; run 35 "DELETE /contenidos/{id} ya borrado -> 404" ""
URL="$BASE/no-es-uuid"; run 36 "DELETE /contenidos/{id} id invalido -> 400" ""

# ---------- H. /actuator/health ----------
URL="http://127.0.0.1:8080/actuator/health"; METHOD=GET
run 37 "GET /actuator/health -> 200 UP" ""

echo "Evidencia en: $OUT"
echo "Cantidad de respuestas: $(grep -c -- "----HTTP:[0-9]*----" "$OUT")"