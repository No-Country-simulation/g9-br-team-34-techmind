# S5-01 — Catálogo de mensajes de error

**Implementado en:** `frontend/index.html`, objeto `CATALOGO_ERRORES`
**Consumido por:** S5-02 (componente de error)

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

| Clave | HTTP | Origen en el backend | Título | Detalle | Acción |
|---|---|---|---|---|---|
| `sin_conexion` | — | `fetch` falla | No se pudo conectar | El navegador no pudo alcanzar la API. Puede estar apagada, o el origen no está permitido en CORS. | Reintentar |
| `datos_invalidos` | 400 | `MethodArgumentNotValid`, `ValidacionException` | Revisá los datos | Alguno de los campos no cumple lo que la API espera. | — |
| `id_invalido` | 400 | `MethodArgumentTypeMismatch` | Identificador con formato inválido | El id de la dirección no tiene forma de UUID. | Volver al inicio |
| `archivo_invalido` | 400 | `MultipartException`, `HttpMediaTypeNotSupported` | No se pudo leer el archivo | El archivo no llegó completo o el formato no está soportado. Se aceptan PDF y DOCX. | — |
| `no_encontrado` | 404 | `ContenidoNoEncontradoException`, `NoResourceFound` | No encontramos ese contenido | Puede haber sido eliminado, o el enlace apunta a algo que ya no existe. | Volver al inicio |
| `archivo_muy_grande` | 413 | `MaxUploadSizeExceeded` | El archivo pesa demasiado | Supera el tamaño máximo que acepta el servidor. | — |
| `no_procesable` | 422 | `ContenidoNoProcesable`, `ProcesamientoException` | No pudimos analizar este contenido | El texto llegó bien pero el modelo no encontró suficiente contenido útil. Suele pasar con textos muy cortos o repetitivos. | — |
| `extraccion_fallida` | 422 | `ExtraccionException` | No pudimos extraer el texto | El archivo o la página no tienen texto legible. Los PDF escaneados necesitan OCR, que todavía no está. | — |
| `servicio_caido` | 503 | `ModeloServiceException` | El clasificador no está disponible | El servicio que analiza los contenidos no responde. Es temporal. | Reintentar |
| `error_interno` | 500 | `Exception` | Algo salió mal de nuestro lado | Ocurrió un error inesperado. Ya quedó registrado. | Reintentar |
| `desconocido` | otros | — | No pudimos completar la operación | Ocurrió algo que no esperábamos. | Reintentar |

## Cómo se resuelve un error

```
¿el fetch falló?            → sin_conexion
¿el backend mandó message?  → se usa ese texto como detalle
¿el status está en la tabla? → título y acción del catálogo
si no                        → desconocido
```

## Nota sobre el 400

`400` cubre tres situaciones distintas (campos inválidos, id malformado, archivo
mal enviado) y el status por sí solo no alcanza para distinguirlas. Se resuelve
pasando una clave explícita desde el punto donde se llama a la API — quien
invoca sabe qué estaba haciendo, la capa de errores no.
