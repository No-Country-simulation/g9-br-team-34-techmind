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

| # | Métrica | Cómo se calcula | Por qué entra |
|---|---|---|---|
| M1 | Total de contenidos | `count(*)` | Cifra de cabecera del dashboard. |
| M2 | Total de categorías activas | `count(distinct categoria)` | Muestra la amplitud del árbol. |
| M3 | Confianza media global | `avg(probabilidad)` | Responde "¿el modelo está seguro de lo que hace?". |
| M4 | Confianza media **por categoría** | `avg` agrupado | Revela en qué temas el modelo flaquea. Es la métrica más accionable para Ciencia de Datos. |
| M5 | Distribución de confianza en tramos | `count` por rangos (<50, 50-70, 70-85, ≥85) | Un promedio de 0,80 puede esconder muchos casos malos; el histograma lo expone. |
| M6 | Contenidos por día | `count` agrupado por fecha | Serie temporal: muestra que la base crece. |
| M7 | Top de palabras clave | `count` sobre la tabla de la colección, ordenado | El vocabulario dominante del repositorio. |
| M8 | Longitud media del texto | `avg(length(texto))` | Contexto para interpretar el resto. |
| M9 | Promedio de palabras clave por contenido | derivado de M1 y el total de filas de la colección | Densidad de extracción del modelo. |

### Se descartan

| Métrica | Por qué no |
|---|---|
| Precisión / recall del clasificador | Requiere etiquetas verdaderas. La base guarda lo que el modelo predijo, no lo correcto: medirlo contra sí mismo daría 100 % siempre. |
| Contenidos por usuario | No hay usuarios todavía (llega con S5-21). |
| Métricas de uso: vistas, búsquedas, clics | No se registra ningún evento. Necesitaría una tabla de auditoría; es un ticket propio, no una agregación. |
| Densidad de relaciones del grafo | Calculable, pero `findIdsRelacionados` corre por contenido: obtener el total exigiría N consultas. Cara y de valor bajo frente al resto. |
| Tendencia o proyección de crecimiento | Con pocos días de datos, cualquier proyección es ruido presentado como información. |

## Advertencias de implementación

1. **`length(texto)` en JPQL** se traduce a la función SQL de longitud, soportada por
   H2 y Postgres. Sobrevive a la migración de S5-18.
2. **Agrupar por fecha:** `fechaProcesamiento` es un `Instant`. Truncar a día con
   funciones nativas ata la consulta al motor. M6 se resuelve trayendo las fechas y
   agrupando en Java: son pocas filas y el código no depende de la base.
3. **Categorías duplicadas:** el modelo devuelve la misma categoría con distinta caja
   ("Backend" y "backend") y se persisten como dos. Toda agregación por categoría las
   cuenta separadas. El frontend lo normaliza al mostrar, pero **la causa sigue abierta**
   y conviene resolverla en el backend o en el modelo.
4. **Base vacía:** `avg` sobre cero filas devuelve `null`, no `0`. Los DTOs usan
   envoltorios y el servicio normaliza, para que el dashboard nunca reciba `null`.

## Endpoint resultante

Una sola llamada devuelve todo el tablero:

```
GET /api/v1/metricas
```

Se elige un endpoint único y no uno por métrica porque el dashboard las muestra
juntas: separarlas obligaría a seis peticiones para pintar una pantalla.
