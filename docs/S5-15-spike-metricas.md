# S5-15 — Spike: métricas derivables de los datos existentes

**Estado:** cerrado · **Salida:** define el alcance de S5-14 (endpoints) y S5-16 (dashboard)

## Pregunta del spike

¿Qué métricas se pueden calcular **sin agregar campos nuevos ni pedir datos a nadie**,
usando sólo lo que hoy persiste `ContenidoAnalizado`?

## Materia prima disponible

| Campo | Tipo | Sirve para |
|---|---|---|
| `id` | UUID | conteos |
| `titulo` | String (≤200) | — |
| `texto` | String (≤10 000) | longitud del contenido |
| `categoria` | String | distribución temática |
| `probabilidad` | Double [0,1] | confianza del modelo |
| `palabrasClave` | List\<String\> (tabla aparte) | vocabulario, densidad |
| `fechaProcesamiento` | Instant | series de tiempo |

Ya existe `contarPorCategoria()` con la proyección `ConteoCategoria`, usada por
`GET /api/v1/categorias`.

## Métricas evaluadas

### Se implementan (S5-14)

El esfuerzo se estima en puntos: **1** = una consulta directa; **2** = consulta con
agrupación o varias llamadas; **3** = requiere lógica en Java además de la consulta.

| # | Métrica | Cómo se calcula | Esfuerzo | Por qué entra |
|---|---|---|---|---|
| M1 | Total de contenidos | `count(*)` | 1 | Cifra de cabecera del dashboard. |
| M2 | Total de categorías activas | `count(distinct categoria)` | 1 | Muestra la amplitud del árbol. |
| M3 | Confianza media global | `avg(probabilidad)` | 1 | Responde "¿el modelo está seguro de lo que hace?". |
| M4 | Confianza media **por categoría** | `avg` agrupado | 2 | Revela en qué temas el modelo flaquea. Es la métrica más accionable para Ciencia de Datos. |
| M5 | Distribución de confianza en tramos | `count` por rangos (<50, 50-70, 70-85, ≥85) | 2 | Un promedio de 0,80 puede esconder muchos casos malos; el histograma lo expone. |
| M6 | Contenidos por día | fechas crudas, agrupadas en Java | 3 | Serie temporal: muestra que la base crece. |
| M7 | Top de palabras clave | `count` sobre la colección, agrupado y ordenado | 2 | El vocabulario dominante del repositorio. |
| M8 | Longitud media del texto | `avg(length(texto))` | 1 | Contexto para interpretar el resto. |
| M9 | Promedio de palabras clave por contenido | derivado de M1 y el total de filas de la colección | 1 | Densidad de extracción del modelo. |
| M10 | Palabras clave únicas | `count(distinct lower(p))` | 1 | Cuántos términos distintos cubre el repositorio, distinto del ranking de M7. |
| M11 | Total de relaciones | lectura única + cruce en Java | 3 | Cuántos pares de contenidos quedaron conectados. Es la medida de cuánto valor agregó el grafo. |

**Total: 18 puntos.**

### Se descartan

| Métrica | Por qué no |
|---|---|
| Precisión / recall del clasificador | Requiere etiquetas verdaderas. La base guarda lo que el modelo predijo, no lo correcto: medirlo contra sí mismo daría 100 % siempre. |
| Contenidos por usuario | No hay usuarios todavía (llega con S5-21). |
| Métricas de uso: vistas, búsquedas, clics | No se registra ningún evento. Necesitaría una tabla de auditoría; es un ticket propio, no una agregación. |
| Documentos exitosos vs. con error | **No es derivable con lo que se persiste.** `ContenidoAnalizado` sólo se guarda cuando el análisis salió bien: no hay campo de estado ni registro de los intentos fallidos. Medirlo exige agregar un campo o una tabla de auditoría, y eso es un ticket de modelo de datos, no una agregación. |
| Tendencia o proyección de crecimiento | Con pocos días de datos, cualquier proyección es ruido presentado como información. |

### Corrección posterior a la primera versión del spike

La primera versión descartó **total de relaciones** con el argumento de que obtenerlo
exigía N llamadas a `findIdsRelacionados`. El argumento estaba mal: el criterio de
relación (misma categoría y al menos una palabra clave compartida) se puede evaluar con
una sola lectura de los contenidos y su colección, cruzando en memoria. La métrica pasó a
implementarse como M11.

## Advertencias de implementación

1. **`length(texto)` en JPQL** se traduce a la función SQL de longitud, soportada por
   H2 y Postgres. Sobrevive a la migración de S5-18.
2. **Agrupar por fecha:** `fechaProcesamiento` es un `Instant`. Truncar a día con
   funciones nativas ata la consulta al motor. M6 se resuelve trayendo las fechas y
   agrupando en Java: son pocas filas y el código no depende de la base.
3. **Categorías duplicadas (sólo en entornos con mock):** `MockModeloInferenciaClient`
   devuelve `"Backend"` con mayúscula, mientras que el modelo real entrega todo en
   minúscula — los datos de entrenamiento están normalizados así. Una base que mezcla
   contenidos creados con el perfil `mock` y con el servicio real termina con dos
   categorías para lo mismo, y toda agregación por categoría las cuenta separadas.
   **Con el modelo real no ocurre.** Conviene alinear el mock para que no genere datos
   distintos a los de producción; el frontend normaliza igual, por defensa.
4. **Base vacía:** `avg` sobre cero filas devuelve `null`, no `0`. Los DTOs usan
   envoltorios y el servicio normaliza, para que el dashboard nunca reciba `null`.

5. **Costo de M11:** el cruce en memoria crece con el cuadrado de la cantidad de
   contenidos *dentro de cada categoría*. Con el volumen actual es despreciable, pero es
   el primer cálculo del tablero que conviene revisar si el repositorio creciera mucho.

## Endpoint resultante

Una sola llamada devuelve todo el tablero:

```
GET /api/v1/metricas
```

Se elige un endpoint único y no uno por métrica porque el dashboard las muestra
juntas: separarlas obligaría a seis peticiones para pintar una pantalla.
