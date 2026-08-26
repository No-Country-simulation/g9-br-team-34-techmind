# S5-01 — Catálogo de mensajes de error

**Implementado en:**
- Backend: `ErrorResponseDTO` (campos `mensajeUsuario` y `sugerencia`) y `GlobalExceptionHandler`
- Frontend: `frontend/index.html`, objeto `CATALOGO_ERRORES`

**Consumido por:** S5-02 (componente de error)

## Dónde vive cada texto

El backend es la fuente principal: cada handler emite un `mensajeUsuario` y, cuando hay
algo accionable, una `sugerencia`. El catálogo del frontend cubre lo que el backend no
puede responder —una petición que nunca llegó— y aporta el título y el tipo de cada error.

Cuando llega `mensajeUsuario`, ese texto gana sobre el detalle del catálogo: el backend
sabe qué falló con más precisión que una tabla genérica.

| Origen | Aporta |
|---|---|
| Backend (`ErrorResponseDTO`) | `mensajeUsuario`, `sugerencia` |
| Catálogo (frontend) | título, tipo (usuario/sistema), acción, y detalle de respaldo |

## Separación de destinatarios (S5-01)

`ErrorResponseDTO` lleva tres textos con públicos distintos:

- `mensajeUsuario` — qué pasó, en lenguaje llano. Es lo que la interfaz muestra.
- `sugerencia` — qué puede hacer. Nulo cuando no hay nada accionable.
- `message` — descripción técnica, sólo para registros.

El handler del 500 emite un `message` fijo en lugar del `getMessage()` de la excepción:
ese texto puede contener rutas de archivos, nombres de tablas o direcciones internas. La
traza completa queda en el registro del servidor.

## Criterio de redacción

Cada mensaje sigue tres reglas:

1. **Dice qué pasó, no qué falló por dentro.** "El servicio que clasifica los
   contenidos no está respondiendo" en lugar de "ModeloServiceException".
2. **Dice qué puede hacer quien lo lee.** Un error sin salida deja a la persona
   mirando la pantalla.
3. **No culpa a quien lo lee.** "El texto necesita al menos 20 caracteres", no
   "Ingresaste mal el texto".

Cuando el backend manda un mensaje propio en `ErrorResponseDTO.message` **ese
gana**, porque es más específico que cualquier texto genérico del catálogo. El
catálogo cubre el caso en que no llega mensaje, o en que el que llega es técnico.

## Tabla

| Clave | Tipo | HTTP | Origen en el backend | Título | Detalle | Acción |
|---|---|---|---|---|---|---|
| `sin_conexion` | sistema | — | `fetch` falla | No se pudo conectar | El navegador no pudo alcanzar la API. Puede estar apagada, o el origen no está permitido en CORS. | Reintentar |
| `datos_invalidos` | usuario | 400 | `MethodArgumentNotValid`, `ValidacionException` | Revisá los datos | Alguno de los campos no cumple lo que la API espera. | — |
| `id_invalido` | usuario | 400 | `MethodArgumentTypeMismatch` | Identificador con formato inválido | El id de la dirección no tiene forma de UUID. | Volver al inicio |
| `archivo_invalido` | usuario | 400 | `MultipartException`, `HttpMediaTypeNotSupported` | No se pudo leer el archivo | El archivo no llegó completo o el formato no está soportado. Se aceptan PDF y DOCX. | — |
| `no_encontrado` | usuario | 404 | `ContenidoNoEncontradoException`, `NoResourceFound` | No encontramos ese contenido | Puede haber sido eliminado, o el enlace apunta a algo que ya no existe. | Volver al inicio |
| `archivo_muy_grande` | usuario | 413 | `MaxUploadSizeExceeded` | El archivo pesa demasiado | Supera el tamaño máximo que acepta el servidor. | — |
| `no_procesable` | usuario | 422 | `ContenidoNoProcesable`, `ProcesamientoException` | No pudimos analizar este contenido | El texto llegó bien pero el modelo no encontró suficiente contenido útil. Suele pasar con textos muy cortos o repetitivos. | — |
| `extraccion_fallida` | usuario | 422 | `ExtraccionException` | No pudimos extraer el texto | El archivo o la página no tienen texto legible. Los PDF escaneados necesitan OCR, que todavía no está. | — |
| `servicio_caido` | sistema | 503 | `ModeloServiceException` | El clasificador no está disponible | El servicio que analiza los contenidos no responde. Es temporal. | Reintentar |
| `error_interno` | sistema | 500 | `Exception` | Algo salió mal de nuestro lado | Ocurrió un error inesperado. Ya quedó registrado. | Reintentar |
| `desconocido` | sistema | otros | — | No pudimos completar la operación | Ocurrió algo que no esperábamos. | Reintentar |

## Cómo se resuelve un error

```
¿el fetch falló?            → sin_conexion
¿el backend mandó message?  → se usa ese texto como detalle
¿el status está en la tabla? → título y acción del catálogo
si no                        → desconocido
```

## Distinción visual (S5-02)

El tipo no es sólo dato: cambia cómo se ve el aviso.

- **usuario** — ámbar y triángulo. Es algo corregible: un dato mal cargado, un archivo
  demasiado grande. No hay nada roto.
- **sistema** — rojo y círculo. Es un fallo del que quien lee no es responsable y no puede
  resolver: el servicio caído, un error interno.

Se distinguen por color **y** por forma del icono, para que la diferencia siga siendo
legible sin percibir color.

## Nota sobre el 400

`400` cubre tres situaciones distintas (campos inválidos, id malformado, archivo
mal enviado) y el status por sí solo no alcanza para distinguirlas. Se resuelve
pasando una clave explícita desde el punto donde se llama a la API — quien
invoca sabe qué estaba haciendo, la capa de errores no.
